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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text

@Composable
fun Tiles(
    modifier: Modifier = Modifier,
    size: TextUnit = TextUnit.Unspecified,
    text: String, textColor: Color = Color.Black, customMod: Boolean = false,
    onclick: () -> Unit = {},
) {
    Text(text = text,
        fontSize = size,
        fontFamily = FontFamily.Monospace,
        color = textColor,
        modifier = if (customMod) modifier.clickable { onclick() } else modifier
            .fillMaxSize()
            .background(shape = CircleShape, color = Color.White)
            .wrapContentSize()
            .clickable {
                onclick()
            })
}

@Composable
fun InputBar(
    modifier: Modifier = Modifier,
    text: String,
    cornerRadius: Dp = 25.dp,
    valueChange: (String) -> Unit,
    onAny: KeyboardActionScope.() -> Unit = {},
    imeAction: ImeAction
) {
    BasicTextField(maxLines = 1,
        value = text,
        onValueChange = valueChange,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        textStyle = TextStyle(
            color = Color.White,
            fontFamily = FontFamily.Monospace
        ),
        keyboardActions = KeyboardActions(onAny = onAny),
        decorationBox = { innerTextField ->
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .border(
                        shape = RoundedCornerShape(cornerRadius),
                        width = 1.dp,
                        color = Color.White
                    )
            ) {
                innerTextField()
            }
        })
}
