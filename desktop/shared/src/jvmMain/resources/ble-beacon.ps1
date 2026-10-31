# Broadcasts the GoCast Bluetooth LE beacons until the desktop app exits.
# Started by BluetoothBeacon.kt. Payload layouts: protocol BeaconPayload.
#   -Payload           name beacon (legacy advertisement), heard by every phone
#   -ShortRoomPayload  room beacon (legacy), heard by every phone; optional
#   -RoomPayload       room + name beacon (Bluetooth 5 extended advertisement); optional
param(
    [Parameter(Mandatory = $true)][string]$Payload,
    [string]$ShortRoomPayload = '',
    [string]$RoomPayload = '',
    [Parameter(Mandatory = $true)][int]$ParentPid,
    [int]$CompanyId = 0xFFFF
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
[void][Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementPublisher, Windows.Devices.Bluetooth, ContentType = WindowsRuntime]
# Windows PowerShell cannot call Add on a WinRT vector directly.
$addData = [System.Collections.Generic.ICollection[Windows.Devices.Bluetooth.Advertisement.BluetoothLEManufacturerData]].GetMethod('Add')

function New-Publisher([string]$csv, [bool]$extended) {
    $bytes = [byte[]]($csv -split ',' | ForEach-Object { [byte]$_ })
    $data = New-Object Windows.Devices.Bluetooth.Advertisement.BluetoothLEManufacturerData
    $data.CompanyId = $CompanyId
    $data.Data = [System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBufferExtensions]::AsBuffer($bytes)
    $publisher = New-Object Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementPublisher
    if ($extended) { $publisher.UseExtendedAdvertisement = $true }
    [void]$addData.Invoke($publisher.Advertisement.ManufacturerData, [object[]]@($data.psobject.BaseObject))
    $publisher
}

function Test-Startable($publisher) {
    $status = "$($publisher.Status)"
    $status -eq 'Created' -or $status -eq 'Stopped' -or $status -eq 'Aborted'
}

# The two legacy beacons take turns: many adapters run only one legacy
# advertisement at a time, and a phone scanning for a second hears both.
$short = @(New-Publisher $Payload $false)
if ($ShortRoomPayload) { $short += New-Publisher $ShortRoomPayload $false }
$extended = $null
if ($RoomPayload) {
    # Needs Windows 10 2004+ and a Bluetooth 5 adapter; without them the legacy beacons still run.
    try { $extended = New-Publisher $RoomPayload $true } catch { }
}

$turn = 0
try {
    while (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue) {
        # Windows aborts a beacon while Bluetooth is off; retrying each round starts it
        # as soon as the user switches Bluetooth on.
        if ($extended -and (Test-Startable $extended)) {
            try { $extended.Start() } catch { }
        }
        if ($short.Count -gt 1) {
            try { $short[$turn].Stop() } catch { }
            $turn = ($turn + 1) % $short.Count
        }
        if (Test-Startable $short[$turn]) {
            try { $short[$turn].Start() } catch { }
        }
        Start-Sleep -Milliseconds 1000
    }
} finally {
    foreach ($publisher in $short) { try { $publisher.Stop() } catch { } }
    if ($extended) { try { $extended.Stop() } catch { } }
}
