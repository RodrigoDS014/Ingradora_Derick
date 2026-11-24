package mx.edu.utez.wallet.sensors.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import mx.edu.utez.wallet.sensors.shared.models.SensorError
import mx.edu.utez.wallet.sensors.shared.models.SensorResult

object QrGeneratorManager {
    fun generate(value: String, size: Int = 720): SensorResult<Bitmap> {
        if (value.isBlank() || size <= 0) {
            return SensorResult.Failure(SensorError.INVALID_PAYLOAD, "No hay contenido para generar el QR")
        }
        return try {
            val matrix = MultiFormatWriter().encode(
                value, BarcodeFormat.QR_CODE, size, size,
                mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 3)
            )
            val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE }
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
            SensorResult.Success(bitmap)
        } catch (e: Exception) {
            SensorResult.Failure(SensorError.INVALID_PAYLOAD, "No se pudo generar el QR")
        }
    }
}
