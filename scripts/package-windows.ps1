[CmdletBinding()]
param(
    [switch]$Release,
    [string]$JavaHome,
    [string]$CertificateThumbprint,
    [string]$WebView2InstallerPath
)

$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$properties = ConvertFrom-StringData ([IO.File]::ReadAllText((Join-Path $workspace 'release.properties')))
$version = $properties.version
if ($version -notmatch '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$') { throw 'Invalid release version.' }

function Invoke-PackageTool {
    param([string]$Tool, [string[]]$Arguments)
    $savedPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Tool @Arguments
        $toolExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $savedPreference }
    if ($toolExit -ne 0) { throw "$Tool failed (exit $toolExit)." }
}

if (!$JavaHome) {
    $candidates = @($env:JAVA_HOME)
    $jdkCache = Join-Path $env:USERPROFILE '.gradle\jdks'
    if (Test-Path -LiteralPath $jdkCache) {
        $candidates += @(Get-ChildItem -LiteralPath $jdkCache -Directory | Select-Object -ExpandProperty FullName)
    }
    $JavaHome = $candidates | Where-Object {
        $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\jpackage.exe')) -and
        (Test-Path -LiteralPath (Join-Path $_ 'release')) -and
        ([IO.File]::ReadAllText((Join-Path $_ 'release')) -match 'JAVA_VERSION="21[.\"]')
    } | Select-Object -First 1
}
if (!$JavaHome -or !(Test-Path -LiteralPath (Join-Path $JavaHome 'bin\jpackage.exe'))) {
    throw 'JDK 21 with jpackage is required. Pass -JavaHome if it is installed elsewhere.'
}
$jdkRelease = [IO.File]::ReadAllText((Join-Path $JavaHome 'release'))
if ($jdkRelease -notmatch 'JAVA_VERSION="21[.\"]' -or $jdkRelease -notmatch 'OS_ARCH="(amd64|x86_64)"') {
    throw 'This packaging entry point requires an x64 JDK 21. Other architectures need their own validated package.'
}

$wixDirectories = @()
if ($env:WIX) { $wixDirectories += (Join-Path $env:WIX 'bin') }
$candleCommand = Get-Command candle.exe -ErrorAction SilentlyContinue
if ($candleCommand) { $wixDirectories += (Split-Path -Parent $candleCommand.Source) }
$programFiles32 = ${env:ProgramFiles(x86)}
if ($programFiles32) {
    $wixDirectories += @(Get-ChildItem -Path (Join-Path $programFiles32 'WiX Toolset v3*') -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'bin' })
}
$wixBin = $wixDirectories | Where-Object {
    (Test-Path -LiteralPath (Join-Path $_ 'candle.exe')) -and
    (Test-Path -LiteralPath (Join-Path $_ 'light.exe'))
} | Select-Object -First 1
if (!$wixBin) { throw 'WiX Toolset 3.x (candle/light) is required by the current jpackage build.' }

$output = Join-Path $workspace ('.local-run\packages\' + $version + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $output | Out-Null
$savedJavaHome = $env:JAVA_HOME
$savedPath = $env:PATH
try {
    $env:JAVA_HOME = $JavaHome
    $env:PATH = (Join-Path $JavaHome 'bin') + ';' + $wixBin + ';' + $savedPath
    $desktop = Join-Path $workspace 'desktop'
    $task = if ($Release) { ':desktopApp:packageReleaseMsi' } else { ':desktopApp:packageMsi' }
    Push-Location -LiteralPath $desktop
    # Like dev.ps1: the Compose packaging tasks are not configuration-cache safe, and the
    # cache check fails the build even after the MSI was written.
    try { Invoke-PackageTool (Join-Path $desktop 'gradlew.bat') @($task, '--console=plain', '--no-configuration-cache') }
    finally { Pop-Location }

    $variant = if ($Release) { 'main-release' } else { 'main' }
    $msiSource = Join-Path $desktop "desktopApp\build\compose\binaries\$variant\msi\gocast-$version.msi"
    if (!(Test-Path -LiteralPath $msiSource)) { throw "Expected MSI was not produced: $msiSource" }
    $msi = Join-Path $output "gocast-$version-windows-x64.msi"
    Copy-Item -LiteralPath $msiSource -Destination $msi

    $package = Join-Path $desktop 'desktopApp\package\windows'
    $webview2 = Join-Path $output 'MicrosoftEdgeWebview2Setup.exe'
    if ($WebView2InstallerPath) {
        Copy-Item -LiteralPath (Resolve-Path -LiteralPath $WebView2InstallerPath).Path -Destination $webview2
    } else {
        # Download only: the build never runs this prerequisite installer.
        Invoke-WebRequest -UseBasicParsing -Uri 'https://go.microsoft.com/fwlink/p/?LinkId=2124703' -OutFile $webview2
    }
    $runtimeSignature = Get-AuthenticodeSignature -LiteralPath $webview2
    if ($runtimeSignature.Status -ne 'Valid' -or $runtimeSignature.SignerCertificate.Subject -notmatch '(^|,\s*)O=Microsoft Corporation(,|$)') {
        throw 'The WebView2 prerequisite does not have a valid Microsoft signature.'
    }
    $theme = Join-Path $output 'GoCastTheme.xml'
    $themeText = [IO.File]::ReadAllText((Join-Path $package 'GoCastTheme.xml')).Replace('{{VERSION}}', $version)
    [IO.File]::WriteAllText($theme, $themeText, (New-Object Text.UTF8Encoding($false)))
    $object = Join-Path $output 'GoCastSetup.wixobj'
    $setup = Join-Path $output "gocast-$version-windows-x64-setup.exe"
    Invoke-PackageTool (Join-Path $wixBin 'candle.exe') @(
        '-nologo', '-ext', 'WixBalExtension', '-ext', 'WixUtilExtension', "-dVersion=$version", "-dMsiPath=$msi",
        "-dIconPath=$(Join-Path $desktop 'desktopApp\icons\icon.ico')",
        "-dLogoPath=$(Join-Path $workspace 'branding\gocast_logo.png')", "-dThemePath=$theme", "-dWebView2Path=$webview2",
        '-out', $object, (Join-Path $package 'GoCastSetup.wxs')
    )
    Invoke-PackageTool (Join-Path $wixBin 'light.exe') @('-nologo', '-ext', 'WixBalExtension', '-ext', 'WixUtilExtension', '-out', $setup, $object)

    # Signing is performed on the final authored files, never before MSI changes.
    # The certificate stays in the Windows certificate store; no private key is copied.
    if ($CertificateThumbprint) {
        $signTool = (Get-Command signtool.exe -ErrorAction Stop).Source
        Invoke-PackageTool $signTool @('sign', '/sha1', $CertificateThumbprint, '/fd', 'SHA256', '/tr', 'http://timestamp.digicert.com', '/td', 'SHA256', $msi)
        # Rebuild the bundle so its embedded MSI is the signed one.
        Invoke-PackageTool (Join-Path $wixBin 'light.exe') @('-nologo', '-ext', 'WixBalExtension', '-ext', 'WixUtilExtension', '-out', $setup, $object)
        $insignia = Join-Path $wixBin 'insignia.exe'
        if (!(Test-Path -LiteralPath $insignia)) { throw 'insignia.exe is required to sign the cached maintenance engine.' }
        $engine = Join-Path $output 'GoCastSetupEngine.exe'
        Invoke-PackageTool $insignia @('-ib', $setup, '-o', $engine)
        Invoke-PackageTool $signTool @('sign', '/sha1', $CertificateThumbprint, '/fd', 'SHA256', '/tr', 'http://timestamp.digicert.com', '/td', 'SHA256', $engine)
        Invoke-PackageTool $insignia @('-ab', $engine, $setup, '-o', $setup)
        Invoke-PackageTool $signTool @('sign', '/sha1', $CertificateThumbprint, '/fd', 'SHA256', '/tr', 'http://timestamp.digicert.com', '/td', 'SHA256', $setup)
        Invoke-PackageTool $signTool @('verify', '/pa', $engine)
        Invoke-PackageTool $signTool @('verify', '/pa', $msi)
        Invoke-PackageTool $signTool @('verify', '/pa', $setup)
    }
    $checksums = @($msi, $setup) | ForEach-Object {
        $hash = Get-FileHash -LiteralPath $_ -Algorithm SHA256
        $hash.Hash.ToLowerInvariant() + '  ' + (Split-Path -Leaf $_)
    }
    [IO.File]::WriteAllLines((Join-Path $output 'SHA256SUMS.txt'), [string[]]$checksums)
    Write-Host "`nGoCast $version packages are ready in:`n$output"
    Write-Host 'Nothing has been installed, launched or published. Validate these packages before releasing them.'
} finally {
    $env:JAVA_HOME = $savedJavaHome
    $env:PATH = $savedPath
}
