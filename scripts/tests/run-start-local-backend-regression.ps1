[CmdletBinding()]
param(
  [string[]]$Case = @('All'),
  [switch]$KeepArtifacts
)

$ErrorActionPreference = 'Stop'

$testsRoot = $PSScriptRoot
$scriptsRoot = (Resolve-Path (Join-Path $testsRoot '..')).Path
$launcherSource = Join-Path $scriptsRoot 'start-local-backend.ps1'
$childLauncherSource = Join-Path $scriptsRoot 'start-local-backend-child.ps1'
$fixtureSource = Join-Path $testsRoot 'fixtures\FakeJava.cs'
$environmentWorker = Join-Path $testsRoot 'helpers\invoke-launcher-environment-worker.ps1'
$processWorker = Join-Path $testsRoot 'helpers\invoke-launcher-process-worker.ps1'
$sensitiveEnvironmentKeys = @('WECHAT_MINIAPP_APP_ID', 'WECHAT_MINIAPP_APP_SECRET')
$syntheticAppId = 'wx0000000000000001'
$syntheticAppSecret = [Guid]::NewGuid().ToString('N')
$suiteId = [Guid]::NewGuid().ToString('N')
$tempBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$suiteRoot = Join-Path $tempBase "kaipai-launcher-regression-$suiteId"
$suiteMarker = Join-Path $suiteRoot '.kaipai-launcher-regression-root'
$sourceSnapshotRoot = Join-Path $suiteRoot 'source-snapshot'
$fixtureExecutable = Join-Path $suiteRoot 'fixture\java.exe'
$trackedProcesses = New-Object System.Collections.Generic.List[System.Diagnostics.Process]
$results = New-Object System.Collections.Generic.List[object]

function Assert-True {
  param(
    [Parameter(Mandatory = $true)][bool]$Condition,
    [Parameter(Mandatory = $true)][string]$Message
  )

  if (-not $Condition) {
    throw $Message
  }
}

function Quote-NativeArgument {
  param([Parameter(Mandatory = $true)][string]$Value)

  if ($Value.Contains('"')) {
    throw 'Harness paths cannot contain double-quote characters.'
  }
  return '"' + $Value + '"'
}

function Remove-SensitiveChildEnvironment {
  param([Parameter(Mandatory = $true)][System.Diagnostics.ProcessStartInfo]$StartInfo)

  foreach ($key in $sensitiveEnvironmentKeys) {
    if ($StartInfo.EnvironmentVariables.ContainsKey($key)) {
      $StartInfo.EnvironmentVariables.Remove($key)
    }
  }
}

function Get-FreeTcpPort {
  $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, 0)
  try {
    $listener.Start()
    return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
  } finally {
    $listener.Stop()
  }
}

function Wait-Until {
  param(
    [Parameter(Mandatory = $true)][scriptblock]$Condition,
    [Parameter(Mandatory = $true)][int]$TimeoutSeconds,
    [Parameter(Mandatory = $true)][string]$FailureMessage
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    if (& $Condition) {
      return
    }
    Start-Sleep -Milliseconds 50
  }
  throw $FailureMessage
}

function Read-FixtureMarker {
  param([Parameter(Mandatory = $true)][string]$Path)

  $values = @{}
  foreach ($line in Get-Content -LiteralPath $Path -ErrorAction Stop) {
    $separator = $line.IndexOf('=')
    if ($separator -gt 0) {
      $values[$line.Substring(0, $separator)] = $line.Substring($separator + 1)
    }
  }
  return $values
}

function Wait-ForFixtureMarker {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [int]$TimeoutSeconds = 10
  )

  Wait-Until -TimeoutSeconds $TimeoutSeconds -FailureMessage 'Fake Java did not publish its marker.' -Condition {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
      return $false
    }
    try {
      $marker = Read-FixtureMarker -Path $Path
      return $marker.ContainsKey('PID') -and $marker.ContainsKey('READY')
    } catch {
      return $false
    }
  }
  return Read-FixtureMarker -Path $Path
}

function Wait-ForOwnedListener {
  param(
    [Parameter(Mandatory = $true)][int]$Port,
    [Parameter(Mandatory = $true)][int]$ProcessId,
    [int]$TimeoutSeconds = 10
  )

  Wait-Until -TimeoutSeconds $TimeoutSeconds -FailureMessage "Process $ProcessId did not own listener port $Port." -Condition {
    $owners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
      Select-Object -ExpandProperty OwningProcess -Unique)
    return $owners -contains $ProcessId
  }
}

function New-TestSandbox {
  param([Parameter(Mandatory = $true)][string]$Name)

  $workspace = Join-Path $suiteRoot ("sandbox-{0}-{1}" -f $Name, [Guid]::NewGuid().ToString('N'))
  $serverRoot = Join-Path $workspace 'kaipaile-server'
  $stagedScripts = Join-Path $serverRoot 'scripts'
  $frontendRoot = Join-Path $workspace 'kaipai-frontend'
  $secretRoot = Join-Path $workspace '.sce\config\local-secrets'
  $fakeJavaHome = Join-Path $workspace 'fake-jdk'
  $fakeJavaBin = Join-Path $fakeJavaHome 'bin'
  $artifacts = Join-Path $workspace 'test-artifacts'

  foreach ($directory in @($stagedScripts, $frontendRoot, $secretRoot, $fakeJavaBin, $artifacts)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
  }

  Copy-Item -LiteralPath (Join-Path $sourceSnapshotRoot 'start-local-backend.ps1') -Destination $stagedScripts
  Copy-Item -LiteralPath (Join-Path $sourceSnapshotRoot 'start-local-backend-child.ps1') -Destination $stagedScripts
  Copy-Item -LiteralPath $fixtureExecutable -Destination (Join-Path $fakeJavaBin 'java.exe')

  $projectConfig = '{"appid":"' + $syntheticAppId + '"}'
  [System.IO.File]::WriteAllText((Join-Path $frontendRoot 'project.config.json'), $projectConfig)

  $secretFile = Join-Path $secretRoot 'wechat-miniapp.env'
  [System.IO.File]::WriteAllLines($secretFile, @(
    "WECHAT_MINIAPP_APP_ID=$syntheticAppId",
    "WECHAT_MINIAPP_APP_SECRET=$syntheticAppSecret"
  ))

  $expectedJar = Join-Path $serverRoot 'expected-backend.jar'
  $otherJar = Join-Path $serverRoot 'other-backend.jar'
  [System.IO.File]::WriteAllText($expectedJar, 'fake jar')
  [System.IO.File]::WriteAllText($otherJar, 'other fake jar')

  return [PSCustomObject]@{
    Workspace = $workspace
    ServerRoot = $serverRoot
    Launcher = Join-Path $stagedScripts 'start-local-backend.ps1'
    SecretFile = $secretFile
    ExpectedJar = $expectedJar
    OtherJar = $otherJar
    JavaHome = $fakeJavaHome
    JavaExecutable = Join-Path $fakeJavaBin 'java.exe'
    RuntimeDirectory = Join-Path $workspace '.sce\runtime\kaipai-local-backend'
    Artifacts = $artifacts
  }
}

function Start-UncapturedProcess {
  param([Parameter(Mandatory = $true)][System.Diagnostics.ProcessStartInfo]$StartInfo)

  $StartInfo.UseShellExecute = $false
  $StartInfo.CreateNoWindow = $true
  $StartInfo.RedirectStandardOutput = $false
  $StartInfo.RedirectStandardError = $false
  Remove-SensitiveChildEnvironment -StartInfo $StartInfo

  $process = New-Object System.Diagnostics.Process
  $process.StartInfo = $StartInfo
  if (-not $process.Start()) {
    throw 'Failed to start a harness child process.'
  }
  $trackedProcesses.Add($process)

  return [PSCustomObject]@{
    Process = $process
    StandardOutputPath = $null
    StandardErrorPath = $null
    ResultPath = $null
  }
}

function Complete-ProcessInvocation {
  param(
    [Parameter(Mandatory = $true)]$Invocation,
    [int]$TimeoutSeconds = 30
  )

  $process = $Invocation.Process
  if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
    $process.Kill()
    $process.WaitForExit()
    throw 'Harness child process exceeded its timeout.'
  }
  $process.WaitForExit()

  $effectiveExitCode = [int]$process.ExitCode
  if ($Invocation.ResultPath -and (Test-Path -LiteralPath $Invocation.ResultPath -PathType Leaf)) {
    $resultText = (Get-Content -Raw -LiteralPath $Invocation.ResultPath).Trim()
    $parsedExitCode = 0
    if ([int]::TryParse($resultText, [ref]$parsedExitCode)) {
      $effectiveExitCode = $parsedExitCode
    }
  }

  return [PSCustomObject]@{
    ExitCode = $effectiveExitCode
    StandardOutput = if ($Invocation.StandardOutputPath -and
        (Test-Path -LiteralPath $Invocation.StandardOutputPath -PathType Leaf)) {
      Get-Content -Raw -LiteralPath $Invocation.StandardOutputPath
    } else { '' }
    StandardError = if ($Invocation.StandardErrorPath -and
        (Test-Path -LiteralPath $Invocation.StandardErrorPath -PathType Leaf)) {
      Get-Content -Raw -LiteralPath $Invocation.StandardErrorPath
    } else { '' }
  }
}

function Start-LauncherInvocation {
  param(
    [Parameter(Mandatory = $true)]$Sandbox,
    [Parameter(Mandatory = $true)][int]$Port,
    [Parameter(Mandatory = $true)][string]$Mode,
    [Parameter(Mandatory = $true)][string]$MarkerPath,
    [int]$StartupTimeoutSeconds = 15,
    [int]$DelayMilliseconds = 0,
    [switch]$ValidateOnly,
    [switch]$Restart
  )

  $powerShellExecutable = Join-Path $PSHOME 'powershell.exe'
  $invocationId = [Guid]::NewGuid().ToString('N')
  $standardOutputPath = Join-Path $Sandbox.Artifacts "$invocationId-launcher.stdout.log"
  $standardErrorPath = Join-Path $Sandbox.Artifacts "$invocationId-launcher.stderr.log"
  $resultPath = Join-Path $Sandbox.Artifacts "$invocationId-launcher.result"
  $arguments = @(
    '-NoProfile',
    '-ExecutionPolicy', 'Bypass',
    '-File', (Quote-NativeArgument -Value $processWorker),
    '-LauncherPath', (Quote-NativeArgument -Value $Sandbox.Launcher),
    '-SecretFile', (Quote-NativeArgument -Value $Sandbox.SecretFile),
    '-JarPath', (Quote-NativeArgument -Value $Sandbox.ExpectedJar),
    '-Port', [string]$Port,
    '-StartupTimeoutSeconds', [string]$StartupTimeoutSeconds,
    '-StandardOutputPath', (Quote-NativeArgument -Value $standardOutputPath),
    '-StandardErrorPath', (Quote-NativeArgument -Value $standardErrorPath),
    '-ResultPath', (Quote-NativeArgument -Value $resultPath)
  )
  if ($Restart) {
    $arguments += '-Restart'
  }
  if ($ValidateOnly) {
    $arguments += '-ValidateOnly'
  }

  $startInfo = New-Object System.Diagnostics.ProcessStartInfo
  $startInfo.FileName = $powerShellExecutable
  $startInfo.Arguments = $arguments -join ' '
  $startInfo.WorkingDirectory = $Sandbox.ServerRoot
  $startInfo.EnvironmentVariables['JAVA_HOME'] = $Sandbox.JavaHome
  $startInfo.EnvironmentVariables['KAIPAI_LAUNCHER_TEST_MODE'] = $Mode
  $startInfo.EnvironmentVariables['KAIPAI_LAUNCHER_TEST_MARKER'] = $MarkerPath
  $startInfo.EnvironmentVariables['KAIPAI_LAUNCHER_TEST_DELAY_MS'] = [string]$DelayMilliseconds
  $invocation = Start-UncapturedProcess -StartInfo $startInfo
  $invocation.StandardOutputPath = $standardOutputPath
  $invocation.StandardErrorPath = $standardErrorPath
  $invocation.ResultPath = $resultPath
  return $invocation
}

function Start-EnvironmentWorkerInvocation {
  param(
    [Parameter(Mandatory = $true)]$Sandbox,
    [Parameter(Mandatory = $true)][int]$Port,
    [Parameter(Mandatory = $true)][string]$FixtureMarker,
    [Parameter(Mandatory = $true)][string]$MonitorStopPath,
    [Parameter(Mandatory = $true)][string]$ParentLeakMarkerPath
  )

  $powerShellExecutable = Join-Path $PSHOME 'powershell.exe'
  $arguments = @(
    '-NoProfile',
    '-ExecutionPolicy', 'Bypass',
    '-File', (Quote-NativeArgument -Value $environmentWorker),
    '-LauncherPath', (Quote-NativeArgument -Value $Sandbox.Launcher),
    '-SecretFile', (Quote-NativeArgument -Value $Sandbox.SecretFile),
    '-JarPath', (Quote-NativeArgument -Value $Sandbox.ExpectedJar),
    '-Port', [string]$Port,
    '-MonitorStopPath', (Quote-NativeArgument -Value $MonitorStopPath),
    '-ParentLeakMarkerPath', (Quote-NativeArgument -Value $ParentLeakMarkerPath)
  )

  $startInfo = New-Object System.Diagnostics.ProcessStartInfo
  $startInfo.FileName = $powerShellExecutable
  $startInfo.Arguments = $arguments -join ' '
  $startInfo.WorkingDirectory = $Sandbox.ServerRoot
  $startInfo.EnvironmentVariables['JAVA_HOME'] = $Sandbox.JavaHome
  $startInfo.EnvironmentVariables['KAIPAI_LAUNCHER_TEST_MODE'] = 'listen'
  $startInfo.EnvironmentVariables['KAIPAI_LAUNCHER_TEST_MARKER'] = $FixtureMarker
  $startInfo.EnvironmentVariables['KAIPAI_LAUNCHER_TEST_DELAY_MS'] = '0'
  return Start-UncapturedProcess -StartInfo $startInfo
}

function Start-FakeJavaListener {
  param(
    [Parameter(Mandatory = $true)]$Sandbox,
    [Parameter(Mandatory = $true)][int]$Port,
    [Parameter(Mandatory = $true)][string]$JarPath,
    [Parameter(Mandatory = $true)][string]$MarkerPath
  )

  $startInfo = New-Object System.Diagnostics.ProcessStartInfo
  $startInfo.FileName = $Sandbox.JavaExecutable
  $startInfo.Arguments = @(
    '-jar', (Quote-NativeArgument -Value $JarPath), "--server.port=$Port"
  ) -join ' '
  $startInfo.WorkingDirectory = $Sandbox.ServerRoot
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  Remove-SensitiveChildEnvironment -StartInfo $startInfo
  $startInfo.EnvironmentVariables['KAIPAI_LAUNCHER_TEST_MODE'] = 'listen'
  $startInfo.EnvironmentVariables['KAIPAI_LAUNCHER_TEST_MARKER'] = $MarkerPath
  $startInfo.EnvironmentVariables['KAIPAI_LAUNCHER_TEST_DELAY_MS'] = '0'

  $process = New-Object System.Diagnostics.Process
  $process.StartInfo = $startInfo
  if (-not $process.Start()) {
    throw 'Failed to start the unrelated-listener fixture.'
  }
  $trackedProcesses.Add($process)
  return $process
}

function Stop-TrackedProcess {
  param([System.Diagnostics.Process]$Process)

  if ($null -eq $Process) {
    return
  }
  try {
    if (-not $Process.HasExited) {
      $Process.Kill()
      $Process.WaitForExit(5000) | Out-Null
    }
  } catch {
    # Outer cleanup also discovers fixture processes by the suite path.
  }
}

function Assert-NoSyntheticSecretInResult {
  param([Parameter(Mandatory = $true)]$Result)

  $combined = [string]$Result.StandardOutput + [string]$Result.StandardError
  Assert-True -Condition (-not $combined.Contains($syntheticAppSecret)) `
    -Message 'Launcher output exposed the synthetic app secret.'
}

function Set-SandboxSecretValues {
  param(
    [Parameter(Mandatory = $true)]$Sandbox,
    [Parameter(Mandatory = $true)][string]$AppId,
    [Parameter(Mandatory = $true)][string]$AppSecret
  )

  [System.IO.File]::WriteAllLines($Sandbox.SecretFile, @(
    "WECHAT_MINIAPP_APP_ID=$AppId",
    "WECHAT_MINIAPP_APP_SECRET=$AppSecret"
  ))
}

function Invoke-ConfigurationValidation {
  param(
    [Parameter(Mandatory = $true)]$Sandbox,
    [Parameter(Mandatory = $true)][int]$Port
  )

  $markerPath = Join-Path $Sandbox.Artifacts 'validation-unused.marker'
  $invocation = Start-LauncherInvocation -Sandbox $Sandbox -Port $Port -Mode 'validate' `
    -MarkerPath $markerPath -StartupTimeoutSeconds 5 -ValidateOnly
  return Complete-ProcessInvocation -Invocation $invocation -TimeoutSeconds 15
}

function Assert-ConfigurationRejected {
  param(
    [Parameter(Mandatory = $true)]$Result,
    [Parameter(Mandatory = $true)][string]$Pattern,
    [Parameter(Mandatory = $true)][string]$Label
  )

  Assert-NoSyntheticSecretInResult -Result $Result
  Assert-True -Condition ($Result.ExitCode -ne 0) `
    -Message "$Label configuration unexpectedly passed validation."
  $combinedOutput = [string]$Result.StandardOutput + [string]$Result.StandardError
  Assert-True -Condition ($combinedOutput -match $Pattern) `
    -Message "$Label configuration failed outside its expected validation branch."
}

function Test-ConfigurationValidation {
  $sandbox = New-TestSandbox -Name 'configuration-validation'
  $port = Get-FreeTcpPort

  $validResult = Invoke-ConfigurationValidation -Sandbox $sandbox -Port $port
  Assert-NoSyntheticSecretInResult -Result $validResult
  Assert-True -Condition ($validResult.ExitCode -eq 0) `
    -Message 'Valid synthetic configuration did not pass validation.'
  Assert-True -Condition ($validResult.StandardOutput -match 'AppSecret format accepted: yes') `
    -Message 'Valid configuration did not report the bounded format-acceptance conclusion.'

  Set-SandboxSecretValues -Sandbox $sandbox -AppId $syntheticAppId `
    -AppSecret 'replace-with-real-app-secret'
  $placeholderResult = Invoke-ConfigurationValidation -Sandbox $sandbox -Port $port
  Assert-ConfigurationRejected -Result $placeholderResult -Pattern 'placeholder|too short' -Label 'Placeholder'

  Set-SandboxSecretValues -Sandbox $sandbox -AppId 'invalid-app-id' `
    -AppSecret $syntheticAppSecret
  $invalidResult = Invoke-ConfigurationValidation -Sandbox $sandbox -Port $port
  Assert-ConfigurationRejected -Result $invalidResult -Pattern 'invalid format' -Label 'Malformed AppId'

  Set-SandboxSecretValues -Sandbox $sandbox -AppId 'wx0000000000000002' `
    -AppSecret $syntheticAppSecret
  $mismatchResult = Invoke-ConfigurationValidation -Sandbox $sandbox -Port $port
  Assert-ConfigurationRejected -Result $mismatchResult -Pattern 'does not match' -Label 'Mismatched AppId'

  Set-SandboxSecretValues -Sandbox $sandbox -AppId $syntheticAppId `
    -AppSecret $syntheticAppSecret
  $projectConfigPath = Join-Path $sandbox.Workspace 'kaipai-frontend\project.config.json'
  Remove-Item -LiteralPath $projectConfigPath -Force
  $missingProjectResult = Invoke-ConfigurationValidation -Sandbox $sandbox -Port $port
  Assert-ConfigurationRejected -Result $missingProjectResult `
    -Pattern 'project.config.json is required' -Label 'Missing project.config.json'
}

function Test-UnrelatedOwnerRefusal {
  $sandbox = New-TestSandbox -Name 'owner-refusal'
  $port = Get-FreeTcpPort
  $listenerMarker = Join-Path $sandbox.Artifacts 'unrelated.marker'
  $unrelated = $null

  try {
    $unrelated = Start-FakeJavaListener -Sandbox $sandbox -Port $port -JarPath $sandbox.OtherJar -MarkerPath $listenerMarker
    $marker = Wait-ForFixtureMarker -Path $listenerMarker
    Wait-ForOwnedListener -Port $port -ProcessId ([int]$marker['PID'])

    $launcherMarker = Join-Path $sandbox.Artifacts 'unexpected-launch.marker'
    $invocation = Start-LauncherInvocation -Sandbox $sandbox -Port $port -Mode 'listen' `
      -MarkerPath $launcherMarker -StartupTimeoutSeconds 5 -Restart
    $result = Complete-ProcessInvocation -Invocation $invocation -TimeoutSeconds 15

    Assert-NoSyntheticSecretInResult -Result $result
    Assert-True -Condition ($result.ExitCode -ne 0) -Message 'Launcher replaced an unrelated listener owner.'
    $combinedOutput = [string]$result.StandardOutput + [string]$result.StandardError
    Assert-True -Condition ($combinedOutput -match 'Refusing to stop process') `
      -Message 'Launcher failed before exercising unrelated-owner refusal.'
    Assert-True -Condition (-not $unrelated.HasExited) -Message 'Unrelated listener process was stopped.'
    Wait-ForOwnedListener -Port $port -ProcessId $unrelated.Id -TimeoutSeconds 2
  } finally {
    Stop-TrackedProcess -Process $unrelated
  }
}

function Test-StartupTimeoutCleanup {
  $sandbox = New-TestSandbox -Name 'timeout-cleanup'
  $port = Get-FreeTcpPort
  $fixtureMarker = Join-Path $sandbox.Artifacts 'timeout.marker'

  $invocation = Start-LauncherInvocation -Sandbox $sandbox -Port $port -Mode 'timeout' `
    -MarkerPath $fixtureMarker -StartupTimeoutSeconds 1
  $result = Complete-ProcessInvocation -Invocation $invocation -TimeoutSeconds 15
  $marker = Wait-ForFixtureMarker -Path $fixtureMarker
  $fixturePid = [int]$marker['PID']

  Assert-NoSyntheticSecretInResult -Result $result
  Assert-True -Condition ($result.ExitCode -ne 0) -Message 'Timeout fixture unexpectedly reported launcher success.'
  Wait-Until -TimeoutSeconds 5 -FailureMessage 'Timed-out backend process remained alive.' -Condition {
    return $null -eq (Get-Process -Id $fixturePid -ErrorAction SilentlyContinue)
  }

  $pidFiles = @(Get-ChildItem -LiteralPath $sandbox.RuntimeDirectory -Filter '*.pid' -File -Recurse -ErrorAction SilentlyContinue)
  foreach ($pidFile in $pidFiles) {
    $publishedPid = [int](Get-Content -Raw -LiteralPath $pidFile.FullName)
    Assert-True -Condition ($publishedPid -ne $fixturePid) -Message 'Timeout left a stale PID artifact.'
  }
}

function Test-ChildOnlyEnvironmentPropagation {
  $sandbox = New-TestSandbox -Name 'child-environment'
  $port = Get-FreeTcpPort
  $fixtureMarker = Join-Path $sandbox.Artifacts 'child.marker'
  $monitorStopPath = Join-Path $sandbox.Artifacts 'monitor.stop'
  $parentLeakMarkerPath = Join-Path $sandbox.Artifacts 'parent-env-leak.marker'
  $fixtureProcess = $null

  try {
    $invocation = Start-EnvironmentWorkerInvocation -Sandbox $sandbox -Port $port `
      -FixtureMarker $fixtureMarker -MonitorStopPath $monitorStopPath `
      -ParentLeakMarkerPath $parentLeakMarkerPath
    $result = Complete-ProcessInvocation -Invocation $invocation -TimeoutSeconds 30
    $marker = Wait-ForFixtureMarker -Path $fixtureMarker
    $fixturePid = [int]$marker['PID']
    $fixtureProcess = Get-Process -Id $fixturePid -ErrorAction SilentlyContinue

    Assert-NoSyntheticSecretInResult -Result $result
    Assert-True -Condition ($result.ExitCode -eq 0) -Message 'Child-environment launcher invocation failed.'
    Assert-True -Condition ($marker['READY'] -eq '1') -Message 'Fake Java child did not become ready.'
    Assert-True -Condition ($marker['APP_ID_PRESENT'] -eq '1') -Message 'AppId did not reach the Java child.'
    Assert-True -Condition ($marker['APP_SECRET_PRESENT'] -eq '1') -Message 'AppSecret did not reach the Java child.'
    Assert-True -Condition (-not (Test-Path -LiteralPath $parentLeakMarkerPath)) `
      -Message 'A WeChat credential entered the launcher parent process environment.'
  } finally {
    Stop-TrackedProcess -Process $fixtureProcess
  }
}

function Test-ConcurrentDifferentPortPidIsolation {
  $sandbox = New-TestSandbox -Name 'pid-isolation'
  $firstPort = Get-FreeTcpPort
  $secondPort = Get-FreeTcpPort
  while ($secondPort -eq $firstPort) {
    $secondPort = Get-FreeTcpPort
  }

  $firstMarkerPath = Join-Path $sandbox.Artifacts 'first.marker'
  $secondMarkerPath = Join-Path $sandbox.Artifacts 'second.marker'
  $firstProcess = $null
  $secondProcess = $null

  try {
    $firstTimestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $firstInvocation = Start-LauncherInvocation -Sandbox $sandbox -Port $firstPort -Mode 'listen' `
      -MarkerPath $firstMarkerPath -StartupTimeoutSeconds 15 -DelayMilliseconds 3500
    $firstMarker = Wait-ForFixtureMarker -Path $firstMarkerPath

    Wait-Until -TimeoutSeconds 2 -FailureMessage 'Could not cross a timestamp boundary for the concurrent launch.' -Condition {
      return (Get-Date -Format 'yyyyMMdd-HHmmss') -ne $firstTimestamp
    }

    $secondInvocation = Start-LauncherInvocation -Sandbox $sandbox -Port $secondPort -Mode 'listen' `
      -MarkerPath $secondMarkerPath -StartupTimeoutSeconds 15 -DelayMilliseconds 3500
    $secondMarker = Wait-ForFixtureMarker -Path $secondMarkerPath

    $firstResult = Complete-ProcessInvocation -Invocation $firstInvocation -TimeoutSeconds 30
    $secondResult = Complete-ProcessInvocation -Invocation $secondInvocation -TimeoutSeconds 30
    Assert-NoSyntheticSecretInResult -Result $firstResult
    Assert-NoSyntheticSecretInResult -Result $secondResult
    Assert-True -Condition ($firstResult.ExitCode -eq 0 -and $secondResult.ExitCode -eq 0) `
      -Message 'One of the overlapping different-port launches failed.'

    $firstPid = [int]$firstMarker['PID']
    $secondPid = [int]$secondMarker['PID']
    $firstProcess = Get-Process -Id $firstPid -ErrorAction SilentlyContinue
    $secondProcess = Get-Process -Id $secondPid -ErrorAction SilentlyContinue
    Assert-True -Condition ($null -ne $firstProcess -and $null -ne $secondProcess) `
      -Message 'One different-port backend was not alive after launch.'
    Wait-ForOwnedListener -Port $firstPort -ProcessId $firstPid -TimeoutSeconds 3
    Wait-ForOwnedListener -Port $secondPort -ProcessId $secondPid -TimeoutSeconds 3

    $pidFiles = @(Get-ChildItem -LiteralPath $sandbox.RuntimeDirectory -Filter '*.pid' -File -Recurse -ErrorAction SilentlyContinue)
    $publishedPids = @($pidFiles | ForEach-Object {
      try {
        [int](Get-Content -Raw -LiteralPath $_.FullName)
      } catch {
        -1
      }
    })
    Assert-True -Condition ($publishedPids -contains $firstPid) `
      -Message 'First port lost its PID artifact when the second port launched.'
    Assert-True -Condition ($publishedPids -contains $secondPid) `
      -Message 'Second port did not publish an isolated PID artifact.'
    Assert-True -Condition ($pidFiles.Count -ge 2) `
      -Message 'Different ports share a single PID artifact.'
  } finally {
    Stop-TrackedProcess -Process $firstProcess
    Stop-TrackedProcess -Process $secondProcess
  }
}

function Test-ConcurrentSamePortMutexIsolation {
  $sandbox = New-TestSandbox -Name 'same-port-mutex'
  $port = Get-FreeTcpPort
  $firstMarkerPath = Join-Path $sandbox.Artifacts 'first.marker'
  $secondMarkerPath = Join-Path $sandbox.Artifacts 'second.marker'
  $firstProcess = $null

  try {
    $firstInvocation = Start-LauncherInvocation -Sandbox $sandbox -Port $port -Mode 'listen' `
      -MarkerPath $firstMarkerPath -StartupTimeoutSeconds 15 -DelayMilliseconds 3500
    $firstMarker = Wait-ForFixtureMarker -Path $firstMarkerPath
    $firstPid = [int]$firstMarker['PID']
    $firstProcess = Get-Process -Id $firstPid -ErrorAction SilentlyContinue
    Assert-True -Condition ($null -ne $firstProcess) `
      -Message 'First same-port backend exited before the competing launch.'

    $secondInvocation = Start-LauncherInvocation -Sandbox $sandbox -Port $port -Mode 'listen' `
      -MarkerPath $secondMarkerPath -StartupTimeoutSeconds 15 -DelayMilliseconds 0
    $secondResult = Complete-ProcessInvocation -Invocation $secondInvocation -TimeoutSeconds 15
    Assert-NoSyntheticSecretInResult -Result $secondResult
    Assert-True -Condition ($secondResult.ExitCode -ne 0) `
      -Message 'Second same-port launcher bypassed the port-scoped mutex.'
    $secondOutput = [string]$secondResult.StandardOutput + [string]$secondResult.StandardError
    Assert-True -Condition ($secondOutput -match 'Another local backend launcher is already active for port') `
      -Message 'Second same-port launcher failed outside the mutex refusal path.'
    Assert-True -Condition (-not (Test-Path -LiteralPath $secondMarkerPath -PathType Leaf)) `
      -Message 'Second same-port launcher started another Java process.'
    Assert-True -Condition (-not $firstProcess.HasExited) `
      -Message 'Second same-port launcher stopped the first Java process.'

    $firstResult = Complete-ProcessInvocation -Invocation $firstInvocation -TimeoutSeconds 30
    Assert-NoSyntheticSecretInResult -Result $firstResult
    Assert-True -Condition ($firstResult.ExitCode -eq 0) `
      -Message 'First same-port launcher did not complete after the competing call was refused.'

    $readyMarker = Read-FixtureMarker -Path $firstMarkerPath
    Assert-True -Condition ($readyMarker['READY'] -eq '1') `
      -Message 'First same-port Java process did not become ready.'
    Assert-True -Condition (-not $firstProcess.HasExited) `
      -Message 'First same-port Java process was not alive after readiness.'
    Wait-ForOwnedListener -Port $port -ProcessId $firstPid -TimeoutSeconds 3

    $pidFiles = @(Get-ChildItem -LiteralPath $sandbox.RuntimeDirectory -Filter '*.pid' -File -Recurse -ErrorAction SilentlyContinue)
    $publishedPids = @($pidFiles | ForEach-Object {
      try {
        [int](Get-Content -Raw -LiteralPath $_.FullName)
      } catch {
        -1
      }
    })
    Assert-True -Condition ($publishedPids -contains $firstPid) `
      -Message 'Competing same-port launcher replaced or removed the first PID artifact.'
    Assert-True -Condition (@($publishedPids | Where-Object { $_ -gt 0 -and $_ -ne $firstPid }).Count -eq 0) `
      -Message 'Same-port runtime published a PID other than the first backend.'
  } finally {
    Stop-TrackedProcess -Process $firstProcess
  }
}

function Invoke-TestCase {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][scriptblock]$Body
  )

  $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
  try {
    & $Body
    $results.Add([PSCustomObject]@{ Name = $Name; Passed = $true; Message = ''; Milliseconds = $stopwatch.ElapsedMilliseconds })
    Write-Host ("[PASS] {0} ({1} ms)" -f $Name, $stopwatch.ElapsedMilliseconds)
  } catch {
    $message = $_.Exception.Message
    if (-not [string]::IsNullOrEmpty($syntheticAppSecret)) {
      $message = $message.Replace($syntheticAppSecret, '[REDACTED]')
    }
    $results.Add([PSCustomObject]@{ Name = $Name; Passed = $false; Message = $message; Milliseconds = $stopwatch.ElapsedMilliseconds })
    Write-Host ("[FAIL] {0}: {1}" -f $Name, $message)
  } finally {
    $stopwatch.Stop()
  }
}

function Stop-SuiteProcesses {
  $escapedRoot = [regex]::Escape($suiteRoot)
  foreach ($processName in @('java.exe', 'powershell.exe')) {
    $candidates = @(Get-CimInstance Win32_Process -Filter "Name = '$processName'" -ErrorAction SilentlyContinue |
      Where-Object { $_.CommandLine -and $_.CommandLine -match $escapedRoot })
    foreach ($candidate in $candidates) {
      Stop-Process -Id $candidate.ProcessId -Force -ErrorAction SilentlyContinue
    }
  }
}

function Remove-SuiteDirectory {
  $resolvedSuiteRoot = [System.IO.Path]::GetFullPath($suiteRoot)
  $resolvedTempBase = [System.IO.Path]::GetFullPath($tempBase).TrimEnd('\') + '\'
  $isUnderTemp = $resolvedSuiteRoot.StartsWith($resolvedTempBase, [System.StringComparison]::OrdinalIgnoreCase)
  if (-not $isUnderTemp -or -not (Test-Path -LiteralPath $suiteMarker -PathType Leaf)) {
    throw 'Refusing to remove an unverified regression-suite directory.'
  }
  Remove-Item -LiteralPath $resolvedSuiteRoot -Recurse -Force
}

$allowedCases = @('All', 'ConfigValidation', 'OwnerRefusal', 'PidIsolation', 'SamePortMutex', 'TimeoutCleanup', 'ChildEnvironment')
$requestedCases = @($Case | ForEach-Object { @([string]$_ -split ',') } |
  ForEach-Object { $_.Trim() } | Where-Object { $_ })
foreach ($requestedCase in $requestedCases) {
  if ($allowedCases -notcontains $requestedCase) {
    throw "Unknown regression case: $requestedCase"
  }
}

$selectedCases = if ($requestedCases -contains 'All') {
  @('ConfigValidation', 'OwnerRefusal', 'PidIsolation', 'SamePortMutex', 'TimeoutCleanup', 'ChildEnvironment')
} else {
  @($requestedCases | Select-Object -Unique)
}

try {
  foreach ($requiredPath in @($launcherSource, $childLauncherSource, $fixtureSource, $environmentWorker, $processWorker)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
      throw "Required harness input is missing: $requiredPath"
    }
  }

  New-Item -ItemType Directory -Path $sourceSnapshotRoot -Force | Out-Null
  New-Item -ItemType Directory -Path (Split-Path -Parent $fixtureExecutable) -Force | Out-Null
  [System.IO.File]::WriteAllText($suiteMarker, 'launcher-regression-suite')
  Copy-Item -LiteralPath $launcherSource -Destination $sourceSnapshotRoot
  Copy-Item -LiteralPath $childLauncherSource -Destination $sourceSnapshotRoot
  Add-Type -Path $fixtureSource -OutputAssembly $fixtureExecutable -OutputType ConsoleApplication

  $launcherHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $launcherSource).Hash.Substring(0, 12)
  Write-Host "Windows PowerShell launcher regression suite (launcher $launcherHash)"
  Write-Host "PowerShell: $($PSVersionTable.PSVersion) $($PSVersionTable.PSEdition)"

  if ($selectedCases -contains 'ConfigValidation') {
    Invoke-TestCase -Name 'configuration preflight accepts valid input and rejects unsafe variants' -Body { Test-ConfigurationValidation }
  }
  if ($selectedCases -contains 'OwnerRefusal') {
    Invoke-TestCase -Name 'unrelated listener owner is refused and remains alive' -Body { Test-UnrelatedOwnerRefusal }
  }
  if ($selectedCases -contains 'PidIsolation') {
    Invoke-TestCase -Name 'overlapping different-port launches isolate PID artifacts' -Body { Test-ConcurrentDifferentPortPidIsolation }
  }
  if ($selectedCases -contains 'SamePortMutex') {
    Invoke-TestCase -Name 'overlapping same-port launch is refused without replacing the first PID' -Body { Test-ConcurrentSamePortMutexIsolation }
  }
  if ($selectedCases -contains 'TimeoutCleanup') {
    Invoke-TestCase -Name 'startup timeout stops child and removes its PID artifact' -Body { Test-StartupTimeoutCleanup }
  }
  if ($selectedCases -contains 'ChildEnvironment') {
    Invoke-TestCase -Name 'WeChat values reach only the Java child environment' -Body { Test-ChildOnlyEnvironmentPropagation }
  }
} finally {
  foreach ($trackedProcess in $trackedProcesses) {
    Stop-TrackedProcess -Process $trackedProcess
  }
  Stop-SuiteProcesses

  if ($KeepArtifacts) {
    Write-Host "Artifacts retained at: $suiteRoot"
  } elseif (Test-Path -LiteralPath $suiteRoot) {
    Remove-SuiteDirectory
  }
}

$failed = @($results | Where-Object { -not $_.Passed })
Write-Host ("Summary: {0} passed, {1} failed." -f ($results.Count - $failed.Count), $failed.Count)
if ($failed.Count -gt 0) {
  exit 1
}
exit 0
