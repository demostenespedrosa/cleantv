$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
& $adb shell pm enable com.google.android.apps.tv.launcherx
Write-Host "Launcher original do Google TV reativado com sucesso!" -ForegroundColor Green
