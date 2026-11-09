param([switch]$SkipBuild, [switch]$EnableDefects)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root
$local = Join-Path $root 'data\local'
New-Item -ItemType Directory -Force -Path $local | Out-Null
$statePath = Join-Path $local 'processes.json'
$credentialsPath = Join-Path $local 'credentials.properties'
$java = Get-ChildItem -Path (Join-Path $root '.tools\jre21\*\bin\java.exe') -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
if (!$java) { $java = (Get-Command java.exe -ErrorAction Stop).Source }

function Wait-Http([string]$Url, [int]$ProcessId) {
    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline) {
        if (!(Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)) {
            throw "Process $ProcessId exited. Inspect data/local/*.err.log and *.out.log."
        }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3
            if ($response.StatusCode -eq 200) { return }
        } catch { Start-Sleep -Milliseconds 750 }
    }
    throw "Startup timed out: $Url. Inspect data/local logs."
}

if (Test-Path -LiteralPath $statePath) {
    $saved = Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json
    $running = @(Get-Process -Id @($saved.demo, $saved.platform) -ErrorAction SilentlyContinue)
    if ($running.Count -eq 2) {
        Wait-Http 'http://localhost:3000/' $saved.platform
        Write-Host 'ChaosMonkey QA is already running: http://localhost:3000'
        Write-Host 'Username: admin. Password: data/local/credentials.properties'
        exit 0
    }
    if ($running.Count -gt 0) { throw 'A partial instance is running. Run scripts/stop-local.ps1, then start again.' }
}

foreach ($port in @(3000, 8090)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) {
        throw "Port $port is occupied. Stop that application before starting ChaosMonkey QA."
    }
}
if (!(Test-Path -LiteralPath $credentialsPath)) {
    $secret = [Guid]::NewGuid().ToString('N')
    [IO.File]::WriteAllText($credentialsPath, "chaos.admin-password=$secret`n", [Text.Encoding]::ASCII)
}
if (!$SkipBuild) {
    Write-Host 'Building Java modules (details: data/local/build.log)...'
    & mvn.cmd -B -ntp package -DskipTests *> (Join-Path $local 'build.log')
    if ($LASTEXITCODE -ne 0) { Get-Content (Join-Path $local 'build.log') -Tail 30; throw 'Java build failed.' }
    Push-Location (Join-Path $root 'chaosmonkey-web')
    try {
        if (!(Test-Path -LiteralPath 'node_modules\.bin\vite.cmd')) {
            & npm.cmd ci *> (Join-Path $local 'npm-install.log')
            if ($LASTEXITCODE -ne 0) { throw 'npm ci failed. See data/local/npm-install.log.' }
        }
        Write-Host 'Building dashboard...'
        & npm.cmd run build *> (Join-Path $local 'web-build.log')
        if ($LASTEXITCODE -ne 0) { Get-Content (Join-Path $local 'web-build.log') -Tail 25; throw 'Dashboard build failed.' }
    } finally { Pop-Location }
}
$browser = Join-Path $env:LOCALAPPDATA 'ms-playwright\chromium_headless_shell-1194\chrome-win\headless_shell.exe'
if (!(Test-Path -LiteralPath $browser)) {
    Write-Host 'Installing the matching Chromium browser...'
    & mvn.cmd -B -ntp -pl chaosmonkey-engine org.codehaus.mojo:exec-maven-plugin:3.6.2:java '-Dexec.mainClass=com.microsoft.playwright.CLI' '-Dexec.args=install chromium' *> (Join-Path $local 'browser-install.log')
    if ($LASTEXITCODE -ne 0) { throw 'Browser installation failed. See data/local/browser-install.log.' }
}
$demoJar = Join-Path $root 'chaosmonkey-demo-app\target\chaosmonkey-demo-app-1.0.0-SNAPSHOT-exec.jar'
$workerJar = Join-Path $root 'chaosmonkey-worker\target\chaosmonkey-worker-1.0.0-SNAPSHOT.jar'
foreach ($jar in @($demoJar, $workerJar)) { if (!(Test-Path -LiteralPath $jar)) { throw "Missing $jar. Run again without -SkipBuild." } }
$started = @()
try {
    $defects = if ($EnableDefects) { 'true' } else { 'false' }
    $demo = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $demoJar + '"'), '--server.address=127.0.0.1', '--server.port=8090', "--demo.allow-defects=$defects") -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput (Join-Path $local 'demo.out.log') -RedirectStandardError (Join-Path $local 'demo.err.log') -PassThru
    $started += $demo.Id
    Wait-Http 'http://localhost:8090/api/products' $demo.Id
    $platform = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $workerJar + '"'), '--spring.profiles.active=local') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput (Join-Path $local 'platform.out.log') -RedirectStandardError (Join-Path $local 'platform.err.log') -PassThru
    $started += $platform.Id
    Wait-Http 'http://localhost:3000/' $platform.Id
    @{ demo = $demo.Id; platform = $platform.Id } | ConvertTo-Json | Set-Content -LiteralPath $statePath
    Write-Host 'ChaosMonkey QA is running: http://localhost:3000'
    Write-Host 'Demo store: http://localhost:8090'
    Write-Host 'Username: admin. Password: data/local/credentials.properties'
    Write-Host 'Stop: powershell -ExecutionPolicy Bypass -File scripts/stop-local.ps1'
} catch {
    foreach ($processId in $started) { Stop-Process -Id $processId -ErrorAction SilentlyContinue }
    Get-Content (Join-Path $local 'platform.err.log') -Tail 15 -ErrorAction SilentlyContinue
    throw
}
