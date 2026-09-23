package com.winlator.simple;

import android.content.Context;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.WindowCallbackWrapper;

import com.winlator.R;
import com.winlator.core.AppUtils;
import com.winlator.inputcontrols.ExternalController;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Wizard that asks for each pad control in turn and records what the pad really sends:
 * a key code, a scan code (for keys Android has no name for) or an analog axis.
 */
public class GamepadRemapDialog {
    private enum StepType {BUTTON, TRIGGER, STICK_X, STICK_Y}

    private static class Step {
        final StepType type;
        final int buttonIdx;
        final String label;

        Step(StepType type, int buttonIdx, String label) {
            this.type = type;
            this.buttonIdx = buttonIdx;
            this.label = label;
        }
    }

    private static final float AXIS_THRESHOLD = 0.6f;

    private final Context context;
    private final Step[] steps;
    private final GamepadRemap.Mapping mapping = new GamepadRemap.Mapping();
    private final HashMap<Integer, Float> lastAxisValues = new HashMap<>();
    private HashMap<Integer, Float> stepBaseline = new HashMap<>();
    private final HashMap<Integer, Float> stepMinValues = new HashMap<>();
    private InputDevice device;
    private int step = 0;
    private AlertDialog dialog;
    private TextView tvPrompt;
    private TextView tvLastInput;

    public GamepadRemapDialog(Context context) {
        this.context = context;
        steps = new Step[]{
            new Step(StepType.BUTTON, ExternalController.IDX_BUTTON_A, "A"),
            new Step(StepType.BUTTON, ExternalController.IDX_BUTTON_B, "B"),
            new Step(StepType.BUTTON, ExternalController.IDX_BUTTON_X, "X"),
            new Step(StepType.BUTTON, ExternalController.IDX_BUTTON_Y, "Y"),
            new Step(StepType.BUTTON, ExternalController.IDX_BUTTON_L1, "LB"),
            new Step(StepType.BUTTON, ExternalController.IDX_BUTTON_R1, "RB"),
            new Step(StepType.TRIGGER, ExternalController.IDX_BUTTON_L2, "LT"),
            new Step(StepType.TRIGGER, ExternalController.IDX_BUTTON_R2, "RT"),
            new Step(StepType.BUTTON, ExternalController.IDX_BUTTON_SELECT, "Back / View / Select"),
            new Step(StepType.BUTTON, ExternalController.IDX_BUTTON_START, "Start / Menu"),
            new Step(StepType.BUTTON, ExternalController.IDX_BUTTON_L3, context.getString(R.string.gamepad_left_stick_click)),
            new Step(StepType.BUTTON, ExternalController.IDX_BUTTON_R3, context.getString(R.string.gamepad_right_stick_click)),
            new Step(StepType.STICK_X, -1, context.getString(R.string.gamepad_right_stick_right)),
            new Step(StepType.STICK_Y, -1, context.getString(R.string.gamepad_right_stick_down)),
        };
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

        // Joystick motion only reaches the window callback, so intercept it there
        Window window = dialog.getWindow();
        window.setCallback(new WindowCallbackWrapper(window.getCallback()) {
            @Override
            public boolean dispatchGenericMotionEvent(MotionEvent event) {
                return onMotion(event) || super.dispatchGenericMotionEvent(event);
            }
        });

        startStep();
        dialog.show();
    }

    private boolean acceptDevice(InputDevice eventDevice) {
        if (device == null) {
            device = eventDevice;
            dialog.setTitle(context.getString(R.string.gamepad_button_setup)+" · "+device.getName());
            return true;
        }
        return eventDevice.getId() == device.getId();
    }

    private boolean onKey(KeyEvent event) {
        InputDevice eventDevice = event.getDevice();
        if (!ExternalController.isGameController(eventDevice)) return false;
        // Events can still arrive while the dialog is closing after the last step
        if (step >= steps.length) return true;
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) return true;

        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();
        tvLastInput.setText(context.getString(R.string.gamepad_last_input, eventDevice.getName(), KeyEvent.keyCodeToString(keyCode)+" / scan "+scanCode, keyCode));

        // The D-pad is handled separately and must not be captured as a button
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) return true;

        Step current = steps[step];
        if (current.type != StepType.BUTTON && current.type != StepType.TRIGGER) return true;
        if (!acceptDevice(eventDevice)) return true;

        // Keys Android has no name for arrive as KEYCODE_UNKNOWN; tell them apart by scan code
        HashMap<Integer, Integer> codes = keyCode != KeyEvent.KEYCODE_UNKNOWN ? mapping.keyCodes : mapping.scanCodes;
        int code = keyCode != KeyEvent.KEYCODE_UNKNOWN ? keyCode : scanCode;
        removeValue(mapping.keyCodes, current.buttonIdx);
        removeValue(mapping.scanCodes, current.buttonIdx);
        codes.put(code, current.buttonIdx);
        nextStep();
        return true;
    }

    private static void removeValue(HashMap<Integer, Integer> codes, int value) {
        for (Iterator<Map.Entry<Integer, Integer>> it = codes.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue() == value) it.remove();
        }
    }

    private boolean onMotion(MotionEvent event) {
        InputDevice eventDevice = event.getDevice();
        if (!ExternalController.isGameController(eventDevice) || (event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) return false;
        if (device != null && eventDevice.getId() != device.getId()) return true;
        if (step >= steps.length) return true;

        Step current = steps[step];
        int detectedAxis = -1;
        float detectedValue = 0;
        float strongest = 0;

        for (InputDevice.MotionRange range : eventDevice.getMotionRanges()) {
            if ((range.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) continue;
            int axis = range.getAxis();
            float value = event.getAxisValue(axis);
            lastAxisValues.put(axis, value);
            Float min = stepMinValues.get(axis);
            if (min == null || value < min) stepMinValues.put(axis, value);

            if (current.type == StepType.BUTTON || isIgnoredAxis(axis)) continue;
            Float baseline = stepBaseline.get(axis);
            float base = baseline != null ? baseline : (current.type == StepType.TRIGGER ? stepMinValues.get(axis) : 0);

            // Triggers only count when pressed (value rising), so releasing a previous one is ignored
            float delta = current.type == StepType.TRIGGER ? value - base : Math.abs(value - base);
            if (delta >= AXIS_THRESHOLD && delta > strongest) {
                strongest = delta;
                detectedAxis = axis;
                detectedValue = value;
            }
        }

        if (strongest > 0) {
            tvLastInput.setText(context.getString(R.string.gamepad_last_axis, eventDevice.getName(), MotionEvent.axisToString(detectedAxis), detectedValue));
        }
        if (detectedAxis == -1 || !acceptDevice(eventDevice)) return true;

        if (current.type == StepType.TRIGGER) {
            Float baseline = stepBaseline.get(detectedAxis);
            float rest;
            if (baseline != null) rest = baseline;
            else {
                // No sample from before the press: the lowest value seen is the rest position,
                // snapped to the axis minimum when close to it
                rest = stepMinValues.get(detectedAxis);
                InputDevice.MotionRange range = eventDevice.getMotionRange(detectedAxis, InputDevice.SOURCE_JOYSTICK);
                if (range != null && Math.abs(rest - range.getMin()) < 0.35f) rest = range.getMin();
            }
            GamepadRemap.AxisSource source = new GamepadRemap.AxisSource(detectedAxis, rest, 1.0f);
            if (current.buttonIdx == ExternalController.IDX_BUTTON_L2) mapping.triggerL = source;
            else mapping.triggerR = source;
            removeValue(mapping.keyCodes, current.buttonIdx);
            removeValue(mapping.scanCodes, current.buttonIdx);
        }
        else {
            GamepadRemap.AxisSource source = new GamepadRemap.AxisSource(detectedAxis, 0, detectedValue > 0 ? 1.0f : -1.0f);
            if (current.type == StepType.STICK_X) mapping.stickRX = source;
            else mapping.stickRY = source;
        }
        nextStep();
        return true;
    }

    /** Left stick and D-pad hat are read directly, and axes already recorded must not be picked twice. */
    private boolean isIgnoredAxis(int axis) {
        if (axis == MotionEvent.AXIS_X || axis == MotionEvent.AXIS_Y || axis == MotionEvent.AXIS_HAT_X || axis == MotionEvent.AXIS_HAT_Y) return true;
        for (GamepadRemap.AxisSource source : new GamepadRemap.AxisSource[]{mapping.triggerL, mapping.triggerR, mapping.stickRX, mapping.stickRY}) {
            if (source != null && source.axis == axis) return true;
        }
        return false;
    }

    private void startStep() {
        stepBaseline = new HashMap<>(lastAxisValues);
        stepMinValues.clear();
        Step current = steps[step];
        int textResId = current.type == StepType.BUTTON || current.type == StepType.TRIGGER ? R.string.gamepad_press_button : R.string.gamepad_move_stick;
        tvPrompt.setText(context.getString(textResId, current.label, step + 1, steps.length));
    }

    private void nextStep() {
        if (step >= steps.length) return;
        step++;
        if (step >= steps.length) finish();
        else startStep();
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
