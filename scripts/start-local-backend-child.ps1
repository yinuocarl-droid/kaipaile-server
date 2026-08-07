[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$JavaExecutable,
  [Parameter(Mandatory = $true)][string]$JarPath,
  [Parameter(Mandatory = $true)][string]$WorkingDirectory,
  [Parameter(Mandatory = $true)][string]$StdoutPath,
  [Parameter(Mandatory = $true)][string]$StderrPath,
  [Parameter(Mandatory = $true)][string]$LaunchPidPath,
  [Parameter(Mandatory = $true)][ValidateRange(1, 65535)][int]$Port
)

$ErrorActionPreference = 'Stop'

foreach ($key in @('WECHAT_MINIAPP_APP_ID', 'WECHAT_MINIAPP_APP_SECRET')) {
  if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($key, 'Process'))) {
    throw "$key is missing from the isolated child environment."
  }
}
if ([Environment]::GetEnvironmentVariable('SERVER_PORT', 'Process') -cne [string]$Port) {
  throw 'SERVER_PORT does not match the requested child launcher port.'
}
if (Test-Path -LiteralPath $LaunchPidPath) {
  throw 'The one-time launch PID path already exists.'
}

foreach ($argumentPath in @($JavaExecutable, $JarPath, $WorkingDirectory, $StdoutPath, $StderrPath, $LaunchPidPath)) {
  if ($argumentPath.Contains('"')) {
    throw 'Child launcher paths cannot contain double-quote characters.'
  }
}

$arguments = "-jar `"$JarPath`" --spring.profiles.active=dev --server.port=$Port"
$startParameters = @{
  FilePath = $JavaExecutable
  ArgumentList = $arguments
  WorkingDirectory = $WorkingDirectory
  RedirectStandardOutput = $StdoutPath
  RedirectStandardError = $StderrPath
  WindowStyle = 'Hidden'
  PassThru = $true
}

$process = $null
$temporaryPidPath = "$LaunchPidPath.$([Guid]::NewGuid().ToString('N')).tmp"
try {
  $process = Start-Process @startParameters
  foreach ($key in @('WECHAT_MINIAPP_APP_ID', 'WECHAT_MINIAPP_APP_SECRET')) {
    [Environment]::SetEnvironmentVariable($key, $null, 'Process')
  }
  $encoding = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($temporaryPidPath, [string]$process.Id, $encoding)
  [System.IO.File]::Move($temporaryPidPath, $LaunchPidPath)
  $process.WaitForExit()
  exit $process.ExitCode
} catch {
  if ($process -and -not $process.HasExited) {
    Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
  }
  throw
} finally {
  foreach ($key in @('WECHAT_MINIAPP_APP_ID', 'WECHAT_MINIAPP_APP_SECRET')) {
    [Environment]::SetEnvironmentVariable($key, $null, 'Process')
  }
  if (Test-Path -LiteralPath $temporaryPidPath -PathType Leaf) {
    [System.IO.File]::Delete($temporaryPidPath)
  }
}
