[CmdletBinding()]
param(
  [string]$SecretFile,
  [string]$JarPath,
  [ValidateRange(1, 65535)]
  [int]$Port = 8010,
  [ValidateRange(1, 120)]
  [int]$StartupTimeoutSeconds = 45,
  [ValidateRange(1, 300)]
  [int]$PortReleaseTimeoutSeconds = 60,
  [switch]$Restart,
  [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptRoot '..')).Path
$workspaceRoot = (Resolve-Path (Join-Path $repoRoot '..')).Path

if ([string]::IsNullOrWhiteSpace($SecretFile)) {
  $SecretFile = Join-Path $workspaceRoot '.sce\config\local-secrets\wechat-miniapp.env'
}

function Read-DotenvFile {
  param([Parameter(Mandatory = $true)][string]$Path)

  $values = @{}
  foreach ($rawLine in Get-Content -LiteralPath $Path -Encoding UTF8) {
    $line = $rawLine.Trim()
    if (-not $line -or $line.StartsWith('#') -or -not $line.Contains('=')) {
      continue
    }

    $separatorIndex = $rawLine.IndexOf('=')
    $key = $rawLine.Substring(0, $separatorIndex).Trim()
    $value = $rawLine.Substring($separatorIndex + 1).Trim()
    if ($value.Length -ge 2 -and
        (($value.StartsWith('"') -and $value.EndsWith('"')) -or
        ($value.StartsWith("'") -and $value.EndsWith("'")))) {
      $value = $value.Substring(1, $value.Length - 2)
    }
    $values[$key] = $value
  }
  return $values
}

function Get-ConfigValueInfo {
  param(
    [Parameter(Mandatory = $true)][string]$Key,
    [Parameter(Mandatory = $true)][hashtable]$FileValues
  )

  $processValue = [Environment]::GetEnvironmentVariable($Key, 'Process')
  if (-not [string]::IsNullOrWhiteSpace($processValue)) {
    return [pscustomobject]@{
      Value = $processValue.Trim()
      Source = 'process environment'
    }
  }
  if ($FileValues.ContainsKey($Key) -and -not [string]::IsNullOrWhiteSpace($FileValues[$Key])) {
    return [pscustomobject]@{
      Value = ([string]$FileValues[$Key]).Trim()
      Source = 'local secret file'
    }
  }
  return [pscustomobject]@{
    Value = ''
    Source = 'missing'
  }
}

function Assert-WeChatConfig {
  param(
    [Parameter(Mandatory = $true)]
    [AllowEmptyString()]
    [string]$AppId,
    [Parameter(Mandatory = $true)]
    [AllowEmptyString()]
    [string]$AppSecret
  )

  if ($AppId -notmatch '^wx[A-Za-z0-9]{16}$') {
    throw 'WECHAT_MINIAPP_APP_ID is missing or has an invalid format.'
  }
  if ([string]::IsNullOrWhiteSpace($AppSecret)) {
    throw 'WECHAT_MINIAPP_APP_SECRET is missing.'
  }
  if ($AppSecret.Length -lt 16 -or
      $AppSecret -match '(?i)replace[-_ ]?with[-_ ]?real|fake|dummy|changeme|example|placeholder|todo|test|sample') {
    throw 'WECHAT_MINIAPP_APP_SECRET is a placeholder or is too short.'
  }

  $projectConfigPath = Join-Path $workspaceRoot 'kaipai-frontend\project.config.json'
  if (-not (Test-Path -LiteralPath $projectConfigPath -PathType Leaf)) {
    throw 'kaipai-frontend/project.config.json is required to verify the mini-program AppId.'
  }

  try {
    $projectConfig = Get-Content -Raw -LiteralPath $projectConfigPath -Encoding UTF8 | ConvertFrom-Json
  } catch {
    throw 'kaipai-frontend/project.config.json is not valid JSON.'
  }
  $projectAppId = ([string]$projectConfig.appid).Trim()
  if ([string]::IsNullOrWhiteSpace($projectAppId)) {
    throw 'kaipai-frontend/project.config.json does not declare appid.'
  }
  if ($projectAppId -cne $AppId) {
    throw 'Backend WECHAT_MINIAPP_APP_ID does not match kaipai-frontend/project.config.json.'
  }
}

function Resolve-JavaExecutable {
  if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $javaFromHome = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path -LiteralPath $javaFromHome -PathType Leaf) {
      return (Resolve-Path -LiteralPath $javaFromHome).Path
    }
  }

  $javaCommand = Get-Command 'java.exe' -ErrorAction SilentlyContinue
  if (-not $javaCommand) {
    throw 'Java was not found. Configure JAVA_HOME with JDK 17 before starting the backend.'
  }
  return (Resolve-Path -LiteralPath $javaCommand.Source).Path
}

function Assert-Java17 {
  param([Parameter(Mandatory = $true)][string]$Executable)

  $versionStartInfo = New-Object System.Diagnostics.ProcessStartInfo
  $versionStartInfo.FileName = $Executable
  $versionStartInfo.Arguments = '-version'
  $versionStartInfo.UseShellExecute = $false
  $versionStartInfo.CreateNoWindow = $true
  $versionStartInfo.RedirectStandardOutput = $true
  $versionStartInfo.RedirectStandardError = $true
  $versionProcess = New-Object System.Diagnostics.Process
  $versionProcess.StartInfo = $versionStartInfo
  if (-not $versionProcess.Start()) {
    throw 'Unable to start the selected Java runtime for version verification.'
  }
  $versionOutput = $versionProcess.StandardOutput.ReadToEnd()
  $versionError = $versionProcess.StandardError.ReadToEnd()
  $versionProcess.WaitForExit()
  $versionExitCode = $versionProcess.ExitCode
  $versionProcess.Dispose()
  $versionText = $versionOutput + [Environment]::NewLine + $versionError
  if ($versionExitCode -ne 0 -or $versionText -notmatch 'version\s+"(?<major>\d+)') {
    throw 'Unable to verify the selected Java runtime version.'
  }
  if ([int]$Matches['major'] -ne 17) {
    throw 'The local backend requires JDK 17.'
  }
}

function Quote-ProcessArgument {
  param([Parameter(Mandatory = $true)][string]$Value)

  if ($Value.Contains('"')) {
    throw 'Process arguments cannot contain double-quote characters.'
  }
  return '"' + $Value + '"'
}

function Resolve-BackendJar {
  param([string]$RequestedPath)

  if (-not [string]::IsNullOrWhiteSpace($RequestedPath)) {
    if (-not (Test-Path -LiteralPath $RequestedPath -PathType Leaf)) {
      throw "Backend jar does not exist: $RequestedPath"
    }
    return (Resolve-Path -LiteralPath $RequestedPath).Path
  }

  $jar = Get-ChildItem -Path (Join-Path $repoRoot 'target') -Filter '*.jar' -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike '*.original' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if (-not $jar) {
    throw 'No backend jar was found under target/. Run scripts/package-backend.ps1 first.'
  }
  return $jar.FullName
}

function Assert-WindowsLauncherSupport {
  if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw 'The local backend launcher currently supports Windows only.'
  }
  foreach ($commandName in @('Get-NetTCPConnection', 'Get-CimInstance', 'Stop-Process')) {
    if (-not (Get-Command $commandName -ErrorAction SilentlyContinue)) {
      throw "Required Windows command is unavailable: $commandName"
    }
  }
}

function Get-PortListeners {
  param([Parameter(Mandatory = $true)][int]$LocalPort)
  return @(Get-NetTCPConnection -LocalPort $LocalPort -State Listen -ErrorAction SilentlyContinue)
}

function Test-ExclusivePortBind {
  param([Parameter(Mandatory = $true)][int]$LocalPort)

  $socket = $null
  try {
    $socket = New-Object System.Net.Sockets.Socket(
      [System.Net.Sockets.AddressFamily]::InterNetworkV6,
      [System.Net.Sockets.SocketType]::Stream,
      [System.Net.Sockets.ProtocolType]::Tcp
    )
    $socket.DualMode = $true
    $socket.ExclusiveAddressUse = $true
    $endpoint = New-Object System.Net.IPEndPoint([System.Net.IPAddress]::IPv6Any, $LocalPort)
    $socket.Bind($endpoint)
    $socket.Listen(1)
    return $true
  } catch [System.Net.Sockets.SocketException] {
    return $false
  } finally {
    if ($socket) {
      $socket.Dispose()
    }
  }
}

function Wait-ExclusivePortBind {
  param(
    [Parameter(Mandatory = $true)][int]$LocalPort,
    [Parameter(Mandatory = $true)][int]$TimeoutSeconds
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    if (Test-ExclusivePortBind -LocalPort $LocalPort) {
      Start-Sleep -Milliseconds 250
      if (Test-ExclusivePortBind -LocalPort $LocalPort) {
        return
      }
    }
    Start-Sleep -Milliseconds 250
  }
  throw "Port $LocalPort could not be exclusively bound within $TimeoutSeconds seconds. No unrelated process was stopped."
}

function Get-BackendProcessIdentity {
  param(
    [Parameter(Mandatory = $true)][int]$ProcessId,
    [Parameter(Mandatory = $true)][string]$ExpectedJarPath,
    [Parameter(Mandatory = $true)][int]$ExpectedPort
  )

  $candidate = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
  if (-not $candidate -or $candidate.Name -notmatch '^java(w)?\.exe$' -or
      [string]::IsNullOrWhiteSpace([string]$candidate.CommandLine) -or -not $candidate.CreationDate) {
    return $null
  }

  $jarMatch = [regex]::Match(
    [string]$candidate.CommandLine,
    '(?i)(?:^|\s)-jar\s+(?:"(?<quoted>[^"]+)"|(?<bare>\S+))'
  )
  if (-not $jarMatch.Success) {
    return $null
  }
  $jarArgument = if ($jarMatch.Groups['quoted'].Success) {
    $jarMatch.Groups['quoted'].Value
  } else {
    $jarMatch.Groups['bare'].Value
  }
  try {
    $canonicalJarArgument = [System.IO.Path]::GetFullPath($jarArgument)
  } catch {
    return $null
  }
  if (-not [string]::Equals(
      $canonicalJarArgument,
      $ExpectedJarPath,
      [System.StringComparison]::OrdinalIgnoreCase)) {
    return $null
  }

  $portPattern = '(?i)(?:^|\s)--server\.port(?:=|\s+)' +
    [regex]::Escape([string]$ExpectedPort) + '(?:\s|$)'
  if ([string]$candidate.CommandLine -notmatch $portPattern) {
    return $null
  }

  return [pscustomobject]@{
    ProcessId = [int]$candidate.ProcessId
    StartTimeUtcTicks = ([DateTime]$candidate.CreationDate).ToUniversalTime().Ticks
  }
}

function Assert-BackendIdentityAndOwnership {
  param(
    [Parameter(Mandatory = $true)]$Snapshot,
    [Parameter(Mandatory = $true)][string]$ExpectedJarPath,
    [Parameter(Mandatory = $true)][int]$ExpectedPort
  )

  $current = Get-BackendProcessIdentity `
    -ProcessId $Snapshot.ProcessId `
    -ExpectedJarPath $ExpectedJarPath `
    -ExpectedPort $ExpectedPort
  if (-not $current -or $current.StartTimeUtcTicks -ne $Snapshot.StartTimeUtcTicks) {
    throw "Process $($Snapshot.ProcessId) identity changed before the requested operation."
  }
  $ownedListeners = @(Get-PortListeners -LocalPort $ExpectedPort |
    Where-Object { $_.OwningProcess -eq $Snapshot.ProcessId })
  if ($ownedListeners.Count -eq 0) {
    throw "Process $($Snapshot.ProcessId) no longer owns port $ExpectedPort."
  }
  return $current
}

function Get-LauncherMutexName {
  param(
    [Parameter(Mandatory = $true)][string]$WorkspacePath,
    [Parameter(Mandatory = $true)][int]$LocalPort
  )

  $keyBytes = [Text.Encoding]::UTF8.GetBytes(
    ($WorkspacePath.ToLowerInvariant() + '|' + [string]$LocalPort)
  )
  $sha256 = [Security.Cryptography.SHA256]::Create()
  try {
    $hashBytes = $sha256.ComputeHash($keyBytes)
  } finally {
    $sha256.Dispose()
  }
  $hash = ([BitConverter]::ToString($hashBytes)).Replace('-', '').Substring(0, 24)
  return "KaiPaiLocalBackend_${hash}_$LocalPort"
}

function Write-AtomicTextFile {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$Content
  )

  $temporaryPath = "$Path.$([Guid]::NewGuid().ToString('N')).tmp"
  $encoding = New-Object System.Text.UTF8Encoding($false)
  try {
    [System.IO.File]::WriteAllText($temporaryPath, $Content, $encoding)
    if (Test-Path -LiteralPath $Path -PathType Leaf) {
      [System.IO.File]::Replace($temporaryPath, $Path, $null)
    } else {
      [System.IO.File]::Move($temporaryPath, $Path)
    }
  } finally {
    if (Test-Path -LiteralPath $temporaryPath -PathType Leaf) {
      [System.IO.File]::Delete($temporaryPath)
    }
  }
}

function Get-SafeLogTail {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$SensitiveValue
  )

  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    return ''
  }
  $tail = (Get-Content -LiteralPath $Path -Tail 20) -join [Environment]::NewLine
  if (-not [string]::IsNullOrEmpty($SensitiveValue)) {
    $tail = $tail -replace [regex]::Escape($SensitiveValue), '[REDACTED]'
  }
  return $tail
}

$secretFileExists = Test-Path -LiteralPath $SecretFile -PathType Leaf
$secretValues = if ($secretFileExists) {
  Read-DotenvFile -Path $SecretFile
} else {
  @{}
}
$wechatAppIdInfo = Get-ConfigValueInfo -Key 'WECHAT_MINIAPP_APP_ID' -FileValues $secretValues
$wechatAppSecretInfo = Get-ConfigValueInfo -Key 'WECHAT_MINIAPP_APP_SECRET' -FileValues $secretValues
$wechatEnvVersionInfo = Get-ConfigValueInfo -Key 'WECHAT_MINIAPP_ENV_VERSION' -FileValues $secretValues
if (-not $secretFileExists -and
    ($wechatAppIdInfo.Source -eq 'missing' -or $wechatAppSecretInfo.Source -eq 'missing')) {
  throw 'Local WeChat secret file is absent and the process environment does not provide both required values.'
}
Assert-WeChatConfig -AppId $wechatAppIdInfo.Value -AppSecret $wechatAppSecretInfo.Value

$credentialSource = if ($wechatAppIdInfo.Source -eq $wechatAppSecretInfo.Source) {
  $wechatAppIdInfo.Source
} else {
  'mixed process environment and local secret file'
}

if ($ValidateOnly) {
  Write-Output 'Local backend WeChat config validation passed.'
  Write-Output "  Credential source: $credentialSource"
  Write-Output '  AppId matches mini-program project: yes'
  Write-Output '  AppSecret format accepted: yes'
  return
}

Assert-WindowsLauncherSupport
$resolvedJarPath = Resolve-BackendJar -RequestedPath $JarPath
$javaExecutable = Resolve-JavaExecutable
Assert-Java17 -Executable $javaExecutable

$childLauncherPath = Join-Path $scriptRoot 'start-local-backend-child.ps1'
if (-not (Test-Path -LiteralPath $childLauncherPath -PathType Leaf)) {
  throw "Internal child launcher does not exist: $childLauncherPath"
}
$childLauncherPath = (Resolve-Path -LiteralPath $childLauncherPath).Path
$powerShellExecutable = Join-Path $PSHOME 'powershell.exe'
if (-not (Test-Path -LiteralPath $powerShellExecutable -PathType Leaf)) {
  throw "Windows PowerShell executable does not exist: $powerShellExecutable"
}
$powerShellExecutable = (Resolve-Path -LiteralPath $powerShellExecutable).Path

$runtimeRoot = Join-Path $workspaceRoot '.sce\runtime\kaipai-local-backend'
$portRuntimeDir = Join-Path $runtimeRoot "port-$Port"
New-Item -ItemType Directory -Path $portRuntimeDir -Force | Out-Null
$launchId = [Guid]::NewGuid().ToString('N')
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$stdoutPath = Join-Path $portRuntimeDir "$timestamp-$launchId-backend.log"
$stderrPath = Join-Path $portRuntimeDir "$timestamp-$launchId-backend-error.log"
$pidPath = Join-Path $portRuntimeDir 'backend.pid'
$launchPidPath = Join-Path $portRuntimeDir ".launch-$launchId.pid"

foreach ($preflightPath in @($stdoutPath, $stderrPath)) {
  try {
    $stream = [System.IO.File]::Open($preflightPath, 'CreateNew', 'Write', 'None')
    $stream.Dispose()
  } catch {
    throw "Local backend runtime path is not writable: $preflightPath"
  }
}
if (Test-Path -LiteralPath $pidPath -PathType Leaf) {
  try {
    $pidStream = [System.IO.File]::Open($pidPath, 'Open', 'ReadWrite', 'Read')
    $pidStream.Dispose()
  } catch {
    throw "Local backend PID path is not writable: $pidPath"
  }
}

$mutexName = Get-LauncherMutexName -WorkspacePath $workspaceRoot -LocalPort $Port
$launcherMutex = New-Object System.Threading.Mutex -ArgumentList $false, $mutexName
$mutexAcquired = $false
$helperProcess = $null
$startedProcessId = $null
$startedProcessStartTicks = $null
$launchSucceeded = $false
$pidPathOwnedForLaunch = $false

try {
  try {
    $mutexAcquired = $launcherMutex.WaitOne(0)
  } catch [System.Threading.AbandonedMutexException] {
    $mutexAcquired = $true
  }
  if (-not $mutexAcquired) {
    throw "Another local backend launcher is already active for port $Port."
  }

  $listeners = @(Get-PortListeners -LocalPort $Port)
  if ($listeners.Count -gt 0) {
    if (-not $Restart) {
      $ownerList = ($listeners | Select-Object -ExpandProperty OwningProcess -Unique) -join ', '
      throw "Port $Port is already in use by process $ownerList. Re-run with -Restart to replace the local backend."
    }

    $ownerSnapshots = @()
    foreach ($ownerProcessId in ($listeners | Select-Object -ExpandProperty OwningProcess -Unique)) {
      $identity = Get-BackendProcessIdentity `
        -ProcessId $ownerProcessId `
        -ExpectedJarPath $resolvedJarPath `
        -ExpectedPort $Port
      if (-not $identity) {
        throw "Refusing to stop process $ownerProcessId on port $Port because its full jar path or server port does not match."
      }
      $ownerSnapshots += $identity
    }

    $currentOwnerIds = @(Get-PortListeners -LocalPort $Port |
      Select-Object -ExpandProperty OwningProcess -Unique |
      Sort-Object)
    $snapshotOwnerIds = @($ownerSnapshots.ProcessId | Sort-Object)
    if (($currentOwnerIds -join ',') -cne ($snapshotOwnerIds -join ',')) {
      throw "Port $Port ownership changed during restart validation; no process was stopped."
    }
    foreach ($snapshot in $ownerSnapshots) {
      Assert-BackendIdentityAndOwnership `
        -Snapshot $snapshot `
        -ExpectedJarPath $resolvedJarPath `
        -ExpectedPort $Port | Out-Null
    }

    $pidPathOwnedForLaunch = $true
    if (Test-Path -LiteralPath $pidPath -PathType Leaf) {
      [System.IO.File]::Delete($pidPath)
    }
    foreach ($snapshot in $ownerSnapshots) {
      Assert-BackendIdentityAndOwnership `
        -Snapshot $snapshot `
        -ExpectedJarPath $resolvedJarPath `
        -ExpectedPort $Port | Out-Null
      Stop-Process -Id $snapshot.ProcessId -Force
    }

    $stopDeadline = (Get-Date).AddSeconds(10)
    $remainingListeners = @(Get-PortListeners -LocalPort $Port)
    while ($remainingListeners.Count -gt 0 -and (Get-Date) -lt $stopDeadline) {
      Start-Sleep -Milliseconds 200
      $remainingListeners = @(Get-PortListeners -LocalPort $Port)
    }
    if ($remainingListeners.Count -gt 0) {
      throw "Port $Port did not become available after stopping the validated backend process."
    }
  }

  if (-not $pidPathOwnedForLaunch) {
    $pidPathOwnedForLaunch = $true
    if (Test-Path -LiteralPath $pidPath -PathType Leaf) {
      [System.IO.File]::Delete($pidPath)
    }
  }

  Wait-ExclusivePortBind `
    -LocalPort $Port `
    -TimeoutSeconds $PortReleaseTimeoutSeconds

  $childEnvironment = @{
    'SPRING_PROFILES_ACTIVE' = 'dev'
    'NACOS_ENABLED' = 'false'
    'SERVER_PORT' = [string]$Port
    'WECHAT_MINIAPP_APP_ID' = $wechatAppIdInfo.Value
    'WECHAT_MINIAPP_APP_SECRET' = $wechatAppSecretInfo.Value
  }
  if ($wechatEnvVersionInfo.Source -ne 'missing') {
    $childEnvironment['WECHAT_MINIAPP_ENV_VERSION'] = $wechatEnvVersionInfo.Value
  }

  $helperArguments = @(
    '-NoLogo',
    '-NoProfile',
    '-NonInteractive',
    '-ExecutionPolicy', 'Bypass',
    '-File', (Quote-ProcessArgument -Value $childLauncherPath),
    '-JavaExecutable', (Quote-ProcessArgument -Value $javaExecutable),
    '-JarPath', (Quote-ProcessArgument -Value $resolvedJarPath),
    '-WorkingDirectory', (Quote-ProcessArgument -Value $repoRoot),
    '-StdoutPath', (Quote-ProcessArgument -Value $stdoutPath),
    '-StderrPath', (Quote-ProcessArgument -Value $stderrPath),
    '-LaunchPidPath', (Quote-ProcessArgument -Value $launchPidPath),
    '-Port', [string]$Port
  ) -join ' '

  $helperStartInfo = New-Object System.Diagnostics.ProcessStartInfo
  $helperStartInfo.FileName = $powerShellExecutable
  $helperStartInfo.Arguments = $helperArguments
  $helperStartInfo.WorkingDirectory = $repoRoot
  $helperStartInfo.UseShellExecute = $false
  $helperStartInfo.CreateNoWindow = $true
  $helperStartInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
  $helperStartInfo.RedirectStandardOutput = $true
  $helperStartInfo.RedirectStandardError = $true
  foreach ($entry in $childEnvironment.GetEnumerator()) {
    $helperStartInfo.EnvironmentVariables[$entry.Key] = $entry.Value
  }

  $helperProcess = New-Object System.Diagnostics.Process
  $helperProcess.StartInfo = $helperStartInfo
  if (-not $helperProcess.Start()) {
    throw 'Failed to start the isolated local backend child launcher.'
  }
  $helperDeadline = (Get-Date).AddSeconds(15)
  while (-not (Test-Path -LiteralPath $launchPidPath -PathType Leaf) -and
      -not $helperProcess.HasExited -and
      (Get-Date) -lt $helperDeadline) {
    Start-Sleep -Milliseconds 100
  }
  if (-not (Test-Path -LiteralPath $launchPidPath -PathType Leaf)) {
    if ($helperProcess.HasExited) {
      throw "The isolated child launcher exited with code $($helperProcess.ExitCode) before publishing its launch PID."
    }
    throw 'The isolated child launcher timed out before publishing its launch PID.'
  }

  $launchPidText = (Get-Content -Raw -LiteralPath $launchPidPath).Trim()
  $parsedProcessId = 0
  if (-not [int]::TryParse($launchPidText, [ref]$parsedProcessId) -or $parsedProcessId -le 0) {
    throw 'The isolated child launcher published an invalid launch PID.'
  }
  $startedProcessId = $parsedProcessId
  $startedIdentity = Get-BackendProcessIdentity `
    -ProcessId $startedProcessId `
    -ExpectedJarPath $resolvedJarPath `
    -ExpectedPort $Port
  if (-not $startedIdentity) {
    throw "Started process $startedProcessId does not match the expected full jar path and server port."
  }
  $startedProcessStartTicks = $startedIdentity.StartTimeUtcTicks

  $readinessUri = "http://127.0.0.1:$Port/api/v3/api-docs/swagger-config"
  $startupDeadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
  $started = $false
  while ((Get-Date) -lt $startupDeadline) {
    $currentIdentity = Get-BackendProcessIdentity `
      -ProcessId $startedProcessId `
      -ExpectedJarPath $resolvedJarPath `
      -ExpectedPort $Port
    if (-not $currentIdentity -or $currentIdentity.StartTimeUtcTicks -ne $startedProcessStartTicks) {
      $safeErrorTail = Get-SafeLogTail `
        -Path $stderrPath `
        -SensitiveValue $wechatAppSecretInfo.Value
      $tailMessage = if ($safeErrorTail) { " Error log tail:`n$safeErrorTail" } else { '' }
      throw "Backend process $startedProcessId exited or changed identity during startup.$tailMessage"
    }

    $ownedBeforeRequest = @(Get-PortListeners -LocalPort $Port |
      Where-Object { $_.OwningProcess -eq $startedProcessId })
    if ($ownedBeforeRequest.Count -gt 0) {
      try {
        $response = Invoke-WebRequest `
          -Uri $readinessUri `
          -UseBasicParsing `
          -MaximumRedirection 0 `
          -TimeoutSec 2
        $contentType = [string]$response.Headers['Content-Type']
        $readinessDocument = $response.Content | ConvertFrom-Json
        $httpReady = [int]$response.StatusCode -eq 200 -and
          $contentType -match '(?i)^application/json(?:;|$)' -and
          [string]$readinessDocument.configUrl -ceq '/api/v3/api-docs/swagger-config' -and
          [string]$readinessDocument.url -ceq '/api/v3/api-docs'
      } catch {
        $httpReady = $false
      }

      if ($httpReady) {
        $identityAfterRequest = Get-BackendProcessIdentity `
          -ProcessId $startedProcessId `
          -ExpectedJarPath $resolvedJarPath `
          -ExpectedPort $Port
        $ownedAfterRequest = @(Get-PortListeners -LocalPort $Port |
          Where-Object { $_.OwningProcess -eq $startedProcessId })
        if ($identityAfterRequest -and
            $identityAfterRequest.StartTimeUtcTicks -eq $startedProcessStartTicks -and
            $ownedAfterRequest.Count -gt 0) {
          $started = $true
          break
        }
      }
    }
    Start-Sleep -Milliseconds 250
  }

  if (-not $started) {
    $safeErrorTail = Get-SafeLogTail `
      -Path $stderrPath `
      -SensitiveValue $wechatAppSecretInfo.Value
    $tailMessage = if ($safeErrorTail) { " Error log tail:`n$safeErrorTail" } else { '' }
    throw "Backend process $startedProcessId did not pass HTTP readiness within $StartupTimeoutSeconds seconds.$tailMessage"
  }

  Write-AtomicTextFile -Path $pidPath -Content ([string]$startedProcessId)
  $launchSucceeded = $true

  Write-Output 'Local backend started with validated WeChat configuration.'
  Write-Output "  PID: $startedProcessId"
  Write-Output "  API: http://127.0.0.1:$Port/api"
  Write-Output "  Readiness: $readinessUri"
  Write-Output "  PID file: $pidPath"
  Write-Output "  Log: $stdoutPath"
} finally {
  if (-not $launchSucceeded) {
    if (-not $startedProcessId -and (Test-Path -LiteralPath $launchPidPath -PathType Leaf)) {
      $cleanupPidText = (Get-Content -Raw -LiteralPath $launchPidPath).Trim()
      $cleanupProcessId = 0
      if ([int]::TryParse($cleanupPidText, [ref]$cleanupProcessId) -and $cleanupProcessId -gt 0) {
        $startedProcessId = $cleanupProcessId
      }
    }
    if (-not $startedProcessId -and $helperProcess) {
      $helperChildren = @(Get-CimInstance Win32_Process `
        -Filter "ParentProcessId = $($helperProcess.Id)" `
        -ErrorAction SilentlyContinue)
      foreach ($helperChild in $helperChildren) {
        $helperChildIdentity = Get-BackendProcessIdentity `
          -ProcessId ([int]$helperChild.ProcessId) `
          -ExpectedJarPath $resolvedJarPath `
          -ExpectedPort $Port
        if ($helperChildIdentity) {
          $startedProcessId = $helperChildIdentity.ProcessId
          $startedProcessStartTicks = $helperChildIdentity.StartTimeUtcTicks
          break
        }
      }
    }
    if ($startedProcessId) {
      $cleanupIdentity = Get-BackendProcessIdentity `
        -ProcessId $startedProcessId `
        -ExpectedJarPath $resolvedJarPath `
        -ExpectedPort $Port
      $sameStartedProcess = $cleanupIdentity -and
        (-not $startedProcessStartTicks -or
        $cleanupIdentity.StartTimeUtcTicks -eq $startedProcessStartTicks)
      if ($sameStartedProcess) {
        Stop-Process -Id $startedProcessId -Force -ErrorAction SilentlyContinue
      }
    }
    if ($pidPathOwnedForLaunch -and (Test-Path -LiteralPath $pidPath -PathType Leaf)) {
      [System.IO.File]::Delete($pidPath)
    }
  }

  if ($helperProcess) {
    if (-not $launchSucceeded -and -not $helperProcess.HasExited) {
      if (-not $helperProcess.WaitForExit(5000)) {
        $helperProcess.Kill()
        $helperProcess.WaitForExit()
      }
    }
    $helperProcess.Dispose()
  }

  if (Test-Path -LiteralPath $launchPidPath -PathType Leaf) {
    [System.IO.File]::Delete($launchPidPath)
  }
  if ($mutexAcquired) {
    $launcherMutex.ReleaseMutex()
  }
  if ($launcherMutex) {
    $launcherMutex.Dispose()
  }
}
