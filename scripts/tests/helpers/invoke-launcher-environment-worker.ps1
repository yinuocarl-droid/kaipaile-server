[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$LauncherPath,
  [Parameter(Mandatory = $true)][string]$SecretFile,
  [Parameter(Mandatory = $true)][string]$JarPath,
  [Parameter(Mandatory = $true)][ValidateRange(1, 65535)][int]$Port,
  [Parameter(Mandatory = $true)][string]$MonitorStopPath,
  [Parameter(Mandatory = $true)][string]$ParentLeakMarkerPath
)

$ErrorActionPreference = 'Stop'

foreach ($key in @('WECHAT_MINIAPP_APP_ID', 'WECHAT_MINIAPP_APP_SECRET')) {
  if (-not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($key, 'Process'))) {
    throw 'Environment worker was not started with a clean parent environment.'
  }
}

# This wrapper widens the observation window for launchers that mutate their
# parent environment immediately before Start-Process.
function Start-Process {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory = $true, Position = 0)][string]$FilePath,
    [object[]]$ArgumentList,
    [string]$WorkingDirectory,
    [string]$RedirectStandardOutput,
    [string]$RedirectStandardError,
    [System.Diagnostics.ProcessWindowStyle]$WindowStyle,
    [switch]$PassThru,
    [switch]$Wait,
    [switch]$NoNewWindow
  )

  Start-Sleep -Milliseconds 300
  Microsoft.PowerShell.Management\Start-Process @PSBoundParameters
}

$monitor = [PowerShell]::Create()
$null = $monitor.AddScript({
  param($StopPath, $LeakMarkerPath)

  while (-not [System.IO.File]::Exists($StopPath)) {
    foreach ($key in @('WECHAT_MINIAPP_APP_ID', 'WECHAT_MINIAPP_APP_SECRET')) {
      $value = [Environment]::GetEnvironmentVariable($key, 'Process')
      if (-not [string]::IsNullOrWhiteSpace($value)) {
        [System.IO.File]::WriteAllText($LeakMarkerPath, 'process-environment-observed')
        return
      }
    }
    [Threading.Thread]::Sleep(1)
  }
}).AddArgument($MonitorStopPath).AddArgument($ParentLeakMarkerPath)
$monitorInvocation = $monitor.BeginInvoke()

try {
  try {
    & $LauncherPath `
      -SecretFile $SecretFile `
      -JarPath $JarPath `
      -Port $Port `
      -StartupTimeoutSeconds 15 | Out-Null
  } catch {
    exit 1
  }
} finally {
  [System.IO.File]::WriteAllText($MonitorStopPath, 'stop')
  try {
    $null = $monitor.EndInvoke($monitorInvocation)
  } finally {
    $monitor.Dispose()
  }
}
exit 0
