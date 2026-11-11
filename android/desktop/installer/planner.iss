; the windows installer: one progress page and a finish page, no folder or licence questions.
; built by `gradlew :desktop:packageSetup`, which passes the version and the folder jpackage made.
#ifndef Version
  #error Version is passed by gradle
#endif
#ifndef Source
  #error Source is passed by gradle
#endif

[Setup]
; the same id for every version, so a new installer replaces the old app
AppId={{7d1a5b0e-3c6f-4e0b-9a52-0f1c8e2d4b61}
AppName=planner
AppVersion={#Version}
AppPublisher=fourthbreakfast
AppPublisherURL=https://github.com/fourthbreakfast/planner
; the same folder the former msi used, so the start-with-windows entry and shortcuts stay valid
DefaultDirName={localappdata}\planner
PrivilegesRequired=lowest
WizardStyle=modern dynamic
DisableWelcomePage=yes
DisableDirPage=yes
DisableProgramGroupPage=yes
DisableReadyPage=yes
DisableReadyMemo=yes
Compression=lzma2/ultra
SolidCompression=yes
OutputDir={#SourcePath}\..\build\setup
OutputBaseFilename=planner-{#Version}-setup
SetupIconFile={#SourcePath}\..\icons\planner.ico
UninstallDisplayIcon={app}\planner.exe
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
CloseApplications=no
RestartApplications=no

[Files]
Source: "{#Source}\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion

[Icons]
Name: "{autoprograms}\planner"; Filename: "{app}\planner.exe"; AppUserModelID: "fourthbreakfast.planner"

[Run]
Filename: "{app}\planner.exe"; Description: "open planner"; Flags: nowait postinstall skipifsilent

[Code]
// the former msi installer: take it away first, keeping the data, which lives outside the install folder
procedure RemoveFormerMsi();
var
  code: Integer;
begin
  Exec('powershell.exe',
    '-NoProfile -NonInteractive -Command "$i = New-Object -ComObject WindowsInstaller.Installer; ' +
    'foreach ($p in $i.RelatedProducts(''{156C78D7-E497-45E4-892F-D6C9DEA7E580}'')) { Start-Process msiexec -ArgumentList ''/x'',$p,''/qn'',''/norestart'' -Wait }"',
    '', SW_HIDE, ewWaitUntilTerminated, code);
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssInstall then RemoveFormerMsi();
end;

// planner sits in the tray with its window hidden, so it cannot be asked to close: stop what runs from the install
// folder, planner and its own syncthing and nothing else, before the files are replaced
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  code: Integer;
begin
  Exec('powershell.exe',
    '-NoProfile -NonInteractive -Command "Get-Process | Where-Object { $_.Path -like ''' + ExpandConstant('{app}') + '\*'' } | Stop-Process -Force; Start-Sleep -Seconds 2"',
    '', SW_HIDE, ewWaitUntilTerminated, code);
  Result := '';
end;
