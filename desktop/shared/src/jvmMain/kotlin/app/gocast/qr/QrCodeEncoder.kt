package app.gocast.qr

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.nayuki.qrcodegen.QrCode
import io.nayuki.qrcodegen.QrCode.Ecc
import org.jetbrains.skia.Image
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pairing QR styled like qr-code-styling.com "extra-rounded".
 *
 * Center logo: the raw `gocast_logo_sm.svg` 1:1 artboard (800×800), placed as a
 * square — no crop, no aspect morph. Tweaks:
 * - [IMAGE_SIZE] — side of the logo square vs QR content
 * - [IMAGE_OFFSET_Y] — vertical nudge (+ down / − up), fraction of QR content
 * - [IMAGE_MARGIN_MODULES] — quiet zone around that square
 */
object QrCodeEncoder {
    private val finderColor = Color(0x73, 0xAC, 0xFE)
    private val moduleColor = Color.WHITE
    private val backgroundColor = Color(0x12, 0x14, 0x1A)

    private const val CORNER_RADIUS_FACTOR = 0.32
    private const val CANVAS = 560
    private const val MARGIN = 16

    /** Side of the 1:1 logo square, as a fraction of QR content size. */
    private const val IMAGE_SIZE = 0.32

    /**
     * Vertical position of the logo square center, as a fraction of QR content.
     * `0` = dead center; positive moves down; negative moves up.
     * Tweak this manually until it looks right.
     */
    private const val IMAGE_OFFSET_Y = 0.0

    /** Quiet zone around the logo square, in module widths. */
    private const val IMAGE_MARGIN_MODULES = 0.75

    /** Matches `gocast_logo_sm.svg` artboard + embedded image placement. */
    private const val SVG_SIZE = 800
    private const val SVG_IMG_X = 35
    private const val SVG_IMG_Y = 224
    private const val SVG_IMG_W = 730
    private const val SVG_IMG_H = 484

    private val logoSquare: BufferedImage by lazy { loadLogoSquareFromSvg() }

    fun encode(text: String): ImageBitmap {
        // Quartile ECC (~25%) leaves room for the center logo.
        val qr = QrCode.encodeText(text, Ecc.QUARTILE)

        val image = BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

            g.color = backgroundColor
            g.fillRect(0, 0, CANVAS, CANVAS)

            val count = qr.size
            val content = CANVAS - 2 * MARGIN
            val dot = content.toDouble() / count
            val x0 = (CANVAS - count * dot) / 2.0
            val y0 = (CANVAS - count * dot) / 2.0

            val logoSide = content * IMAGE_SIZE
            val quiet = dot * IMAGE_MARGIN_MODULES
            val holeHalf = logoSide / 2 + quiet
            val cx = CANVAS / 2.0
            val cy = CANVAS / 2.0 + content * IMAGE_OFFSET_Y

            paintModules(g, qr, x0, y0, dot, cx, cy, holeHalf)
            paintFinders(g, count, x0, y0, dot)
            paintLogoSquare(g, cx, cy, logoSide)
        } finally {
            g.dispose()
        }

        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return Image.makeFromEncoded(baos.toByteArray()).toComposeImageBitmap()
    }

    private fun paintModules(
        g: Graphics2D,
        qr: QrCode,
        x0: Double,
        y0: Double,
        dot: Double,
        cx: Double,
        cy: Double,
        holeHalf: Double,
    ) {
        val n = qr.size

        fun visible(row: Int, col: Int): Boolean {
            if (row !in 0 until n || col !in 0 until n) return false
            if (!qr.getModule(col, row)) return false
            if (isFinderCell(row, col, n)) return false
            val mx = x0 + col * dot + dot / 2
            val my = y0 + row * dot + dot / 2
            // Square quiet zone matching the 1:1 logo artboard.
            return abs(mx - cx) > holeHalf || abs(my - cy) > holeHalf
        }

        g.color = moduleColor
        for (row in 0 until n) {
            for (col in 0 until n) {
                if (!visible(row, col)) continue
                drawExtraRoundedDot(
                    g,
                    x0 + col * dot,
                    y0 + row * dot,
                    dot,
                    left = visible(row, col - 1),
                    right = visible(row, col + 1),
                    top = visible(row - 1, col),
                    bottom = visible(row + 1, col),
                )
            }
        }
    }

    /** Draw the SVG artboard as a transparent 1:1 square — no plate, no crop. */
    private fun paintLogoSquare(g: Graphics2D, cx: Double, cy: Double, side: Double) {
        val x = (cx - side / 2).roundToInt()
        val y = (cy - side / 2).roundToInt()
        val px = side.roundToInt().coerceAtLeast(1)
        val old = g.composite
        g.composite = AlphaComposite.SrcOver
        g.drawImage(logoSquare, x, y, px, px, null)
        g.composite = old
    }

    /**
     * Rebuilds `gocast_logo_sm.svg` as an 800×800 ARGB square (Skia can't paint
     * the embedded data-URI PNG, so we place it exactly as the SVG does).
     */
    private fun loadLogoSquareFromSvg(): BufferedImage {
        val svg = QrCodeEncoder::class.java.getResourceAsStream("/gocast_logo_sm.svg")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Missing resource: /gocast_logo_sm.svg")
        val match = Regex("""data:image/png;base64,([A-Za-z0-9+/=]+)""").find(svg)
            ?: error("gocast_logo_sm.svg has no embedded PNG")
        val mark = ImageIO.read(ByteArrayInputStream(Base64.getDecoder().decode(match.groupValues[1])))
            ?: error("Failed to decode embedded logo PNG")

        val square = BufferedImage(SVG_SIZE, SVG_SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = square.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(mark, SVG_IMG_X, SVG_IMG_Y, SVG_IMG_W, SVG_IMG_H, null)
        } finally {
            g.dispose()
        }
        return square
    }

    /** Port of kozakdenys/qr-code-styling `QRDot._drawExtraRounded`. */
    private fun drawExtraRoundedDot(
        g: Graphics2D,
        x: Double,
        y: Double,
        size: Double,
        left: Boolean,
        right: Boolean,
        top: Boolean,
        bottom: Boolean,
    ) {
        // Overlap neighbors slightly so Java2D antialiasing doesn't leave dark grid seams.
        val pad = max(1.0, size * 0.05)
        val dx = x - pad / 2
        val dy = y - pad / 2
        val s = size + pad
        val neighbors = listOf(left, right, top, bottom).count { it }

        when {
            neighbors == 0 -> g.fill(Ellipse2D.Double(dx, dy, s, s))
            neighbors > 2 || (left && right) || (top && bottom) -> {
                g.fill(Rectangle2D.Double(dx, dy, s, s))
            }
            neighbors == 2 -> {
                val rotation = when {
                    left && top -> PI / 2
                    top && right -> PI
                    right && bottom -> -PI / 2
                    else -> 0.0
                }
                fillRotated(g, dx, dy, s, rotation, ::cornerExtraRoundedPath)
            }
            neighbors == 1 -> {
                val rotation = when {
                    top -> PI / 2
                    right -> PI
                    bottom -> -PI / 2
                    else -> 0.0
                }
                fillRotated(g, dx, dy, s, rotation, ::sideRoundedPath)
            }
        }
    }

    private fun fillRotated(
        g: Graphics2D,
        x: Double,
        y: Double,
        size: Double,
        rotation: Double,
        build: (Double) -> Path2D.Double,
    ) {
        val path = build(size)
        val tx = AffineTransform()
        tx.translate(x, y)
        tx.rotate(rotation, size / 2, size / 2)
        g.fill(tx.createTransformedShape(path))
    }

    private fun sideRoundedPath(size: Double): Path2D.Double {
        val path = Path2D.Double()
        path.moveTo(0.0, 0.0)
        path.lineTo(0.0, size)
        path.lineTo(size / 2, size)
        path.append(Arc2D.Double(0.0, 0.0, size, size, 270.0, 180.0, Arc2D.OPEN), true)
        path.closePath()
        return path
    }

    private fun cornerExtraRoundedPath(size: Double): Path2D.Double {
        val path = Path2D.Double()
        path.moveTo(0.0, 0.0)
        path.lineTo(0.0, size)
        path.lineTo(size, size)
        path.append(Arc2D.Double(-size, 0.0, size * 2, size * 2, 0.0, 90.0, Arc2D.OPEN), true)
        path.closePath()
        return path
    }

    private fun paintFinders(g: Graphics2D, count: Int, x0: Double, y0: Double, dot: Double) {
        val size = 7 * dot
        for ((mx, my) in listOf(0 to 0, (count - 7) to 0, 0 to (count - 7))) {
            drawFinder(g, x0 + mx * dot, y0 + my * dot, size)
        }
    }

    private fun drawFinder(g: Graphics2D, x: Double, y: Double, size: Double) {
        val module = size / 7.0
        val outerRadius = size * CORNER_RADIUS_FACTOR
        val gapRadius = size * 0.24
        val eyeRadius = size * 0.22

        g.color = finderColor
        g.fill(RoundRectangle2D.Double(x, y, size, size, outerRadius, outerRadius))

        g.color = backgroundColor
        g.fill(
            RoundRectangle2D.Double(
                x + module,
                y + module,
                size - 2 * module,
                size - 2 * module,
                gapRadius,
                gapRadius,
            ),
        )

        g.color = finderColor
        g.fill(
            RoundRectangle2D.Double(
                x + 2 * module,
                y + 2 * module,
                size - 4 * module,
                size - 4 * module,
                eyeRadius,
                eyeRadius,
            ),
        )
    }

    private fun isFinderCell(row: Int, col: Int, size: Int): Boolean {
        val inTopLeft = row < 7 && col < 7
        val inTopRight = row < 7 && col >= size - 7
        val inBottomLeft = row >= size - 7 && col < 7
        return inTopLeft || inTopRight || inBottomLeft
    }
}
