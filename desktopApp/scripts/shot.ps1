# RabBeat desktop screenshot helper.
# Usage: shot.ps1 -Start nowplaying -Out build\shots\02.png -Theme DARK -Window 1280x820 -WaitSeconds 12
param(
    [string]$Start = "library",
    [Parameter(Mandatory = $true)][string]$Out,
    [string]$Theme = "DARK",
    [string]$Window = "1280x820",
    [int]$WaitSeconds = 12,
    [string]$DataDir = "",
    [switch]$NoAutoplay
)

[Console]::OutputEncoding = [Text.Encoding]::UTF8
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class ShotWin {
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
}
"@ -ErrorAction SilentlyContinue
[ShotWin]::SetProcessDPIAware() | Out-Null

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $DataDir) { $DataDir = Join-Path $repoRoot "build\demo-data" }
$DataDir = [System.IO.Path]::GetFullPath($DataDir)
$Out = [System.IO.Path]::GetFullPath($Out)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Out) | Out-Null

$gradleArgs = @(":desktopApp:run",
    "-Prabbeat.dataDir=$DataDir",
    "-Prabbeat.start=$Start",
    "-Prabbeat.theme=$Theme",
    "-Prabbeat.window=$Window")
if (-not $NoAutoplay) { $gradleArgs += "-Prabbeat.autoplay=true" }

$launcher = Start-Process -FilePath "cmd.exe" `
    -ArgumentList ("/c", "gradlew.bat " + ($gradleArgs -join " ")) `
    -WorkingDirectory $repoRoot -PassThru -WindowStyle Hidden

Write-Output "launched gradle (pid $($launcher.Id)): gradlew.bat $($gradleArgs -join ' ')"

$javaPid = $null
$hwnd = [IntPtr]::Zero
$deadline = (Get-Date).AddSeconds(300)
$escapedDir = [regex]::Escape($DataDir)

while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    if (-not $javaPid) {
        $proc = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match "^java" -and $_.CommandLine -match "-Drabbeat\.dataDir" -and $_.CommandLine -match $escapedDir } |
            Select-Object -First 1
        if ($proc) { $javaPid = $proc.ProcessId; Write-Output "app java pid: $javaPid" }
        continue
    }
    $p = Get-Process -Id $javaPid -ErrorAction SilentlyContinue
    if ($p -and $p.MainWindowHandle -ne 0 -and $p.MainWindowTitle -eq "RabBeat") {
        $hwnd = $p.MainWindowHandle
        break
    }
    if (-not $p) { Write-Error "app process exited before showing a window"; }
}

if ($hwnd -eq [IntPtr]::Zero) {
    Write-Error "timed out waiting for the RabBeat window"
    exit 1
}

[ShotWin]::ShowWindow($hwnd, 9) | Out-Null   # SW_RESTORE
[ShotWin]::SetForegroundWindow($hwnd) | Out-Null
Write-Output "window found, waiting $WaitSeconds s before capture"
Start-Sleep -Seconds $WaitSeconds

$rect = New-Object ShotWin+RECT
[ShotWin]::GetWindowRect($hwnd, [ref]$rect) | Out-Null
$w = $rect.Right - $rect.Left
$h = $rect.Bottom - $rect.Top
$bmp = New-Object System.Drawing.Bitmap $w, $h
$g = [System.Drawing.Graphics]::FromImage($bmp)
$hdc = $g.GetHdc()
# PW_RENDERFULLCONTENT (2) captures even when the window is occluded.
$printed = [ShotWin]::PrintWindow($hwnd, $hdc, 2)
$g.ReleaseHdc($hdc)
if (-not $printed) {
    Write-Output "PrintWindow failed, falling back to screen copy"
    $g.CopyFromScreen($rect.Left, $rect.Top, 0, 0, (New-Object System.Drawing.Size $w, $h))
}
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
Write-Output "saved $Out ($w x $h)"

# Kill only the java process we started (matched by the dataDir flag).
$procs = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match "^java" -and $_.CommandLine -match "-Drabbeat\.dataDir" -and $_.CommandLine -match $escapedDir }
foreach ($pr in $procs) {
    Write-Output "stopping pid $($pr.ProcessId)"
    Stop-Process -Id $pr.ProcessId -Force -ErrorAction SilentlyContinue
}
Stop-Process -Id $launcher.Id -Force -ErrorAction SilentlyContinue
