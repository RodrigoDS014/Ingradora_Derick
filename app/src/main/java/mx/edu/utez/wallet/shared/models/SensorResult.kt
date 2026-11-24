package mx.edu.utez.wallet.sensors.shared.models

/** Motivos de fallo comunes a QR, NFC y validación. */
enum class SensorError {
    UNSUPPORTED, DISABLED, PERMISSION_DENIED, INVALID_PAYLOAD,
    IO, TAG_READ_ONLY, TAG_TOO_SMALL, REJECTED, NETWORK
}

/** Resultado uniforme de todas las operaciones del paquete sensors. */
sealed interface SensorResult<out T> {
    data class Success<out T>(val value: T) : SensorResult<T>
    data class Failure(val error: SensorError, val message: String) : SensorResult<Nothing>
}
