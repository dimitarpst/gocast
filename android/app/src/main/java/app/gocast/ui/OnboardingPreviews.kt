package app.gocast.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
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
