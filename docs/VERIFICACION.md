# Verificación de la primera entrega

## Versión 0.2.0 — 11 de noviembre de 2026

- APK debug generado desde la nueva carpeta con una compilación limpia.
- 29 pruebas de Gradle sin fallos: 23 del núcleo, 4 del servidor y 2 del decodificador QR.
- 36 solicitudes y 68 aserciones de Postman sin fallos contra el servidor local real,
  con datos ficticios separados. Se cubrieron la revocación biométrica, revisión QR
  sin consumo, restablecimiento de contraseña, auditoría, catálogo y comprobantes.
- OpenAPI y colección válidos. Android Lint: 0 errores y 18 advertencias.
- La lectura de imágenes QR se probó en JVM; la cámara y la biometría de un teléfono
  físico quedan por verificar con los pasos del README.

## Verificación de la versión 0.1.0

Fecha: 22 de octubre de 2026.

| Comprobación | Resultado |
| --- | --- |
| Compilación de la app Android | Correcta; APK debug generado e instalado |
| Pruebas del núcleo compartido | 18 pruebas, 0 fallos |
| Pruebas HTTP y persistencia del servidor | 4 pruebas, 0 fallos |
| Colección Postman contra servidor local real | 21 solicitudes y 41 aserciones, 0 fallos |
| Estructura de la colección Postman v3 | 21 solicitudes, 0 errores y 0 advertencias |
| Sintaxis OpenAPI | Sin incidencias; validación local, sin gobernanza de cuenta |
| Android Lint | 0 errores y 11 advertencias de actualización de versiones/estilo |
| Inicio en emulador Android 36 | Instalación y apertura correctas; se identificaron textos y controles del login |
| Recorridos visuales completos | Pendientes de completar en teléfono o emulador estable |

Las pruebas del núcleo incluyen permisos, contraseña inicial, invalidación de sesiones,
alumnos inactivos, saldo insuficiente, compras simultáneas, reintentos de recarga/cobro,
tokens vencidos, manipulados o reutilizados, baja lógica y escritura fallida sin saldo parcial.

La colección se ejecutó contra el servidor Ktor real en `127.0.0.1:8087`, con un archivo
separado de datos ficticios. No utilizó mocks ni publicó resultados a un workspace.

Comandos de verificación:

```powershell
.\gradlew.bat :shared:test :server:test :server:installDist :app:assembleDebug :app:lintDebug
postman collection lint postman/collections/utez-wallet
postman spec lint docs/openapi.yaml --issueType=syntax
postman collection run postman/collections/utez-wallet --env-var 'baseUrl=http://127.0.0.1:8087/api/v1' --no-report-events
```

La última compilación y revisión de Android también se completó con `--offline`,
usando las dependencias descargadas. Los reportes JUnit están en
`shared/build/test-results/test/` y `server/build/test-results/test/`.
El reporte Android está en `app/build/reports/lint-results-debug.html`.

## Límite de la revisión visual

Se intentaron dispositivos Android 37 y 36. El sistema del emulador presentó reinicios
de SurfaceFlinger con `Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma`.
En Android 36 se logró instalar y abrir la aplicación, pero el fallo del sistema volvió
a interrumpir la automatización. No se da por verificada la navegación completa,
la captura fotográfica ni las compras desde la interfaz Android.

El APK se puede instalar en un teléfono con Android 8.0 o posterior y probar con
**Demo en este dispositivo**, sin NFC, servidor ni conexión a Oracle.
Las reglas de las compras y recargas sí se comprobaron con las pruebas indicadas arriba.
