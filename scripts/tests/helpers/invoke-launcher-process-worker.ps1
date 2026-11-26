[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$LauncherPath,
  [Parameter(Mandatory = $true)][string]$SecretFile,
  [Parameter(Mandatory = $true)][string]$JarPath,
  [Parameter(Mandatory = $true)][ValidateRange(1, 65535)][int]$Port,
  [Parameter(Mandatory = $true)][ValidateRange(1, 120)][int]$StartupTimeoutSeconds,
  [Parameter(Mandatory = $true)][string]$StandardOutputPath,
  [Parameter(Mandatory = $true)][string]$StandardErrorPath,
  [Parameter(Mandatory = $true)][string]$ResultPath,
  [switch]$ValidateOnly,
  [switch]$Restart
)

$ErrorActionPreference = 'Stop'

function Quote-NativeArgument {
  param([Parameter(Mandatory = $true)][string]$Value)

  if ($Value.Contains('"')) {
    throw 'Launcher worker paths cannot contain double-quote characters.'
  }
  return '"' + $Value + '"'
}

$powerShellExecutable = Join-Path $PSHOME 'powershell.exe'
$arguments = @(
  '-NoLogo',
  '-NoProfile',
  '-NonInteractive',
  '-ExecutionPolicy', 'Bypass',
  '-File', (Quote-NativeArgument -Value $LauncherPath),
  '-SecretFile', (Quote-NativeArgument -Value $SecretFile),
  '-JarPath', (Quote-NativeArgument -Value $JarPath),
  '-Port', [string]$Port,
  '-StartupTimeoutSeconds', [string]$StartupTimeoutSeconds
)
if ($Restart) {
  $arguments += '-Restart'
}
if ($ValidateOnly) {
  $arguments += '-ValidateOnly'
}

$launcherProcess = $null
$workerExitCode = 125
try {
  $launcherProcess = Start-Process `
    -FilePath $powerShellExecutable `
    -ArgumentList ($arguments -join ' ') `
    -WorkingDirectory (Split-Path -Parent (Split-Path -Parent $LauncherPath)) `
    -RedirectStandardOutput $StandardOutputPath `
    -RedirectStandardError $StandardErrorPath `
    -WindowStyle Hidden `
    -PassThru
  $launcherProcess.WaitForExit()
  $workerExitCode = [int]$launcherProcess.ExitCode
  if ($workerExitCode -eq 0 -and
      (Test-Path -LiteralPath $StandardErrorPath -PathType Leaf) -and
      (Get-Item -LiteralPath $StandardErrorPath).Length -gt 0) {
    $workerExitCode = 1
  }
} catch {
  $workerExitCode = 125
} finally {
  if ($launcherProcess) {
    $launcherProcess.Dispose()
  }
}
[System.IO.File]::WriteAllText($ResultPath, [string]$workerExitCode)
exit $workerExitCode
