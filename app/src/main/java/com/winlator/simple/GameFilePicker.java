package com.winlator.simple;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.winlator.R;
import com.winlator.core.Callback;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/** Minimal file browser over the phone's shared storage that returns the chosen .exe/.bat/.lnk file, or a folder in folder mode. */
public class GameFilePicker {
    private static final String PREF_LAST_DIR = "simple_last_game_dir";
    private final Context context;
    private final SharedPreferences preferences;
    private final Callback<File> callback;
    private final boolean pickFolder;
    private final File rootDir = new File(GameLibrary.PHONE_STORAGE);
    private final ArrayList<File> entries = new ArrayList<>();
    private final ArrayList<String> labels = new ArrayList<>();
    private File currentDir;
    private AlertDialog dialog;
    private ArrayAdapter<String> adapter;

    public GameFilePicker(Context context, Callback<File> callback) {
        this(context, false, callback);
    }

    public GameFilePicker(Context context, boolean pickFolder, Callback<File> callback) {
        this.context = context;
        this.callback = callback;
        this.pickFolder = pickFolder;
        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        File lastDir = new File(preferences.getString(PREF_LAST_DIR, rootDir.getPath()));
        currentDir = lastDir.isDirectory() && isInsideRoot(lastDir) ? lastDir : rootDir;
    }

    public void show() {
        adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, labels);
        ListView listView = new ListView(context);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> onEntryClicked(entries.get(position)));

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
            .setTitle(R.string.phone_storage)
            .setView(listView)
            .setNegativeButton(android.R.string.cancel, null);
        if (pickFolder) {
            builder.setPositiveButton(R.string.scan_this_folder, (d, which) -> {
                preferences.edit().putString(PREF_LAST_DIR, currentDir.getPath()).apply();
                callback.call(currentDir);
            });
        }
        dialog = builder.create();
        refresh();
        dialog.show();
    }

    private boolean isInsideRoot(File file) {
        return file.getPath().equals(rootDir.getPath()) || file.getPath().startsWith(rootDir.getPath()+"/");
    }

    private void onEntryClicked(File file) {
        if (file.isDirectory()) {
            currentDir = file;
            refresh();
        }
        else {
            preferences.edit().putString(PREF_LAST_DIR, currentDir.getPath()).apply();
            dialog.dismiss();
            callback.call(file);
        }
    }

    private void refresh() {
        entries.clear();
        labels.clear();

        if (!currentDir.getPath().equals(rootDir.getPath())) {
            entries.add(currentDir.getParentFile());
            labels.add("⬆  ..");
        }

        File[] files = currentDir.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> {
                int value = Boolean.compare(b.isDirectory(), a.isDirectory());
                return value != 0 ? value : a.getName().compareToIgnoreCase(b.getName());
            });

            for (File file : files) {
                if (file.getName().startsWith(".")) continue;
                if (file.isDirectory()) {
                    entries.add(file);
                    labels.add("📁  "+file.getName());
                }
                else if (!pickFolder && GameLibrary.isGameFile(file)) {
                    entries.add(file);
                    labels.add("🎮  "+file.getName());
                }
            }
        }

        String relativePath = currentDir.getPath().substring(rootDir.getPath().length());
        dialog.setTitle(context.getString(R.string.phone_storage)+(relativePath.isEmpty() ? "" : relativePath));
        adapter.notifyDataSetChanged();
    }
}
