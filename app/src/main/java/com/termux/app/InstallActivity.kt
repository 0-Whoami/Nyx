package com.termux.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.system.Os
import android.util.Pair
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import com.termux.shared.file.FileUtils
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.file.TermuxFileUtils
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class InstallActivity : AppCompatActivity() {
    private val progress = mutableLongStateOf(0L)
    private var totalBytes = 0L
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
        FileUtils.deleteFile(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH, false)
        FileUtils.createDirectoryFile(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH)
        val data =
            if (intent.data != null) intent.data else Uri.parse("")
        val install = data!!.getBooleanQueryParameter("install", true)
        if (install) {

            if (data.getBooleanQueryParameter("link", false)) {
                setupStorageSymlinks(this)
                startActivity(Intent(this, TermuxActivity::class.java))
                finish()
            }

            clearData()
            val url = data.getQueryParameter("url")
            if (url != null)
                installBoot(url)
            else
                installBoot("")
        }
        setContentView(ComposeView(this).apply { setContent { Progress(install) } })
    }

    private fun clearData() {
        FileUtils.clearDirectory(TermuxConstants.TERMUX_PREFIX_DIR_PATH)
    }

    private fun installBoot(url: String) {
        if (url.isEmpty())
            setupBootstrapIfNeeded(this, determineZipUrl())
        else
            setupBootstrapIfNeeded(this, url)
    }

    private fun getProgress(): Float {
        return if (totalBytes == 0L) progress.longValue.toFloat() else progress.longValue / totalBytes.toFloat()
    }

    private fun setupBootstrapIfNeeded(activity: Activity, url: String?) {
        if (FileUtils.directoryFileExists(TermuxConstants.TERMUX_PREFIX_DIR_PATH)) {
            if (TermuxFileUtils.isTermuxPrefixDirectoryEmpty) {
                activity.finish()
                return
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var error: Boolean
                // Delete prefix staging directory or any file at its destination
                error = FileUtils.deleteFile(
                    TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH,
                    true
                )
                if (!error) {
                    showBootstrapErrorDialog(
                        activity, "Err"
                    )
                    return@launch
                }
                // Delete prefix directory or any file at its destination
                error = FileUtils.deleteFile(
                    TermuxConstants.TERMUX_PREFIX_DIR_PATH,
                    true
                )
                if (!error) {
                    showBootstrapErrorDialog(
                        activity, "Err"
                    )
                    return@launch
                }
                // Create prefix staging directory if it does not already exist and set required permissions
                error = TermuxFileUtils.isTermuxPrefixStagingDirectoryAccessible(
                    createDirectoryIfMissing = true,
                    setMissingPermissions = true
                )
                if (!error) {
                    showBootstrapErrorDialog(
                        activity, "Err"
                    )
                    return@launch
                }
                // Create prefix directory if it does not already exist and set required permissions
                error = TermuxFileUtils.isTermuxPrefixDirectoryAccessible(
                    createDirectoryIfMissing = true,
                    setMissingPermissions = true
                )
                if (!error) {
                    showBootstrapErrorDialog(
                        activity, "Err"
                    )
                    return@launch
                }
                val buffer = ByteArray(8096)
                val symlinks: MutableList<Pair<String, String>> =
                    ArrayList(50)
                val zipUrl = URL(url)
                ZipInputStream(zipUrl.openStream()).use { zipInput ->
                    totalBytes = zipInput.available().toLong()
                    var zipEntry: ZipEntry?
                    while (zipInput.nextEntry.also { zipEntry = it } != null) {
                        if (zipEntry!!.name == "SYMLINKS.txt") {
                            val symlinksReader =
                                BufferedReader(InputStreamReader(zipInput))
                            var line: String?
                            while (symlinksReader.readLine().also { line = it } != null) {
                                val parts =
                                    line!!.split("←".toRegex()).dropLastWhile { it.isEmpty() }
                                        .toTypedArray()
                                if (parts.size != 2) throw RuntimeException("Malformed symlink line: $line")
                                val oldPath = parts[0]
                                val newPath =
                                    TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH + "/" + parts[1]
                                symlinks.add(
                                    Pair.create(
                                        oldPath,
                                        newPath
                                    )
                                )
                                error = ensureDirectoryExists(
                                    File(newPath).parentFile!!
                                )
                                if (!error) {
                                    showBootstrapErrorDialog(
                                        activity,
                                        "Err"
                                    )
                                    return@launch
                                }
                            }
                        } else {
                            val zipEntryName = zipEntry!!.name
                            val targetFile =
                                File(
                                    TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH,
                                    zipEntryName
                                )
                            val isDirectory = zipEntry!!.isDirectory
                            error =
                                ensureDirectoryExists(if (isDirectory) targetFile else targetFile.parentFile)
                            if (!error) {
                                showBootstrapErrorDialog(
                                    activity,
                                    "Err"
                                )
                                return@launch
                            }
                            if (!isDirectory) {
                                FileOutputStream(targetFile).use { outStream ->
                                    var readBytes: Int
                                    while (zipInput.read(buffer)
                                            .also { readBytes = it } != -1
                                    ) {
                                        progress.longValue += readBytes
                                        outStream.write(buffer, 0, readBytes)
                                    }
                                }
                                if (zipEntryName.startsWith("bin/") || zipEntryName.startsWith("libexec") || zipEntryName.startsWith(
                                        "lib/apt/apt-helper"
                                    ) || zipEntryName.startsWith("lib/apt/methods")
                                ) {
                                    Os.chmod(targetFile.absolutePath, 448)
                                }
                            }
                        }
                    }
                }
                if (symlinks.isEmpty()) throw RuntimeException("No SYMLINKS.txt encountered")
                progress.longValue = 0
                totalBytes = symlinks.size.toLong()
                for (symlink in symlinks) {
                    progress.longValue++
                    Os.symlink(symlink.first, symlink.second)
                }
                if (!TermuxConstants.TERMUX_STAGING_PREFIX_DIR.renameTo(TermuxConstants.TERMUX_PREFIX_DIR)) {
                    throw RuntimeException("Moving termux prefix staging to prefix directory failed")
                }
                // Recreate env file since termux prefix was wiped earlier
                TermuxShellEnvironment.writeEnvironmentToFile(activity)
            } catch (exception: Exception) {
                showBootstrapErrorDialog(
                    activity,
                    exception.stackTraceToString()
                )
            }
        }
    }

    private fun showBootstrapErrorDialog(activity: Activity, massage: String?) {
//        activity.startActivity(new Intent(activity, ConfirmationActivity.class).putExtra(ConfirmationActivity.EXTRA_ANIMATION_DURATION_MILLIS,6000).putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,ConfirmationActivity.FAILURE_ANIMATION).putExtra(ConfirmationActivity.EXTRA_MESSAGE,massage));
        activity.startActivity(
            Intent().setClassName(
                "com.termux.termuxsettings",
                "com.termux.termuxsettings.presentation.MainActivity"
            ).putExtra("msg", massage)
        )
//        Toast.makeText(activity, massage, Toast.LENGTH_LONG).show()
        activity.runOnUiThread { activity.finish() }
        // Send a notification with the exception so that the user knows why bootstrap setup failed
    }

    private fun ensureDirectoryExists(directory: File): Boolean {
        return FileUtils.createDirectoryFile(directory.absolutePath)
    }

    private fun determineZipUrl(): String {
        return "https://github.com/termux/termux-packages/releases/latest/download/bootstrap-" + determineTermuxArchName() + ".zip"
    }

    private fun setupStorageSymlinks(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val error: Boolean
                val storageDir = TermuxConstants.TERMUX_STORAGE_HOME_DIR
                error = FileUtils.clearDirectory(
                    storageDir.absolutePath
                )
                if (!error) {
//                    context.startActivity(new Intent(context, ConfirmationActivity.class).putExtra(ConfirmationActivity.EXTRA_ANIMATION_DURATION_MILLIS,6000).putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,ConfirmationActivity.FAILURE_ANIMATION).putExtra(ConfirmationActivity.EXTRA_MESSAGE,error.getMinimalErrorString()));
                    return@launch
                }
                // Get primary storage root "/storage/emulated/0" symlink
                val sharedDir = Environment.getExternalStorageDirectory()
                Os.symlink(
                    sharedDir.absolutePath,
                    File(storageDir, "shared").absolutePath
                )
                val documentsDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                Os.symlink(
                    documentsDir.absolutePath,
                    File(storageDir, "documents").absolutePath
                )
                val downloadsDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                Os.symlink(
                    downloadsDir.absolutePath,
                    File(storageDir, "downloads").absolutePath
                )
                val dcimDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                Os.symlink(
                    dcimDir.absolutePath,
                    File(storageDir, "dcim").absolutePath
                )
                val picturesDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                Os.symlink(
                    picturesDir.absolutePath,
                    File(storageDir, "pictures").absolutePath
                )
                val musicDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                Os.symlink(
                    musicDir.absolutePath,
                    File(storageDir, "music").absolutePath
                )
                val moviesDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                Os.symlink(
                    moviesDir.absolutePath,
                    File(storageDir, "movies").absolutePath
                )
                val podcastsDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS)
                Os.symlink(
                    podcastsDir.absolutePath,
                    File(storageDir, "podcasts").absolutePath
                )
                val audiobooksDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS)
                Os.symlink(
                    audiobooksDir.absolutePath,
                    File(storageDir, "audiobooks").absolutePath
                )

                // Dir 0 should ideally be for primary storage
                // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/app/ContextImpl.java;l=818
                // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/os/Environment.java;l=219
                // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/os/Environment.java;l=181
                // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/StorageManagerService.java;l=3796
                // https://cs.android.com/android/platform/superproject/+/android-7.0.0_r36:frameworks/base/services/core/java/com/android/server/MountService.java;l=3053
                // Create "Android/data/com.termux" symlinks
                var dirs = context.getExternalFilesDirs(null)
                if (dirs != null && dirs.isNotEmpty()) {
                    for (i in dirs.indices) {
                        val dir = dirs[i] ?: continue
                        val symlinkName = "external-$i"
                        Os.symlink(
                            dir.absolutePath,
                            File(storageDir, symlinkName).absolutePath
                        )
                    }
                }
                // Create "Android/media/com.termux" symlinks
                dirs = context.externalMediaDirs
                if (dirs != null && dirs.isNotEmpty()) {
                    for (i in dirs.indices) {
                        val dir = dirs[i] ?: continue
                        val symlinkName = "media-$i"
                        Os.symlink(
                            dir.absolutePath,
                            File(storageDir, symlinkName).absolutePath
                        )
                    }
                }
                //                context.startActivity(new Intent(context, ConfirmationActivity.class).putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,ConfirmationActivity.SUCCESS_ANIMATION).putExtra(ConfirmationActivity.EXTRA_MESSAGE,"Done"));
            } catch (error: java.lang.Exception) {
//                context.startActivity(new Intent(context, ConfirmationActivity.class).putExtra(ConfirmationActivity.EXTRA_ANIMATION_DURATION_MILLIS,6000).putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,ConfirmationActivity.FAILURE_ANIMATION).putExtra(ConfirmationActivity.EXTRA_MESSAGE,error.getMessage()));
            }
        }
    }

    private fun determineTermuxArchName(): String {
        for (androidArch in Build.SUPPORTED_ABIS) {
            when (androidArch) {
                "arm64-v8a" -> return "aarch64"
                "armeabi-v7a" -> return "arm"
                "x86_64" -> return "x86_64"
            }
        }
        return ""
    }

    @Composable
    fun Progress(install: Boolean) {
        val cid by remember { mutableIntStateOf(View.generateViewId()) }
        var first by remember { mutableStateOf(true) }
        val secColor by remember { mutableStateOf(Color(0xFF14FFEC)) }
        val textColor by remember { mutableStateOf(Color(0xFFFFFFFF)) }
        val waveColor by remember { mutableStateOf(Color(0xFFC7EEFF)) }
        val complememntColor by remember { mutableStateOf(Color.Black) }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (install && getProgress() <= 100)
                Tiles(
                    textColor = textColor,
                    text = "${getProgress() * 100}%",
                    modifier = Modifier
                        .fillMaxSize(getProgress())
                        .padding(5.times(getProgress()).dp)
                        .border(
                            width = 1.dp,
                            color = waveColor,
                            shape = CircleShape
                        )
                        .padding(10.times(getProgress()).dp)
                        .border(
                            width = 1.dp,
                            color = waveColor,
                            shape = CircleShape
                        )
                        .padding(15.times(getProgress()).dp)
                        .border(
                            width = 1.dp,
                            color = waveColor,
                            shape = CircleShape
                        )
                        .padding(20.times(getProgress()).dp)
                        .border(
                            width = 1.dp,
                            color = waveColor,
                            shape = CircleShape
                        )
                        .wrapContentSize(),
                    customMod = true
                )
            else {
                if (!install) {
                    AndroidView(modifier = Modifier.size(1.dp), factory = {
                        FragmentContainerView(it).apply { id = cid }
                    }, update = {
                        if (first) {
                            supportFragmentManager.beginTransaction()
                                .replace(it.id, WearReceiverFragment::class.java, null).commit()
                            first = false
                        }
                    })
                }
                val infiniteTransition = rememberInfiniteTransition(label = "")
                val percentage by infiniteTransition.animateFloat(
                    initialValue = 0.45f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(durationMillis = 5000),
                        repeatMode = RepeatMode.Restart
                    ), label = ""
                )
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.0f,
                    targetValue = 1.00f,
                    animationSpec = infiniteRepeatable(
                        tween(durationMillis = 15500),
                        repeatMode = RepeatMode.Reverse
                    ), label = ""
                )
                val percentage1 by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(durationMillis = 5000, delayMillis = 1000),
                        repeatMode = RepeatMode.Restart
                    ), label = ""
                )
                val alpha1 by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1.00f,
                    animationSpec = infiniteRepeatable(
                        tween(durationMillis = 2500, delayMillis = 1000),
                        repeatMode = RepeatMode.Reverse
                    ), label = ""
                )
                Box(
                    modifier = Modifier
                        .border(
                            width = (2 * alpha).dp,
                            color = waveColor.copy(alpha = alpha),
                            shape = CircleShape
                        )
                        .fillMaxSize(percentage)
                )
                Box(
                    modifier = Modifier
                        .border(
                            width = (2 * alpha1).dp,
                            color = waveColor.copy(alpha = alpha1),
                            shape = CircleShape
                        )
                        .alpha(alpha1)
                        .fillMaxSize(percentage1)
                )
                Tiles(
                    textColor = textColor,
                    text = if (progress.longValue != 0L) "%.1f".format(progress.longValue / 1000000.0) + " mb" else "Listening...",
                    modifier = Modifier
                        .border(shape = CircleShape, width = 1.dp, color = secColor)
                        .fillMaxSize(.5f)
                        .shadow(
                            elevation = 30.times(alpha).dp,
                            shape = CircleShape,
                            ambientColor = secColor,
                            spotColor = secColor
                        )
                        .background(color = complememntColor, shape = CircleShape)
                        .wrapContentSize(),
                    customMod = true
                )

            }
        }
    }

}

