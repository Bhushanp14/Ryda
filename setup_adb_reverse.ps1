Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host "         RYDA - ADB Reverse Port Forwarding Setup (Port 8000)        " -ForegroundColor Cyan
Write-Host "=====================================================================" -ForegroundColor Cyan

$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adbPath)) {
    $adbPath = "adb"
}

Write-Host "Checking connected Android devices..." -ForegroundColor Yellow
$deviceLines = & $adbPath devices -l | Where-Object { $_ -match "\s+device\b" }

if (-not $deviceLines) {
    Write-Host "ERROR: No Android devices detected. Ensure USB/Wireless ADB is enabled." -ForegroundColor Red
    exit 1
}

$deviceSerials = @()
foreach ($line in $deviceLines) {
    if ($line -match "^(\S+)\s+device") {
        $deviceSerials += $Matches[1]
    }
}

Write-Host "Found $($deviceSerials.Count) device(s): $($deviceSerials -join ', ')" -ForegroundColor Green
$targetSerial = $deviceSerials[0]

Write-Host "Checking Django server on local port 8000..." -ForegroundColor Yellow
$netstat = netstat -ano | Select-String ":8000\s+.*LISTENING"

if (-not $netstat) {
    Write-Host "WARNING: No local process is listening on port 8000." -ForegroundColor Red
    Write-Host "Please start Django backend first: python manage.py runserver 0.0.0.0:8000" -ForegroundColor Red
} else {
    Write-Host "Django Backend is actively listening on 0.0.0.0:8000" -ForegroundColor Green
}

Write-Host "Applying reverse port mapping (tcp:8000 -> tcp:8000) on device $targetSerial..." -ForegroundColor Yellow
& $adbPath -s $targetSerial reverse tcp:8000 tcp:8000

Write-Host "Verifying reverse mappings on $targetSerial..." -ForegroundColor Yellow
$reverseList = & $adbPath -s $targetSerial reverse --list

if ($reverseList -match "tcp:8000\s+tcp:8000") {
    Write-Host "SUCCESS: ADB reverse mapping verified on device!" -ForegroundColor Green
    Write-Host "Device 127.0.0.1:8000 is now linked to PC 0.0.0.0:8000" -ForegroundColor Green
} else {
    Write-Host "ERROR: Failed to verify reverse mapping on device." -ForegroundColor Red
    exit 1
}
