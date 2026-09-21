package org.chimeramc.launcher.ui.activities;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import org.chimeramc.launcher.R;
import org.chimeramc.launcher.ui.animation.DynamicAnim;
import org.chimeramc.launcher.ui.fragments.ControllerSettingsFragment;
import org.chimeramc.launcher.ui.fragments.SkinsSettingsFragment;
import org.chimeramc.launcher.util.PersonalizationManager;

/**
 * Hosts Controller and Skins behind one "Customize" tab.
 *
 * Seven top-level tabs did not fit a phone once the news bell and account avatar took their
 * space, so the two personalisation screens share this host with an inner segmented control.
 * The two fragments reuse the existing activity layouts, which keeps every view id and
 * behaviour intact.
 */
public class CustomizeActivity extends BaseActivity {

    private static final String TAG_CONTROLLER = "customize_controller";
    private static final String TAG_SKINS = "customize_skins";

    private TextView controllerTab;
    private TextView skinsTab;
    private int accent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customize);

        PersonalizationManager pm = new PersonalizationManager(this);
        accent = pm.getAccentColor();
        View root = findViewById(R.id.customize_root);
        if (root != null) {
            pm.applyAccentToView(root, this);
        }

        controllerTab = findViewById(R.id.customize_tab_controller);
        skinsTab = findViewById(R.id.customize_tab_skins);
        controllerTab.setOnClickListener(v -> showController());
        skinsTab.setOnClickListener(v -> showSkins());
        DynamicAnim.applyPressScale(controllerTab);
        DynamicAnim.applyPressScale(skinsTab);

        if (savedInstanceState == null) {
            showController();
        } else {
            boolean skinsVisible = getSupportFragmentManager().findFragmentByTag(TAG_SKINS) != null;
            styleTabs(!skinsVisible);
        }
    }

    private void showController() {
        if (getSupportFragmentManager().findFragmentByTag(TAG_CONTROLLER) == null) {
            replace(new ControllerSettingsFragment(), TAG_CONTROLLER);
        }
        styleTabs(true);
    }

    private void showSkins() {
        if (getSupportFragmentManager().findFragmentByTag(TAG_SKINS) == null) {
            replace(new SkinsSettingsFragment(), TAG_SKINS);
        }
        styleTabs(false);
    }

    private void replace(Fragment fragment, String tag) {
        getSupportFragmentManager()
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.customize_container, fragment, tag)
                .commitAllowingStateLoss();
    }

    private void styleTabs(boolean controllerActive) {
        styleTab(controllerTab, controllerActive);
        styleTab(skinsTab, !controllerActive);
    }

    private void styleTab(TextView tab, boolean active) {
        if (tab == null) return;
        if (active) {
            tab.setTextColor(accent != 0 ? accent : getColor(R.color.on_surface));
            tab.setTypeface(tab.getTypeface(), android.graphics.Typeface.BOLD);
        } else {
            tab.setTextColor(getColor(R.color.text_secondary));
            tab.setTypeface(tab.getTypeface(), android.graphics.Typeface.NORMAL);
        }
    }

    private Fragment currentFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.customize_container);
    }

    // The controller illustration highlights the physically pressed button, so this host
    // keeps its raw presses and declines bumper-driven tab switching while it is showing.
    @Override
    protected boolean shouldHandleNavKeys() {
        Fragment current = currentFragment();
        return !(current instanceof ControllerSettingsFragment);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Fragment current = currentFragment();
        if (current instanceof ControllerSettingsFragment) {
            ((ControllerSettingsFragment) current).handleHardwareKey(keyCode, true);
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Fragment current = currentFragment();
        if (current instanceof ControllerSettingsFragment) {
            ((ControllerSettingsFragment) current).handleHardwareKey(keyCode, false);
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        Fragment current = currentFragment();
        if (current instanceof ControllerSettingsFragment) {
            ((ControllerSettingsFragment) current).handleHardwareMotion(event);
        }
        return super.onGenericMotionEvent(event);
    }
}
