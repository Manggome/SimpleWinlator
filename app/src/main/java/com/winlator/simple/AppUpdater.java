package com.winlator.simple;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import com.winlator.BuildConfig;
import com.winlator.R;
import com.winlator.core.AppUtils;
import com.winlator.core.HttpUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks GitHub Releases for a newer SimpleWinlator APK and installs it. */
public abstract class AppUpdater {
    private static final String LATEST_RELEASE_URL = "https://api.github.com/repos/Manggome/SimpleWinlator/releases/latest";
    private static final String PREF_LAST_CHECK = "simple_last_update_check";
    private static final long AUTO_CHECK_INTERVAL = 24L * 60 * 60 * 1000;

    private static class Release {
        String version;
        String notes;
        String apkUrl;
    }

    /** Silent check at most once a day; only shows something if an update exists. */
    public static void autoCheck(Activity activity) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        long now = System.currentTimeMillis();
        if (now - preferences.getLong(PREF_LAST_CHECK, 0) < AUTO_CHECK_INTERVAL) return;
        preferences.edit().putLong(PREF_LAST_CHECK, now).apply();
        check(activity, false);
    }

    public static void check(Activity activity, boolean manual) {
        if (manual) AppUtils.showToast(activity, R.string.checking_for_updates);
        HttpUtils.download(LATEST_RELEASE_URL, (content) -> activity.runOnUiThread(() -> {
            if (activity.isFinishing()) return;
            Release release = parseRelease(content);
            if (release == null) {
                if (manual) AppUtils.showToast(activity, R.string.unable_to_check_for_updates);
            }
            else if (isNewer(release.version, BuildConfig.VERSION_NAME)) {
                showUpdateDialog(activity, release);
            }
            else if (manual) AppUtils.showToast(activity, R.string.app_is_up_to_date);
        }));
    }

    private static Release parseRelease(String content) {
        if (content == null) return null;
        try {
            JSONObject data = new JSONObject(content);
            Release release = new Release();
            release.version = data.getString("tag_name").replaceFirst("^v", "");
            release.notes = data.optString("body", "");

            JSONArray assets = data.getJSONArray("assets");
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                if (asset.getString("name").endsWith(".apk")) {
                    release.apkUrl = asset.getString("browser_download_url");
                    break;
                }
            }
            return release.apkUrl != null ? release : null;
        }
        catch (JSONException e) {
            return null;
        }
    }

    /** Compares versions like "11.2-simple3" number by number. */
    static boolean isNewer(String candidate, String current) {
        ArrayList<Integer> a = numbersOf(candidate);
        ArrayList<Integer> b = numbersOf(current);
        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            int x = i < a.size() ? a.get(i) : 0;
            int y = i < b.size() ? b.get(i) : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static ArrayList<Integer> numbersOf(String version) {
        ArrayList<Integer> numbers = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\d+").matcher(version);
        while (matcher.find()) numbers.add(Integer.parseInt(matcher.group()));
        return numbers;
    }

    private static void showUpdateDialog(Activity activity, Release release) {
        String message = activity.getString(R.string.update_available_message, release.version, BuildConfig.VERSION_NAME);
        if (!release.notes.isEmpty()) message += "\n\n"+release.notes;

        new AlertDialog.Builder(activity)
            .setTitle(R.string.update_available)
            .setMessage(message)
            .setNegativeButton(R.string.later, null)
            .setPositiveButton(R.string.update, (dialog, which) -> download(activity, release))
            .show();
    }

    private static void download(Activity activity, Release release) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            AppUtils.showToast(activity, R.string.allow_install_unknown_apps);
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:"+activity.getPackageName())));
            return;
        }

        File updatesDir = new File(activity.getFilesDir(), "updates");
        updatesDir.mkdirs();
        File apkFile = new File(updatesDir, "SimpleWinlator.apk");
        if (apkFile.exists()) apkFile.delete();

        HttpUtils.download(activity, release.apkUrl, apkFile, (success) -> {
            if (success) install(activity, apkFile);
            else AppUtils.showToast(activity, R.string.unable_to_download_update);
        });
    }

    private static void install(Activity activity, File apkFile) {
        Uri uri = FileProvider.getUriForFile(activity, "com.winlator.FileProvider", apkFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }
}
