<#
.SYNOPSIS
Build and refresh the connected Android development app and local desktop window.
.EXAMPLE
.\scripts\dev.ps1
.EXAMPLE
.\scripts\dev.ps1 -AndroidOnly -Onboarding
.EXAMPLE
.\scripts\dev.ps1 -AndroidOnly -Screen Quality
#>
[CmdletBinding()]
param(
    [switch]$AndroidOnly,
    [switch]$DesktopOnly,
    [switch]$Onboarding,
    [ValidateSet('Home', 'Quality')][string]$Screen = 'Home',
    [string]$Serial,
    [string]$JavaHome,
    [string]$SdkRoot
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoRoot = Split-Path -Parent $PSScriptRoot
$runAndroid = -not $DesktopOnly
$runDesktop = -not $AndroidOnly
$buildLabel = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$runRoot = Join-Path $repoRoot '.local-run'
$logDir = Join-Path $runRoot $buildLabel
$desktopStatePath = Join-Path $runRoot 'desktop.json'
$debugPackage = 'app.gocast.debug'

function Invoke-Logged {
    param([string]$FilePath, [string[]]$Arguments, [string]$LogPath)
    if (-not (Test-Path -LiteralPath $FilePath -PathType Leaf)) { throw "Command file not found: $FilePath" }
    $savedPreference = $ErrorActionPreference
    try {
        # Windows PowerShell 5.1 wraps native stderr (including harmless JVM
        # warnings) in ErrorRecords. The process exit code decides success.
        $ErrorActionPreference = 'Continue'
        & $FilePath @Arguments 2>&1 | ForEach-Object { "$_" } | Tee-Object -FilePath $LogPath -ErrorAction Stop | Out-Host
        $commandExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $savedPreference }
    if ($commandExitCode -ne 0) {
        throw "Command failed (exit $commandExitCode). See $LogPath"
    }
}

function Find-Jdk21 {
    if ($JavaHome) { $candidates = @($JavaHome) }
    else {
        $candidates = @($env:JAVA_HOME)
        $jdkCache = Join-Path $env:USERPROFILE '.gradle\jdks'
        if (Test-Path $jdkCache) {
            $candidates += @(Get-ChildItem $jdkCache -Directory | Select-Object -ExpandProperty FullName)
        }
        $candidates += Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
        $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
        if ($javaCommand) { $candidates += Split-Path -Parent (Split-Path -Parent $javaCommand.Source) }
    }
    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        $releaseFile = Join-Path $candidate 'release'
        if ((Test-Path (Join-Path $candidate 'bin\java.exe')) -and (Test-Path $releaseFile)) {
            if ((Get-Content -Raw $releaseFile) -match '(?m)^JAVA_VERSION="21[.\-"]') {
                return (Resolve-Path $candidate).Path
            }
        }
    }
    throw 'JDK 21 was not found. Pass -JavaHome <JDK-21-folder> or install it through Android Studio.'
}

function Find-AndroidSdk {
    $candidates = @($SdkRoot)
    if (-not $SdkRoot) {
        $candidates += @(
            $env:ANDROID_HOME,
            $env:ANDROID_SDK_ROOT,
            (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
            (Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk')
        )
    }
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path (Join-Path $candidate 'platform-tools\adb.exe'))) {
            return (Resolve-Path $candidate).Path
        }
    }
    throw 'Android SDK platform-tools were not found. Pass -SdkRoot <Android-SDK-folder> or install them in Android Studio.'
}

function Get-DesktopListenerIds {
    # A listener has a zero remote endpoint. Ignore the state text because
    # netstat localizes it (LISTENING / ABHOEREN / ECOUTE on different Windows).
    $netstatLines = & netstat.exe -ano -p tcp
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect TCP port 8765 (netstat failed).' }
    @($netstatLines | ForEach-Object {
        if ($_ -match '^\s*TCP\s+\S+:8765\s+(?:0\.0\.0\.0|\[::\]):0\s+\S+\s+(\d+)\s*$') { [int]$Matches[1] }
    } | Select-Object -Unique)
}

function Get-TrackedDesktop {
    if (-not (Test-Path $desktopStatePath)) { return $null }
    $saved = Get-Content -Raw $desktopStatePath | ConvertFrom-Json
    $process = Get-Process -Id $saved.pid -ErrorAction SilentlyContinue
    if (-not $process) { return $null }
    # A PID alone is unsafe: Windows reuses them. Match creation time and executable.
    if ($process.StartTime.ToUniversalTime().Ticks.ToString() -ne $saved.startTimeUtcTicks -or
        $process.Path -ne $saved.javaExecutable -or $saved.workspace -ne $repoRoot) {
        return $null
    }
    return $process
}

function Assert-DesktopPortAvailable {
    param($Tracked)
    $unexpectedIds = @(Get-DesktopListenerIds | Where-Object { -not $Tracked -or $_ -ne $Tracked.Id })
    if ($unexpectedIds.Count -gt 0) {
        $owners = @($unexpectedIds | ForEach-Object {
            $owner = Get-Process -Id $_ -ErrorAction SilentlyContinue
            if ($owner) { "$($owner.ProcessName) (PID $_)" } else { "PID $_" }
        }) -join ', '
        throw "TCP port 8765 is already used by $owners. Close that app (including any installed GoCast or old Gradle run) and rerun."
    }
}

if ($AndroidOnly -and $DesktopOnly) { throw 'Use -AndroidOnly or -DesktopOnly, or omit both to refresh both apps.' }
if ($DesktopOnly -and ($Onboarding -or $Screen -ne 'Home')) { throw '-Onboarding and -Screen apply to the Android app.' }
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$refreshLock = $null
$originalJavaHome = $env:JAVA_HOME
$originalAndroidHome = $env:ANDROID_HOME
$originalSdkRoot = $env:ANDROID_SDK_ROOT
try {
    try {
        $refreshLock = [System.IO.File]::Open((Join-Path $runRoot 'refresh.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    } catch { throw 'Another GoCast refresh is running. Wait for it to finish and rerun.' }
    $env:JAVA_HOME = Find-Jdk21
    Write-Host "GoCast development build $buildLabel" -ForegroundColor Cyan
    Write-Host "JDK: $env:JAVA_HOME"
    Write-Host "Logs: $logDir"

    $trackedDesktop = $null
    if ($runDesktop) {
        $trackedDesktop = Get-TrackedDesktop
        Assert-DesktopPortAvailable $trackedDesktop
    }
    if ($runAndroid) {
        $resolvedSdk = Find-AndroidSdk
        $env:ANDROID_HOME = $resolvedSdk
        $env:ANDROID_SDK_ROOT = $resolvedSdk
        $adb = Join-Path $resolvedSdk 'platform-tools\adb.exe'
        $deviceLines = & $adb devices
        if ($LASTEXITCODE -ne 0) { throw 'ADB could not list devices. Check Android SDK platform-tools.' }
        $readyDevices = @($deviceLines | ForEach-Object {
            if ($_ -match '^(\S+)\s+device\s*$') { $Matches[1] }
        })
        if ($Serial) {
            if ($Serial -notin $readyDevices) { throw "Phone $Serial is not authorized/online. Unlock it, enable USB debugging and accept its USB debugging prompt." }
        } elseif ($readyDevices.Count -eq 1) { $Serial = $readyDevices[0] }
        elseif ($readyDevices.Count -eq 0) { throw 'No authorized Android device. Connect/unlock the phone, enable USB debugging, and accept its USB debugging prompt.' }
        else { throw "Several Android devices are connected. Pass -Serial <device-id>. Online devices: $($readyDevices -join ', ')" }
        Write-Host "Phone: $Serial ($debugPackage)"
    }

    # Finish both builds before restarting either app. No clean/uninstall/data reset.
    if ($runAndroid) {
        Push-Location (Join-Path $repoRoot 'android')
        try {
            Invoke-Logged '.\gradlew.bat' @(':app:assembleDebug', "-PgocastDevBuild=$buildLabel", '--console=plain') (Join-Path $logDir 'android-build.log')
        } finally { Pop-Location }
    }
    if ($runDesktop) {
        Push-Location (Join-Path $repoRoot 'desktop')
        try {
            Invoke-Logged '.\gradlew.bat' @(':desktopApp:prepareDevRun', "-PgocastDevRunDir=$logDir\desktop-runtime", '--console=plain', '--no-configuration-cache') (Join-Path $logDir 'desktop-build.log')
        } finally { Pop-Location }
    }

    if ($runDesktop) {
        # Recheck after building, in case another app started meanwhile.
        $trackedDesktop = Get-TrackedDesktop
        Assert-DesktopPortAvailable $trackedDesktop
        if ($trackedDesktop) {
            Write-Host "Closing previous development window (PID $($trackedDesktop.Id))..."
            if (-not $trackedDesktop.CloseMainWindow() -or -not $trackedDesktop.WaitForExit(5000)) {
                $stillTracked = Get-TrackedDesktop
                if ($stillTracked -and $stillTracked.Id -eq $trackedDesktop.Id) {
                    Write-Host 'Previous development instance did not exit; stopping only its recorded process.'
                    Stop-Process -InputObject $stillTracked -Force
                    if (-not $stillTracked.WaitForExit(5000)) { throw 'The recorded development process did not stop.' }
                } elseif (Get-Process -Id $trackedDesktop.Id -ErrorAction SilentlyContinue) {
                    throw 'Process identity changed during refresh; no process was stopped.'
                }
            }
        }
        Assert-DesktopPortAvailable $null
        $launchInfo = Get-Content -Raw (Join-Path $repoRoot 'desktop\desktopApp\build\dev-run\launch.json') | ConvertFrom-Json
        $savedEnvironment = @{}
        try {
            foreach ($entry in $launchInfo.environment.PSObject.Properties) {
                $savedEnvironment[$entry.Name] = [Environment]::GetEnvironmentVariable($entry.Name, 'Process')
                [Environment]::SetEnvironmentVariable($entry.Name, [string]$entry.Value, 'Process')
            }
            $desktopStdout = Join-Path $logDir 'desktop.stdout.log'
            $desktopStderr = Join-Path $logDir 'desktop.stderr.log'
            $launchArgs = @('"-Dgocast.devWorkspace={0}"' -f $repoRoot; '"-Dgocast.devBuild={0}"' -f $buildLabel; '"@{0}"' -f $launchInfo.argsFile) -join ' '
            $desktopProcess = Start-Process -FilePath $launchInfo.javaExecutable -ArgumentList $launchArgs -WorkingDirectory $launchInfo.workingDirectory -WindowStyle Hidden -RedirectStandardOutput $desktopStdout -RedirectStandardError $desktopStderr -PassThru
        } finally {
            foreach ($entry in $savedEnvironment.GetEnumerator()) {
                [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
            }
        }
        @{
            pid = $desktopProcess.Id
            startTimeUtcTicks = $desktopProcess.StartTime.ToUniversalTime().Ticks.ToString()
            javaExecutable = $launchInfo.javaExecutable
            workspace = $repoRoot
            build = $buildLabel
            logs = $logDir
        } | ConvertTo-Json | Set-Content -Encoding UTF8 $desktopStatePath
        $deadline = [DateTime]::UtcNow.AddSeconds(30)
        do {
            Start-Sleep -Milliseconds 250
            $desktopProcess.Refresh()
            if ($desktopProcess.HasExited) { throw "Desktop exited (code $($desktopProcess.ExitCode)). See $desktopStderr and $desktopStdout" }
            $expectedTitle = "gocast Dev $([char]0x00B7) $buildLabel"
            $desktopReady = $desktopProcess.MainWindowTitle -eq $expectedTitle -and $desktopProcess.Id -in @(Get-DesktopListenerIds)
        } until ($desktopReady -or [DateTime]::UtcNow -ge $deadline)
        if (-not $desktopReady) { throw "Desktop did not finish startup within 30 seconds. See $desktopStderr and $desktopStdout. Close its window before rerunning if it remains open." }
        Write-Host "Desktop ready: gocast Dev / $buildLabel (PID $($desktopProcess.Id))" -ForegroundColor Green
    }

    if ($runAndroid) {
        $apkDir = Join-Path $repoRoot 'android\app\build\outputs\apk\debug'
        $metadata = Get-Content -Raw (Join-Path $apkDir 'output-metadata.json') | ConvertFrom-Json
        if ($metadata.applicationId -ne $debugPackage) { throw "Unexpected APK identity $($metadata.applicationId). Expected $debugPackage; installation cancelled." }
        Invoke-Logged $adb @('-s', $Serial, 'install', '-r', '-t', (Join-Path $apkDir 'app-debug.apk')) (Join-Path $logDir 'android-install.log')
        Invoke-Logged $adb @('-s', $Serial, 'shell', 'am', 'force-stop', $debugPackage) (Join-Path $logDir 'android-stop.log')
        $startArgs = @('-s', $Serial, 'shell', 'am', 'start', '-W', '-n', "$debugPackage/app.gocast.MainActivity", '--es', 'gocast.devScreen', $Screen.ToLowerInvariant())
        if ($Onboarding) { $startArgs += @('--ez', 'gocast.showOnboarding', 'true') }
        Invoke-Logged $adb $startArgs (Join-Path $logDir 'android-start.log')
        Start-Sleep -Seconds 1
        $phonePid = & $adb -s $Serial shell pidof $debugPackage
        if ($LASTEXITCODE -ne 0 -or -not $phonePid) {
            # A crash may have already removed the PID. Filter by the development
            # package's UID so its failure is still recorded without other apps' logs.
            $packageInfo = & $adb -s $Serial shell pm list packages -U $debugPackage
            if (($packageInfo -join "`n") -match '\buid:(\d+)') {
                & $adb -s $Serial logcat -d -t 200 "--uid=$($Matches[1])" | Set-Content -Encoding UTF8 (Join-Path $logDir 'android-startup.log')
            }
            throw "Android exited after launch. See $logDir and Android Studio Logcat for $debugPackage."
        }
        & $adb -s $Serial logcat -d -t 200 "--pid=$($phonePid.Trim())" | Set-Content -Encoding UTF8 (Join-Path $logDir 'android-startup.log')
        Write-Host "Phone ready: gocast Dev / $buildLabel (PID $phonePid)" -ForegroundColor Green
    }
    Write-Host "Refresh complete. Build label: $buildLabel" -ForegroundColor Cyan
    Write-Host "Next refresh: .\scripts\dev.ps1  |  Logs: $logDir"
} finally {
    $env:JAVA_HOME = $originalJavaHome
    $env:ANDROID_HOME = $originalAndroidHome
    $env:ANDROID_SDK_ROOT = $originalSdkRoot
    if ($refreshLock) { $refreshLock.Dispose() }
}
