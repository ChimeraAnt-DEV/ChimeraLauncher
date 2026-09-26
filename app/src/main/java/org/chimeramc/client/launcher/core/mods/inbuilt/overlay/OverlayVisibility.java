package org.chimeramc.client.core.mods.inbuilt.overlay;

/**
 * Decides whether the in-game overlays — the mods whose UI lives on the game HUD, such as the
 * FPS/CPS readouts, the armor HUD, the hit-timing pill and the hitboxes — should be on screen.
 *
 * The normal signal comes from the preloader's game-state hooks: the HUD screen is up and no
 * menu is open. Those hooks are address-based and only install when the shipped signature rules
 * resolve; when they do not, the flags stay {@code false} for the whole session. Treating
 * {@code false} as "not in game" hid every overlay permanently, so a mod's UI only appeared
 * while the Mod Menu itself was open and vanished the moment it closed.
 *
 * So a session that has never once reported a HUD screen falls back to visible (minus any real
 * menu) rather than hidden: a missing signal must not silently delete a mod's UI. Once the HUD
 * hook has fired the hook is proven live and its answer is authoritative, which is what keeps
 * an overlay from leaking onto the loading screen or into a menu.
 *
 * Pure and context-free so the rule is unit-testable without a running game.
 */
public final class OverlayVisibility {

    private OverlayVisibility() {
    }

    /**
     * Whether the game-HUD overlays belong on screen.
     *
     * @param hudScreenOpen the game's HUD screen is up (native hook)
     * @param pauseOpen     the pause menu is open (native hook)
     * @param showingMenu   the game is showing a menu (native hook)
     * @param hudEditorMode the HUD editor is arranging overlays; always visible
     * @param gameWorldSeen the HUD hook has reported "open" at least once this session
     * @param sessionActive a game session is running or resumed
     */
    public static boolean showGameOverlays(boolean hudScreenOpen, boolean pauseOpen,
                                           boolean showingMenu, boolean hudEditorMode,
                                           boolean gameWorldSeen, boolean sessionActive) {
        if (hudEditorMode) return true;
        // A real menu always wins: nothing should float over the pause screen.
        if (showingMenu || pauseOpen) return false;
        if (gameWorldSeen) return hudScreenOpen;
        return sessionActive;
    }

    /**
     * Whether an individual overlay whose "show everywhere" toggle is set may be drawn.
     *
     * The toggle is an explicit user override, so it is honoured even while a menu is open.
     */
    public static boolean showEverywhereOverride(boolean showEverywhere) {
        return showEverywhere;
    }
}
