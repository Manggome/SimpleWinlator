package com.winlator.simple;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;

import com.winlator.R;
import com.winlator.core.AppUtils;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.ExternalController;

/**
 * Hands player 1 to a physical gamepad while one is connected.
 *
 * A touch profile that emulates a gamepad occupies XInput slot 0 and pushes a physical pad to
 * player 2, so that profile is turned off while a pad is connected. Other profiles (keyboard/mouse
 * bindings) stay active so their external-controller bindings keep working; only their on-screen
 * buttons are hidden. Everything is restored when the last pad disconnects.
 */
public class GamepadAutoSwitcher implements InputManager.InputDeviceListener {
    public static final String PREF_ENABLED = "simple_auto_gamepad";

    public interface Host {
        ControlsProfile getProfile();
        void showProfile(ControlsProfile profile);
        void hideProfile();
        boolean isTouchButtonsVisible();
        void setTouchButtonsVisible(boolean visible);
    }

    private final Context context;
    private final Host host;
    private final InputManager inputManager;
    private boolean switched = false;
    private ControlsProfile savedProfile;
    private boolean hidTouchButtonsOnly = false;

    public GamepadAutoSwitcher(Context context, Host host) {
        this.context = context;
        this.host = host;
        inputManager = (InputManager)context.getSystemService(Context.INPUT_SERVICE);
    }

    public void start() {
        inputManager.registerInputDeviceListener(this, new Handler(Looper.getMainLooper()));
        evaluate(false);
    }

    public void stop() {
        inputManager.unregisterInputDeviceListener(this);
    }

    private static boolean isAnyGamepadConnected() {
        for (int deviceId : InputDevice.getDeviceIds()) {
            if (ExternalController.isGameController(InputDevice.getDevice(deviceId))) return true;
        }
        return false;
    }

    private void evaluate(boolean notify) {
        boolean connected = isAnyGamepadConnected();

        if (connected && !switched) {
            switched = true;
            ControlsProfile profile = host.getProfile();
            if (profile == null) return;

            if (profile.isVirtualGamepad()) {
                savedProfile = profile;
                host.hideProfile();
            }
            else if (host.isTouchButtonsVisible()) {
                hidTouchButtonsOnly = true;
                host.setTouchButtonsVisible(false);
            }
            else return;

            if (notify) AppUtils.showToast(context, R.string.gamepad_connected_touch_hidden);
            else AppUtils.showToast(context, R.string.gamepad_detected_touch_hidden);
        }
        else if (!connected && switched) {
            switched = false;
            boolean restored = savedProfile != null || hidTouchButtonsOnly;

            if (savedProfile != null) host.showProfile(savedProfile);
            if (hidTouchButtonsOnly) host.setTouchButtonsVisible(true);
            savedProfile = null;
            hidTouchButtonsOnly = false;

            if (restored) AppUtils.showToast(context, R.string.gamepad_disconnected_touch_restored);
        }
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        evaluate(true);
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        evaluate(true);
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        evaluate(true);
    }
}
