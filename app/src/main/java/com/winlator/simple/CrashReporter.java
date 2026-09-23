package com.winlator.simple;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.winlator.BuildConfig;
import com.winlator.R;
import com.winlator.core.AppUtils;
import com.winlator.core.FileUtils;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Saves the stack trace of an app crash and shows it on the next launch so it can be reported. */
public abstract class CrashReporter {
    private static final String FILENAME = "last_crash.txt";
    private static boolean installed = false;

    public static synchronized void install(Context context) {
        if (installed) return;
        installed = true;

        final File crashFile = new File(context.getApplicationContext().getFilesDir(), FILENAME);
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                StringWriter trace = new StringWriter();
                throwable.printStackTrace(new PrintWriter(trace));
                String report = "SimpleWinlator "+BuildConfig.VERSION_NAME+"\n" +
                    "Android "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+"), "+Build.MANUFACTURER+" "+Build.MODEL+"\n" +
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(new Date())+"\n" +
                    "Thread: "+thread.getName()+"\n\n"+trace;
                FileUtils.writeString(crashFile, report);
            }
            catch (Throwable ignored) {}
            if (defaultHandler != null) defaultHandler.uncaughtException(thread, throwable);
        });
    }

    /** Shows the report of the previous crash, if any, then deletes it. */
    public static void showPendingReport(Activity activity) {
        File crashFile = new File(activity.getFilesDir(), FILENAME);
        if (!crashFile.isFile()) return;
        final String report = FileUtils.readString(crashFile);
        crashFile.delete();
        if (report == null || report.isEmpty()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        TextView textView = new TextView(builder.getContext());
        textView.setText(report);
        textView.setTextSize(11);
        textView.setTextIsSelectable(true);
        int padding = (int)(16 * activity.getResources().getDisplayMetrics().density);
        textView.setPadding(padding, padding, padding, padding);
        ScrollView scrollView = new ScrollView(builder.getContext());
        scrollView.addView(textView);

        builder.setTitle(R.string.app_crashed_last_time)
            .setView(scrollView)
            .setNegativeButton(android.R.string.ok, null)
            .setPositiveButton(R.string.copy, (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("SimpleWinlator crash", report));
                AppUtils.showToast(activity, R.string.crash_report_copied);
            })
            .show();
    }
}
