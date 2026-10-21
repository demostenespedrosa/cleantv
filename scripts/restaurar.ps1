# Uso: .\restaurar.ps1 [-Camada 1|2|3] [-Ip 192.168.1.3]
# Reativa tudo que o debloat.ps1 desativou (por padrao, todas as camadas).
param(
    [int[]]$Camada = @(1, 2, 3),
    [string]$Ip = '192.168.1.3'
)
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
. (Join-Path $PSScriptRoot 'pacotes.ps1')
& $adb connect "${Ip}:5555" | Out-Null
$serial = "${Ip}:5555"

$lista = @()
if ($Camada -contains 3) { $lista += $Camada3 }
if ($Camada -contains 1) { $lista += $Camada1 }
if ($Camada -contains 2) { $lista += $Camada2 }
foreach ($p in $lista) {
    $r = & $adb -s $serial shell pm enable $p 2>&1
    Write-Host "  + $p -> $r"
}
if ($Camada -contains 3) {
    & $adb -s $serial shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity
}
& $adb -s $serial shell settings put global window_animation_scale 1.0
& $adb -s $serial shell settings put global transition_animation_scale 1.0
& $adb -s $serial shell settings put global animator_duration_scale 1.0
Write-Host 'Restaurado.' -ForegroundColor Green
