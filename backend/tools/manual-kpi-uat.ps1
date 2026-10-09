param(
    [ValidateSet('Setup', 'Start', 'UseUat', 'UseDevelopment', 'Verify', 'CheckSource')]
    [string] $Action = 'Verify'
)
$ErrorActionPreference = 'Stop'
$backend = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location $backend
try {
    $state = Join-Path $backend 'target/manual-kpi-uat'
    New-Item -ItemType Directory -Path $state -Force | Out-Null
    $cpFile = Join-Path $backend 'target/phase1-classpath.txt'
    if (-not (Test-Path -LiteralPath $cpFile)) {
        & .\mvnw.cmd dependency:build-classpath '-Dmdep.outputFile=target/phase1-classpath.txt'
        if ($LASTEXITCODE -ne 0) { throw 'Dependency classpath build failed' }
    }
    $dependencies = (Get-Content -LiteralPath $cpFile -Raw).Trim()
    $runtime = "$(Join-Path $backend 'target/classes');$dependencies"
    $java = (Get-Command java).Source
    $cfg = @{}
    Get-Content -LiteralPath (Join-Path $backend '.env') | ForEach-Object {
        if ($_ -match '^([A-Z_]+)=(.*)$') { $cfg[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'") }
    }
    $source = [uri]($cfg.DB_URL.Substring(5))
    if ($source.Host -notin @('localhost', '127.0.0.1') -or $source.AbsolutePath -eq '/apex_manual_uat') {
        throw 'Expected the unchanged local development .env, not a UAT source'
    }
    $targetUrl = "jdbc:postgresql://$($source.Host):$($source.Port)/apex_manual_uat"

    function Invoke-Fixture([string] $command, [int] $port = 8082) {
        & $java --class-path $dependencies tools/ManualKpiAssessmentUat.java $command "http://localhost:$port"
        if ($LASTEXITCODE -ne 0) { throw "UAT fixture action failed: $command" }
    }
    function Start-Backend([int] $port, [bool] $uat) {
        if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
            throw "Port $port is occupied; stop/switch the expected backend explicitly"
        }
        $arguments = "-cp `"$runtime`" com.tbm.careerpathlearning.CareerPathLearningBackendApplication --spring.profiles.active=dev --server.port=$port"
        $label = 'development'
        if ($uat) {
            $arguments = '-Dspring.devtools.restart.enabled=false ' + $arguments
            $label = "uat-$port"
            $uploads = Join-Path $state 'uploads'
            New-Item -ItemType Directory -Path $uploads -Force | Out-Null
            $arguments += " --server.address=127.0.0.1 --spring.datasource.url=$targetUrl --file.upload-dir=`"$uploads`" --spring.flyway.enabled=false"
            $arguments += ' --spring.mail.host=127.0.0.1 --spring.mail.port=1025 --spring.mail.properties.mail.smtp.auth=false'
            $arguments += ' --spring.mail.properties.mail.smtp.starttls.enable=false --spring.mail.properties.mail.smtp.starttls.required=false'
            $arguments += ' --spring.mail.properties.mail.debug=false --mail-server.no-reply=apex-uat@example.test'
            $arguments += ' --jwt.secret=APEX-local-disposable-UAT-only-2026-unique-signing-key-not-for-production-1234567890'
        }
        $process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $backend -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $state "$label.out.log") -RedirectStandardError (Join-Path $state "$label.err.log")
        $process.Id | Set-Content -LiteralPath (Join-Path $state "$label.pid")
        for ($i = 0; $i -lt 90; $i++) {
            if ($process.HasExited) { throw "Backend exited. Read $state/$label.err.log" }
            $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($listener) {
                $listener.OwningProcess | Set-Content -LiteralPath (Join-Path $state "$label.pid")
                return
            }
            Start-Sleep -Seconds 1
        }
        throw "Backend did not become ready. Read $state/$label.out.log"
    }
    function Start-Mail {
        $pidFile = Join-Path $state 'mail.pid'
        $listener = Get-NetTCPConnection -LocalPort 1025 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($listener) {
            $owner = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
            $saved = if (Test-Path -LiteralPath $pidFile) { [int](Get-Content -LiteralPath $pidFile) } else { -1 }
            if ($owner.CommandLine -notlike '*tools/ManualKpiAssessmentUat.java mail*' -or
                ($saved -ne $owner.ProcessId -and $saved -ne $owner.ParentProcessId)) {
                throw 'Port 1025 belongs to an unverified service; refusing to use it'
            }
            $owner.ProcessId | Set-Content -LiteralPath $pidFile
            return
        }
        $process = Start-Process -FilePath $java -ArgumentList "--class-path `"$dependencies`" tools/ManualKpiAssessmentUat.java mail" `
            -WorkingDirectory $backend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $state 'mail.out.log') `
            -RedirectStandardError (Join-Path $state 'mail.err.log')
        $process.Id | Set-Content -LiteralPath $pidFile
        for ($i = 0; $i -lt 30; $i++) {
            if ($process.HasExited) { throw 'Local mail capture failed to start' }
            $listener = Get-NetTCPConnection -LocalPort 1025 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($listener) { $listener.OwningProcess | Set-Content -LiteralPath $pidFile; return }
            Start-Sleep -Seconds 1
        }
        throw 'Local mail capture did not become ready'
    }
    function Stop-ExpectedBackend([int] $port, [bool] $uat) {
        $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $listener) { return }
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
        if ($process.Name -ne 'java.exe' -or $process.CommandLine -notlike '*com.tbm.careerpathlearning.CareerPathLearningBackendApplication*') {
            throw "Port $port is not the expected APEX backend; refusing to stop it"
        }
        $isUat = $process.CommandLine -like '*--spring.datasource.url=*apex_manual_uat*'
        if ($isUat -ne $uat) { throw 'Backend/database mode does not match the requested switch' }
        if (-not $uat) {
            # Verify the inherited Maven argfile or explicit runtime belongs to this checkout.
            $belongs = $process.CommandLine.Contains($backend)
            if ($process.CommandLine -match '@([^\s"]+)') {
                $argfile = $matches[1]
                if (Test-Path -LiteralPath $argfile) {
                    $content = Get-Content -LiteralPath $argfile -Raw
                    $belongs = $content.Contains($backend) -or $content.Contains($backend.Replace('\', '\\'))
                }
            }
            if (-not $belongs) { throw 'Development process does not belong to this checkout' }
        }
        Stop-Process -Id $process.ProcessId
        for ($i = 0; $i -lt 15; $i++) {
            if (-not (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)) { return }
            Start-Sleep -Seconds 1
        }
        throw "Port $port did not stop"
    }

    switch ($Action) {
        'Setup' {
            & .\mvnw.cmd '-DskipTests' package
            if ($LASTEXITCODE -ne 0) { throw 'Backend build failed; no database setup performed' }
            Invoke-Fixture 'clone'
            Invoke-Fixture 'bootstrap'
            Start-Mail
            Start-Backend 8082 $true
            Invoke-Fixture 'seed'
            Invoke-Fixture 'verify'
            Invoke-Fixture 'smoke'
            Invoke-Fixture 'negative'
            Write-Host 'Setup verified on port 8082. Run -Action UseUat to connect the existing Angular UI.'
        }
        'Start' { Start-Mail; Start-Backend 8082 $true }
        'UseUat' {
            Invoke-Fixture 'check-source'
            Invoke-Fixture 'check-uat'
            Start-Mail
            $listener = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($listener) {
                $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
                if ($process.CommandLine -like '*--spring.datasource.url=*apex_manual_uat*' -and
                    $process.CommandLine.Contains($backend) -and
                    $process.CommandLine -like '*com.tbm.careerpathlearning.CareerPathLearningBackendApplication*') {
                    Write-Host 'The isolated UAT backend is already connected on port 8081.'
                    return
                }
                Stop-ExpectedBackend 8081 $false
            }
            Stop-ExpectedBackend 8082 $true
            Start-Backend 8081 $true
            Write-Host 'Angular localhost:4200 now uses apex_manual_uat. Log out and use a disposable UAT account.'
        }
        'UseDevelopment' {
            Invoke-Fixture 'check-source'
            Stop-ExpectedBackend 8081 $true
            Start-Backend 8081 $false
            Write-Host 'Original development backend restored. Log out before using your original accounts.'
        }
        'Verify' {
            $port = 8082
            $listener = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($listener) {
                $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
                if ($process.CommandLine -like '*--spring.datasource.url=*apex_manual_uat*') { $port = 8081 }
            }
            Invoke-Fixture 'verify' $port
        }
        'CheckSource' { Invoke-Fixture 'check-source' }
    }
} finally { Pop-Location }
