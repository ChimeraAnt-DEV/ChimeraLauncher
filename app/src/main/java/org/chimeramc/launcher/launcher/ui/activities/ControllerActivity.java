package org.chimeramc.launcher.ui.activities;

import android.hardware.input.InputManager;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.chimeramc.launcher.R;
import org.chimeramc.launcher.launcher.controller.ControllerInputProcessor;
import org.chimeramc.launcher.launcher.controller.ControllerProfile;
import org.chimeramc.launcher.launcher.controller.ControllerProfileManager;
import org.chimeramc.launcher.launcher.controller.ControllerType;
import org.chimeramc.launcher.ui.dialogs.CustomAlertDialog;
import org.chimeramc.launcher.ui.views.ControllerIllustrationView;
import org.chimeramc.launcher.ui.animation.DynamicAnim;

import java.util.ArrayList;
import java.util.List;

public class ControllerActivity extends BaseActivity {
    private ControllerIllustrationView illustration;
    private TextView statusText;
    private TextView illustrationLabel;
    private LinearLayout profileChips;
    private ControllerProfileManager profileManager;
    private ControllerType currentType = ControllerType.XBOX;
    private ControllerType detectedType;
    private int manualIndex;
    private boolean autoSelected;
    private InputManager inputManager;
    private final InputManager.InputDeviceListener deviceListener = new InputManager.InputDeviceListener() {
        @Override
        public void onInputDeviceAdded(int deviceId) {
            refreshDetection();
        }

        @Override
        public void onInputDeviceRemoved(int deviceId) {
            refreshDetection();
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
            refreshDetection();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);
        DynamicAnim.applyPressScaleRecursively(findViewById(android.R.id.content));
        setupNavBar();

        org.chimeramc.launcher.util.PersonalizationManager pm = new org.chimeramc.launcher.util.PersonalizationManager(this);
        View root = findViewById(R.id.controller_root);
        if (root != null) {
            pm.applyAccentToView(root, this);
        }
        applyCompactAndRounding(pm);

        illustration = findViewById(R.id.controller_illustration);
        if (illustration != null) {
            illustration.setAccentColor(pm.getAccentColor());
        }
        statusText = findViewById(R.id.controller_status);
        illustrationLabel = findViewById(R.id.controller_illustration_label);
        profileChips = findViewById(R.id.controller_profile_chips);
        profileManager = new ControllerProfileManager(this);

        findViewById(R.id.controller_next_button).setOnClickListener(v -> nextIllustration());
        findViewById(R.id.controller_edit_button).setOnClickListener(v -> openEditor());
        findViewById(R.id.controller_profile_create).setOnClickListener(v -> createProfile());
        findViewById(R.id.controller_profile_rename).setOnClickListener(v -> renameProfile());
        findViewById(R.id.controller_profile_duplicate).setOnClickListener(v -> duplicateProfile());
        findViewById(R.id.controller_profile_delete).setOnClickListener(v -> deleteProfile());

        inputManager = (InputManager) getSystemService(android.content.Context.INPUT_SERVICE);
        if (inputManager != null) {
            inputManager.registerInputDeviceListener(deviceListener, null);
        }
        refreshDetection();
        refreshProfiles();
    }

    private void setupNavBar() {
        setActiveNavTab(R.id.nav_tab_controller);
        findViewById(R.id.nav_tab_controller).setOnClickListener(v -> {
        });
    }

    @Override
    protected void onDestroy() {
        if (inputManager != null) {
            try {
                inputManager.unregisterInputDeviceListener(deviceListener);
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    private void refreshDetection() {
        ControllerType found = findConnectedController();
        if (found != null) {
            detectedType = found;
            currentType = found;
            autoSelected = true;
            statusText.setText(getString(R.string.controller_status_connected, found.getDisplayName()));
        } else {
            detectedType = null;
            autoSelected = false;
            statusText.setText(getString(R.string.controller_status_none));
        }
        illustration.setType(currentType);
        illustrationLabel.setText(currentType.getDisplayName());
        ControllerInputProcessor.detectAndLoad(this);
        refreshProfiles();
    }

    private ControllerType findConnectedController() {
        int[] ids = InputDevice.getDeviceIds();
        for (int id : ids) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null) continue;
            int sources = device.getSources();
            boolean gamepad = (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
            boolean joystick = (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
            if (gamepad || joystick) {
                ControllerType type = ControllerType.from(device);
                if (type != null) return type;
            }
        }
        return null;
    }

    private void nextIllustration() {
        ControllerType[] order = {ControllerType.XBOX, ControllerType.DS4, ControllerType.DUAL_SENSE};
        if (autoSelected) {
            manualIndex = 0;
        }
        manualIndex = (manualIndex + 1) % order.length;
        currentType = order[manualIndex];
        autoSelected = false;
        statusText.setText(getString(R.string.controller_status_preview, currentType.getDisplayName()));
        illustration.setType(currentType);
        illustrationLabel.setText(currentType.getDisplayName());
        refreshProfiles();
    }

    private void applyCompactAndRounding(org.chimeramc.launcher.util.PersonalizationManager pm) {
        View root = findViewById(R.id.controller_root);
        if (root instanceof android.view.ViewGroup viewGroup){
            int pad = (int)((pm.isCompactMode() ? 8 : 16) * getResources().getDisplayMetrics().density);
            viewGroup.setPadding(pad, pad, pad, pad);
        }
    }

    private void applyGlassToChips() {
        org.chimeramc.launcher.util.PersonalizationManager pm = new org.chimeramc.launcher.util.PersonalizationManager(this);
        int[] buttonIds = {
                R.id.controller_next_button, R.id.controller_edit_button,
                R.id.controller_profile_create, R.id.controller_profile_rename,
                R.id.controller_profile_duplicate, R.id.controller_profile_delete
        };
        for (int id : buttonIds) {
            View v = findViewById(id);
            if (v != null) {
                pm.applyGlassToView(v);
            }
        }
    }

    private void refreshProfiles() {
        if (profileChips == null) return;
        profileChips.removeAllViews();
        List<ControllerProfile> profiles = profileManager.getProfiles(currentType);
        int active = profileManager.getActiveSlot(currentType);
        View emptyHint = findViewById(R.id.empty_controller_profiles);
        boolean hasProfiles = profiles != null && !profiles.isEmpty();
        if (emptyHint != null) {
            emptyHint.setVisibility(hasProfiles ? View.GONE : View.VISIBLE);
        }
        if (!hasProfiles) return;
        for (int i = 0; i < profiles.size(); i++) {
            final int slot = i;
            TextView chip = new TextView(this);
            chip.setText(getString(R.string.controller_profile_slot, i + 1, profiles.get(i).getName()));
            chip.setTextSize(12f);
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            chip.setGravity(android.view.Gravity.CENTER);
            if (i == active) {
                chip.setTextColor(getColor(R.color.on_primary));
                chip.setBackgroundResource(R.drawable.bg_filter_chip);
                chip.setSelected(true);
            } else {
                chip.setTextColor(getColor(R.color.on_surface));
                chip.setBackgroundResource(R.drawable.bg_filter_chip);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> selectProfile(slot));
            profileChips.addView(chip);
        }
        ControllerProfile activeProfile = profileManager.getActiveProfile(currentType);
        ControllerInputProcessor.setActiveProfile(currentType, activeProfile);
    }

    private void selectProfile(int slot) {
        profileManager.setActiveSlot(currentType, slot);
        ControllerProfile activeProfile = profileManager.getActiveProfile(currentType);
        ControllerInputProcessor.setActiveProfile(currentType, activeProfile);
        Toast.makeText(this, getString(R.string.controller_profile_active_changed, activeProfile.getName()), Toast.LENGTH_SHORT).show();
        refreshProfiles();
    }

    private void createProfile() {
        if (profileManager.getProfiles(currentType).size() >= ControllerProfile.MAX_SLOTS) {
            Toast.makeText(this, R.string.controller_profile_limit, Toast.LENGTH_SHORT).show();
            return;
        }
        promptForName(getString(R.string.controller_profile_name_prompt), name -> {
            int slot = profileManager.addProfile(currentType, name);
            if (slot >= 0) {
                profileManager.setActiveSlot(currentType, slot);
                Toast.makeText(this, R.string.controller_profile_created, Toast.LENGTH_SHORT).show();
                refreshProfiles();
            }
        });
    }

    private void renameProfile() {
        int active = profileManager.getActiveSlot(currentType);
        promptForName(getString(R.string.controller_profile_name_prompt), name -> {
            profileManager.renameProfile(currentType, active, name);
            Toast.makeText(this, R.string.controller_profile_renamed, Toast.LENGTH_SHORT).show();
            refreshProfiles();
        });
    }

    private void duplicateProfile() {
        int active = profileManager.getActiveSlot(currentType);
        profileManager.duplicateProfile(currentType, active, null);
        Toast.makeText(this, R.string.controller_profile_duplicated, Toast.LENGTH_SHORT).show();
        refreshProfiles();
    }

    private void deleteProfile() {
        int active = profileManager.getActiveSlot(currentType);
        profileManager.deleteProfile(currentType, active);
        Toast.makeText(this, R.string.controller_profile_deleted, Toast.LENGTH_SHORT).show();
        refreshProfiles();
    }

    private void promptForName(String title, NameCallback callback) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        CustomAlertDialog dialog = new CustomAlertDialog(this)
            .setTitleText(title)
            .setCustomView(input)
            .setPositiveButton(getString(R.string.controller_ok), v -> {
                String name = input.getText().toString();
                callback.onName(name);
            })
            .setNegativeButton(getString(R.string.controller_cancel), v -> {
            });
        dialog.show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        illustration.handleKeyEvent(keyCode, true);
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        illustration.handleKeyEvent(keyCode, false);
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            illustration.handleMotionEvent(event);
        }
        return super.onGenericMotionEvent(event);
    }

    private void openEditor() {
        ControllerProfile profile = profileManager.getActiveProfile(currentType);
        ControllerProfile working = profile.copy();
        android.widget.LinearLayout content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        content.setPadding(dp(8), dp(4), dp(8), dp(4));

        TextView nameLabel = label(getString(R.string.controller_editor_name_label));
        android.widget.EditText nameInput = new android.widget.EditText(this);
        nameInput.setText(working.getName());
        content.addView(nameLabel);
        content.addView(nameInput);

        content.addView(label(getString(R.string.controller_editor_deadzone_label)));
        android.widget.SeekBar leftDz = seekBar(working.getLeftDeadZone());
        android.widget.SeekBar rightDz = seekBar(working.getRightDeadZone());
        content.addView(label(getString(R.string.controller_editor_left_deadzone)));
        content.addView(leftDz);
        content.addView(label(getString(R.string.controller_editor_right_deadzone)));
        content.addView(rightDz);

        content.addView(label(getString(R.string.controller_editor_sensitivity_label)));
        android.widget.SeekBar leftSens = seekBarSens(working.getLeftStickSensitivity());
        android.widget.SeekBar rightSens = seekBarSens(working.getRightStickSensitivity());
        content.addView(label(getString(R.string.controller_editor_left_sensitivity)));
        content.addView(leftSens);
        content.addView(label(getString(R.string.controller_editor_right_sensitivity)));
        content.addView(rightSens);

        android.widget.Switch vibration = new android.widget.Switch(this);
        vibration.setChecked(working.isVibrationEnabled());
        content.addView(label(getString(R.string.controller_editor_vibration)));
        content.addView(vibration);

        CustomAlertDialog dialog = new CustomAlertDialog(this)
            .setTitleText(getString(R.string.controller_edit))
            .setCustomView(content)
            .setPositiveButton(getString(R.string.controller_save), v -> {
                working.setName(nameInput.getText().toString());
                working.setLeftDeadZone(leftDz.getProgress() / 100f);
                working.setRightDeadZone(rightDz.getProgress() / 100f);
                working.setLeftStickSensitivity(0.25f + leftSens.getProgress() / 100f * 2.75f);
                working.setRightStickSensitivity(0.25f + rightSens.getProgress() / 100f * 2.75f);
                working.setVibrationEnabled(vibration.isChecked());
                int active = profileManager.getActiveSlot(currentType);
                List<ControllerProfile> profiles = profileManager.getProfiles(currentType);
                profiles.set(active, working);
                profileManager.saveProfiles(currentType, profiles);
                refreshProfiles();
            })
            .setNegativeButton(getString(R.string.controller_cancel), v -> {
            });
        dialog.show();
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTextColor(getColor(R.color.on_surface));
        tv.setPadding(0, dp(6), 0, dp(2));
        return tv;
    }

    private android.widget.SeekBar seekBar(float value) {
        android.widget.SeekBar sb = new android.widget.SeekBar(this);
        sb.setMax(100);
        sb.setProgress((int) (value * 100f));
        return sb;
    }

    private android.widget.SeekBar seekBarSens(float value) {
        android.widget.SeekBar sb = new android.widget.SeekBar(this);
        sb.setMax(100);
        float scaled = (value - 0.25f) / 2.75f;
        sb.setProgress((int) (scaled * 100f));
        return sb;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private interface NameCallback {
        void onName(String name);
    }
}
