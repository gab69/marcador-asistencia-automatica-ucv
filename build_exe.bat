@echo off
cd /d "%~dp0"
echo === 1/3 Preparando entorno de Python ===
if not exist venv (
  python -m venv venv
  venv\Scripts\python -m pip install -r requirements.txt
)
echo === 2/3 Compilando ejecutable (PyInstaller) ===
if not exist icono.ico venv\Scripts\python -c "from PySide6.QtWidgets import QApplication; import app; a=QApplication([]); app.crear_icono().pixmap(256,256).save(icono.ico,ICO)"
venv\Scripts\pyinstaller --noconfirm --windowed --name UCVMarcador --icon icono.ico --collect-all selenium app.py || goto :error
echo === 3/3 Generando instalador (Inno Setup) ===
set "ISCC="
for %%P in ("%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe" "%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe" "%ProgramFiles%\Inno Setup 6\ISCC.exe") do if exist %%P set "ISCC=%%~P"
if not defined ISCC (
  echo No se encontro Inno Setup 6. Instalalo con: winget install JRSoftware.InnoSetup
  goto :error
)
"%ISCC%" instalador.iss || goto :error
echo.
echo Listo: instalador\UCVMarcador_Setup_1.2.1.exe
pause
exit /b 0
:error
echo Hubo un error.
pause
exit /b 1
