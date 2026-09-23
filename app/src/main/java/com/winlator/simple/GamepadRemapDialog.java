package com.winlator.simple;

import android.content.Context;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.winlator.R;
import com.winlator.core.AppUtils;
import com.winlator.inputcontrols.ExternalController;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Wizard that asks for each pad button in turn and records the key code the pad really sends. */
public class GamepadRemapDialog {
    private static final byte[] STEP_BUTTONS = {
        ExternalController.IDX_BUTTON_A, ExternalController.IDX_BUTTON_B, ExternalController.IDX_BUTTON_X, ExternalController.IDX_BUTTON_Y,
        ExternalController.IDX_BUTTON_L1, ExternalController.IDX_BUTTON_R1, ExternalController.IDX_BUTTON_L2, ExternalController.IDX_BUTTON_R2,
        ExternalController.IDX_BUTTON_SELECT, ExternalController.IDX_BUTTON_START, ExternalController.IDX_BUTTON_L3, ExternalController.IDX_BUTTON_R3
    };
    private static final String[] STEP_LABELS = {"A", "B", "X", "Y", "LB", "RB", "LT", "RT", "Back / View", "Start / Menu", "L3", "R3"};

    private final Context context;
    private final HashMap<Integer, Integer> mapping = new HashMap<>();
    private InputDevice device;
    private int step = 0;
    private AlertDialog dialog;
    private TextView tvPrompt;
    private TextView tvLastInput;

    public GamepadRemapDialog(Context context) {
        this.context = context;
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        Context dialogContext = builder.getContext();

        int padding = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, context.getResources().getDisplayMetrics());
        LinearLayout layout = new LinearLayout(dialogContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding / 2, padding, 0);

        tvPrompt = new TextView(dialogContext);
        tvPrompt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tvPrompt.setGravity(Gravity.CENTER);
        tvPrompt.setPadding(0, padding / 2, 0, padding / 2);
        layout.addView(tvPrompt);

        tvLastInput = new TextView(dialogContext);
        tvLastInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvLastInput.setGravity(Gravity.CENTER);
        tvLastInput.setText(R.string.gamepad_remap_hint);
        layout.addView(tvLastInput);

        dialog = builder
            .setTitle(R.string.gamepad_button_setup)
            .setView(layout)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.skip, null)
            .setPositiveButton(R.string.reset_to_default, null)
            .create();

        dialog.setOnShowListener((d) -> {
            // Keep the dialog open on skip/reset instead of the default dismiss
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener((v) -> nextStep());
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener((v) -> resetToDefault());
        });
        dialog.setOnKeyListener((d, keyCode, event) -> onKey(event));

        refreshPrompt();
        dialog.show();
    }

    private boolean onKey(KeyEvent event) {
        InputDevice eventDevice = event.getDevice();
        if (!ExternalController.isGameController(eventDevice)) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) return true;

        int keyCode = event.getKeyCode();
        tvLastInput.setText(context.getString(R.string.gamepad_last_input, eventDevice.getName(), KeyEvent.keyCodeToString(keyCode), keyCode));

        // The D-pad is handled separately and must not be captured as a button
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) return true;

        if (device == null) {
            device = eventDevice;
            dialog.setTitle(context.getString(R.string.gamepad_button_setup)+" · "+device.getName());
        }
        else if (eventDevice.getId() != device.getId()) return true;

        // One key code per button: drop any earlier assignment of this code
        for (Iterator<Map.Entry<Integer, Integer>> it = mapping.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getKey() == keyCode) it.remove();
        }
        mapping.put(keyCode, (int)STEP_BUTTONS[step]);
        nextStep();
        return true;
    }

    private void nextStep() {
        step++;
        if (step >= STEP_BUTTONS.length) finish();
        else refreshPrompt();
    }

    private void refreshPrompt() {
        tvPrompt.setText(context.getString(R.string.gamepad_press_button, STEP_LABELS[step], step + 1, STEP_BUTTONS.length));
    }

    private void finish() {
        dialog.dismiss();
        if (device == null || mapping.isEmpty()) return;
        GamepadRemap.save(context, device, mapping);
        AppUtils.showToast(context, R.string.gamepad_remap_saved);
    }

    private void resetToDefault() {
        InputDevice target = device;
        if (target == null) {
            for (int deviceId : InputDevice.getDeviceIds()) {
                InputDevice candidate = InputDevice.getDevice(deviceId);
                if (ExternalController.isGameController(candidate)) {
                    target = candidate;
                    break;
                }
            }
        }
        dialog.dismiss();
        if (target == null) {
            AppUtils.showToast(context, R.string.no_gamepad_connected);
            return;
        }
        GamepadRemap.save(context, target, null);
        AppUtils.showToast(context, R.string.gamepad_remap_reset);
    }
}
