# Uso: .\debloat.ps1 [-Camada 1|2|3] [-Ip 192.168.1.3]
# Desativa pacotes (reversivel) e acelera animacoes.
param(
    [int[]]$Camada = @(1),
    [string]$Ip = '192.168.1.3'
)
$ErrorActionPreference = 'Continue'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
. (Join-Path $PSScriptRoot 'pacotes.ps1')

& $adb connect "${Ip}:5555" | Out-Null
$serial = "${Ip}:5555"
$instalados = (& $adb -s $serial shell pm list packages) -replace '^package:', ''

$lista = @()
if ($Camada -contains 1) { $lista += $Camada1 }
if ($Camada -contains 2) { $lista += $Camada2 }
if ($Camada -contains 3) {
    $tvHome = (& $adb -s $serial shell pm list packages com.demostenes.tvhome)
    if (-not $tvHome) { Write-Host 'TV Home nao esta instalado! Abortando camada 3.' -ForegroundColor Red; exit 1 }
    $lista += $Camada3
}

foreach ($p in $lista) {
    if ($instalados -notcontains $p) { Write-Host "  - $p (nao instalado)" -ForegroundColor DarkGray; continue }
    & $adb -s $serial shell am force-stop $p | Out-Null
    $r = & $adb -s $serial shell pm disable-user --user 0 $p 2>&1
    Write-Host "  x $p -> $r"
}

if ($Camada -contains 3) {
    & $adb -s $serial shell cmd package set-home-activity com.demostenes.tvhome/.MainActivity
    & $adb -s $serial shell input keyevent KEYCODE_HOME
}

# Animacoes mais rapidas (0.5x)
& $adb -s $serial shell settings put global window_animation_scale 0.5
& $adb -s $serial shell settings put global transition_animation_scale 0.5
& $adb -s $serial shell settings put global animator_duration_scale 0.5
Write-Host 'Pronto.' -ForegroundColor Green
