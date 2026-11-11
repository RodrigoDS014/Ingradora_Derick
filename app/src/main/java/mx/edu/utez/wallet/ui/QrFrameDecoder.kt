package mx.edu.utez.wallet.ui

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/** Pure decoder, also used by JVM tests with generated and rotated QR images. */
object QrFrameDecoder {
    fun decode(luminance: ByteArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || luminance.size.toLong() < width.toLong() * height) return null
        val source = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
        return try { QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.TRY_HARDER to true)).text }
            catch (_: com.google.zxing.ReaderException) { null }
    }
}
