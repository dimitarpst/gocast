package app.gocast.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.gocast.R
import app.gocast.settings.CastQualityProfile

@Composable
internal fun SharingPreview() {
    var sharing by rememberSaveable { mutableStateOf(true) }
    PreviewCard {
        Text(
            stringResource(if (sharing) R.string.walkthrough_casting else R.string.walkthrough_stopped),
            style = MaterialTheme.typography.headlineSmall,
            color = WalkthroughColors.Text,
        )
        Text(
            stringResource(R.string.walkthrough_controls_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = WalkthroughColors.Muted,
        )
        Button(
            onClick = { sharing = !sharing },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (sharing) WalkthroughColors.Text else WalkthroughColors.Blue,
                contentColor = WalkthroughColors.Background,
            ),
        ) {
            Text(stringResource(if (sharing) R.string.stop_cast else R.string.walkthrough_try_again))
        }
    }
}

@Composable
internal fun QualityPreview() {
    var selectedId by rememberSaveable { mutableStateOf(CastQualityProfile.ID_BALANCED) }
    val profile = CastQualityProfile.BuiltIns.first { it.id == selectedId }
    PreviewCard {
        Text(
            stringResource(R.string.quality_settings),
            style = MaterialTheme.typography.titleLarge,
            color = WalkthroughColors.Text,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CastQualityProfile.BuiltIns.forEach { preset ->
                FilterChip(
                    selected = selectedId == preset.id,
                    onClick = { selectedId = preset.id },
                    label = { Text(preset.name) },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = WalkthroughColors.Surface,
                        labelColor = WalkthroughColors.Text,
                        selectedContainerColor = WalkthroughColors.Blue,
                        selectedLabelColor = WalkthroughColors.Background,
                    ),
                )
            }
        }
        Text(
            stringResource(R.string.walkthrough_preset_summary, profile.settings.captureFps, profile.settings.maxBitrateBps / 1_000_000),
            style = MaterialTheme.typography.bodyLarge,
            color = WalkthroughColors.Text,
        )
        Text(
            stringResource(R.string.walkthrough_quality_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = WalkthroughColors.Muted,
        )
    }
}

@Composable
private fun PreviewCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = WalkthroughColors.Surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
internal fun ConnectionIllustration(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.intro_illustration_description)
    Canvas(modifier.semantics { contentDescription = description }) {
        val w = size.width
        val h = size.height
        val border = Color(0xFF424751)
        val stroke = 1.5.dp.toPx()
        val screenRadius = CornerRadius(14.dp.toPx())

        // Monitor and stand. The same blue composition appears on both screens.
        drawRoundRect(
            color = border,
            topLeft = Offset(w * .35f, h * .72f),
            size = Size(w * .10f, h * .14f),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
        drawRoundRect(
            color = border,
            topLeft = Offset(w * .26f, h * .85f),
            size = Size(w * .28f, h * .025f),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
        drawRoundRect(WalkthroughColors.Surface, Offset(w * .03f, h * .08f), Size(w * .77f, h * .66f), screenRadius)
        drawRoundRect(
            border, Offset(w * .03f, h * .08f), Size(w * .77f, h * .66f), screenRadius,
            style = Stroke(stroke),
        )
        drawRoundRect(
            Color(0xFF2D323C), Offset(w * .065f, h * .14f), Size(w * .70f, h * .49f),
            CornerRadius(8.dp.toPx()),
        )
        drawRoundRect(
            WalkthroughColors.Blue, Offset(w * .09f, h * .18f), Size(w * .39f, h * .41f),
            CornerRadius(6.dp.toPx()),
        )
        drawCircle(Color(0xFFCAE0FF), w * .065f, Offset(w * .285f, h * .32f))
        drawRoundRect(
            Color(0xFF537AB7), Offset(w * .13f, h * .46f), Size(w * .27f, h * .055f),
            CornerRadius(5.dp.toPx()),
        )
        drawRoundRect(
            Color(0xFFB9C2D0), Offset(w * .51f, h * .18f), Size(w * .23f, h * .18f),
            CornerRadius(6.dp.toPx()),
        )
        drawRoundRect(
            Color(0xFF454E5F), Offset(w * .51f, h * .40f), Size(w * .23f, h * .19f),
            CornerRadius(6.dp.toPx()),
        )
        drawCircle(border, 2.dp.toPx(), Offset(w * .415f, h * .685f))

        // Phone overlaps the monitor rather than sitting in another decorative card.
        val phone = Offset(w * .70f, h * .30f)
        val phoneSize = Size(w * .25f, h * .65f)
        drawRoundRect(
            WalkthroughColors.Background, phone - Offset(3.dp.toPx(), 3.dp.toPx()),
            Size(phoneSize.width + 6.dp.toPx(), phoneSize.height + 6.dp.toPx()), screenRadius,
        )
        drawRoundRect(WalkthroughColors.Surface, phone, phoneSize, screenRadius)
        drawRoundRect(border, phone, phoneSize, screenRadius, style = Stroke(stroke))
        drawRoundRect(
            WalkthroughColors.Blue, Offset(w * .72f, h * .37f), Size(w * .21f, h * .45f),
            CornerRadius(6.dp.toPx()),
        )
        drawCircle(Color(0xFFCAE0FF), w * .04f, Offset(w * .825f, h * .52f))
        drawRoundRect(
            Color(0xFF537AB7), Offset(w * .745f, h * .70f), Size(w * .16f, h * .04f),
            CornerRadius(4.dp.toPx()),
        )
        drawLine(border, Offset(w * .80f, h * .335f), Offset(w * .85f, h * .335f), 2.dp.toPx(), StrokeCap.Round)
        drawLine(border, Offset(w * .80f, h * .90f), Offset(w * .85f, h * .90f), 2.dp.toPx(), StrokeCap.Round)
    }
}
