# Broadcasts the GoCast Bluetooth LE beacon until the desktop app exits.
# Started by BluetoothBeacon.kt. Payload layout: protocol BeaconPayload.
param(
    [Parameter(Mandatory = $true)][string]$Payload,
    [Parameter(Mandatory = $true)][int]$ParentPid,
    [int]$CompanyId = 0xFFFF
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
[void][Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementPublisher, Windows.Devices.Bluetooth, ContentType = WindowsRuntime]

$bytes = [byte[]]($Payload -split ',' | ForEach-Object { [byte]$_ })
$data = New-Object Windows.Devices.Bluetooth.Advertisement.BluetoothLEManufacturerData
$data.CompanyId = $CompanyId
$data.Data = [System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBufferExtensions]::AsBuffer($bytes)

$publisher = New-Object Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementPublisher
# Windows PowerShell cannot call Add on a WinRT vector directly.
[System.Collections.Generic.ICollection[Windows.Devices.Bluetooth.Advertisement.BluetoothLEManufacturerData]].GetMethod('Add').Invoke(
    $publisher.Advertisement.ManufacturerData,
    [object[]]@($data.psobject.BaseObject)
)

try {
    while (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue) {
        # Windows aborts the beacon while Bluetooth is off; retry so it starts
        # as soon as the user switches Bluetooth on.
        $status = "$($publisher.Status)"
        if ($status -eq 'Created' -or $status -eq 'Stopped' -or $status -eq 'Aborted') {
            $publisher.Start()
        }
        Start-Sleep -Seconds 3
    }
} finally {
    $publisher.Stop()
}
