# Contrato inicial — UTEZ Wallet v1

Prototipo escolar. Prefijo `/api/v1` salvo `/health`, que vive en la raíz. JSON UTF-8. Importes enteros en centavos MXN.
Servidor inicial con almacenamiento JSON local; Oracle y NFC físico quedan pendientes.

## Ampliación 0.2

- `POST /auth/biometric`: sesión autenticada sin contraseña pendiente; emite `{id, token,
  expiresAt}` para este dispositivo, vigente 30 días. Solo se persiste SHA-256 del token.
- `POST /auth/biometric/login`: público, recibe `{token}` y devuelve una sesión normal;
  se comprueban cuenta, vencimiento y revocación. Android guarda el token cifrado con
  una clave de Keystore que exige biometría fuerte en cada uso; nunca guarda la contraseña.
- `DELETE /auth/biometric/{id}`: revoca únicamente un permiso biométrico propio.
  Cambiar/restablecer contraseña o dar de baja revoca todos los permisos del alumno.
- `POST /tokens/inspect`: administrador, `{token}`; devuelve alumno, finalidad, vencimiento,
  importe y líneas de compra. Revisa firma/estado sin consumir el código. Cobrar o validar
  exige una confirmación aparte y vuelve a comprobar las reglas.
- `POST /admin/students/{id}/password`: administrador, `{temporaryPassword}`; fuerza cambio,
  revoca sesiones, QR y permisos biométricos. La contraseña no aparece en respuestas o auditoría.
- `GET /admin/students/{id}/movements`: administrador; últimos 100 movimientos del alumno.
- `GET /admin/audit?studentId=...`: administrador; últimos 200 eventos, con autor, acción,
  fecha y detalle; para alumno incluye alta/edición/estatus/baja/restablecimiento.
- `GET/POST /admin/products` y `PUT/DELETE /admin/products/{id}`: administrador; nombre,
  descripción, precio en centavos y disponibilidad. El borrado es lógico.
- `GET /receipts/{id}`: comprobante por ID de movimiento, solo alumno propietario o administrador.
  Incluye líneas, importe, nombre/matrícula, fecha, método y aviso de simulación.

Los pedidos nuevos guardan nombre y precio unitario de cada producto al crearse.
Cambiar el catálogo no modifica un pedido ya revisado (vigencia: 5 minutos). Desactivar
o retirar un producto impide autorizarlo o cobrarlo; no modifica comprobantes anteriores.
Los campos nuevos de persistencia tienen valores por defecto para conservar los datos de v0.1.
La app tiene modo Demo local y modo Servidor explícitos; nunca cambia de modo al fallar la red.

Autenticación: `Authorization: Bearer <token>` opaco, aleatorio, con vencimiento de 8 horas.
Sesiones en memoria; reiniciar el servidor las invalida. El servidor verifica rol y estado actuales.
Errores: `{ "code": "INSUFFICIENT_FUNDS", "message": "Saldo insuficiente" }`.
401 sesión inválida, 403 permiso/estado/contraseña pendiente, 404 recurso ausente,
409 conflicto/reutilización, 422 datos inválidos. Las recargas y cobros requieren
`Idempotency-Key` (máximo 100 caracteres); repetir la misma clave y cuerpo devuelve el mismo resultado.
Reutilizar la clave con otro cuerpo devuelve 409. El estado actual se vuelve a validar en cada solicitud.

| Método | Ruta | Permiso | Comportamiento |
| --- | --- | --- | --- |
| GET | /health | Público | Estado y almacenamiento utilizado |
| POST | /auth/login | Público | `username`, `password`; devuelve sesión y usuario |
| POST | /auth/logout | Autenticado | Revoca sesión actual |
| POST | /auth/password | Autenticado | `currentPassword`, `newPassword`; revoca otras sesiones |
| GET | /me | Autenticado | Perfil, rol, estado y saldo |
| PUT | /me/photo | Alumno | `photoBase64`: JPEG de hasta 200 KB |
| GET | /me/movements | Alumno | Historial descendente, máximo 100 registros |
| GET | /catalog | Autenticado | Productos y precios oficiales del prototipo |
| POST | /wallet/topups | Alumno activo | `amountCents`, `testCard`; recarga simulada, idempotente |
| POST | /access/simulate | Alumno activo | Acceso explícitamente simulado; no prueba lectura NFC |
| POST | /tokens/access | Alumno activo | QR firmado de acceso, 45 segundos y un uso |
| POST | /tokens/access/redeem | Administrador | `token`; valida acceso y consume QR |
| POST | /orders | Alumno activo / administrador | `studentId` (admin), `items` de `productId`, `quantity`; precios calculados por servidor |
| POST | /orders/{id}/approve | Alumno propietario activo | QR firmado ligado a orden e importe, 45 segundos |
| POST | /payments | Admin / alumno propietario | `token`; consume QR y descuenta una sola vez; alumno representa autoterminal de demo |
| GET | /admin/students | Administrador | `query`, `career`, `status`; listado filtrado |
| POST | /admin/students | Administrador | Alta con contraseña temporal y cambio obligatorio |
| PUT | /admin/students/{id} | Administrador | Edición de nombre, matrícula, carrera y correo |
| PATCH | /admin/students/{id}/status | Administrador | `status`: ACTIVO / INACTIVO |
| DELETE | /admin/students/{id} | Administrador | Baja lógica, conserva movimientos y revoca operaciones |

Los alumnos inactivos pueden consultar su perfil e historial pero no recargar, pagar ni acceder.
Los tokens de acceso no sirven para pagos. El QR no contiene fotografía ni datos bancarios.
La firma y su vencimiento se comprueban con el reloj del servidor; un token consumido no se reutiliza.
Las órdenes vencen a los 5 minutos. Una orden pagada no se puede volver a aprobar.
Saldo, movimiento, token consumido y resultado idempotente se guardan como una sola actualización.
Se permite una sola instancia del servidor sobre el archivo local. No es una solución de producción.

OpenAPI es la definición estructurada; este documento explica decisiones. La colección Postman
se genera del contrato y añade aserciones de regresión. No se publica ningún recurso externo.
