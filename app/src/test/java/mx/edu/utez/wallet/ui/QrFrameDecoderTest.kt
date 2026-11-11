package mx.edu.utez.wallet.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.*
import org.junit.Test

class QrFrameDecoderTest {
    @Test fun readsRotatedQrFrames() {
        val data = "wallet-qr-test." + "abcXYZ123".repeat(28)
        val size = 384
        val image = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size)
        val frame = ByteArray(size * size) { index ->
            if (image[index / size, size - 1 - index % size]) 0 else 255.toByte()
        }
        assertEquals(data, QrFrameDecoder.decode(frame, size, size))
    }
    @Test fun ignoresEmptyAndInvalidFrames() {
        assertNull(QrFrameDecoder.decode(ByteArray(64 * 64) { 255.toByte() }, 64, 64))
        assertNull(QrFrameDecoder.decode(ByteArray(4), 64, 64))
    }
}
