package com.example.pdfreader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Every pop-up window of the app goes through this one: the same photo as the menu behind it,
 * darkened so the text stays easy to read. (It has the same name and parameters as the Material
 * AlertDialog on purpose: the app's own version wins, so all windows look the same.)
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    val maxH = (LocalConfiguration.current.screenHeightDp - 72).dp
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            usePlatformDefaultWidth = false
        )
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(
                modifier = modifier.padding(horizontal = 20.dp).fillMaxWidth().widthIn(max = 560.dp).heightIn(max = maxH),
                shape = RoundedCornerShape(28.dp),
                color = Color.Black
            ) {
                Box {
                    Image(
                        painter = painterResource(R.drawable.menu_bg),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.6f)))
                    Column(Modifier.padding(24.dp)) {
                        if (title != null) {
                            ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
                            Spacer(Modifier.height(16.dp))
                        }
                        if (text != null) {
                            Box(Modifier.weight(1f, fill = false)) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            dismissButton?.invoke()
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}
