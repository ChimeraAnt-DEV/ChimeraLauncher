package org.chimeramc.launcher.core.minecraft

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import org.chimeramc.launcher.core.versions.GameVersion
import org.levimc.launcher.util.NativeBridgeHelper
import org.levimc.launcher.util.NativeImageGuard
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile

class GamePackageManager private constructor(
    private val context: Context,
    private val version: GameVersion?,
    private val launchTrace: LaunchTrace?,
    private val progressListener: MinecraftRuntimePreparer.ProgressListener?
) {

    private val packageContext: Context
    private val assetManager: AssetManager
    private val nativeLibDir: String
    private val applicationInfo: ApplicationInfo
    private var launchAbi: String? = null

    private val knownPackages = arrayOf(MinecraftLauncher.MC_PACKAGE_NAME)

    private val requiredLibs = arrayOf(
        "libc++_shared.so",
        "libfmod.so",
        "libMediaDecoders_Android.so",
        "libminecraftpe.so",
    )

    private val optionalLibs = arrayOf(
        "libHttpClient.Android.so",
    )

    private val extractableLibs = requiredLibs + optionalLibs

    private val systemLoadedLibs = arrayOf(
        "libPlayFabMultiplayer.so",
        "libmaesdk.so",
        "libgxcore.so",
    )

    data class LibraryLoadResult(
        val name: String,
        val fileName: String,
        val source: String,
        val loaded: Boolean,
        val durationMs: Long,
        val detail: String? = null
    )

    init {
        report("GamePackageManager init started")
        val packageName = detectGamePackage() ?: throw IllegalStateException("Minecraft not found")
        report("Detected Minecraft package: $packageName")
        packageContext = context.createPackageContext(
            packageName,
            Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE
        )
        
        if (version != null && !version.isInstalled) {
            applicationInfo = MinecraftLauncher(context).createFakeApplicationInfo(version, MinecraftLauncher.MC_PACKAGE_NAME)
        } else {
            applicationInfo = packageContext.applicationInfo
        }
        // The entire native-library path (extraction + loading) must derive from the
        // version's own ABI, not the device's primary ABI, so 32-bit-only versions load
        // their 32-bit libraries consistently from the matching architecture folder.
        launchAbi = getDeviceAbi(collectApkFiles())
        nativeLibDir = MinecraftLauncher.getRuntimeLibAbiDir(
            context,
            MinecraftLauncher.getStorageProfileId(version),
            launchAbi!!
        ).absolutePath
        File(nativeLibDir).mkdirs()
        if (version != null && !version.isInstalled) {
            // Keep the fake ApplicationInfo aligned with the version-aware native lib dir.
            applicationInfo.nativeLibraryDir = nativeLibDir
        }

        extractLibraries()
        report("Creating AssetManager")
        assetManager = createAssetManager()
        report("AssetManager ready")
        setupSecurityProvider()
        report("GamePackageManager init finished")
    }

    private fun detectGamePackage(): String? {
        return knownPackages.firstOrNull { isPackageInstalled(it) }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** ABI (e.g. arm64-v8a / armeabi-v7a) this launch uses for extracting and loading native libraries. */
    fun getLaunchAbi(): String {
        return launchAbi ?: getDeviceAbi(collectApkFiles()).also { launchAbi = it }
    }

    /**
     * Verifies the version's declared ABI can actually load in this launcher's process.
     *
     * This is about which build of the launcher is installed, not about what the device can
     * do. The APK's {@code ndk.abiFilters} decide whether it ships an {@code arm64-v8a} or an
     * {@code armeabi-v7a} set of native libraries; Android then fixes the process to a single
     * bitness at install time. Game libraries must match that bitness, so both directions of
     * mismatch are fatal: 32-bit game libs cannot load in a 64-bit process, and 64-bit game
     * libs cannot load in a 32-bit process.
     *
     * {@code android:use32bitAbi} is an installer preference for an APK that ships both ABIs.
     * It cannot make a running process change bitness, so it is not a lever here.
     */
    fun requireLauncherSupportsVersion(version: GameVersion?): Boolean {
        return abiCompatibility(version) != AbiCompatibility.INCOMPATIBLE
    }

    /** How a version's declared ABI relates to the bitness of this launcher process. */
    enum class AbiCompatibility {
        /** No ABI declared, or the declared ABI matches this process. */
        OK,
        /** Declared ABI and process bitness disagree, so the game libraries cannot load. */
        INCOMPATIBLE,
    }

    fun abiCompatibility(version: GameVersion?): AbiCompatibility {
        return if (AbiBitness.compatibleWith(version?.abiList, processIs64Bit())) {
            AbiCompatibility.OK
        } else {
            AbiCompatibility.INCOMPATIBLE
        }
    }

    fun abiMismatchMessage(version: GameVersion?): String? {
        val declared = version?.abiList
        if (declared.isNullOrBlank()) return null
        if (abiCompatibility(version) != AbiCompatibility.INCOMPATIBLE) return null
        return if (AbiBitness.is32BitAbi(declared)) {
            buildString {
                append("This Minecraft version ships only $declared (32-bit), but Chimera Launcher is ")
                append("running as a 64-bit process, and 32-bit game libraries cannot load in a 64-bit process. ")
                if (deviceSupports32Bit()) {
                    append("This device can run 32-bit code, so installing the 32-bit build of Chimera Launcher ")
                    append("will run this version. Android fixes an app's bitness when it is installed, so the ")
                    append("launcher cannot become 32-bit just for this version.")
                } else {
                    append("This device has no 32-bit support at all, so this version cannot run here on any ")
                    append("launcher build. Use a 64-bit build of this Minecraft version.")
                }
            }
        } else {
            buildString {
                append("This Minecraft version ships only $declared (64-bit), but Chimera Launcher is ")
                append("running as a 32-bit process, and 64-bit game libraries cannot load in a 32-bit ")
                append("process. Install the 64-bit build of Chimera Launcher to run this version.")
            }
        }
    }

    /**
     * Whether the running process can load 64-bit libraries. Read from the process so it
     * reflects the build that was actually installed, not what the device could theoretically run.
     */
    private fun processIs64Bit(): Boolean = try {
        android.os.Process.is64Bit()
    } catch (_: Throwable) {
        // is64Bit() is API 23+; minSdk here is 28, so this branch is defensive only.
        Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
    }

    /** True when this device can execute 32-bit ARM code at all (has armeabi-v7a). */
    fun deviceSupports32Bit(): Boolean = Build.SUPPORTED_32_BIT_ABIS.contains("armeabi-v7a")

    private fun getDeviceAbi(apkFiles: List<File> = emptyList()): String {
        resolveAbiFromApks(apkFiles)?.let { return it }
        if (version != null && "armeabi-v7a".equals(version.abiList)) {

            return if (Build.SUPPORTED_32_BIT_ABIS.contains("armeabi-v7a")) {
                "armeabi-v7a"
            } else {
                Build.SUPPORTED_64_BIT_ABIS.firstOrNull {
                    it.contains("arm64-v8a") || it.contains("x86_64")
                } ?: Build.SUPPORTED_32_BIT_ABIS.firstOrNull {
                    it.contains("armeabi-v7a") || it.contains("x86")
                } ?: (Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a")
            }
        }
        return Build.SUPPORTED_64_BIT_ABIS.firstOrNull {
            it.contains("arm64-v8a") || it.contains("x86_64")
        } ?: Build.SUPPORTED_32_BIT_ABIS.firstOrNull {
            it.contains("armeabi-v7a") || it.contains("x86")
        } ?: (Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a")
    }

    private fun resolveAbiFromApks(apkFiles: List<File>): String? {

        val candidates = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        val present = mutableMapOf<String, Boolean>()
        candidates.forEach { present[it] = false }
        for (file in apkFiles) {
            if (!file.isFile) continue
            try {
                ZipFile(file).use { zip ->
                    for (abi in candidates) {
                        if (!present[abi]!! && zip.getEntry("lib/$abi/libminecraftpe.so") != null) {
                            present[abi] = true
                        }
                    }
                }
            } catch (e: Exception) {
                mirrorLogcat('W', "Failed to inspect ABI of ${file.name}: ${e.message}")
            }
        }
        if (present.none { it.value }) return null
        val explicit = version?.abiList?.takeIf { present[it] == true }
        if (explicit != null) return explicit
        for (pref in candidates) {
            if (present[pref] == true) return pref
        }
        return null
    }

    private fun extractLibraries() {
        report("Preparing Minecraft library cache")
        val outputDir = File(nativeLibDir)
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val apkFiles = collectApkFiles()
        val manifestString = buildExtractionManifest(apkFiles)
        val manifestFile = File(outputDir, ".extraction_manifest")
        val markerMatches = try {
            manifestFile.isFile && manifestFile.readText() == manifestString
        } catch (_: Exception) {
            false
        }
        val cacheRequiredLibs = getCacheRequiredLibs()
        var allPresent = markerMatches
        if (allPresent) {
            for (lib in cacheRequiredLibs) {
                val file = File(outputDir, lib)
                if (!file.exists() || file.length() == 0L) {
                    allPresent = false
                    break
                }
            }
        }
        
        if (allPresent) {
            report("Minecraft library cache hit: ${outputDir.absolutePath}")
            for (lib in extractableLibs) {
                try {
                    val file = File(outputDir, lib)
                    if (file.exists()) {
                        ensureReadOnly(file)
                    }
                } catch (e: Exception) {
                    mirrorLogcat('W', "Failed ensureReadOnly: ${e.message}")
                }
            }
            return
        }
        report("Minecraft library cache miss: extracting libraries")

        if (version != null && !version.isInstalled) {
            val apkPaths = apkFiles.map { it.absolutePath }
            apkPaths.forEach { extractFromApk(it, outputDir, getDeviceAbi(apkFiles)) }
            val missingLibs = cacheRequiredLibs.filter { lib -> !File(outputDir, lib).exists() }
            if (missingLibs.isNotEmpty()) {
                throw IllegalStateException(
                    "Required native libraries for ABI ${getDeviceAbi(apkFiles)} are missing from this version's APK/splits."
                )
            }
        } else {
            val appInfo = packageContext.applicationInfo
            if (File(appInfo.nativeLibraryDir).exists()) {
                copyFromNativeDir(appInfo.nativeLibraryDir, outputDir)
            }
            val apkPaths = apkFiles.map { it.absolutePath }
            apkPaths.forEach { extractFromApk(it, outputDir, getDeviceAbi(apkFiles)) }
        }
        verifyLibraries(outputDir)

        if (cacheRequiredLibs.all { File(outputDir, it).let { f -> f.exists() && f.length() > 0 } }) {
            try {
                manifestFile.writeText(manifestString)
                File(outputDir, ".extraction_marker").delete()
                report("Minecraft library cache manifest written")
            } catch (e: Exception) {
                mirrorLogcat('W', "Failed to write extraction manifest: ${e.message}")
                report("Failed to write extraction manifest: ${e.message}")
            }
        }
    }

    private fun collectApkFiles(): List<File> {
        val paths = mutableListOf<String>()
        if (version != null && !version.isInstalled) {
            applicationInfo.sourceDir?.let { paths.add(it) }
            applicationInfo.splitSourceDirs?.let { paths.addAll(it) }
        } else {
            val appInfo = packageContext.applicationInfo
            appInfo.sourceDir?.let { paths.add(it) }
            appInfo.splitPublicSourceDirs?.let { paths.addAll(it) }
        }

        return paths
            .map { File(it) }
            .filter {
                if (it.exists()) {
                    true
                } else {
                    mirrorLogcat('W', "APK file not found: ${it.absolutePath}")
                    report("APK file not found: ${it.absolutePath}")
                    false
                }
            }
            .sortedBy { it.absolutePath }
    }

    private fun buildExtractionManifest(apkFiles: List<File>): String {
        return buildString {
            append("extractor=").append(EXTRACTOR_VERSION).append('\n')
            append("abi=").append(getDeviceAbi(apkFiles)).append('\n')
            append("token=").append(NativeImageGuard.TOKEN).append('\n')
            append("mode=").append(if (version != null && !version.isInstalled) "isolated" else "installed").append('\n')
            append("http=").append(shouldLoadHttpClient()).append('\n')
            apkFiles.forEach { file ->
                append("apk=")
                    .append(file.absolutePath)
                    .append('|')
                    .append(file.length())
                    .append('|')
                    .append(file.lastModified())
                    .append('\n')
            }
        }
    }

    private fun copyFromNativeDir(sourceDir: String, destDir: File) {
        val source = File(sourceDir)
        if (!source.exists()) {
            mirrorLogcat('W', "Source native library directory does not exist: $sourceDir")
            return
        }

        extractableLibs.forEach { lib ->
            val srcFile = File(source, lib)
            val dstFile = File(destDir, lib)
            if (srcFile.exists() && srcFile.length() > 0) {
                try {
                    srcFile.inputStream().use { input ->
                        copyStreamToReadOnlyFile(input, dstFile)
                    }
                    report("Copied $lib from native lib directory")
                    if (!processNativeImage(dstFile, true)) {
                        dstFile.delete()
                        throw IOException("Failed to prepare native library: ${dstFile.name}")
                    }
                    logFileOperation("Copied", lib)
                } catch (e: Exception) {
                    logFileOperation("Failed to copy", lib, e = e)
                }
            } else {
                mirrorLogcat('W', "Library $lib not found in $sourceDir")
            }
        }
    }

    private fun extractFromApk(apkPath: String, outputDir: File, abi: String) {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            mirrorLogcat('W', "APK file does not exist: $apkPath")
            return
        }
        if (!apkPath.contains("arm") && !apkPath.contains("x86") && !apkPath.contains("base.apk")) {
            return
        }

        try {
            ZipFile(apkPath).use { zip ->
                val abiPath = "lib/$abi"
                extractableLibs.forEach { lib ->
                    val entry = zip.getEntry("$abiPath/$lib")
                    if (entry == null) {
                        return@forEach
                    }
                    val output = File(outputDir, lib)
                    zip.getInputStream(entry).use { input ->
                        copyStreamToReadOnlyFile(input, output)
                    }
                    report("Extracted $lib from ${apkFile.name} for $abi")
                    if (!processNativeImage(output, true)) {
                        output.delete()
                        throw IOException("Failed to prepare native library: ${output.name}")
                    }
                }
            }
        } catch (e: Exception) {
            mirrorLogcat('W', "Failed to extract libraries from $apkPath: ${e.message}")
        }
    }

    private fun copyStreamToReadOnlyFile(input: InputStream, output: File) {
        ensureParentDirectory(output)
        if (output.exists() && !output.delete()) {
            throw IOException("Failed to replace existing file: ${output.absolutePath}")
        }

        val tempFile = File(output.absolutePath + ".tmp")
        if (tempFile.exists()) tempFile.delete()

        FileOutputStream(tempFile).use { out ->
            input.copyTo(out)
            out.fd.sync()
        }

        if (!tempFile.renameTo(output)) {
            tempFile.delete()
            throw IOException("Failed to rename temporary file to ${output.absolutePath}")
        }

        ensureReadOnly(output)
    }

    private fun ensureParentDirectory(file: File) {
        val parent = file.parentFile ?: return
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Failed to create parent directory: ${parent.absolutePath}")
        }
    }

    private fun ensureReadOnly(file: File) {
        if (!file.isFile) {
            throw IOException("Expected regular file: ${file.absolutePath}")
        }
        if (!file.setReadable(true, true) && !file.canRead()) {
            throw IOException("Failed to mark file readable: ${file.absolutePath}")
        }
        if (!file.setReadOnly() && file.canWrite()) {
            throw IOException("Failed to keep file read-only: ${file.absolutePath}")
        }
    }

    private fun verifyLibraries(dir: File) {
        val missing = getCacheRequiredLibs().filterNot {
            File(dir, it).let { f -> f.exists() && f.length() > 0 }
        }
        if (missing.isNotEmpty()) {
            mirrorLogcat('W', "Missing libraries in $dir: ${missing.joinToString()}")
            report("Missing libraries in ${dir.absolutePath}: ${missing.joinToString()}")
        }
    }

    private fun processNativeImage(file: File, force: Boolean = false): Boolean {
        if (!file.name.endsWith(".so")) {
            return true
        }
        return if (force) {
            NativeImageGuard.processRequired(file)
        } else {
            NativeImageGuard.processIfNeeded(file)
        }
    }

    private fun processNativeImages(dir: File) {
        NativeImageGuard.processDirectory(dir)
    }

    private fun getCacheRequiredLibs(): Array<String> {
        return if (shouldLoadHttpClient()) {
            requiredLibs + optionalLibs
        } else {
            requiredLibs
        }
    }

    private fun shouldLoadHttpClient(): Boolean {
        val versionCode = version?.versionCode ?: return false
        val targetVersion = if (versionCode.contains("beta")) "1.21.130.20" else "1.21.130"
        return isVersionAtLeast(versionCode, targetVersion)
    }

    private fun isVersionAtLeast(currentVersion: String, targetVersion: String): Boolean {
        return try {
            val current = currentVersion.replace(Regex("[^0-9.]"), "").split(".")
            val target = targetVersion.split(".")
            val maxLength = maxOf(current.size, target.size)

            for (i in 0 until maxLength) {
                val currentPart = current.getOrNull(i)?.toIntOrNull() ?: 0
                val targetPart = target.getOrNull(i)?.toIntOrNull() ?: 0

                if (currentPart > targetPart) return true
                if (currentPart < targetPart) return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun mirrorLogcat(level: Char, message: String) {
        when (level) {
            'W' -> Log.w(TAG, message)
            'E' -> Log.e(TAG, message)
            else -> Log.i(TAG, message)
        }
        LaunchLog.appendLogcat(context, level, TAG, message)
    }

    private fun logFileOperation(action: String, lib: String, extra: String? = null, e: Exception? = null) {
        val message = buildString {
            append("$action $lib")
            if (extra != null) append(" $extra")
            if (e != null) append(": ${e.message}")
        }
        if (e != null) mirrorLogcat('W', message)
    }

    private fun report(message: String) {
        LaunchLog.append(context, message)
        if (progressListener != null) {
            progressListener.onLog(message)
        } else {
            launchTrace?.mark(message)
        }
    }

    private fun createAssetManager(): AssetManager {
        val assets = AssetManager::class.java.newInstance()
        val addAssetPathMethod = AssetManager::class.java.getMethod("addAssetPath", String::class.java)

        val paths = mutableListOf<String>()
        
        if (version != null && !version.isInstalled) {
            val baseApk = File(applicationInfo.sourceDir)
            if (baseApk.exists()) {
                paths.add(applicationInfo.sourceDir)
            } else {
                mirrorLogcat('W', "Base APK for assets not found: ${applicationInfo.sourceDir}")
            }
            applicationInfo.splitSourceDirs?.forEach {
                if (File(it).exists()) {
                    paths.add(it)
                } else {
                    mirrorLogcat('W', "Split APK for assets not found: $it")
                }
            }
        } else {
            paths.add(packageContext.packageResourcePath)
            val splitPath = packageContext.packageResourcePath.replace("base.apk", "split_install_pack.apk")
            if (File(splitPath).exists()) paths.add(splitPath)
        }
        
        paths.add(context.packageResourcePath)

        paths.forEach { path ->
            try {
                addAssetPathMethod.invoke(assets, path)
            } catch (e: Exception) {
                mirrorLogcat('W', "Failed to add asset path $path: ${e.message}")
            }
        }
        return assets
    }

    private fun setupSecurityProvider() {
        try {
            java.security.Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1)
        } catch (e: Exception) {
            mirrorLogcat('W', "Conscrypt init failed: ${e.message}")
        }
    }

    fun resolveLibraryPath(name: String): String? {
        val libFile = File(nativeLibDir, toLibraryFileName(name))
        return if (libFile.exists() && libFile.length() > 0) {
            libFile.absolutePath
        } else {
            null
        }
    }

    /**
     * A 32-bit-only version must run as a 32-bit process end-to-end. When it is launched
     * from a 64-bit process, any 32-bit library will fail to dlopen; surface that clearly
     * instead of reporting a generic "is 32-bit instead of 64-bit" error.
     */
    private fun processBitnessMismatchDetail(e: UnsatisfiedLinkError): String? {
        val abi = getLaunchAbi()
        if (!AbiBitness.is32BitAbi(abi)) return null
        if (!processIs64Bit()) return null
        return "${e.message ?: e.javaClass.simpleName} - 32-bit version ($abi) requires a 32-bit process, " +
            "but the current process is 64-bit"
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun loadLibrary(name: String): Boolean {
        return loadLibraryDetailed(name).loaded
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun loadLibraryDetailed(name: String): LibraryLoadResult {
        val fileName = toLibraryFileName(name)
        val normalizedName = normalizeLibraryName(name)
        val startedAt = SystemClock.elapsedRealtime()

        if (systemLoadedLibs.contains(fileName)) {
            val source = if (normalizedName == "gxcore") {
                "launcher bundled bootstrap"
            } else {
                "launcher bundled library"
            }
            try {
                if (normalizedName == "gxcore") {
                    if (!NativeBridgeHelper.isGxCoreEnabled()) {
                        mirrorLogcat('W', "gxcore verification disabled by feature flag — skipping bootstrap")
                        return LibraryLoadResult(
                            normalizedName,
                            fileName,
                            source,
                            true,
                            elapsedSince(startedAt),
                            "skipped gxcore bootstrap (feature flag gxcoreEnabled=false)"
                        )
                    }
                    if (!NativeBridgeHelper.bootstrapGxCore()) {
                        val detail = "gxcore bootstrap failed"
                        mirrorLogcat('E', "Failed to load $fileName from $source: $detail")
                        return LibraryLoadResult(normalizedName, fileName, source, false, elapsedSince(startedAt), detail)
                    }
                } else {
                    System.loadLibrary(normalizedName)
                }
                return LibraryLoadResult(
                    normalizedName,
                    fileName,
                    source,
                    true,
                    elapsedSince(startedAt)
                )
            } catch (e: UnsatisfiedLinkError) {
                val detail = e.message ?: e.javaClass.simpleName
                mirrorLogcat('E', "Failed to load $fileName from $source: $detail")
                return LibraryLoadResult(normalizedName, fileName, source, false, elapsedSince(startedAt), detail)
            } catch (e: Exception) {
                val detail = e.message ?: e.javaClass.simpleName
                mirrorLogcat('E', "Failed to load $fileName from $source: $detail")
                return LibraryLoadResult(normalizedName, fileName, source, false, elapsedSince(startedAt), detail)
            }
        }

        val resolvedPath = resolveLibraryPath(name)
        val libFile = resolvedPath?.let(::File) ?: File(nativeLibDir, fileName)
        val source = "Minecraft extracted library cache"
        return if (libFile.exists() && libFile.length() > 0) {
            try {
                ensureReadOnly(libFile)
                System.load(libFile.absolutePath)
                LibraryLoadResult(normalizedName, fileName, source, true, elapsedSince(startedAt), libFile.absolutePath)
            } catch (e: UnsatisfiedLinkError) {
                val detail = processBitnessMismatchDetail(e) ?: (e.message ?: e.javaClass.simpleName)
                mirrorLogcat('E', "Failed to load $fileName from ${libFile.absolutePath}: $detail")
                LibraryLoadResult(normalizedName, fileName, source, false, elapsedSince(startedAt), detail)
            } catch (e: Exception) {
                val detail = e.message ?: e.javaClass.simpleName
                mirrorLogcat('E', "Failed to load $fileName from ${libFile.absolutePath}: $detail")
                LibraryLoadResult(normalizedName, fileName, source, false, elapsedSince(startedAt), detail)
            }
        } else {
            val detail = "$fileName not found in $nativeLibDir"
            mirrorLogcat('W', detail)
            LibraryLoadResult(normalizedName, fileName, source, false, elapsedSince(startedAt), detail)
        }
    }

    fun loadAllLibraries(
        excludeLibs: Set<String> = emptySet(),
        trace: LaunchTrace? = null,
        listener: MinecraftRuntimePreparer.ProgressListener? = null,
        progressStart: Int = 46,
        progressEnd: Int = 74,
        excludeReasons: Map<String, String> = emptyMap()
    ): List<LibraryLoadResult> {
        val processSupports64Bit = processIs64Bit()
        val allLibs = (requiredLibs + systemLoadedLibs).filterNot { lib ->
            // PlayFab/maesdk/gxcore are arm64-only closed-source prebuilts with no 32-bit
            // counterpart. On a 32-bit process they cannot be loaded, so exclude them up
            // front instead of letting System.loadLibrary fail mid-launch. Keyed off the
            // process bitness, not the device's capability: an arm64 device running the
            // 32-bit launcher build is still a 32-bit process.
            systemLoadedLibs.contains(lib) && !processSupports64Bit
        }
        val loadableLibs = allLibs.filterNot { lib ->
            val libName = normalizeLibraryName(lib)
            excludeLibs.contains(libName) || excludeLibs.contains(lib)
        }
        val total = loadableLibs.size.coerceAtLeast(1)
        var loadIndex = 0
        val results = mutableListOf<LibraryLoadResult>()

        allLibs.forEach { lib ->
            val libName = normalizeLibraryName(lib)
            if (excludeLibs.contains(libName) || excludeLibs.contains(lib)) {
                val reason = excludeReasons[libName]
                    ?: excludeReasons[lib]
                    ?: "not required in this launch path"
                listener?.onLog("Skipped native library: $lib")
                trace?.mark("System.load skipped", "$lib - $reason")
                return@forEach
            }

            loadIndex += 1
            val progress = progressStart + ((progressEnd - progressStart) * (loadIndex - 1) / total)
            listener?.onProgress(progress, "Loading Minecraft", "$loadIndex/$total")
            listener?.onLog("Loading native library: $lib")
            trace?.mark("System.load started", lib)

            val result = loadLibraryDetailed(libName)
            results.add(result)
            val detail = formatLoadResult(result)
            trace?.mark(
                if (result.loaded) "System.load finished" else "System.load failed",
                detail
            )
            if (!result.loaded) {
                mirrorLogcat('E', "Failed to load bundle library $libName: ${result.detail ?: "unknown error"}")
                listener?.onLog("Failed to load native library: ${result.fileName}")
            } else {
                listener?.onLog("Loaded native library: ${result.fileName}")
            }
        }

        return results
    }

    private fun toLibraryFileName(name: String): String {
        return if (name.startsWith("lib") && name.endsWith(".so")) name else "lib${normalizeLibraryName(name)}.so"
    }

    private fun normalizeLibraryName(name: String): String {
        return name.removePrefix("lib").removeSuffix(".so")
    }

    private fun elapsedSince(startedAt: Long): Long {
        return SystemClock.elapsedRealtime() - startedAt
    }

    private fun formatLoadResult(result: LibraryLoadResult): String {
        val status = if (result.loaded) "Loaded" else "Failed to load"
        return buildString {
            append(status)
            append(' ')
            append(result.fileName)
            append(" in ")
            append(result.durationMs)
            append("ms from ")
            append(result.source)
            result.detail?.let {
                append(" - ")
                append(it)
            }
        }
    }

    fun getAssets(): AssetManager = assetManager

    fun getGameStringResource(name: String): String? {
        return try {
            val resources = android.content.res.Resources(
                assetManager,
                context.resources.displayMetrics,
                context.resources.configuration
            )
            val packageName = applicationInfo.packageName ?: MinecraftLauncher.MC_PACKAGE_NAME
            val identifier = resources.getIdentifier(name, "string", packageName)
            if (identifier == 0) null else resources.getString(identifier).takeIf { it.isNotBlank() }
        } catch (error: Exception) {
            null
        }
    }

    fun getPackageContext(): Context = packageContext

    fun getApplicationInfo(): ApplicationInfo = applicationInfo

    fun getVersionName(): String? {
        return try {
            context.packageManager.getPackageInfo(packageContext.packageName, 0).versionName
        } catch (e: Exception) {
            version?.versionCode
        }
    }

    companion object {
        private const val TAG = "GamePackageManager"
        private const val EXTRACTOR_VERSION = 3

        @Volatile
        private var instance: GamePackageManager? = null
        private var lastVersionKey: String? = null

        @JvmStatic
        fun getInstance(context: Context, version: GameVersion? = null): GamePackageManager {
            return getInstance(context, version, null, null)
        }

        @JvmStatic
        fun getInstance(
            context: Context,
            version: GameVersion? = null,
            launchTrace: LaunchTrace? = null,
            progressListener: MinecraftRuntimePreparer.ProgressListener? = null
        ): GamePackageManager {
            return synchronized(this) {
                val newVersionKey = buildVersionKey(version)
                if (instance == null || newVersionKey != lastVersionKey) {
                    instance = GamePackageManager(context.applicationContext, version, launchTrace, progressListener)
                    lastVersionKey = newVersionKey
                }
                instance!!
            }
        }

        private fun buildVersionKey(version: GameVersion?): String {
            if (version == null) return "installed-default"
            return listOf(
                version.isInstalled.toString(),
                version.packageName.orEmpty(),
                version.versionCode.orEmpty(),
                version.directoryName.orEmpty(),
                version.versionDir?.absolutePath.orEmpty(),
                version.abiList.orEmpty()
            ).joinToString("|")
        }

        fun isInitialized() = instance != null
    }
}
