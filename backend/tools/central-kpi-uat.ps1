param(
    [ValidateSet('UseUat', 'UseDevelopment', 'Status')]
    [string] $Action = 'Status'
)
$ErrorActionPreference = 'Stop'
$backend = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$state = Join-Path $backend 'target/central-kpi-uat'
$jar = Join-Path $backend 'target/career-path-learning-backend-0.0.1-SNAPSHOT.jar'
$database = 'apex_central_results_uat'
$jarName = 'career-path-learning-backend-0.0.1-SNAPSHOT.jar'

function Get-Backend([int] $port) {
    $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $listener) { return $null }
    return Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
}

function Get-BackendMode($process) {
    if (-not $process) { return 'Stopped' }
    if ($process.Name -ne 'java.exe' -or $process.CommandLine -notlike "*$jarName*") { return 'Unknown' }
    if ($process.CommandLine -like "*--spring.datasource.url=*$database*") { return 'CentralUat' }
    if ($process.CommandLine -like '*--spring.datasource.url=*') { return 'Unknown' }
    return 'Development'
}

function Stop-Expected([int] $port, [string] $mode) {
    $process = Get-Backend $port
    if (-not $process) { return }
    if ((Get-BackendMode $process) -ne $mode) {
        throw "Port $port is not the expected $mode backend; refusing to stop it"
    }
    Stop-Process -Id $process.ProcessId
    for ($i = 0; $i -lt 20; $i++) {
        if (-not (Get-Backend $port)) { return }
        Start-Sleep -Seconds 1
    }
    throw "Backend on port $port did not stop"
}

function Start-Backend([string] $mode) {
    if (Get-Backend 8081) { throw 'Port 8081 is occupied' }
    if (-not (Test-Path -LiteralPath $jar)) { throw "Backend JAR not found: $jar" }
    $label = if ($mode -eq 'CentralUat') { 'uat-8081' } else { 'development-8081' }
    $arguments = "-jar `"$jar`" --spring.profiles.active=dev --server.address=127.0.0.1 --server.port=8081"
    if ($mode -eq 'CentralUat') {
        $uploads = Join-Path $state 'uploads'
        New-Item -ItemType Directory -Force -Path $uploads | Out-Null
        $arguments += " --spring.datasource.url=jdbc:postgresql://localhost:5432/$database"
        $arguments += " --spring.flyway.enabled=false --file.upload-dir=`"$uploads`""
        $arguments += ' --spring.mail.host=127.0.0.1 --spring.mail.port=1025'
        $arguments += ' --spring.mail.properties.mail.smtp.auth=false'
        $arguments += ' --spring.mail.properties.mail.smtp.starttls.enable=false'
        $arguments += ' --spring.mail.properties.mail.smtp.starttls.required=false'
        $arguments += ' --mail-server.no-reply=apex-uat@example.test'
        $arguments += ' --jwt.secret=APEX-local-central-UAT-only-2026-unique-signing-key-not-for-production-1234567890'
    }
    $process = Start-Process -FilePath (Get-Command java).Source -ArgumentList $arguments `
        -WorkingDirectory $backend -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $state "$label.out.log") `
        -RedirectStandardError (Join-Path $state "$label.err.log")
    for ($i = 0; $i -lt 90; $i++) {
        if ($process.HasExited) { throw "Backend exited during startup; inspect $state/$label.err.log" }
        $running = Get-Backend 8081
        if ($running) {
            if ((Get-BackendMode $running) -ne $mode) { throw 'A different process took port 8081' }
            return
        }
        Start-Sleep -Seconds 1
    }
    throw "Backend did not become ready; inspect $state/$label.out.log"
}

function Invoke-Fixture([string] $command, [int] $port = 8082) {
    $cpFile = Join-Path $backend 'target/phase1-classpath.txt'
    if (-not (Test-Path -LiteralPath $cpFile)) { throw 'Backend dependency classpath is unavailable' }
    $dependencies = (Get-Content -LiteralPath $cpFile -Raw).Trim()
    & java --class-path $dependencies tools/CentralKpiResultsUat.java $command "http://localhost:$port"
    if ($LASTEXITCODE -ne 0) { throw "Central UAT verification failed: $command" }
}

Push-Location $backend
try {
    switch ($Action) {
        'Status' {
            foreach ($port in 8081, 8082) {
                $process = Get-Backend $port
                $mode = Get-BackendMode $process
                $identifier = if ($process) { $process.ProcessId } else { '-' }
                Write-Host "Port $port : $mode (PID $identifier)"
            }
        }
        'UseUat' {
            Invoke-Fixture 'check-source'
            Invoke-Fixture 'check-uat'
            $mail = Get-NetTCPConnection -LocalPort 1025 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
            if (-not $mail) { throw 'Local mail capture is not running on port 1025' }
            $mailProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($mail.OwningProcess)"
            if ($mailProcess.CommandLine -notlike '*ManualKpiAssessmentUat.java mail*') {
                throw 'Port 1025 is not the expected local mail capture'
            }
            $at8081 = Get-Backend 8081
            if ((Get-BackendMode $at8081) -eq 'CentralUat') {
                Invoke-Fixture 'probe' 8081
                Write-Host 'Angular already uses central-results UAT on port 8081.'
                break
            }
            if ($at8081 -and (Get-BackendMode $at8081) -ne 'Development') {
                throw 'Port 8081 is not the expected development backend'
            }
            if ((Get-BackendMode (Get-Backend 8082)) -eq 'CentralUat') {
                Invoke-Fixture 'probe' 8082
                Stop-Expected 8082 'CentralUat'
            } elseif (Get-Backend 8082) {
                throw 'Port 8082 belongs to an unexpected process'
            }
            if ($at8081) { Stop-Expected 8081 'Development' }
            try {
                Start-Backend 'CentralUat'
                Invoke-Fixture 'probe' 8081
            } catch {
                if (-not (Get-Backend 8081)) { Start-Backend 'Development' }
                throw
            }
            Write-Host 'Angular localhost:4200 now uses isolated central-results UAT. Log out and sign in with a disposable UAT account.'
        }
        'UseDevelopment' {
            Invoke-Fixture 'check-uat'
            $at8081 = Get-Backend 8081
            if ((Get-BackendMode $at8081) -eq 'Development') {
                Write-Host 'Angular already uses the development backend.'
                break
            }
            if ((Get-BackendMode $at8081) -ne 'CentralUat') {
                throw 'Port 8081 is not the expected central-results UAT backend'
            }
            Stop-Expected 8081 'CentralUat'
            Start-Backend 'Development'
            Write-Host 'Angular localhost:4200 now uses the unchanged development database. Log out before using development accounts.'
        }
    }
} finally {
    Pop-Location
}
