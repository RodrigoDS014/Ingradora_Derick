
package mx.edu.utez.wallet.sensors.qr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import mx.edu.utez.wallet.sensors.shared.models.SensorError
import mx.edu.utez.wallet.sensors.shared.models.SensorResult

/**
 * Escáner QR con solicitud de permiso integrada.
 */
@Composable
fun QrScannerView(
    onResult: (SensorResult<String>) -> Unit,
    onDismiss: () -> Unit
) {

    val context = LocalContext.current

    var granted by remember {
        mutableStateOf(
            QrPermissionsHandler.hasCameraPermission(context)
        )
    }

    val request = rememberCameraPermissionRequester(
        onGranted = {
            granted = true
        },
        onDenied = {
            onResult(
                SensorResult.Failure(
                    SensorError.PERMISSION_DENIED,
                    "Permite el acceso a la cámara en los ajustes de la aplicación"
                )
            )
            onDismiss()
        }
    )

    LaunchedEffect(Unit) {
        if (!granted) request()
    }

    if (granted) {

        QrScanner(
            onCode = {
                onResult(SensorResult.Success(it))
            },
            dismiss = onDismiss
        )
    }
}
