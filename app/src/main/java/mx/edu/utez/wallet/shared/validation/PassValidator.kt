package mx.edu.utez.wallet.sensors.shared.validation

import kotlinx.coroutines.CancellationException
import mx.edu.utez.wallet.core.Movement
import mx.edu.utez.wallet.core.QrReview
import mx.edu.utez.wallet.core.TransactionResult
import mx.edu.utez.wallet.core.WalletException
import mx.edu.utez.wallet.data.WalletRepository
import mx.edu.utez.wallet.sensors.shared.models.SensorError
import mx.edu.utez.wallet.sensors.shared.models.SensorResult
import java.io.IOException

/**
 * Único punto por el que QR y NFC llegan al servidor. No valida firmas ni vigencia en el
 * teléfono: eso lo hace WalletEngine (modo Demo) o el servidor Ktor, con su propio reloj.
 * Requiere una sesión de administrador ya iniciada en el repositorio.
 */
class PassValidator(private val repository: WalletRepository) {
    /** Solo lectura: no consume el código. */
    suspend fun inspect(raw: String): SensorResult<QrReview> = call(raw) { repository.inspect(it) }

    /** Consume un pase de acceso (un solo uso). */
    suspend fun redeemAccess(raw: String): SensorResult<Movement> = call(raw) { repository.redeemAccess(it) }

    /** Cobra una orden autorizada; la clave de idempotencia evita doble cargo en reintentos. */
    suspend fun pay(raw: String, idempotencyKey: String): SensorResult<TransactionResult> =
        call(raw) { repository.pay(it, idempotencyKey) }

    private suspend fun <T> call(raw: String, block: suspend (String) -> T): SensorResult<T> {
        val value = raw.trim()
        if (value.isEmpty() || value.length > MAX_LENGTH) {
            return SensorResult.Failure(SensorError.INVALID_PAYLOAD, "El código está vacío o no tiene el formato esperado")
        }
        return try {
            SensorResult.Success(block(value))
        } catch (e: CancellationException) {
            throw e
        } catch (e: WalletException) {
            SensorResult.Failure(SensorError.REJECTED, e.message)
        } catch (e: IOException) {
            SensorResult.Failure(SensorError.NETWORK, "No hay conexión con el servidor")
        } catch (e: Exception) {
            SensorResult.Failure(SensorError.NETWORK, "No se pudo validar el código")
        }
    }

    private companion object { const val MAX_LENGTH = 2048 }
}
