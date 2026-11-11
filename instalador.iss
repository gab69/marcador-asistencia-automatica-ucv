; Instalador de UCV Marcador (Inno Setup 6)
; Compilar: build_exe.bat  (o ISCC.exe instalador.iss tras ejecutar PyInstaller)

#define AppNombre "UCV Marcador"
#define AppVersion "1.2.1"
#define AppExe "UCVMarcador.exe"

[Setup]
AppId={{6E3C1B52-8F0A-4C7E-9D51-2A4B7C9E1F30}
AppName={#AppNombre}
AppVersion={#AppVersion}
AppPublisher=@GabRec
; Instalación por usuario: no pide permisos de administrador y la app puede
; escribir su configuración, sesión y evidencias en su propia carpeta.
PrivilegesRequired=lowest
DefaultDirName={localappdata}\UCVMarcador
DefaultGroupName={#AppNombre}
DisableProgramGroupPage=yes
OutputDir=instalador
OutputBaseFilename=UCVMarcador_Setup_{#AppVersion}
SetupIconFile=icono.ico
UninstallDisplayIcon={app}\{#AppExe}
UninstallDisplayName={#AppNombre}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "es"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "escritorio"; Description: "Crear acceso directo en el escritorio"; GroupDescription: "Accesos directos:"
Name: "inicio"; Description: "Iniciar UCV Marcador con Windows (minimizado en la bandeja)"; GroupDescription: "Inicio automático:"

[Files]
Source: "dist\UCVMarcador\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs; \
  Excludes: "linux-*,macos,config.json,chrome_perfil,evidencias,grabaciones"

[Icons]
Name: "{group}\{#AppNombre}"; Filename: "{app}\{#AppExe}"
Name: "{group}\Desinstalar {#AppNombre}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppNombre}"; Filename: "{app}\{#AppExe}"; Tasks: escritorio
Name: "{userstartup}\{#AppNombre}"; Filename: "{app}\{#AppExe}"; Parameters: "--minimizado"; Tasks: inicio

[Run]
Filename: "{app}\{#AppExe}"; Description: "Abrir {#AppNombre} ahora"; Flags: nowait postinstall skipifsilent

[UninstallRun]
; Cerrar la app si sigue abierta antes de borrar archivos
Filename: "{sys}\taskkill.exe"; Parameters: "/F /IM {#AppExe}"; Flags: runhidden; RunOnceId: "CerrarApp"

[UninstallDelete]
; Borra también config, sesión guardada, grabaciones y evidencias
Type: filesandordirs; Name: "{app}"

[Code]
function ChromeInstalado(): Boolean;
begin
  Result :=
    RegKeyExists(HKLM, 'SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\chrome.exe') or
    RegKeyExists(HKCU, 'SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\chrome.exe') or
    FileExists(ExpandConstant('{pf}\Google\Chrome\Application\chrome.exe')) or
    FileExists(ExpandConstant('{pf32}\Google\Chrome\Application\chrome.exe')) or
    FileExists(ExpandConstant('{localappdata}\Google\Chrome\Application\chrome.exe'));
end;

function InitializeSetup(): Boolean;
begin
  Result := True;
  if not ChromeInstalado() then
    Result := MsgBox('No se encontró Google Chrome en esta PC.' + #13#10 +
      'UCV Marcador necesita Chrome para funcionar.' + #13#10#13#10 +
      '¿Deseas continuar con la instalación de todos modos?',
      mbConfirmation, MB_YESNO) = IDYES;
end;
