package com.termux.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

@Composable
fun Tiles(modifier: Modifier = Modifier, text: String = "", onclick: () -> Unit = {}) {
    Text(text = text,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colors.surface,
        modifier = modifier
            .fillMaxSize()
            .background(shape = CircleShape, color = MaterialTheme.colors.onSurface)
            .wrapContentSize()
            .clickable {
                onclick()
            })
}
@Composable
fun InputBar(modifier: Modifier=Modifier,text:String,valueChange:(String)->Unit,onSend: KeyboardActionScope.() -> Unit={},imeAction: ImeAction){
    BasicTextField(maxLines = 1,
        value = text,
        onValueChange =valueChange,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        textStyle = TextStyle(
            color = MaterialTheme.colors.onSurface,
            fontFamily = FontFamily.Monospace
        ),
        keyboardActions = KeyboardActions(onAny = onSend),
        decorationBox = { innerTextField ->
            Box(
                modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp).border(
                        shape = RoundedCornerShape(25.dp),
                        width = 1.dp,
                        color = MaterialTheme.colors.onBackground
                    )
            ) {
                innerTextField()
            }
        })
}
