package app.gocast.qr

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.image.BufferedImage

object QrCodeEncoder {
    fun encode(text: String, size: Int = 512): ImageBitmap{
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)

        for(x in 0 until size){
            for(y in 0 until size){
                val color = if(matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                image.setRGB(x, y, color)
            }
        }

        return image.toComposeImageBitmap()
    }
}