package com.termux.app;

import static com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR;
import static com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH;
import static com.termux.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR;
import static com.termux.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.system.Os;
import android.util.Pair;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.file.TermuxFileUtils;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * Install the Termux bootstrap packages if necessary by following the below steps:
 * <p/>
 * (1) If $PREFIX already exist, assume that it is correct and be done. Note that this relies on that we do not create a
 * broken $PREFIX directory below.
 * <p/>
 * (2) A progress dialog is shown with "Installing..." message and a spinner.
 * <p/>
 * (3) A staging directory, $STAGING_PREFIX, is cleared if left over from broken installation below.
 * <p/>
 * (4) The architecture is determined and an appropriate bootstrap zip url is determined in {@link #determineZipUrl()}.
 * <p/>
 * (5) The zip, containing entries relative to the $PREFIX, is is downloaded and extracted by a zip input stream
 * continuously encountering zip file entries:
 * <p/>
 * (5.1) If the zip entry encountered is SYMLINKS.txt, go through it and remember all symlinks to setup.
 * <p/>
 * (5.2) For every other zip entry, extract it into $STAGING_PREFIX and set execute permissions if necessary.
 */
final class TermuxInstaller {
    static void showdialuge(AppCompatActivity appCompatActivity,String title,String massage,boolean showProgrss){
        Bundle bundle=new Bundle();
        bundle.putString("title",title);
        bundle.putString("massage",massage);
        bundle.putBoolean("pro",showProgrss);
        appCompatActivity.getSupportFragmentManager().beginTransaction().add(R.id.compose_fragment_container, SimpleDialog.class,bundle,"dia").commitNow();
    }
   static void dismiss(AppCompatActivity appCompatActivity){
       appCompatActivity.getSupportFragmentManager().beginTransaction().remove(Objects.requireNonNull(appCompatActivity.getSupportFragmentManager().findFragmentByTag("dia"))).commitNow();
    }
    /**
     * Performs bootstrap setup if necessary.
     */
    static void setupBootstrapIfNeeded(final AppCompatActivity activity, final Runnable whenDone) {
        String bootstrapErrorMessage;
        Error filesDirectoryAccessibleError;
        // This will also call Context.getFilesDir(), which should ensure that termux files directory
        // is created if it does not already exist
        filesDirectoryAccessibleError = TermuxFileUtils.isTermuxFilesDirectoryAccessible(activity, true, true);
        boolean isFilesDirectoryAccessible = filesDirectoryAccessibleError == null;
        // Termux can only be run as the primary user (device owner) since only that
        // account has the expected file system paths. Verify that:
        if (!isFilesDirectoryAccessible) {
            bootstrapErrorMessage = Error.getMinimalErrorString(filesDirectoryAccessibleError);
            //noinspection SdCardPath
            showdialuge(activity,activity.getString(R.string.bootstrap_error_title),bootstrapErrorMessage,false);
            //MessageDialogUtils.showMessage(activity, activity.getString(R.string.bootstrap_error_title), bootstrapErrorMessage, null);
            return;
        }
        // If prefix directory exists, even if its a symlink to a valid directory and symlink is not broken/dangling
        if (FileUtils.directoryFileExists(TERMUX_PREFIX_DIR_PATH, true)) {
            if (!TermuxFileUtils.isTermuxPrefixDirectoryEmpty()) {
                whenDone.run();
                return;
            }
        }
        showdialuge(activity,null,activity.getString(R.string.bootstrap_installer_body),true);
        new Thread(() -> {
            try {

                Error error;
                // Delete prefix staging directory or any file at its destination
                error = FileUtils.deleteFile("termux prefix staging directory", TERMUX_STAGING_PREFIX_DIR_PATH, true);
                if (error != null) {
                    showBootstrapErrorDialog(activity, whenDone);
                    return;
                }
                // Delete prefix directory or any file at its destination
                error = FileUtils.deleteFile("termux prefix directory", TERMUX_PREFIX_DIR_PATH, true);
                if (error != null) {
                    showBootstrapErrorDialog(activity, whenDone);
                    return;
                }
                // Create prefix staging directory if it does not already exist and set required permissions
                error = TermuxFileUtils.isTermuxPrefixStagingDirectoryAccessible(true, true);
                if (error != null) {
                    showBootstrapErrorDialog(activity, whenDone);
                    return;
                }
                // Create prefix directory if it does not already exist and set required permissions
                error = TermuxFileUtils.isTermuxPrefixDirectoryAccessible(true, true);
                if (error != null) {
                    showBootstrapErrorDialog(activity, whenDone);
                    return;
                }
                final byte[] buffer = new byte[8096];
                final List<Pair<String, String>> symlinks = new ArrayList<>(50);
                final URL zipUrl = determineZipUrl();
                try (ZipInputStream zipInput = new ZipInputStream(zipUrl.openStream())) {
                    ZipEntry zipEntry;
                    while ((zipEntry = zipInput.getNextEntry()) != null) {
                        if (zipEntry.getName().equals("SYMLINKS.txt")) {
                            BufferedReader symlinksReader = new BufferedReader(new InputStreamReader(zipInput));
                            String line;
                            while ((line = symlinksReader.readLine()) != null) {
                                String[] parts = line.split("←");
                                if (parts.length != 2)
                                    throw new RuntimeException("Malformed symlink line: " + line);
                                String oldPath = parts[0];
                                String newPath = TERMUX_STAGING_PREFIX_DIR_PATH + "/" + parts[1];
                                symlinks.add(Pair.create(oldPath, newPath));
                                error = ensureDirectoryExists(new File(newPath).getParentFile());
                                if (error != null) {
                                    showBootstrapErrorDialog(activity, whenDone);
                                    return;
                                }
                            }
                        } else {
                            String zipEntryName = zipEntry.getName();
                            File targetFile = new File(TERMUX_STAGING_PREFIX_DIR_PATH, zipEntryName);
                            boolean isDirectory = zipEntry.isDirectory();
                            error = ensureDirectoryExists(isDirectory ? targetFile : targetFile.getParentFile());
                            if (error != null) {
                                showBootstrapErrorDialog(activity, whenDone);
                                return;
                            }
                            if (!isDirectory) {
                                try (FileOutputStream outStream = new FileOutputStream(targetFile)) {
                                    int readBytes;
                                    while ((readBytes = zipInput.read(buffer)) != -1)
                                        outStream.write(buffer, 0, readBytes);
                                }
                                if (zipEntryName.startsWith("bin/") || zipEntryName.startsWith("libexec") || zipEntryName.startsWith("lib/apt/apt-helper") || zipEntryName.startsWith("lib/apt/methods")) {
                                    //noinspection OctalInteger
                                    Os.chmod(targetFile.getAbsolutePath(), 0700);
                                }
                            }
                        }
                    }
                }
                if (symlinks.isEmpty())
                    throw new RuntimeException("No SYMLINKS.txt encountered");
                for (Pair<String, String> symlink : symlinks) {
                    Os.symlink(symlink.first, symlink.second);
                }
                if (!TERMUX_STAGING_PREFIX_DIR.renameTo(TERMUX_PREFIX_DIR)) {
                    throw new RuntimeException("Moving termux prefix staging to prefix directory failed");
                }
                // Recreate env file since termux prefix was wiped earlier
                TermuxShellEnvironment.writeEnvironmentToFile(activity);
                activity.runOnUiThread(whenDone);
            } catch (final Exception ignored) {
            } finally {
                activity.runOnUiThread(() -> {
                    try {
                        dismiss(activity);
                    } catch (RuntimeException e) {
                        // Activity already dismissed - ignore.
                    }
                });
            }
        }).start();
    }

    public static void showBootstrapErrorDialog(AppCompatActivity activity, Runnable whenDone) {
        // Send a notification with the exception so that the user knows why bootstrap setup failed
        activity.runOnUiThread(() -> {
            try {
                new AlertDialog.Builder(activity).setTitle(R.string.bootstrap_error_title).setMessage(R.string.bootstrap_error_body).setNegativeButton(R.string.bootstrap_error_abort, (dialog, which) -> {
                    dialog.dismiss();
                    activity.finish();
                }).setPositiveButton(R.string.bootstrap_error_try_again, (dialog, which) -> {
                    dialog.dismiss();
                    FileUtils.deleteFile("termux prefix directory", TERMUX_PREFIX_DIR_PATH, true);
                    TermuxInstaller.setupBootstrapIfNeeded(activity, whenDone);
                }).show();
            } catch (WindowManager.BadTokenException e1) {
                // Activity already dismissed - ignore.
            }
        });
    }


    static void setupStorageSymlinks(final Context context) {
        new Thread(() -> {
            try {
                Error error;
                File storageDir = TermuxConstants.TERMUX_STORAGE_HOME_DIR;
                error = FileUtils.clearDirectory("~/storage", storageDir.getAbsolutePath());
                if (error != null) {
                    return;
                }
                // Get primary storage root "/storage/emulated/0" symlink
                File sharedDir = Environment.getExternalStorageDirectory();
                Os.symlink(sharedDir.getAbsolutePath(), new File(storageDir, "shared").getAbsolutePath());
                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                Os.symlink(documentsDir.getAbsolutePath(), new File(storageDir, "documents").getAbsolutePath());
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                Os.symlink(downloadsDir.getAbsolutePath(), new File(storageDir, "downloads").getAbsolutePath());
                File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                Os.symlink(dcimDir.getAbsolutePath(), new File(storageDir, "dcim").getAbsolutePath());
                File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                Os.symlink(picturesDir.getAbsolutePath(), new File(storageDir, "pictures").getAbsolutePath());
                File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                Os.symlink(musicDir.getAbsolutePath(), new File(storageDir, "music").getAbsolutePath());
                File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                Os.symlink(moviesDir.getAbsolutePath(), new File(storageDir, "movies").getAbsolutePath());
                File podcastsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS);
                Os.symlink(podcastsDir.getAbsolutePath(), new File(storageDir, "podcasts").getAbsolutePath());

                    File audiobooksDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS);
                    Os.symlink(audiobooksDir.getAbsolutePath(), new File(storageDir, "audiobooks").getAbsolutePath());

                // Dir 0 should ideally be for primary storage
                // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/app/ContextImpl.java;l=818
                // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/os/Environment.java;l=219
                // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/os/Environment.java;l=181
                // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/StorageManagerService.java;l=3796
                // https://cs.android.com/android/platform/superproject/+/android-7.0.0_r36:frameworks/base/services/core/java/com/android/server/MountService.java;l=3053
                // Create "Android/data/com.termux" symlinks
                File[] dirs = context.getExternalFilesDirs(null);
                if (dirs != null && dirs.length > 0) {
                    for (int i = 0; i < dirs.length; i++) {
                        File dir = dirs[i];
                        if (dir == null)
                            continue;
                        String symlinkName = "external-" + i;
                        Os.symlink(dir.getAbsolutePath(), new File(storageDir, symlinkName).getAbsolutePath());
                    }
                }
                // Create "Android/media/com.termux" symlinks
                dirs = context.getExternalMediaDirs();
                if (dirs != null && dirs.length > 0) {
                    for (int i = 0; i < dirs.length; i++) {
                        File dir = dirs[i];
                        if (dir == null)
                            continue;
                        String symlinkName = "media-" + i;
                        Os.symlink(dir.getAbsolutePath(), new File(storageDir, symlinkName).getAbsolutePath());
                    }
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    private static Error ensureDirectoryExists(File directory) {
        return FileUtils.createDirectoryFile(directory.getAbsolutePath());
    }

    private static URL determineZipUrl() throws MalformedURLException {
        String archName = determineTermuxArchName();
        String url = "https://github.com/termux/termux-packages/releases/latest/download/bootstrap-" + archName + ".zip";
        return new URL(url);
    }

    private static String determineTermuxArchName() {
        // Note that we cannot use System.getProperty("os.arch") since that may give e.g. "aarch64"
        // while a 64-bit runtime may not be installed (like on the Samsung Galaxy S5 Neo).
        // Instead we search through the supported abi:s on the device, see:
        // http://developer.android.com/ndk/guides/abis.html
        // Note that we search for abi:s in preferred order (the ordering of the
        // Build.SUPPORTED_ABIS list) to avoid e.g. installing arm on an x86 system where arm
        // emulation is available.
        for (String androidArch : Build.SUPPORTED_ABIS) {
            switch (androidArch) {
                case "arm64-v8a":
                    return "aarch64";
                case "armeabi-v7a":
                    return "arm";
                case "x86_64":
                    return "x86_64";
                case "x86":
                    return "i686";
            }
        }
        throw new RuntimeException("Unable to determine arch from Build.SUPPORTED_ABIS =  " + Arrays.toString(Build.SUPPORTED_ABIS));
    }
}
