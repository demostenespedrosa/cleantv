# Uso: .\instalar-launcher.ps1 [-Apk caminho.apk] [-AtivarPadrao] [-Ip 192.168.1.3]
# Instala o APK do TV Home na TV e (opcionalmente) desativa o Google TV Launcher original.
param(
    [string]$Apk = '',
    [switch]$AtivarPadrao,
    [string]$Ip = '192.168.1.3'
)
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'

if (-not $Apk) {
    $found = Get-ChildItem -Path $PSScriptRoot, (Join-Path $PSScriptRoot '..') -Filter '*.apk' -Recurse -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($found) { $Apk = $found.FullName }
}
if (-not $Apk -or -not (Test-Path $Apk)) {
    Write-Host 'APK nao encontrado. Passe -Apk caminho\para\app-release.apk' -ForegroundColor Red
    exit 1
}

Write-Host "Conectando em ${Ip}:5555..."
& $adb connect "${Ip}:5555" | Out-Null
$serial = "${Ip}:5555"

Write-Host "Instalando $Apk ..."
& $adb -s $serial install -r $Apk

Write-Host 'Abrindo TV Home para teste...'
& $adb -s $serial shell am start -n com.demostenes.tvhome/.MainActivity

if ($AtivarPadrao) {
    Write-Host 'Ativando TV Home como launcher padrao e desativando o launcher original...'
    & (Join-Path $PSScriptRoot 'debloat.ps1') -Camada 3 -Ip $Ip
}
