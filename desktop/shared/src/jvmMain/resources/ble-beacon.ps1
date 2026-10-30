# Broadcasts the GoCast Bluetooth LE beacons until the desktop app exits.
# Started by BluetoothBeacon.kt. Payload layouts: protocol BeaconPayload.
#   -Payload      short beacon (legacy advertisement), heard by every phone
#   -RoomPayload  room beacon (Bluetooth 5 extended advertisement), optional
param(
    [Parameter(Mandatory = $true)][string]$Payload,
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

$publishers = @(New-Publisher $Payload $false)
if ($RoomPayload) {
    # Needs Windows 10 2004+ and a Bluetooth 5 adapter; without them the short beacon still runs.
    try { $publishers += New-Publisher $RoomPayload $true } catch { }
}

try {
    while (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue) {
        # Windows aborts a beacon while Bluetooth is off; retry so it starts
        # as soon as the user switches Bluetooth on.
        foreach ($publisher in $publishers) {
            $status = "$($publisher.Status)"
            if ($status -eq 'Created' -or $status -eq 'Stopped' -or $status -eq 'Aborted') {
                try { $publisher.Start() } catch { }
            }
        }
        Start-Sleep -Seconds 3
    }
} finally {
    foreach ($publisher in $publishers) { $publisher.Stop() }
}
