@file:OptIn(ExperimentalWearFoundationApi::class)

package com.termux.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.activity.ConfirmationActivity
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.termux.R
import com.termux.shared.file.FileUtils
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY
import com.termux.shared.termux.file.TermuxFileUtils
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
           setContent {
               val installed by remember {
                   mutableStateOf(
                       if(FileUtils.directoryFileExists(
                           TermuxConstants.TERMUX_PREFIX_DIR_PATH,
                           true
                       )) "" else "✕")
               }
               val symlink by remember { mutableStateOf(if(TermuxFileUtils.isTermuxPrefixDirectoryEmpty()) "✕" else "") }
               var url by remember{ mutableStateOf("") }
            var expandInstall by remember { mutableStateOf(false) }
            var expandFailsafe by remember { mutableStateOf(false) }
            var isPermissionGranted by remember { mutableStateOf(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED) }
            val permissionRequester = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) {
                isPermissionGranted= it
            }
            var show by remember { mutableStateOf(false) }
            val list = rememberScalingLazyListState()
            if(!show){
                    val coroutine = rememberCoroutineScope()
                    val focus = rememberActiveFocusRequester()
                    ScalingLazyColumn(anchorType = ScalingLazyListAnchorType.ItemStart, scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = .5f), modifier = Modifier
                        .onRotaryScrollEvent {
                            coroutine.launch {
                                list.scrollBy(it.verticalScrollPixels)
                                list.animateScrollBy(0f)
                            }
                            true
                        }
                        .focusRequester(focus)
                        .focusable(), state = list){
                        item { Text(text = "TermuxHub") }
                        item{
                            Chip(shape = RoundedCornerShape(15.dp),modifier = Modifier.fillMaxWidth(),
                                secondaryLabel = {
                                    Text(text = "$installed Bootstrap Installed\n$symlink Symlinked", color = MaterialTheme.colors.background.copy(alpha = .7f), fontSize = 10.sp, modifier = Modifier.padding(5.dp))
                                    Button(modifier = Modifier.size(40.dp)
                                        .padding(2.dp), colors = ButtonDefaults.buttonColors(backgroundColor = Color(0,0,0,50)), onClick = { permissionRequester.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                installBoot()
                                show=true
                                TermuxInstaller.setupStorageSymlinks(this@MainActivity)}) {
                                    Text(text = "↺",color=MaterialTheme.colors.background)
                                }
                                    Button( modifier = Modifier.size(40.dp)
                                        .padding(2.dp), colors = ButtonDefaults.buttonColors(backgroundColor = Color(0,0,0,50)), onClick = {
                                        if (isPermissionGranted){
                                            startShellAndFinish()
                                            }else{startFailsafe()}}) {
                                        Icon(painter = painterResource(id = R.drawable.rounded_arrow), contentDescription =null , tint = MaterialTheme.colors.background, modifier = Modifier.offset(x=6.dp))
                                        Icon(painter = painterResource(id = R.drawable.rounded_arrow), contentDescription =null , tint = MaterialTheme.colors.background, modifier = Modifier
                                            .rotate(180f)
                                            .offset(x = 6.dp))
                                    }             },
                                onClick = { permissionRequester.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) },
                                label = { Text(text = if (isPermissionGranted) " All Good" else "Permission Not Granted", color = MaterialTheme.colors.background, fontSize = 20.sp, modifier = Modifier.padding(5.dp)) },
                                colors = ChipDefaults.chipColors(backgroundColor = if (isPermissionGranted) Color(23,230, 156) else MaterialTheme.colors.error)
                            )
                        }
                        item{
                            Column(modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(25.dp))
                            ) {
                                CustomChip(enable = isPermissionGranted,onClick = {
                                    if (expandInstall){
                                        show = true
                                        clearData()
                                        if (url.isEmpty())
                                        installBoot()
                                        else
                                            TermuxInstaller.setupBootstrapIfNeeded(this@MainActivity,url)
                                    }
                                    expandInstall=true}, label = "Install Bootstrap")
                                if (expandInstall) {
                                    BasicTextField(
                                        value = url,
                                        onValueChange = { url = it },
                                        textStyle = TextStyle(color = MaterialTheme.colors.onSurface, fontSize = 12.sp),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        decorationBox = { innerTextField ->
                                            Box(
                                                modifier = Modifier
                                                    .background(MaterialTheme.colors.surface)
                                                    .border(
                                                        width = 1.dp,
                                                        color = MaterialTheme.colors.onSurface
                                                    )
                                                    .fillMaxWidth()
                                                    .height(45.dp)
                                                    , contentAlignment = Alignment.Center
                                            ) {
                                                innerTextField()
                                            }
                                        })
                                    CustomChip(
                                        onClick = { TermuxInstaller.setupStorageSymlinks(this@MainActivity) },
                                        label = "Create Symlink",
                                        enable = isPermissionGranted
                                    )
                                }
                                 }
                        }
                        item {
                            Column(modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(25.dp))
                            ) {
                                CustomChip(
                                    onClick = { if (expandFailsafe){ startShellAndFinish() }
                                              expandFailsafe=true},
                                    label = "Open Shell",
                                    isPermissionGranted
                                )
                                if(expandFailsafe){
                                    CustomChip(
                                        onClick = { startFailsafe() },
                                        label = "Failsafe Shell",
                                        enable = true
                                    )
                                    CustomChip(
                                        onClick = { startConnection() },
                                        label = "Phone Connected Shell",
                                        enable = isPermissionGranted
                                    )
                                }
                            }
                        }

                    }
            }
            else
                Progress()
        }
    }
    private fun clearData(){
       FileUtils.clearDirectory("force Install",TermuxConstants.TERMUX_PREFIX_DIR_PATH)
    }
    @Composable
    fun CustomChip(onClick:()->Unit, label:String,enable:Boolean){
        Chip(enabled = enable, icon = { Text(text = "●")},onClick = onClick, shape = RectangleShape, label = { Text(text = label)}, colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.surface), modifier = Modifier.fillMaxWidth())
    }
    private fun startShellAndFinish(){
        startActivity(
            Intent(
                this@MainActivity,
                TermuxActivity::class.java
            )
        )
        finishAndRemoveTask()
    }
    private fun startFailsafe(){
        startActivity(
            Intent(
                this@MainActivity,
                TermuxActivity::class.java
            ).putExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION,true)
        )
        finishAndRemoveTask()
    }
    private fun startConnection(){
        startActivity(
            Intent(
                this@MainActivity,
                TermuxActivity::class.java
            ).putExtra(TERMUX_ACTIVITY.EXTRA_PHONE_LISTENER,true)
        )
        startActivity(
            Intent(
                this@MainActivity,
                ConfirmationActivity::class.java
            ).putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,ConfirmationActivity.OPEN_ON_PHONE_ANIMATION)
        )
        finishAndRemoveTask()
    }
    private fun installBoot(){
TermuxInstaller.setupBootstrapIfNeeded(this)
    }

}

@Composable
fun Progress(){
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(
        modifier = Modifier.fillMaxSize(),
        indicatorColor = MaterialTheme.colors.primaryVariant,
        trackColor = MaterialTheme.colors.onPrimary,
        strokeWidth = 10.dp
    )
    Text(text = "Installing....")
    }
}
