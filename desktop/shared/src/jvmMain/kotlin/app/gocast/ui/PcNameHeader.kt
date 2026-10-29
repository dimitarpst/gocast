package app.gocast.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun PcNameHeader(
    displayName: String,
    onRenameClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val active = hovered || focused
    val editAlpha by animateFloatAsState(if (active) 1f else 0f, tween(120))
    Surface(
        onClick = onRenameClick,
        modifier = modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics { contentDescription = "Rename this PC: $displayName" },
        shape = MaterialTheme.shapes.small,
        color = if (active) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f) else Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 52.dp).padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "This PC",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            EditHint(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(16.dp).alpha(editAlpha))
        }
    }
}

@Composable
private fun EditHint(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        scale(size.minDimension / 24f, pivot = Offset.Zero) {
            val pencil = Path().apply {
                moveTo(3.5f, 16.5f)
                lineTo(16.5f, 3.5f)
                cubicTo(17.3f, 2.7f, 18.7f, 2.7f, 19.5f, 3.5f)
                lineTo(20.5f, 4.5f)
                cubicTo(21.3f, 5.3f, 21.3f, 6.7f, 20.5f, 7.5f)
                lineTo(7.5f, 20.5f)
                lineTo(2.5f, 21.5f)
                close()
                moveTo(14.5f, 5.5f)
                lineTo(18.5f, 9.5f)
            }
            drawPath(pencil, color, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
internal fun RenamePcDialog(
    currentName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember {
        mutableStateOf(TextFieldValue(currentName, selection = TextRange(0, currentName.length)))
    }
    var saveError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val name = draft.text.trim()
    val validation = when {
        name.isEmpty() -> "Enter a name for this PC."
        name.length > 48 -> "Use 48 characters or fewer."
        name.any { it.isISOControl() } -> "Use a single line for the PC name."
        else -> null
    }
    val error = saveError ?: validation

    fun save() {
        if (validation != null) return
        try {
            if (name != currentName) onSave(name)
            onDismiss()
        } catch (_: Exception) {
            saveError = "Couldn't finish renaming this PC. Try again."
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onDismiss()
                true
            } else false
        },
        title = { Text("Rename this PC") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it; saveError = null },
                singleLine = true,
                label = { Text("PC name") },
                shape = MaterialTheme.shapes.large,
                isError = error != null,
                supportingText = { Text(error ?: "Shown on your phone when you find this PC.") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
        confirmButton = { Button(onClick = { save() }, enabled = validation == null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
