package app.gocast.ui

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.Arc2D
import javax.swing.JPanel
import javax.swing.JWindow
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.min
import kotlin.math.sin

/**
 * Always-on-top [JWindow] owned by the main window — sits above WebView2 HWND
 * to mask white/first-paint flash. Not a nested ComposePanel (Skia crash).
 */
class CastConnectingOverlay(private val owner: Window) {
    private var overlay: JWindow? = null
    private var stageTimer: Timer? = null
    private var spinPhase = 0.0
    private var fade = 0f
    private var fadingOut = false

    private val syncBoundsListener = object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) = syncBounds()
        override fun componentMoved(e: ComponentEvent?) = syncBounds()
        override fun componentShown(e: ComponentEvent?) = syncBounds()
    }

    private val ownerWindowListener = object : WindowAdapter() {
        override fun windowIconified(e: WindowEvent?) {
            overlay?.isVisible = false
        }

        override fun windowDeiconified(e: WindowEvent?) {
            val w = overlay ?: return
            if (!w.isDisplayable) return
            syncBounds()
            w.isVisible = true
            w.toFront()
        }
    }

    /** Show on EDT over owner content bounds. Idempotent — won't restart fade-in. */
    fun show() = runOnEdt {
        ensureCreated()
        val w = overlay ?: return@runOnEdt
        // Already fading in or holding — ignore duplicate show() (e.g. recomposition).
        if (w.isVisible && !fadingOut) return@runOnEdt
        stopStage()
        fadingOut = false
        fade = 0f
        setOpacity(w, 0f)
        syncBounds()
        w.isVisible = true
        w.toFront()
        SwingUtilities.invokeLater {
            w.toFront()
            w.isAlwaysOnTop = true
        }
        stageTimer = Timer(TickMs) { tickFrame(w) }.also { it.start() }
    }

    /** Soft fade-out then dispose. Safe if never shown. */
    fun hide() = runOnEdt {
        val w = overlay ?: return@runOnEdt
        if (!w.isVisible) {
            disposeInternal()
            return@runOnEdt
        }
        fadingOut = true
        if (stageTimer == null) stageTimer = Timer(TickMs) { tickFrame(w) }.also { it.start() }
    }

    fun dispose() = runOnEdt { disposeInternal() }

    private fun tickFrame(w: JWindow) {
        spinPhase += SpinStep
        when {
            fadingOut -> {
                fade = (fade - FadeOutStep).coerceAtLeast(0f)
                setOpacity(w, fade * fade)
                w.contentPane.repaint()
                if (fade <= 0f) {
                    stopStage()
                    disposeInternal()
                }
            }
            fade < 1f -> {
                fade = (fade + FadeInStep).coerceAtMost(1f)
                val t = 1f - fade
                setOpacity(w, 1f - t * t)
                w.contentPane.repaint()
            }
            else -> w.contentPane.repaint()
        }
    }

    private fun ensureCreated() {
        if (overlay != null) return
        val window = JWindow(owner).apply {
            isAlwaysOnTop = true
            focusableWindowState = false
            isFocusable = false
            type = Window.Type.POPUP
            background = Bg
        }
        window.contentPane = object : JPanel(null) {
            init { isOpaque = true; background = Bg }
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                paintConnecting(g as Graphics2D, width, height, spinPhase)
            }
        }
        owner.addComponentListener(syncBoundsListener)
        owner.addWindowListener(ownerWindowListener)
        overlay = window
    }

    private fun syncBounds() {
        val w = overlay ?: return
        if (!owner.isShowing) return
        try {
            val c = if (owner is RootPaneContainer) owner.contentPane else owner
            val loc = c.locationOnScreen
            if (c.width > 0 && c.height > 0) w.setBounds(loc.x, loc.y, c.width, c.height)
        } catch (_: Exception) {
            w.setBounds(owner.x, owner.y, owner.width, owner.height)
        }
    }

    private fun disposeInternal() {
        stopStage()
        owner.removeComponentListener(syncBoundsListener)
        owner.removeWindowListener(ownerWindowListener)
        overlay?.isVisible = false
        overlay?.dispose()
        overlay = null
        fade = 0f
        fadingOut = false
    }

    private fun stopStage() {
        stageTimer?.stop()
        stageTimer = null
    }

    private companion object {
        val Bg = Color(0x12, 0x14, 0x1A)
        val Track = Color(0x1E, 0x22, 0x2C)
        val Text = Color(0xE8.toInt(), 0xEC.toInt(), 0xF5.toInt())
        val Accent = Color(0x6E, 0xA8.toInt(), 0xFE.toInt())
        val LabelFont = Font("Segoe UI", Font.PLAIN, 13)
        val RingStroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        const val TickMs = 16
        const val SpinStep = 0.085
        // ~1s fade-in (matches CastOverlayFadeInMs in App); leave fade-out as-is.
        const val FadeInStep = 0.016f
        const val FadeOutStep = 0.055f

        fun paintConnecting(g: Graphics2D, w: Int, h: Int, phase: Double) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                val cx = w / 2.0
                val cy = h / 2.0 - 8.0
                val size = min(w, h).coerceAtMost(56).toDouble()
                val x = cx - size / 2
                val y = cy - size / 2
                g2.stroke = RingStroke
                g2.color = Track
                g2.draw(Arc2D.Double(x, y, size, size, 0.0, 360.0, Arc2D.OPEN))
                g2.color = Accent
                g2.draw(Arc2D.Double(x, y, size, size, Math.toDegrees(phase), -(88.0 + 18.0 * sin(phase * 0.55)), Arc2D.OPEN))
                g2.font = LabelFont
                g2.color = Text
                val text = "Connecting"
                val fm = g2.fontMetrics
                g2.drawString(text, ((w - fm.stringWidth(text)) / 2).toFloat(), (cy + size / 2 + 28).toFloat())
            } finally {
                g2.dispose()
            }
        }

        fun setOpacity(window: Window, value: Float) {
            try {
                window.opacity = value.coerceIn(0f, 1f)
            } catch (_: Exception) { /* platform may reject */ }
        }

        fun runOnEdt(block: () -> Unit) {
            if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
        }
    }
}
