# UCV Marcador

App de bandeja del sistema que inicia sesión en UCV Talent con Selenium, va a
"Marcación manual" y guarda capturas como evidencia (y, si lo activas, hace clic en "Marcar").

## Instalar en cualquier PC (sin Python)
Ejecuta `instalador\UCVMarcador_Setup_1.2.1.exe`. No pide permisos de administrador:
se instala en `%LOCALAPPDATA%\UCVMarcador`, crea accesos en el menú Inicio y, opcionalmente,
en el escritorio y en el inicio de Windows (arranca minimizado en la bandeja).
Requisito en la PC destino: Google Chrome e internet.

## Desinstalar
- Desde la app: sección **Acerca de > Desinstalar**.
- O menú Inicio > UCV Marcador > Desinstalar, o Configuración > Aplicaciones.
Se borra todo: configuración, sesión guardada, grabaciones y evidencias.

## Generar el instalador (desarrollador)
Requiere Python 3.10+ e Inno Setup 6 (`winget install JRSoftware.InnoSetup`).
Doble clic en `build_exe.bat` -> `instalador\UCVMarcador_Setup_1.2.1.exe`.

## Ejecutar desde el código
Doble clic en `run.bat` (la primera vez crea `venv` e instala dependencias).

## Primer uso
1. Abre la app y pulsa **🔑 Iniciar sesión (una vez)**. Se abre Chrome: inicia sesión
   en UCV Talent normalmente y **cierra la ventana**. La sesión queda guardada en
   `chrome_perfil/` (no se guarda tu contraseña).
2. Pulsa **▶ Probar ahora** con «Mostrar el navegador» activado para ver el recorrido.
3. Deja **Modo solo evidencia** activado hasta comprobar que las capturas
   (carpeta `evidencias`) son correctas.
4. Usuario/contraseña son opcionales: solo se usan si la sesión expira (login genérico).

Ventanas de UCV Talent (según la página): la entrada se puede marcar hasta 20 min después
de la hora; la salida desde 20 min antes hasta 1 min antes.
La página exige ubicación activa: si en headless el botón no se habilita, activa
«Mostrar el navegador».

## Grabar acciones (opcional)
En el recuadro «Grabar acciones» elige turno y tipo (ej. Tarde / Entrada) y pulsa **⏺ Grabar**.
Se abre Chrome: haz los clics como siempre (pestaña, turno, «Marcar») y cierra la ventana.
Desde entonces, a esa hora la app repite esos clics **en segundo plano** (Chrome sin ventana).
- Solo se graban clics, nunca lo que escribes.
- Antes de pulsar «Marcar» comprueba que el botón esté habilitado y respeta «Modo solo evidencia».
- Turnos/tipos sin grabación usan el flujo automático integrado.
- Las grabaciones quedan en `grabaciones/*.json`.

## Archivos
- `app.py` – interfaz + bandeja + scheduler
- `worker.py` – automatización con Selenium
- `grabador.py` – grabar clics y reproducirlos en segundo plano
- `scheduler.py` – cálculo de próximas ejecuciones
- `config.py` – carga/guarda `config.json`
- `instalador.iss` – script de Inno Setup
- `build_exe.bat` – genera el .exe y el instalador
