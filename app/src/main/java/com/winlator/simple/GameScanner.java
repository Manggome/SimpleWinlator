package com.winlator.simple;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Finds game executables under a folder, skipping installers, redistributables and crash handlers. */
public abstract class GameScanner {
    private static final int MAX_DEPTH = 6;
    private static final int MAX_RESULTS = 300;

    private static final Pattern IGNORED_EXE = Pattern.compile(
        "^(unins\\d*|uninstall.*|setup.*|install.*|.*redist.*|vc_?redist.*|vcredist.*|dxsetup|dxwebsetup|dotnet.*|ndp\\d+.*|" +
        "oalinst|physx.*|ue\\dprereqsetup.*|unitycrashhandler(32|64)?|crashhandler.*|crashreporter.*|crashpad_handler|" +
        "bugsplat.*|errorreporter|cefsharp\\.browsersubprocess|notification_helper|zfgamebrowser|quicksfv|" +
        "dxdiag|7z.*|unrar|winrar|autorun|launcher_helper|updater|update|patch.*|config(uration)?tool|touchup|cleanup)\\.exe$",
        Pattern.CASE_INSENSITIVE);

    private static final Set<String> IGNORED_DIRS = new HashSet<>(Arrays.asList(
        "_commonredist", "commonredist", "redist", "redistributables", "directx", "dotnetfx", "vcredist", "__installer",
        "installer", "_installer", "support", "prerequisites", "monobleedingedge", "thirdparty", "crashreportclient",
        "__macosx", "android", "data", "windows", "system32", "syswow64"
    ));

    public static class Result {
        public final File file;
        /** Top-level folder under the scan root that contains this exe, or null if the exe is directly in the root. */
        public final File gameDir;
        public final int depth;
        public boolean recommended;

        Result(File file, File gameDir, int depth) {
            this.file = file;
            this.gameDir = gameDir;
            this.depth = depth;
        }

        /** Display name: the game folder's name if the exe lives inside one, otherwise the exe name. */
        public String suggestedName() {
            if (gameDir != null) return gameDir.getName();
            String name = file.getName();
            int index = name.lastIndexOf('.');
            return index > 0 ? name.substring(0, index) : name;
        }
    }

    public static ArrayList<Result> scan(File rootDir) {
        ArrayList<Result> results = new ArrayList<>();
        walk(rootDir, null, 0, results);

        LinkedHashMap<String, Result> bestByGame = new LinkedHashMap<>();
        for (Result result : results) {
            if (result.gameDir == null) {
                result.recommended = true;
                continue;
            }

            String key = result.gameDir.getPath();
            Result best = bestByGame.get(key);
            if (best == null || isBetterCandidate(result, best)) bestByGame.put(key, result);
        }
        for (Result best : bestByGame.values()) best.recommended = true;

        results.sort((a, b) -> {
            String ga = a.gameDir != null ? a.gameDir.getName() : a.file.getName();
            String gb = b.gameDir != null ? b.gameDir.getName() : b.file.getName();
            int value = ga.compareToIgnoreCase(gb);
            if (value == 0) value = Boolean.compare(b.recommended, a.recommended);
            if (value == 0) value = a.file.getPath().compareToIgnoreCase(b.file.getPath());
            return value;
        });
        return results;
    }

    private static boolean isBetterCandidate(Result candidate, Result current) {
        int candidateScore = nameMatchScore(candidate);
        int currentScore = nameMatchScore(current);
        if (candidateScore != currentScore) return candidateScore > currentScore;
        if (candidate.depth != current.depth) return candidate.depth < current.depth;
        return candidate.file.length() > current.file.length();
    }

    /** 2 if the exe name matches the game folder name, 1 if one contains the other, else 0. */
    private static int nameMatchScore(Result result) {
        String folder = normalize(result.gameDir.getName());
        String exe = normalize(result.file.getName().replaceAll("(?i)\\.exe$", ""));
        if (folder.isEmpty() || exe.isEmpty()) return 0;
        if (folder.equals(exe)) return 2;
        return folder.contains(exe) || exe.contains(folder) ? 1 : 0;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ENGLISH).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static void walk(File dir, File gameDir, int depth, ArrayList<Result> results) {
        if (depth > MAX_DEPTH || results.size() >= MAX_RESULTS) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (results.size() >= MAX_RESULTS) return;
            String name = file.getName();
            if (name.startsWith(".")) continue;

            if (file.isDirectory()) {
                if (IGNORED_DIRS.contains(name.toLowerCase(Locale.ENGLISH))) continue;
                walk(file, gameDir != null ? gameDir : file, depth + 1, results);
            }
            else if (name.toLowerCase(Locale.ENGLISH).endsWith(".exe") && !IGNORED_EXE.matcher(name).matches()) {
                results.add(new Result(file, gameDir, depth));
            }
        }
    }
}
