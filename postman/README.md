# Pruebas locales de API

Colección v3: `postman/collections/utez-wallet`. Mantiene solicitudes y aserciones
derivadas de `docs/openapi.yaml`. La colección cubre una selección de regresiones;
las pruebas Kotlin cubren concurrencia, vencimientos y persistencia.

Ejecuta en orden contra **un servidor de pruebas** con las contraseñas iniciales.
La colección recarga $10 y compra un café de $25; cambia el saldo ficticio y registra
un acceso. Inactiva y reactiva al alumno inicial, crea un alumno temporal y lo da de baja.
También crea y retira un producto de prueba, revisa un comprobante y prueba la revocación biométrica.
No ejecutes solo el paso de inactivación si necesitas conservar el estatus activo.

```powershell
$env:WALLET_DATA_FILE = "$PWD/.tools/postman-wallet.json"
.\gradlew.bat -PbackendOnly :server:run
```

En otra terminal, con Postman CLI compatible con colecciones v3:

```powershell
postman spec lint docs/openapi.yaml --issueType=syntax
postman collection lint postman/collections/utez-wallet
postman collection run postman/collections/utez-wallet --no-report-events
```

No requiere un workspace ni publicar recursos. `baseUrl` está definido a nivel de
colección. Tokens, identificadores y valores esperados se encadenan durante la corrida;
las claves de idempotencia cambian entre corridas. La cuenta demo debe tener saldo
suficiente para comprar el café. La segunda ejecución reduce de nuevo el saldo $15.
Los QR se consumen inmediatamente dentro de sus 45 segundos de vigencia.

La validación de gobernanza y la opción `--output` pueden requerir inicio de sesión;
no se necesitan para la validación local de sintaxis ni para ejecutar estas pruebas.
