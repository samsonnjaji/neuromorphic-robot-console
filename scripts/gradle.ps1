$ErrorActionPreference = "Stop"
$GradleVersion = "9.7.0"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ToolsDirectory = Join-Path $ProjectRoot ".tools"
$DistributionDirectory = Join-Path $ToolsDirectory "gradle-$GradleVersion"
$GradleExecutable = Join-Path $DistributionDirectory "bin/gradle.bat"

if (-not (Test-Path $GradleExecutable)) {
    New-Item -ItemType Directory -Force -Path $ToolsDirectory | Out-Null
    $Archive = Join-Path $ToolsDirectory "gradle-$GradleVersion-bin.zip"
    $BaseUrl = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"
    $Expected = "84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae"

    Write-Host "Downloading Gradle $GradleVersion..."
    Invoke-WebRequest -Uri $BaseUrl -OutFile $Archive

    $Actual = (Get-FileHash -Path $Archive -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($Expected -ne $Actual) {
        Remove-Item $Archive -Force
        throw "Gradle distribution checksum verification failed."
    }

    Expand-Archive -Path $Archive -DestinationPath $ToolsDirectory -Force
    Remove-Item $Archive -Force
}

Push-Location $ProjectRoot
try {
    & $GradleExecutable @args
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
