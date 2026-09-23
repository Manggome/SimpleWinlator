package com.winlator.simple;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AlertDialog;

import com.winlator.R;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.AppUtils;
import com.winlator.core.FileUtils;
import com.winlator.core.PreloaderDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;

/** Scans a folder for games and lets the user pick which ones to add. */
public class GameScanDialog {
    private final Activity activity;
    private final Container container;
    private final Runnable onGamesAdded;

    public GameScanDialog(Activity activity, Container container, Runnable onGamesAdded) {
        this.activity = activity;
        this.container = container;
        this.onGamesAdded = onGamesAdded;
    }

    public void scan(File rootDir) {
        final PreloaderDialog preloaderDialog = new PreloaderDialog(activity);
        preloaderDialog.show(R.string.searching_for_games);
        final Handler handler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            ArrayList<GameScanner.Result> results = GameScanner.scan(rootDir);
            Set<String> addedPaths = GameLibrary.getAddedGamePaths(new ContainerManager(activity));
            handler.post(() -> {
                preloaderDialog.close();
                if (activity.isFinishing()) return;
                showResults(rootDir, results, addedPaths);
            });
        });
    }

    private void showResults(File rootDir, ArrayList<GameScanner.Result> results, Set<String> addedPaths) {
        if (results.isEmpty()) {
            AppUtils.showToast(activity, R.string.no_games_found);
            return;
        }

        String rootPath = rootDir.getPath();
        String[] labels = new String[results.size()];
        boolean[] checked = new boolean[results.size()];
        for (int i = 0; i < results.size(); i++) {
            GameScanner.Result result = results.get(i);
            String dosPath = GameLibrary.toDOSPath(container, result.file.getPath());
            boolean alreadyAdded = dosPath != null && addedPaths.contains(dosPath.toLowerCase(Locale.ENGLISH));

            String relativePath = result.file.getPath().substring(rootPath.length()).replaceFirst("^/", "");
            String size = String.format(Locale.ENGLISH, "%.1f MB", result.file.length() / 1048576.0f);
            labels[i] = relativePath+"  ("+size+")"+(alreadyAdded ? "  "+activity.getString(R.string.already_added) : "");
            checked[i] = result.recommended && !alreadyAdded;
        }

        new AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.found_games, results.size()))
            .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.add, (dialog, which) -> addSelected(results, checked))
            .show();
    }

    private void addSelected(ArrayList<GameScanner.Result> results, boolean[] checked) {
        ArrayList<GameScanner.Result> selected = new ArrayList<>();
        HashMap<String, Integer> countByGame = new HashMap<>();
        for (int i = 0; i < results.size(); i++) {
            if (!checked[i]) continue;
            GameScanner.Result result = results.get(i);
            selected.add(result);
            String key = result.suggestedName();
            countByGame.put(key, countByGame.getOrDefault(key, 0) + 1);
        }
        if (selected.isEmpty()) return;

        ArrayList<File> files = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        for (GameScanner.Result result : selected) {
            String name = result.suggestedName();
            // Several exes picked from the same game folder: tell them apart by exe name
            if (countByGame.get(name) > 1) name += " - "+FileUtils.getBasename(result.file.getPath());
            files.add(result.file);
            names.add(name);
        }

        final PreloaderDialog preloaderDialog = new PreloaderDialog(activity);
        preloaderDialog.show(R.string.adding_games);
        GameLibrary.addGamesAsync(container, files, names, (count) -> {
            preloaderDialog.close();
            AppUtils.showToast(activity, activity.getString(R.string.games_added, count));
            onGamesAdded.run();
        });
    }
}
