# UCV Marcador para Android

Desarrollado por @GabRec

Versión Android de UCV Marcador. Marca en UCV Talent a las horas configuradas,
en segundo plano, aunque el teléfono esté bloqueado.

## Instalar
1. Copia `apk/UCVMarcador_1.0.3.apk` al teléfono (cable, Drive, WhatsApp, etc.).
2. Ábrelo y permite «Instalar apps desconocidas» para la app con la que lo abriste.
3. Requisitos: Android 8.0 o superior, conexión a internet y ubicación activada.

## Primer uso
1. **Iniciar sesión**: inicia sesión en UCV Talent y pulsa «Listo». La sesión queda guardada.
2. **Permisos** › «Conceder permisos pendientes» (pulsar varias veces hasta ver todo en ✔):
   notificaciones, alarmas exactas, ubicación, ubicación «Permitir todo el tiempo»
   y sin restricción de batería.
3. **Horarios**: pon la hora exacta a la que debe marcar cada turno.
   La salida solo se puede marcar entre 20 y 1 minuto antes de la hora programada
   (ej. salida 22:00 → configura 21:55).
4. **Prueba manual** › «Ejecutar visible» para ver el recorrido paso a paso.
5. Deja **Modo solo evidencia** activado hasta comprobar las capturas
   (Galería › Pictures/UCVMarcador). Luego desactívalo para marcar de verdad.

## Cómo funciona
- Alarma exacta del sistema (`setAlarmClock`) a la hora configurada, incluso en reposo.
- Un servicio en primer plano abre UCV Talent en un WebView oculto, pulsa la pestaña
  «Marcación manual», el turno y «Marcar», guarda capturas y notifica el resultado.
- La ubicación es la real del GPS del teléfono; solo se comparte con ucv.edu.pe.
- Si el teléfono estuvo apagado y la hora pasó hace más de 2 minutos, no marca tarde.
- Tras reiniciar el teléfono la alarma se reprograma sola.

## Desinstalar
Desde la app: «Acerca de › Desinstalar», o manteniendo pulsado el ícono › Desinstalar.

## Compilar (desarrollador)
Requiere Android Studio (usa su JDK) y el SDK de Android.
```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat assembleRelease
```
El APK queda en `app/build/outputs/apk/release/app-release.apk`.

**Firma:** `ucvmarcador.jks` y `keystore.properties` son la llave de firma.
Guárdalos en un lugar seguro y no los compartas: sin ellos no se pueden publicar
actualizaciones que se instalen encima de la versión actual.

## Archivos principales (`app/src/main/java/pe/gabrec/ucvmarcador/`)
- `MainActivity` – pantalla principal (horarios, permisos, prueba, registro)
- `LoginActivity` – inicio de sesión en UCV Talent
- `PruebaActivity` – prueba con navegador visible
- `Marcador` – motor de marcación (pasos en la página)
- `MarcadorService` – marcación en segundo plano
- `Programador`, `AlarmReceiver`, `BootReceiver` – alarmas exactas
- `Evidencias`, `Registro`, `Notificaciones`, `Config`

## Google Play
- Nombre de paquete: `pe.gabrec.ucvmarcador` (no se puede cambiar después).
- Sube `apk/UCVMarcador_1.0.2.aab` (Play no acepta APK). Generar: `gradlew.bat bundleRelease`.
- Acepta «Firma de apps de Google Play»: `ucvmarcador.jks` queda como llave de subida.
- Cada versión nueva debe subir `versionCode` en `app/build.gradle`.
