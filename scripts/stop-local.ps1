$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$statePath = Join-Path $root 'data\local\processes.json'
if (!(Test-Path -LiteralPath $statePath)) { Write-Host 'No recorded local instance.'; exit 0 }
$state = Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json
foreach ($entry in @(@{ Id = $state.platform; Jar = 'chaosmonkey-worker-1.0.0-SNAPSHOT.jar' }, @{ Id = $state.demo; Jar = 'chaosmonkey-demo-app-1.0.0-SNAPSHOT-exec.jar' })) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($entry.Id)" -ErrorAction SilentlyContinue
    if ($process -and $process.CommandLine.Contains($root) -and $process.CommandLine.Contains($entry.Jar)) {
        Stop-Process -Id $entry.Id
    } elseif ($process) { throw "PID $($entry.Id) belongs to a different application; it was not stopped." }
}
Remove-Item -LiteralPath $statePath
Write-Host 'ChaosMonkey QA stopped. Saved projects and results are preserved.'
