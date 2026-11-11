# UTEZ Student Wallet & Access Control

Prototipo Android nativo en Kotlin. Incluye interfaz oscura, servidor Kotlin/Ktor y
reglas compartidas. Se puede probar sin stickers NFC, Oracle ni tarjetas bancarias.

## Probar la aplicación

1. Abre **esta carpeta** en Android Studio y sincroniza Gradle.
2. Usa JDK 25 para Gradle e instala Android SDK 36 y Build Tools 36.0.0 si el IDE lo solicita.
3. Ejecuta el módulo `app` en un emulador o teléfono con Android 8.0 o posterior.
4. Mantén activado **Demo en este dispositivo**. Pulsa Alumno o Administrador para
   completar las credenciales y después Iniciar sesión.

| Cuenta | Usuario | Contraseña de prueba |
| --- | --- | --- |
| Alumno activo | `20240001` | `Alumno123!` |
| Administrador | `admin` | `Admin123!` |
| Alumno inactivo | `20240002` | `Alumno123!` |

El alumno inicial tiene $185.00 MXN ficticios. Las cuentas creadas por administrador
empiezan con saldo cero y deben cambiar su contraseña temporal. Los cambios sobreviven
al cierre de la app; borrar sus datos restablece la demo. Las contraseñas anteriores
dejan de funcionar si las cambias. Son cuentas exclusivas de demostración.

## Funciones implementadas

- Inicio de sesión y navegación según rol; cambio de contraseña inicial obligatorio.
- Credencial y monedero seleccionables, saldo y estatus del alumno.
- Directorio con búsqueda, filtros por carrera/estatus, alta, edición, inactivación y baja lógica.
- Recargas simuladas con una tarjeta ficticia predefinida; no solicita datos bancarios reales.
- Catálogo, carrito, revisión del importe, QR temporal y autoterminal para probar con un solo celular.
- QR de acceso firmado y temporal, con validación de un uso en terminal administrativa.
- Historial de compras, recargas y accesos; botón de acceso explícitamente simulado.
- Fotografía mediante la aplicación de cámara del dispositivo (miniatura JPEG para el prototipo).
- Modo Servidor con URL configurable. Los errores de red nunca cambian una compra a modo local.

## Probar la versión 0.2

- **Biometría en tu teléfono:** registra una huella en los ajustes de Android. Entra con
  contraseña, abre **Perfil → Activar biometría** y acepta el diálogo de Android. Pulsa
  **Bloquear y probar biometría** y luego **Entrar con biometría**. Funciona en Demo,
  sin internet ni servidor. Si el teléfono no admite biometría fuerte, usa la contraseña.
  **Cerrar sesión y olvidar acceso** elimina el permiso guardado para esa cuenta.
- **QR con cámara:** inicia el servidor local y conecta los dos teléfonos a la misma API.
  En uno, el alumno genera un QR de acceso o compra. En el otro, el administrador abre
  **Terminal → Escanear QR con cámara**. La app muestra la finalidad, el alumno y el
  total si es un cobro. Confirma para usar el QR. El pegado manual sigue disponible.
- **Administración:** en **Alumnos**, abre **Historial y cambios** para ver movimientos,
  estatus y autor de los cambios. **Restablecer clave** asigna una contraseña temporal y
  revoca las sesiones y los accesos biométricos anteriores del alumno.
- **Cafetería:** el administrador gestiona nombre, precio y disponibilidad en **Catálogo**.
  El alumno ve los artículos agotados y puede consultar o compartir comprobantes desde
  **Historial**. Los cambios de precio no alteran comprobantes anteriores.

## Ejecutar el servidor

En PowerShell, con `JAVA_HOME` apuntando a JDK 25:

```powershell
.\gradlew.bat -PbackendOnly :server:run
```

La API queda en `http://127.0.0.1:8080`; estado: `http://127.0.0.1:8080/health`.
La base provisional es un archivo `server/data/wallet.json`. El servidor lo crea automáticamente.
`WALLET_DATA_FILE` permite escoger otra ruta. Una sola instancia puede usar el archivo.
Una escritura fallida no publica el nuevo saldo. No edites el archivo con el servidor activo.
Sesiones y firmas QR se renuevan al reiniciar el servidor: vuelve a iniciar sesión y generar códigos.

Para usar un **emulador**, desactiva Demo e introduce `http://10.0.2.2:8080`.
Para usar un teléfono conectado por USB, ejecuta `adb reverse tcp:8080 tcp:8080` y usa
`http://127.0.0.1:8080` en la app. Para dos teléfonos por Wi-Fi:

```powershell
$env:WALLET_HOST = '0.0.0.0'
.\gradlew.bat -PbackendOnly :server:run
```

Usa `http://IP_DE_LA_COMPUTADORA:8080` en ambos teléfonos y permite el puerto en la red local.
Esta configuración expone el servidor a la red del salón. HTTP solo está permitido
en la compilación debug de Android; una futura entrega remota necesitará HTTPS.

Los modos Demo y Servidor tienen **datos independientes**. Dos demos en distintos
teléfonos no pueden validar los códigos entre sí. El servidor puede funcionar sin
internet externo si teléfonos y computadora comparten una red local.

## Recorridos para la exposición

**Alumno:** iniciar sesión → seleccionar monedero → recargar → cafetería → agregar productos
→ preparar compra → revisar importe → autorizar QR → pagar en autoterminal → revisar historial.

**Control escolar:** entrar como administrador → registrar alumno → iniciar sesión con su
matrícula → cambiar contraseña temporal → volver a administrador → inactivar → comprobar
que no se permiten recargas, compras ni accesos.

**QR con dos dispositivos:** ambos en modo Servidor → alumno crea QR → copia el contenido
o lo comparte al portapapeles del dispositivo de pruebas → administrador lo pega en Terminal.
La vigencia es de 45 segundos. También puedes usar el botón Escanear QR con cámara en Terminal.
En un mismo celular puedes copiar el QR de acceso, cerrar el diálogo, cerrar sesión y
entrar como administrador antes de que venza. La autoterminal evita ese cambio para las compras.

## Estructura

- `app/`: interfaz Compose, ViewModel y adaptadores local/HTTP.
- `shared/`: modelos, validaciones, autenticación, firma QR y transacciones.
- `server/`: API Ktor y persistencia local atómica.
- `docs/openapi.yaml`: contrato estructurado de la API.
- `docs/API.md`: permisos, estados y comportamiento de reintentos.
- `postman/`: colección local de regresión, sin conexión a workspace externo.

## Compilar y comprobar

```powershell
.\gradlew.bat :shared:test :server:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Solo backend, sin Android SDK: `./gradlew -PbackendOnly :shared:test :server:test`.
APK: `app/build/outputs/apk/debug/app-debug.apk`.
Reportes: `shared/build/reports/tests/test/` y `server/build/reports/tests/test/`.
La colección Postman puede importarse y ejecutarse contra un servidor de prueba.
Lee `postman/README.md` antes de ejecutarla: modifica saldo ficticio y crea un alumno de prueba.
Los resultados y el alcance de las comprobaciones realizadas están en `docs/VERIFICACION.md`.

## Pendientes y límites del prototipo

- Conectar Oracle Cloud mediante un repositorio de transacciones SQL; el almacenamiento
  JSON actual soporta un único proceso y no sustituye una base de datos multiusuario de producción.
- Leer el sticker real y decidir su formato al conocer el hardware del profesor.
- Un rol de cafetería separado del administrador, si el equipo lo necesita.
- La demo local funciona sin internet. El modo Servidor **no implementa acceso offline
  provisional** ni sincronización de eventos: sin servidor disponible, no autoriza operaciones.
- Expirar y consumir QR reduce la reutilización; una captura podría ser usada primero
  mientras siga vigente. La simulación de acceso no demuestra presencia física.
- Recargas y cobros son de prueba; no hay pasarela ni integración bancaria.
- Antes de un uso real harían falta despliegue HTTPS, límites de solicitudes, respaldo,
  gestión de secretos, auditoría y revisión del modelo de acceso físico.

Tecnologías: [Android Compose](https://developer.android.com/compose),
[Kotlin integrado en AGP](https://developer.android.com/build/migrate-to-built-in-kotlin),
[Ktor](https://ktor.io/docs/server-testing.html). Las versiones se fijan en Gradle.
