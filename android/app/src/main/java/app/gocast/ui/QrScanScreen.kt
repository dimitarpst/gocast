package app.gocast.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.gocast.R
import app.gocast.domain.JoinUrlParser
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ScanScrim = Color(0x99000000)

/**
 * A code seen by the camera: its corners in screen pixels, and whether it is a GoCast join
 * link. `gocast` is null while the code is found but not readable yet (too far, blurred).
 */
private class Detection(val corners: List<Offset>, val gocast: Boolean?)

private fun cameraGranted(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/** Full-screen QR scanner whose frame springs onto the code it finds. */
@Composable
fun QrScanScreen(
    onScanned: (Uri) -> Unit,
    onBack: () -> Unit,
    onFindWifi: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCamera by remember { mutableStateOf(cameraGranted(context)) }
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCamera = granted
        denied = !granted
    }
    LaunchedEffect(Unit) {
        if (!hasCamera) permission.launch(Manifest.permission.CAMERA)
    }
    // Coming back from system Settings may have turned the camera on.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && cameraGranted(context)) {
                hasCamera = true
                denied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Light status-bar icons over the dark camera.
    val view = LocalView.current
    DisposableEffect(view) {
        val insets = (view.context as? Activity)?.window
            ?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = insets?.isAppearanceLightStatusBars
        insets?.isAppearanceLightStatusBars = false
        onDispose { if (wasLight != null) insets?.isAppearanceLightStatusBars = wasLight }
    }
    BackHandler(onBack = onBack)

    var detection by remember { mutableStateOf<Detection?>(null) }
    var seen by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Uri?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val hasFlash = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    // Let the frame settle back to its resting square once the code leaves view.
    LaunchedEffect(seen) {
        if (seen == 0) return@LaunchedEffect
        delay(500)
        if (result == null) detection = null
    }
    // Hold the wrapped frame for a beat so the snap is visible, then connect.
    LaunchedEffect(result) {
        val uri = result ?: return@LaunchedEffect
        delay(450)
        onScanned(uri)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCamera) {
            CameraPreview(torchOn = torchOn) { corners, raw ->
                if (result == null) {
                    val uri = raw?.let(Uri::parse)
                    val gocast = if (raw == null) null else JoinUrlParser.parse(uri) != null
                    detection = Detection(corners, gocast)
                    seen++
                    if (gocast == true && uri != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        result = uri
                    }
                }
            }
            ScanFrame(detection)
        }

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.back), tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            ScanPanel(
                denied = denied && !hasCamera,
                invalid = detection?.gocast == false,
                hasFlash = hasFlash && hasCamera,
                torchOn = torchOn,
                onToggleTorch = { torchOn = !torchOn },
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
                onFindWifi = onFindWifi,
            )
        }
    }
}

@Composable
private fun ScanPanel(
    denied: Boolean,
    invalid: Boolean,
    hasFlash: Boolean,
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    onOpenSettings: () -> Unit,
    onFindWifi: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_qr_code),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(
                        when {
                            denied -> R.string.scan_qr_camera_denied
                            invalid -> R.string.scan_qr_invalid
                            else -> R.string.scan_qr_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (invalid && !denied) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (denied) {
                    FilledTonalButton(onClick = onOpenSettings, shape = CircleShape, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.scan_open_settings))
                    }
                } else if (hasFlash) {
                    FilledTonalButton(
                        onClick = onToggleTorch,
                        shape = CircleShape,
                        modifier = Modifier.weight(1f),
                        colors = if (torchOn) {
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            ButtonDefaults.filledTonalButtonColors()
                        },
                    ) {
                        Text(stringResource(R.string.scan_flashlight))
                    }
                }
                FilledTonalButton(onClick = onFindWifi, shape = CircleShape, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.find_on_wifi))
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    torchOn: Boolean,
    onCode: (List<Offset>, String?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val controller = remember { LifecycleCameraController(context) }
    DisposableEffect(controller, lifecycleOwner) {
        // Potential barcodes = codes located but not decodable yet, so the frame can lock on early.
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAllPotentialBarcodes()
                .build(),
        )
        val executor = Executors.newSingleThreadExecutor()
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        // Default analysis is 640×480; more pixels read the QR from farther away.
        controller.setImageAnalysisResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        android.util.Size(1280, 960),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build(),
        )
        controller.setImageAnalysisAnalyzer(executor, QrAnalyzer(scanner) { corners, raw -> currentOnCode(corners, raw) })
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            scanner.close()
            executor.shutdown()
        }
    }
    LaunchedEffect(torchOn) { controller.enableTorch(torchOn) }
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * Reads QR codes and reports their corners in PreviewView pixels. CameraController hands
 * us the sensor-to-view matrix; this is the same mapping CameraX's MlKitAnalyzer uses.
 */
private class QrAnalyzer(
    private val scanner: BarcodeScanner,
    private val onCode: (List<Offset>, String?) -> Unit,
) : ImageAnalysis.Analyzer {
    @Volatile private var sensorToView: Matrix? = null

    override fun getTargetCoordinateSystem() = ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED

    override fun updateTransform(matrix: Matrix?) {
        sensorToView = matrix?.let(::Matrix)
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val image = proxy.image
        val toView = sensorToView
        if (image == null || toView == null) {
            proxy.close()
            return
        }
        val rotation = proxy.imageInfo.rotationDegrees
        val analysisToView = Matrix()
        Matrix(proxy.imageInfo.sensorToBufferTransformMatrix).apply {
            val source = RectF(0f, 0f, proxy.width.toFloat(), proxy.height.toFloat())
            val rotated = if (rotation % 180 == 0) RectF(source) else RectF(0f, 0f, source.height(), source.width())
            postConcat(rectToRect(source, rotated, rotation))
            invert(analysisToView)
        }
        analysisToView.postConcat(toView)
        // ML Kit delivers results on the main thread.
        scanner.process(InputImage.fromMediaImage(image, rotation, analysisToView))
            .addOnSuccessListener { codes ->
                val located = codes.filter { it.cornerPoints?.size == 4 }
                val code = located.firstOrNull { it.rawValue != null }
                    ?: located.firstOrNull()
                    ?: return@addOnSuccessListener
                onCode(code.cornerPoints!!.map { Offset(it.x.toFloat(), it.y.toFloat()) }, code.rawValue)
            }
            .addOnCompleteListener { proxy.close() }
    }

    private fun rectToRect(source: RectF, target: RectF, rotation: Int) = Matrix().apply {
        val normalized = RectF(-1f, -1f, 1f, 1f)
        setRectToRect(source, normalized, Matrix.ScaleToFit.FILL)
        postRotate(rotation.toFloat())
        postConcat(Matrix().apply { setRectToRect(normalized, target, Matrix.ScaleToFit.FILL) })
    }
}

@Composable
private fun ScanFrame(detection: Detection?) {
    var size by remember { mutableStateOf(Size.Zero) }
    val padding = with(LocalDensity.current) { 14.dp.toPx() }
    val target = when {
        size == Size.Zero -> emptyList()
        detection != null -> clockwiseFromTopLeft(detection.corners).map { it.awayFrom(centroid(detection.corners), padding) }
        else -> restingSquare(size)
    }
    val corners = remember { List(4) { Animatable(Offset.Zero, Offset.VectorConverter) } }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        if (target.isEmpty()) return@LaunchedEffect
        if (!placed) {
            corners.forEachIndexed { i, corner -> corner.snapTo(target[i]) }
            placed = true
        } else {
            val springy = spring<Offset>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)
            corners.forEachIndexed { i, corner -> launch { corner.animateTo(target[i], springy) } }
        }
    }
    val color by animateColorAsState(
        when (detection?.gocast) {
            true -> MaterialTheme.colorScheme.primary
            false -> MaterialTheme.colorScheme.error
            null -> Color.White
        },
        label = "frame",
    )
    Canvas(Modifier.fillMaxSize().onSizeChanged { size = it.toSize() }) {
        if (!placed) return@Canvas
        val quad = corners.map { it.value }
        val hole = Path().apply {
            moveTo(quad[0].x, quad[0].y)
            quad.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        val scrim = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, this@Canvas.size))
            addPath(hole)
        }
        drawPath(scrim, ScanScrim)
        val stroke = Stroke(
            width = 5.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.cornerPathEffect(10.dp.toPx()),
        )
        val arm = 30.dp.toPx()
        quad.forEachIndexed { i, corner ->
            val before = quad[(i + 3) % 4]
            val after = quad[(i + 1) % 4]
            val bracket = Path().apply {
                val a = corner.toward(before, arm)
                val b = corner.toward(after, arm)
                moveTo(a.x, a.y)
                lineTo(corner.x, corner.y)
                lineTo(b.x, b.y)
            }
            drawPath(bracket, color, style = stroke)
        }
    }
}

/** Resting frame: a square a little above centre, clear of the bottom panel. */
private fun restingSquare(size: Size): List<Offset> {
    val side = min(size.width, size.height) * 0.66f
    val left = (size.width - side) / 2f
    val top = size.height * 0.40f - side / 2f
    return listOf(
        Offset(left, top),
        Offset(left + side, top),
        Offset(left + side, top + side),
        Offset(left, top + side),
    )
}

private fun centroid(points: List<Offset>) =
    Offset(points.sumOf { it.x.toDouble() }.toFloat() / points.size, points.sumOf { it.y.toDouble() }.toFloat() / points.size)

/** ML Kit starts at the code's own top-left; reorder to the screen's so the frame never twists. */
private fun clockwiseFromTopLeft(points: List<Offset>): List<Offset> {
    val c = centroid(points)
    val sorted = points.sortedBy { atan2(it.y - c.y, it.x - c.x) }
    val start = sorted.indices.minBy { sorted[it].x + sorted[it].y }
    return List(4) { sorted[(start + it) % 4] }
}

private fun Offset.awayFrom(center: Offset, distance: Float): Offset {
    val d = this - center
    val length = d.getDistance()
    return if (length == 0f) this else this + d / length * distance
}

private fun Offset.toward(other: Offset, maxLength: Float): Offset {
    val d = other - this
    val length = d.getDistance()
    return if (length == 0f) this else this + d / length * min(maxLength, length * 0.35f)
}
