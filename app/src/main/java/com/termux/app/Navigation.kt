@file:OptIn(ExperimentalWearFoundationApi::class)

package com.termux.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.termux.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min


@OptIn(ExperimentalFoundationApi::class)
class Navigation : Fragment() {
    private lateinit var mActivity: TermuxActivity
//    override fun onDestroy() {
//        super.onDestroy()
//        mActivity.terminalView.focusable = View.FOCUSABLE
//        mActivity.terminalView.requestFocus()
//    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mActivity = activity as TermuxActivity
        mActivity.terminalView.touchTransparency = false
        return ComposeView(requireContext()).apply {
            setContent { QuickSwitch() }
//            setContent {
//                var showDialog by remember { mutableStateOf(true) }
//                val list = rememberScalingLazyListState()
//                Dialog(
//                    showDialog = showDialog,
//                    onDismissRequest = {
//                        mActivity.supportFragmentManager.beginTransaction().remove(this@Navigation)
//                            .commit()
//                        showDialog = false
//                    }) {
//                    val coroutine = rememberCoroutineScope()
//                    val focus = rememberActiveFocusRequester()
//                    Alert(title = { Text(fontFamily = FontFamily.Monospace, text = "Menu") },
//                        modifier = Modifier
//                            .onRotaryScrollEvent {
//                                coroutine.launch {
//                                    list.scrollBy(it.verticalScrollPixels)
//                                    list.animateScrollBy(0f)
//                                }
//                                true
//                            }
//                            .focusRequester(focus)
//                            .focusable(),
//                        scrollState = list,
//                        content = {
//                            item {
//                                Text(
//                                    color = MaterialTheme.colors.error,
//                                    fontFamily = FontFamily.Monospace,
//                                    text = "✕ Exit",
//                                    modifier = Modifier
//                                        .fillMaxWidth()
//                                        .height(45.dp)
//                                        .background(MaterialTheme.colors.background)
//                                        .border(
//                                            width = 1.dp,
//                                            shape = MaterialTheme.shapes.small,
//                                            color = MaterialTheme.colors.error
//                                        )
//                                        .padding(15.dp)
//                                        .wrapContentHeight()
//                                        .clickable {
//                                            context?.startService(
//                                                Intent(
//                                                    context,
//                                                    TermuxService::class.java
//                                                ).setAction(TERMUX_SERVICE.ACTION_STOP_SERVICE)
//                                            )
//                                            mActivity.finishActivityIfNotFinishing()
//                                        }
//                                )
//                            }
//                            item { Text(fontFamily = FontFamily.Monospace, text = "Sessions") }
//                            items(mActivity.termuxService.termuxSessions) {
//                                Text(
//                                    color = MaterialTheme.colors.background,
//                                    fontFamily = FontFamily.Monospace,
//                                    text = "● ${if (it.terminalSession.mSessionName != null) it.terminalSession.mSessionName else "Unnamed Session"}",
//                                    textAlign = TextAlign.Center,
//                                    modifier = Modifier
//                                        .clickable {
//                                            showDialog = false
//                                            mActivity.termuxTerminalSessionClient.setCurrentSession(
//                                                it.terminalSession
//                                            )
//                                        }
//                                        .fillMaxWidth()
//                                        .height(45.dp)
//                                        .background(
//                                            shape = MaterialTheme.shapes.small,
//                                            color = MaterialTheme.colors.onSurface
//                                        )
//                                        .wrapContentHeight()
//                                )
//                            }
//                            item {
//                                Text(
//                                    color = MaterialTheme.colors.onBackground,
//                                    fontFamily = FontFamily.Monospace,
//                                    text = "+ Add New Session",
//                                    modifier = Modifier
//                                        .fillMaxWidth()
//                                        .height(45.dp)
//                                        .border(
//                                            width = 1.dp,
//                                            shape = MaterialTheme.shapes.small,
//                                            color = MaterialTheme.colors.onSurface
//                                        )
//                                        .wrapContentSize()
//                                        .clickable {
//                                            mActivity.supportFragmentManager
//                                                .beginTransaction()
//                                                .setReorderingAllowed(true)
//                                                .add(
//                                                    R.id.compose_fragment_container,
//                                                    SessionConfirmation::class.java,
//                                                    null,
//                                                    "session"
//                                                )
//                                                .commit()
//                                        }
//                                )
//                            }
//                            item { Text(text = "Terminal", fontFamily = FontFamily.Monospace) }
//                            item {
//                                val actions = listOf(
//                                    "⊻",
//                                    "◀▶",
//                                    "▲▼"//▴▾◂▸
//                                )
//
//                                val pagestate = rememberPagerState(pageCount = {3})
//                                rememberCoroutineScope().launch { pagestate.scrollToPage(mActivity.terminalView.rotaryNavigationMode)}
//                                Row(verticalAlignment = Alignment.CenterVertically){
//                                    HorizontalPager(state = pagestate, modifier = Modifier
//                                        .height(45.dp)
//                                        .width(90.dp)) {
//                                        Text(
//                                            fontSize = 20.sp,
//                                            color = MaterialTheme.colors.background,
//                                            fontFamily = FontFamily.Monospace,
//                                            text = actions[it],
//                                            modifier = Modifier
//                                                .height(45.dp)
//                                                .width(85.dp)
//                                                .background(
//                                                    shape = CircleShape,
//                                                    color = MaterialTheme.colors.onBackground
//                                                )
//                                                .wrapContentSize()
//                                                .clickable {
//                                                    showDialog = false
//                                                    mActivity.terminalView.rotaryNavigationMode = it
//                                                }
//                                        )
//                                    }
//                                    Text(
//                                        fontFamily = FontFamily.Monospace,
//                                        text = "Rotary input",
//                                        modifier = Modifier.padding(5.dp)
//                                    )
//                                }
//                            }
//                            item {
//                                var sp by remember { mutableIntStateOf(mActivity.mTermuxTerminalViewClient.CURRENT_FONTSIZE) }
//                                Row {
//                                    Row(
//                                        verticalAlignment = Alignment.CenterVertically,
//                                        horizontalArrangement = Arrangement.SpaceBetween,
//                                        modifier = Modifier
//                                            .padding(end = 10.dp)
//                                            .border(
//                                                shape = MaterialTheme.shapes.small,
//                                                width = 1.dp,
//                                                color = MaterialTheme.colors.onSurface
//                                            )
//                                    ) {
//                                        Text(
//                                            text = "+",
//                                            fontFamily = FontFamily.Monospace,
//                                            fontSize = 30.sp,
//                                            modifier = Modifier
//                                                .padding(
//                                                    horizontal = 10.dp,
//                                                    vertical = 5.dp
//                                                )
//                                                .clickable {
//                                                    sp += 2
//                                                    mActivity.mTermuxTerminalViewClient.changeFontSize(
//                                                        true
//                                                    )
//                                                }
//                                        )
//                                        Text(
//                                            text = "Tt",
//                                            fontFamily = FontFamily.Monospace,
//                                            fontSize = 15.sp,
//                                            modifier = Modifier.padding(horizontal = 10.dp)
//                                        )
//                                        Text(
//                                            text = "$sp",
//                                            fontFamily = FontFamily.Monospace,
//                                            fontSize = (sp / 1.642).sp,
//                                            modifier = Modifier.padding(horizontal = 5.dp)
//                                        )
//                                        Text(
//                                            text = "-",
//                                            fontFamily = FontFamily.Monospace,
//                                            fontSize = 30.sp,
//                                            modifier = Modifier
//                                                .padding(
//                                                    horizontal = 10.dp,
//                                                    vertical = 5.dp
//                                                )
//                                                .clickable {
//                                                    sp -= 2
//                                                    mActivity.mTermuxTerminalViewClient.changeFontSize(
//                                                        false
//                                                    )
//                                                }
//                                        )
//                                    }
//                                    Text(
//                                        color = MaterialTheme.colors.surface,
//                                        fontFamily = FontFamily.Monospace,
//                                        text = "◳",
//                                        fontSize = 30.sp,
//                                        textAlign = TextAlign.Center,
//                                        modifier = Modifier
//                                            .clickable {
//                                                mActivity.terminalView.requestFocus()
//                                                mActivity.terminalView.touchTransparency = true
//                                                mActivity.terminalView.rotaryNavigationMode = 3
//                                                showDialog = false
//                                            }
//                                            .size(45.dp)
//                                            .background(
//                                                shape = CircleShape,
//                                                color = MaterialTheme.colors.onSurface
//                                            )
//                                    )
//                                }
//                            }
//                            item { Text(fontFamily = FontFamily.Monospace, text = "Extra") }
//                            item {
//                                var key by remember { mutableStateOf("0") }
//                                val enable by remember {
//                                    mutableStateOf(
//                                        mActivity.supportFragmentManager.findFragmentByTag(
//                                            "extra"
//                                        ) != null
//                                    )
//                                }
//                                Row {
//                                    Text(
//                                        color = if (enable) MaterialTheme.colors.background else MaterialTheme.colors.onBackground,
//                                        fontFamily = FontFamily.Monospace,
//                                        text = "Extra keys",
//                                        modifier = Modifier
//                                            .height(45.dp)
//                                            .weight(3f)
//                                            .background(
//                                                shape = MaterialTheme.shapes.small,
//                                                color = if (enable) MaterialTheme.colors.onBackground else MaterialTheme.colors.background
//                                            )
//                                            .border(
//                                                shape = MaterialTheme.shapes.small, width = 1.dp,
//                                                color = MaterialTheme.colors.onBackground
//                                            )
//                                            .wrapContentSize()
//                                            .clickable {
//                                                if (enable) {
//                                                    mActivity.supportFragmentManager
//                                                        .beginTransaction()
//                                                        .setReorderingAllowed(true)
//                                                        .remove(
//                                                            Objects.requireNonNull<Fragment>(
//                                                                mActivity.supportFragmentManager.findFragmentByTag(
//                                                                    "extra"
//                                                                )
//                                                            )
//                                                        )
//                                                        .commit()
//                                                } else {
//                                                    mActivity.supportFragmentManager
//                                                        .beginTransaction()
//                                                        .setReorderingAllowed(true)
//                                                        .add(
//                                                            R.id.compose_fragment_container,
//                                                            ExtraKeysFragment::class.java,
//                                                            Bundle().apply {
//                                                                putInt(
//                                                                    "key",
//                                                                    key.toInt()
//                                                                )
//                                                            },
//                                                            "extra"
//                                                        )
//                                                        .commit()
//                                                }
//                                                showDialog = false
//                                            }
//                                    )
//                                    BasicTextField(
//                                        value = key,
//                                        onValueChange = { key = it },
//                                        textStyle = TextStyle(
//                                            color = MaterialTheme.colors.surface,
//                                            fontSize = 12.sp,
//                                            fontFamily = FontFamily.Monospace,
//                                            textAlign = TextAlign.Center
//                                        ),
//                                        keyboardOptions = KeyboardOptions(
//                                            imeAction = ImeAction.Done,
//                                            keyboardType = KeyboardType.Number
//                                        ),
//                                        decorationBox = { innerTextField ->
//                                            Box(
//                                                contentAlignment = Alignment.Center,
//                                                modifier = Modifier
//                                                    .padding(start = 5.dp)
//                                                    .size(50.dp)
//                                                    .background(
//                                                        shape = CircleShape,
//                                                        color = MaterialTheme.colors.onSurface
//                                                    )
//                                            ) {
//                                                innerTextField()
//                                            }
//                                        })
//
//                                }
//                            }
//                            item {
//                                val enable by remember {
//                                mutableStateOf(
//                                    mActivity.supportFragmentManager.findFragmentByTag(
//                                        "edit"
//                                    ) != null
//                                )
//                            }
//                                Text(
//                                color = if (enable) MaterialTheme.colors.background else MaterialTheme.colors.onBackground,
//                                fontFamily = FontFamily.Monospace,
//                                fontSize = 13.sp,
//                                text = "Toggle TextField",
//                                modifier = Modifier
//                                    .fillMaxWidth()
//                                    .height(45.dp)
//                                    .background(
//                                        shape = MaterialTheme.shapes.small,
//                                        color = if (enable) MaterialTheme.colors.onBackground else MaterialTheme.colors.background
//                                    )
//                                    .border(
//                                        shape = MaterialTheme.shapes.small, width = 1.dp,
//                                        color = MaterialTheme.colors.onBackground
//                                    )
//                                    .padding(5.dp)
//                                    .wrapContentSize()
//                                    .clickable {
//                                        if (enable)
//                                            mActivity.supportFragmentManager
//                                                .beginTransaction()
//                                                .setReorderingAllowed(true)
//                                                .remove(
//                                                    mActivity.supportFragmentManager.findFragmentByTag(
//                                                        "edit"
//                                                    )!!
//                                                )
//                                                .commit()
//                                        else
//                                            mActivity.supportFragmentManager
//                                                .beginTransaction()
//                                                .setReorderingAllowed(true)
//                                                .add(
//                                                    R.id.compose_fragment_container,
//                                                    InputBarFragment::class.java,
//                                                    null,
//                                                    "edit"
//                                                )
//                                                .commit()
//                                        showDialog = false
//                                    }
//                            )}
//                            item {
//                                Text(
//                                    fontFamily = FontFamily.Monospace,
//                                    text = "Toogle Connection",
//                                    color = MaterialTheme.colors.onSurface,
//                                    modifier = Modifier
//                                        .fillMaxWidth()
//                                        .height(45.dp)
//                                        .background(
//                                            shape = MaterialTheme.shapes.small,
//                                            color = MaterialTheme.colors.background
//                                        )
//                                        .border(
//                                            shape = MaterialTheme.shapes.small, width = 1.dp,
//                                            color = MaterialTheme.colors.onBackground
//                                        )
//                                        .wrapContentSize()
//                                        .clickable {
//                                            if (mActivity.supportFragmentManager.findFragmentByTag("wear") != null)
//                                                mActivity.supportFragmentManager
//                                                    .beginTransaction()
//                                                    .setReorderingAllowed(true)
//                                                    .remove(
//                                                        mActivity.supportFragmentManager.findFragmentByTag(
//                                                            "wear"
//                                                        )!!
//                                                    )
//                                                    .commit()
//                                            else
//                                                mActivity.supportFragmentManager
//                                                    .beginTransaction()
//                                                    .setReorderingAllowed(true)
//                                                    .add(
//                                                        R.id.compose_fragment_container,
//                                                        WearReceiverFragment::class.java,
//                                                        null,
//                                                        "wear"
//                                                    )
//                                                    .commit()
//                                            showDialog = false
//                                        }
//                                )
//
//                            }
//                        })
//                }
//            }
        }
    }

    @Composable
    fun QuickSwitch() {
        val coroutine = rememberCoroutineScope()
        val focus = rememberActiveFocusRequester()
        var state by remember { mutableIntStateOf((mActivity.termuxService.getIndexOfSession(mActivity.currentSession))) }
        val ssize by remember { mutableIntStateOf(mActivity.termuxService.termuxSessionsSize) }
        val max by remember { mutableIntStateOf(8 + ssize) }
        var count by remember { mutableIntStateOf(0) }

        Box(modifier = Modifier
            .onRotaryScrollEvent {
                coroutine.launch {
                    count = 0
                    state = if (it.horizontalScrollPixels > 0) {
                        min(state + 1, max - 1)
                    } else {
                        max(state - 1, -1)
                    }
                }
                true
            }
            .focusRequester(focus)
            .focusable()
            .size(25.dp)
        ) {
            if (state in 0 ..<ssize)
                Tiles("$state")
            else when (state) {
                -1->Tiles("+")
                ssize + 0 -> Scroll()
                ssize + 1 -> LR_Arrow()
                ssize + 2 -> UD_Arrow()
                ssize + 3 -> Window()
                ssize + 4 -> Textsizechanger()
                ssize + 5 -> ExtraKeys()
                ssize + 6 -> TextField()
                ssize + 7 -> ConnectionPhone()
            }
        }

//        if (count != 0) Text(
//            text = "$count",
//            fontFamily = FontFamily.Monospace,
//            color = MaterialTheme.colors.surface
//        )
        LaunchedEffect(count) {
            delay(1000)
            count++
            if (count == 2) {
                if (state in 0..<ssize)
                    mActivity.termuxTerminalSessionClient.setCurrentSession(mActivity.termuxService.termuxSessions[state].terminalSession)
                else when (state) {
                    -1-> newSession()
                    ssize + 0 -> scroll()
                    ssize + 1 -> lr()
                    ssize + 2 -> ud()
                    ssize + 3 -> moveWindow()
                    ssize + 4 -> textSizeChanger()
                    ssize + 5 -> ExtraKeysToogle()
                    ssize + 6 -> TextFieldToogle()
                    ssize + 7 -> connectIonPhone()

                }
                exit()
            }
        }

    }
    @Composable
    private  fun Textsizechanger(){
        Tiles("Tt"){textSizeChanger()}
    }
    private fun textSizeChanger(){
        mActivity.supportFragmentManager.beginTransaction().replace(R.id.quickNav,TextSizeChanger::class.java,null,"Tt").commit()
    }
    @Composable
    private fun ConnectionPhone(){
        Tiles("P"){connectIonPhone()}
    }
    private fun connectIonPhone(){
        if (mActivity.supportFragmentManager.findFragmentByTag("wear") != null)
                                                mActivity.supportFragmentManager
                                                    .beginTransaction()
                                                    .setReorderingAllowed(true)
                                                    .remove(
                                                        mActivity.supportFragmentManager.findFragmentByTag(
                                                            "wear"
                                                        )!!
                                                    )
                                                    .commit()
                                            else
                                                mActivity.supportFragmentManager
                                                    .beginTransaction()
                                                    .setReorderingAllowed(true)
                                                    .add(
                                                        R.id.compose_fragment_container,
                                                        WearReceiverFragment::class.java,
                                                        null,
                                                        "wear"
                                                    )
                                                    .commit()
    }
    private fun scroll(){mActivity.terminalView.rotaryNavigationMode=0}
    private fun lr(){mActivity.terminalView.rotaryNavigationMode=1}
    private fun ud(){mActivity.terminalView.rotaryNavigationMode=2}
    private fun moveWindow(){
        mActivity.terminalView.touchTransparency=true
        mActivity.terminalView.rotaryNavigationMode = 3
    }
    private fun ExtraKeysToogle(){
        if (mActivity.supportFragmentManager.findFragmentByTag("extra")!=null)
                mActivity.supportFragmentManager
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .remove(
                        mActivity.supportFragmentManager.findFragmentByTag(
                            "extra"
                        )!!
                    )
                    .commit()
            else
                mActivity.supportFragmentManager
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .add(
                        R.id.compose_fragment_container,
                        ExtraKeysFragment::class.java,
                        Bundle().apply { putInt("key",0)},
                        "extra"
                    )
                    .commit()
    }
    private fun TextFieldToogle(){
        if (mActivity.supportFragmentManager.findFragmentByTag("edit")!=null)
            mActivity.supportFragmentManager
                .beginTransaction()
                .setReorderingAllowed(true)
                .remove(
                    mActivity.supportFragmentManager.findFragmentByTag(
                        "edit"
                    )!!
                )
                .commit()
        else
            mActivity.supportFragmentManager
                .beginTransaction()
                .setReorderingAllowed(true)
                .add(
                    R.id.compose_fragment_container,
                    InputBarFragment::class.java,
                    null,
                    "edit"
                )
                .commit()
    }
    private fun newSession(){
        mActivity.supportFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .replace(
                R.id.quickNav,
                SessionConfirmation::class.java,
                null,
                "session"
            )
            .commit()
    }
    private fun exit() {
        mActivity.supportFragmentManager
            .beginTransaction()
            .remove(this@Navigation)
            .commit()
    }

    @Composable
    fun Tiles(text: String = "",onclick:()->Unit={}) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colors.surface,
            modifier = Modifier
                .fillMaxSize()
                .background(shape = CircleShape, color = MaterialTheme.colors.onSurface)
                .wrapContentSize()
                .clickable {
                    onclick()
                    exit()
                }
        )
    }

    @Composable
    fun Scroll() {
        Tiles("⊻") { scroll() }
    }

    @Composable
    fun LR_Arrow() {
        Tiles("◂▸"){lr()}
    }

    @Composable
    fun UD_Arrow() {
        Tiles("▴▾"){ud()}
    }

    @Composable
    fun Window() {
        Tiles("◳"){moveWindow()}
    }

    @Composable
    fun ExtraKeys() {
        Tiles("E"){ExtraKeysToogle()}
    }

    @Composable
    fun TextField() {
        Tiles("T"){TextFieldToogle()}
    }
}
