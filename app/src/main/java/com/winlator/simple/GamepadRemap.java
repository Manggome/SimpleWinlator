package com.winlator.simple;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.InputDevice;

import androidx.preference.PreferenceManager;

import com.winlator.inputcontrols.ExternalController;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

/**
 * Per-gamepad button remapping (Android key code -> XInput button index).
 * Some Bluetooth pads report non-standard key codes (e.g. A/B swapped), so the user records
 * the real codes once with {@link GamepadRemapDialog}.
 */
public abstract class GamepadRemap {
    private static final String PREF_KEY = "simple_gamepad_remaps";
    private static HashMap<String, HashMap<Integer, Integer>> remaps;

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
                JSONObject mapping = data.getJSONObject(device);
                HashMap<Integer, Integer> map = new HashMap<>();
                for (Iterator<String> keys = mapping.keys(); keys.hasNext(); ) {
                    String keyCode = keys.next();
                    map.put(Integer.parseInt(keyCode), mapping.getInt(keyCode));
                }
                remaps.put(device, map);
            }
        }
        catch (JSONException | NumberFormatException e) {}
    }

    public static synchronized boolean hasRemap(InputDevice device) {
        return remaps != null && device != null && remaps.containsKey(deviceKey(device));
    }

    /** Button index for this key code, honoring a saved remap; -1 if it is not a button. */
    public static synchronized int getButtonIdx(InputDevice device, int keyCode) {
        HashMap<Integer, Integer> map = remaps != null && device != null ? remaps.get(deviceKey(device)) : null;
        int defaultIdx = ExternalController.getButtonIdxByKeyCode(keyCode);
        if (map == null) return defaultIdx;

        Integer idx = map.get(keyCode);
        if (idx != null) return idx;
        // Unmapped code: keep the default only if no recorded key already produces that button
        return defaultIdx != -1 && !map.containsValue(defaultIdx) ? defaultIdx : -1;
    }

    public static synchronized void save(Context context, InputDevice device, HashMap<Integer, Integer> map) {
        if (remaps == null) load(context);
        String key = deviceKey(device);
        if (map == null || map.isEmpty()) remaps.remove(key);
        else remaps.put(key, new HashMap<>(map));

        try {
            JSONObject data = new JSONObject();
            for (String device1 : remaps.keySet()) {
                JSONObject mapping = new JSONObject();
                for (Integer keyCode : remaps.get(device1).keySet()) mapping.put(String.valueOf(keyCode), remaps.get(device1).get(keyCode));
                data.put(device1, mapping);
            }
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREF_KEY, data.toString()).apply();
        }
        catch (JSONException e) {}
    }
}
