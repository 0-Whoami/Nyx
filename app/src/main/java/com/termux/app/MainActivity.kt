package com.termux.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
           setContent {
            var isPermissionGranted by remember { mutableStateOf(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED) }
            val permissionRequester = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) {
                isPermissionGranted= it
            }
            var show by remember { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.SpaceAround, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                Chip(onClick = {permissionRequester.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)}, label = { Text(text = if(isPermissionGranted)"All Good" else "Permission Not Granted")}, colors = ChipDefaults.chipColors(backgroundColor = Color.Transparent) )
                Chip(onClick = {show=true}, label = { Text(text = "Install Bootstrap")} )
                Chip(onClick = { startActivity(Intent(this@MainActivity,TermuxActivity::class.java))}, label = { Text(text = "Open Terminal")} )
            }
            if (show)
                Progress()
        }
    }
    private fun installBoot(){

    }
    private fun verifyRWPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val permissions = arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            ActivityCompat.requestPermissions(this, permissions, 1738)
        }
    }
}

@Composable
fun Progress(){
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(
        modifier = Modifier.fillMaxSize(),
        indicatorColor = MaterialTheme.colors.primaryVariant,
        trackColor = MaterialTheme.colors.onPrimary,
        strokeWidth = 5.dp
    )
    Text(text = "Installing....")
    }
}
