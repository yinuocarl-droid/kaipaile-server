param(
  [ValidateSet('dev', 'prod')]
  [string]$Environment = 'dev',

  [ValidateSet('auto', 'true', 'false')]
  [string]$NacosEnabled = 'auto',

  [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptRoot '..')

$resolvedNacosEnabled = $NacosEnabled
if ($resolvedNacosEnabled -eq 'auto') {
  if ($Environment -eq 'prod') {
    $resolvedNacosEnabled = 'true'
  } else {
    $resolvedNacosEnabled = 'false'
  }
}

$env:SPRING_PROFILES_ACTIVE = $Environment
$env:NACOS_ENABLED = $resolvedNacosEnabled

$mvnArgs = @('clean', 'package')
if ($SkipTests) {
  $mvnArgs = @('-DskipTests') + $mvnArgs
}

Write-Host "Backend package environment:"
Write-Host "  SPRING_PROFILES_ACTIVE=$env:SPRING_PROFILES_ACTIVE"
Write-Host "  NACOS_ENABLED=$env:NACOS_ENABLED"
Write-Host "  Command: mvn $($mvnArgs -join ' ')"

Push-Location $repoRoot
try {
  & mvn @mvnArgs
  if ($LASTEXITCODE -ne 0) {
    throw "Maven package failed with exit code $LASTEXITCODE"
  }

  $jar = Get-ChildItem -Path (Join-Path $repoRoot 'target') -Filter '*.jar' |
    Where-Object { $_.Name -notlike '*.original' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

  if (-not $jar) {
    throw 'Package completed but no jar was found under target/.'
  }

  Write-Host "Package succeeded:"
  Write-Host "  Jar: $($jar.FullName)"
  Write-Host "Runtime reminder:"
  Write-Host "  Set SPRING_PROFILES_ACTIVE=$Environment and NACOS_ENABLED=$resolvedNacosEnabled when starting this jar."
} finally {
  Pop-Location
}
