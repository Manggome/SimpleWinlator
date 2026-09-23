package com.winlator.simple;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.preference.PreferenceManager;

import com.winlator.inputcontrols.ExternalController;
import com.winlator.math.Mathf;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

/**
 * Per-gamepad input remapping recorded with {@link GamepadRemapDialog}.
 * Some Bluetooth pads send non-standard key codes, unnamed keys (only a scan code), put the
 * triggers on analog axes, or put the right stick on other axes than Z/RZ.
 */
public abstract class GamepadRemap {
    private static final String PREF_KEY = "simple_gamepad_remaps";
    private static HashMap<String, Mapping> remaps;

    /** How an axis is read: value is normalized with the recorded rest value and direction. */
    public static class AxisSource {
        public final int axis;
        public final float rest;
        public final float full;

        public AxisSource(int axis, float rest, float full) {
            this.axis = axis;
            this.rest = rest;
            this.full = full;
        }

        /** 0..1 for triggers. */
        float readTrigger(MotionEvent event, int historyPos) {
            float value = historyPos < 0 ? event.getAxisValue(axis) : event.getHistoricalAxisValue(axis, historyPos);
            return Mathf.clamp((value - rest) / (full - rest), 0.0f, 1.0f);
        }

        /** -1..1 for sticks, oriented so the recorded direction is positive. */
        float readStick(MotionEvent event, int historyPos) {
            float value = ExternalController.getCenteredAxis(event, axis, historyPos);
            return full < 0 ? -value : value;
        }

        JSONObject toJSON() throws JSONException {
            JSONObject data = new JSONObject();
            data.put("axis", axis);
            data.put("rest", rest);
            data.put("full", full);
            return data;
        }

        static AxisSource fromJSON(JSONObject data) {
            return data == null ? null : new AxisSource(data.optInt("axis"), (float)data.optDouble("rest", 0), (float)data.optDouble("full", 1));
        }
    }

    public static class Mapping {
        public final HashMap<Integer, Integer> keyCodes = new HashMap<>();
        public final HashMap<Integer, Integer> scanCodes = new HashMap<>();
        public AxisSource triggerL, triggerR, stickRX, stickRY;

        boolean isEmpty() {
            return keyCodes.isEmpty() && scanCodes.isEmpty() && triggerL == null && triggerR == null && stickRX == null && stickRY == null;
        }

        boolean producesButton(int buttonIdx) {
            return keyCodes.containsValue(buttonIdx) || scanCodes.containsValue(buttonIdx) ||
                   (buttonIdx == ExternalController.IDX_BUTTON_L2 && triggerL != null) ||
                   (buttonIdx == ExternalController.IDX_BUTTON_R2 && triggerR != null);
        }
    }

    public static String deviceKey(InputDevice device) {
        return String.format(Locale.ENGLISH, "%s#%04x:%04x", device.getName(), device.getVendorId(), device.getProductId());
    }

    public static synchronized void load(Context context) {
        remaps = new HashMap<>();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            JSONObject data = new JSONObject(preferences.getString(PREF_KEY, "{}"));
            for (Iterator<String> it = data.keys(); it.hasNext(); ) {
                String device = it.next();
                JSONObject item = data.getJSONObject(device);
                Mapping mapping = new Mapping();
                readCodes(item.optJSONObject("keys"), mapping.keyCodes);
                readCodes(item.optJSONObject("scans"), mapping.scanCodes);
                mapping.triggerL = AxisSource.fromJSON(item.optJSONObject("triggerL"));
                mapping.triggerR = AxisSource.fromJSON(item.optJSONObject("triggerR"));
                mapping.stickRX = AxisSource.fromJSON(item.optJSONObject("stickRX"));
                mapping.stickRY = AxisSource.fromJSON(item.optJSONObject("stickRY"));
                remaps.put(device, mapping);
            }
        }
        catch (JSONException | NumberFormatException e) {}
    }

    private static void readCodes(JSONObject data, HashMap<Integer, Integer> target) {
        if (data == null) return;
        for (Iterator<String> keys = data.keys(); keys.hasNext(); ) {
            String code = keys.next();
            target.put(Integer.parseInt(code), data.optInt(code));
        }
    }

    public static synchronized Mapping get(InputDevice device) {
        return remaps != null && device != null ? remaps.get(deviceKey(device)) : null;
    }

    /** Button index for this key event, honoring a saved remap; -1 if it is not a button. */
    public static synchronized int getButtonIdx(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int defaultIdx = ExternalController.getButtonIdxByKeyCode(keyCode);
        Mapping mapping = get(event.getDevice());
        if (mapping == null) return defaultIdx;

        Integer idx = keyCode != KeyEvent.KEYCODE_UNKNOWN ? mapping.keyCodes.get(keyCode) : mapping.scanCodes.get(event.getScanCode());
        if (idx != null) return idx;
        // Unrecorded code: keep the default only if nothing recorded already produces that button
        return defaultIdx != -1 && !mapping.producesButton(defaultIdx) ? defaultIdx : -1;
    }

    public static synchronized void save(Context context, InputDevice device, Mapping mapping) {
        if (remaps == null) load(context);
        String key = deviceKey(device);
        if (mapping == null || mapping.isEmpty()) remaps.remove(key);
        else remaps.put(key, mapping);

        try {
            JSONObject data = new JSONObject();
            for (String name : remaps.keySet()) {
                Mapping item = remaps.get(name);
                JSONObject json = new JSONObject();
                json.put("keys", codesToJSON(item.keyCodes));
                json.put("scans", codesToJSON(item.scanCodes));
                if (item.triggerL != null) json.put("triggerL", item.triggerL.toJSON());
                if (item.triggerR != null) json.put("triggerR", item.triggerR.toJSON());
                if (item.stickRX != null) json.put("stickRX", item.stickRX.toJSON());
                if (item.stickRY != null) json.put("stickRY", item.stickRY.toJSON());
                data.put(name, json);
            }
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREF_KEY, data.toString()).apply();
        }
        catch (JSONException e) {}
    }

    private static JSONObject codesToJSON(HashMap<Integer, Integer> codes) throws JSONException {
        JSONObject data = new JSONObject();
        for (Integer code : codes.keySet()) data.put(String.valueOf(code), codes.get(code));
        return data;
    }
}
