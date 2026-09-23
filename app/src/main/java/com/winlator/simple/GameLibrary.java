package com.winlator.simple;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import com.winlator.R;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.Drive;
import com.winlator.container.Shortcut;
import com.winlator.core.Callback;
import com.winlator.core.FileUtils;
import com.winlator.core.PreloaderDialog;
import com.winlator.core.StringUtils;
import com.winlator.core.WineRegistryEditor;
import com.winlator.core.WineUtils;
import com.winlator.win32.PEParser;
import com.winlator.xenvironment.RootFS;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * SimpleWinlator: games are stored as regular Winlator shortcuts (.desktop files on the
 * container's Desktop), so the per-shortcut settings dialog and launcher keep working as-is.
 */
public abstract class GameLibrary {
    public static final String PHONE_STORAGE = Environment.getExternalStorageDirectory().getPath();
    private static final String DRIVE_LETTERS = "FGHIJKLMNOPQRSTUVWXY";
    private static final String ICON_PREFIX = "simplewinlator_";

    /** Returns the first container, creating a default one if none exists. */
    public static void ensureDefaultContainer(Activity activity, Callback<Container> callback) {
        final ContainerManager manager = new ContainerManager(activity);
        if (!manager.getContainers().isEmpty()) {
            Container container = manager.getContainers().get(0);
            if (ensurePhoneStorageDrive(container)) container.saveData();
            callback.call(container);
            return;
        }

        final PreloaderDialog preloaderDialog = new PreloaderDialog(activity);
        preloaderDialog.show(R.string.preparing_default_container);
        try {
            JSONObject data = new JSONObject();
            data.put("name", activity.getString(R.string.default_container_name));
            data.put("drives", Container.DEFAULT_DRIVES+"F:"+PHONE_STORAGE);
            manager.createContainerAsync(data, (container) -> {
                if (container != null) writeDefaultRegistryKeys(container);
                preloaderDialog.close();
                callback.call(container);
            });
        }
        catch (JSONException e) {
            preloaderDialog.close();
            callback.call(null);
        }
    }

    private static void writeDefaultRegistryKeys(Container container) {
        File userRegFile = new File(container.getRootDir(), ".wine/user.reg");
        try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
            registryEditor.setStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", "disable");
            registryEditor.setStringValue("Software\\Wine\\Direct3D", "shader_backend", "glsl");
            registryEditor.setStringValue("Software\\Wine\\Direct3D", "UseGLSL", "enabled");
        }
    }

    /** Makes sure the phone's shared storage root is reachable from Wine. Returns true if a drive was added. */
    public static boolean ensurePhoneStorageDrive(Container container) {
        String drives = container.getDrives();
        String usedLetters = "";
        for (Drive drive : container.drivesIterator()) {
            if (drive.path.equals(PHONE_STORAGE)) return false;
            usedLetters += drive.letter.toUpperCase(Locale.ENGLISH);
        }

        for (int i = 0; i < DRIVE_LETTERS.length(); i++) {
            char letter = DRIVE_LETTERS.charAt(i);
            if (usedLetters.indexOf(letter) == -1) {
                container.setDrives(drives+letter+":"+PHONE_STORAGE);
                return true;
            }
        }
        return false;
    }

    public static boolean isGameFile(File file) {
        String name = file.getName().toLowerCase(Locale.ENGLISH);
        return name.endsWith(".exe") || name.endsWith(".bat") || name.endsWith(".lnk");
    }

    /** Converts an Android path into a Wine DOS path using the container's drive mappings, or null if unmapped. */
    public static String toDOSPath(Container container, String unixPath) {
        for (Drive drive : container.drivesIterator()) {
            if (unixPath.startsWith(drive.path+"/")) return WineUtils.unixToDOSPath(unixPath, container);
        }
        return null;
    }

    public static void addGameAsync(Activity activity, Container container, File gameFile, Callback<Shortcut> callback) {
        final Handler handler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            Shortcut shortcut = addGame(container, gameFile);
            handler.post(() -> callback.call(shortcut));
        });
    }

    private static Shortcut addGame(Container container, File gameFile) {
        String dosPath = toDOSPath(container, gameFile.getPath());
        if (dosPath == null) return null;

        String name = StringUtils.clearReservedChars(FileUtils.getBasename(gameFile.getPath())).trim();
        if (name.isEmpty()) name = "Game";

        File desktopDir = new File(container.getUserDir(), "Desktop");
        desktopDir.mkdirs();
        File desktopFile = new File(desktopDir, name+".desktop");
        for (int i = 2; desktopFile.exists(); i++) desktopFile = new File(desktopDir, name+" ("+i+").desktop");

        String iconName = ICON_PREFIX+Integer.toHexString(gameFile.getPath().hashCode());
        boolean hasIcon = gameFile.getName().toLowerCase(Locale.ENGLISH).endsWith(".exe") && saveIcon(container, gameFile, iconName);

        String content = "[Desktop Entry]\n" +
            "Name="+name+"\n" +
            "Exec=env WINEPREFIX=\""+RootFS.WINEPREFIX+"\" wine "+escapeExecPath(dosPath)+"\n" +
            "Type=Application\n" +
            "StartupNotify=true\n" +
            (hasIcon ? "Icon="+iconName+"\n" : "") +
            "StartupWMClass="+gameFile.getName().toLowerCase(Locale.ENGLISH)+"\n";

        if (!FileUtils.writeString(desktopFile, content)) return null;
        return new Shortcut(container, desktopFile);
    }

    /** Inverse of {@link StringUtils#unescapeDOSPath}, matching the format written by winemenubuilder. */
    private static String escapeExecPath(String dosPath) {
        return dosPath.replace("\\", "\\\\\\\\").replace(" ", "\\\\ ");
    }

    private static boolean saveIcon(Container container, File exeFile, String iconName) {
        Bitmap icon;
        try {
            icon = PEParser.extractIcon(exeFile);
        }
        catch (Exception e) {
            return false;
        }
        if (icon == null) return false;

        File iconsDir = container.getIconsDir(64);
        iconsDir.mkdirs();
        try (FileOutputStream outStream = new FileOutputStream(new File(iconsDir, iconName+".png"))) {
            return icon.compress(Bitmap.CompressFormat.PNG, 100, outStream);
        }
        catch (IOException e) {
            return false;
        }
    }
}
