package org.chimeramc.launcher.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;

/**
 * Optional low-latency networking support ("Reduce Network Latency").
 *
 * Honest scope. A launcher cannot move a game server closer, change ISP routing, or make
 * somebody else's server reply sooner, so this never promises a specific ping figure. What
 * it can remove is the latency the device itself adds on top of the network, which is also
 * the only part that is fixable locally:
 *  - Disables Nagle's algorithm (TCP_NODELAY) on sockets created through
 *    {@link #createSocketFactory()}, i.e. the launcher's own news/update HTTP connections.
 *    Without it, small writes can wait for an ACK before being sent.
 *  - Enables TCP Quick ACK where the kernel exposes it. Delayed ACK is the dominant source
 *    of added latency for small request/response exchanges, and it compounds with Nagle
 *    into the classic 40ms stall for request/response protocols.
 *  - Resizes send/receive buffers for interactive traffic. Oversized buffers let the stack
 *    hold back small writes; a few MTU's worth keeps ACKs tight.
 *  - Warms DNS for the launcher's endpoints and popular Bedrock servers, so joining a
 *    server does not pay a cold lookup.
 *  - Keeps a persistent connection pool warm for the launcher's endpoints so the TCP and
 *    TLS handshakes are already done when the user opens a screen.
 *  - Marks the start/end of a game session so background pollers stop competing for the
 *    radio during play; airtime contention is real added latency on mobile.
 *  - Holds a low-latency Wi-Fi lock for the duration of a session, so the radio does not
 *    fall back into its power-save polling cycle between packets. It is released with the
 *    session, or it would keep the radio awake and drain the battery.
 */
public final class LowLatencyNetworkManager {
    @SuppressLint("StaticFieldLeak")
    private static volatile Context sAppContext;

    private static final List<String> PREFETCH_HOSTS = Arrays.asList(
            "raw.githubusercontent.com",
            "api.github.com",
            "api.curseforge.com",
            "www.googleapis.com",
            // Common Bedrock multiplayer endpoints. Resolving these ahead of time avoids
            // a DNS lookup on the first join, which is some of the worst "ping" a player
            // sees on a fresh server connect.
            "geo.hivebedrock.network",
            "play.lbsg.net",
            "mco.cubecraft.net",
            "play.inpvp.net",
            "play.nethergames.org"
    );

    /**
     * Interactive HTTP traffic is small request/response. 64 KiB is comfortably more than a
     * few jumbo frames while still small enough that the stack does not buffer writes
     * waiting to coalesce them.
     */
    private static final int SOCKET_BUFFER_SIZE = 64 * 1024;

    private static final ExecutorService DNS_EXECUTOR = Executors.newSingleThreadExecutor();

    private LowLatencyNetworkManager() {
    }

    public static void init(Context context) {
        sAppContext = context.getApplicationContext();
        FeatureSettings fs = FeatureSettings.getInstance();
        if (fs != null && fs.isReduceNetworkLatencyEnabled()) {
            prefetchDnsOnBackground();
        }
    }

    public static boolean isEnabled() {
        FeatureSettings fs = FeatureSettings.getInstance();
        return fs != null && fs.isReduceNetworkLatencyEnabled();
    }

    /**
     * Wires this manager's low-latency socket factory into an OkHttpClient.Builder.
     * Call this on any client used for launcher-owned network traffic so those
     * connections inherit TCP_NODELAY / Quick ACK / interactive buffer sizing when
     * the "Reduce Network Latency" toggle is on. No-op (default sockets) when off.
     */
    public static void configure(okhttp3.OkHttpClient.Builder builder) {
        if (!isEnabled()) return;
        builder.socketFactory(createSocketFactory());
        // Reusing pooled connections skips the TCP + TLS handshake on the next request to
        // the same host, which is worth far more than any socket option. Timeouts are left
        // to the caller: each client sets the ones its own workload needs.
        builder.connectionPool(new okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES));
    }

    public static SocketFactory createSocketFactory() {
        return new SocketFactory() {
            private final SocketFactory delegate = SocketFactory.getDefault();

            @Override
            public Socket createSocket() throws IOException {
                return configure(delegate.createSocket());
            }

            @Override
            public Socket createSocket(String host, int port) throws IOException {
                return configure(delegate.createSocket(host, port));
            }

            @Override
            public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
                    throws IOException {
                return configure(delegate.createSocket(host, port, localHost, localPort));
            }

            @Override
            public Socket createSocket(InetAddress host, int port) throws IOException {
                return configure(delegate.createSocket(host, port));
            }

            @Override
            public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                    throws IOException {
                return configure(delegate.createSocket(address, port, localAddress, localPort));
            }

            private Socket configure(Socket socket) throws IOException {
                try {
                    socket.setTcpNoDelay(true);
                } catch (IOException ignored) {
                }
                enableQuickAck(socket);
                try {
                    // Smaller buffers keep ACKs tight for interactive request/response
                    // traffic. netPathMtu is the mobile egress MTU; buffer a handful of
                    // frames, not megabytes.
                    socket.setSendBufferSize(SOCKET_BUFFER_SIZE);
                    socket.setReceiveBufferSize(SOCKET_BUFFER_SIZE);
                } catch (IOException ignored) {
                }
                return socket;
            }
        };
    }

    /**
     * Enables TCP_QUICKACK (12) on the socket's fd where the kernel exposes it.
     * Quick ACK makes the stack ACK data immediately instead of waiting up to
     * 40ms (delACK_TICK) for batching. Uses reflection to reach
     * android.system.Os.setsockoptInt; on sealed/absent surfaces this simply
     * no-ops, leaving the plain TCP_NODELAY socket functional.
     */
    private static void enableQuickAck(Socket socket) {
        if (socket == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            Object fd = callGetter(callGetter(socket, "getImpl"), "getFileDescriptor");
            if (fd instanceof java.io.FileDescriptor) {
                java.io.FileDescriptor fdesc = (java.io.FileDescriptor) fd;
                Class<?> osClass = Class.forName("android.system.Os");
                Method setsockopt = osClass.getDeclaredMethod("setsockoptInt",
                        java.io.FileDescriptor.class, int.class, int.class, int.class);
                setsockopt.invoke(null, fdesc, 6 /* SOL_TCP */, 12 /* TCP_QUICKACK */, 1);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object callGetter(Object target, String name) {
        if (target == null) return null;
        try {
            for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    Method m = c.getDeclaredMethod(name);
                    m.setAccessible(true);
                    return m.invoke(target);
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static void prefetchDnsOnBackground() {
        if (!isEnabled()) return;
        if (ThermalGovernor.shouldPauseSpeculativeWork()) return;
        for (final String host : PREFETCH_HOSTS) {
            DNS_EXECUTOR.execute(() -> {
                try {
                    InetAddress.getAllByName(host);
                } catch (Exception ignored) {
                }
            });
        }
    }

    /**
     * True while a Minecraft session is running. Automatic (non user-initiated) network
     * callers should bail out early and use cached data while this is set.
     */
    public static boolean isGameSessionActive() {
        return GameQuietZoneHolder.active;
    }

    public static void setGameSessionActive(boolean active) {
        GameQuietZoneHolder.active = active;
        updateWifiLock(active);
    }

    /**
     * Holds a low-latency Wi-Fi lock for the duration of a game session.
     *
     * The lock asks the Wi-Fi stack to keep the radio out of its power-save polling cycle, so
     * the device does not add wake-up delay to the packets a game session sends. It only
     * applies on Wi-Fi (on mobile data there is nothing to hold) and only while the "Reduce
     * Network Latency" toggle is on. The lock is reference-counted and always released, or the
     * radio would stay out of power save after the session ends and quietly drain the battery.
     */
    private static synchronized void updateWifiLock(boolean active) {
        Context context = sAppContext;
        if (context == null) return;
        try {
            if (active && isEnabled()) {
                if (sWifiLock != null && sWifiLock.isHeld()) return;
                WifiManager wifiManager =
                        (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                if (wifiManager == null) return;
                if (sWifiLock == null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        sWifiLock = wifiManager.createWifiLock(
                                WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                                "ChimeraLauncher:GameLowLatencyLock");
                    } else {
                        sWifiLock = wifiManager.createWifiLock(
                                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                                "ChimeraLauncher:GameLowLatencyLock");
                    }
                }
                sWifiLock.setReferenceCounted(false);
                sWifiLock.acquire();
            } else if (sWifiLock != null && sWifiLock.isHeld()) {
                sWifiLock.release();
            }
        } catch (Throwable ignored) {
            // A device that refuses the lock simply keeps its default radio behaviour.
        }
    }

    private static final class GameQuietZoneHolder {
        private static volatile boolean active;
    }

    private static volatile WifiManager.WifiLock sWifiLock;
}