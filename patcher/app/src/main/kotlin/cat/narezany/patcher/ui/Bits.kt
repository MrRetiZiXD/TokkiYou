package cat.narezany.patcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cat.narezany.patcher.R

/** A row of a list that does something when pressed. */
fun Modifier.clickableRow(pressed: () -> Unit): Modifier = this.clickable(onClick = pressed)

/** A mod's own icon, as its launcher icon looks. One the app does not know gets a plain one. */
@Composable
fun ModIcon(name: String, size: Dp) {
    val shape = RoundedCornerShape(percent = 28)
    when (name) {
        "tokkiyou" -> Image(painterResource(R.drawable.ic_tokkiyou), contentDescription = null,
            modifier = Modifier.size(size).clip(shape))
        // Lite's is the same note on black, as it is on the home screen
        "tokkiyou-lite" -> Image(painterResource(R.drawable.ic_tokkiyou_lite), contentDescription = null,
            modifier = Modifier.size(size).clip(shape))
        "tokkiyouc" -> Image(painterResource(R.drawable.ic_tokkiyouc), contentDescription = null,
            modifier = Modifier.size(size).clip(shape))
        else -> Box(Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Extension, contentDescription = null, modifier = Modifier.size(size * 0.5f),
                tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** A small round label: a version, "beta", how a mod stands. */
@Composable
fun Pill(text: String, container: Color, content: Color) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = content,
        modifier = Modifier.clip(CircleShape).background(container).padding(horizontal = 12.dp, vertical = 5.dp))
}

/** An icon on a round patch of colour, which is how a row of a list is led. */
@Composable
fun Roundel(icon: ImageVector, container: Color = MaterialTheme.colorScheme.secondaryContainer,
            content: Color = MaterialTheme.colorScheme.onSecondaryContainer, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * Rows that belong together, as Material 3 groups them: one container cut
 * into rows by a hairline of background, round at the two ends of the group
 * and nearly square between.
 */
class GroupScope {
    internal val rows = ArrayList<@Composable (Shape) -> Unit>()

    fun row(
        icon: ImageVector? = null,
        title: String,
        note: String? = null,
        pressed: (() -> Unit)? = null,
        trailing: (@Composable () -> Unit)? = null,
    ) {
        rows.add { shape ->
            Row(
                Modifier.fillMaxWidth().clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .let { if (pressed != null) it.clickable(onClick = pressed) else it }
                    .heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Roundel(icon)
                    Spacer(Modifier.width(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (!note.isNullOrEmpty()) {
                        Text(note, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (trailing != null) {
                    Spacer(Modifier.width(12.dp))
                    trailing()
                }
            }
        }
    }
}

@Composable
fun Group(title: String? = null, modifier: Modifier = Modifier, content: GroupScope.() -> Unit) {
    val scope = GroupScope().apply(content)
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 10.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val last = scope.rows.lastIndex
            scope.rows.forEachIndexed { at, row ->
                val top = if (at == 0) 24.dp else 5.dp
                val bottom = if (at == last) 24.dp else 5.dp
                row(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
            }
        }
    }
}
