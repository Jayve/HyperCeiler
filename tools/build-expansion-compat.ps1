<#
.SYNOPSIS
    Rebuilds the offline fallback artifact for hyperceiler-expansion (local-maven).

.DESCRIPTION
    Sources live in local-maven-src/expansion-compat and only cover the members the
    main repository actually references. Requires JDK 17+ and the Android SDK
    (platforms/android-37*). The script is kept ASCII-only on purpose: Windows
    PowerShell 5.1 reads .ps1 files as ANSI when there is no BOM.
#>
[CmdletBinding()]
param(
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$Javac = "javac",
    [string]$Jar = "jar"
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

if (-not $SdkRoot) {
    foreach ($candidate in @("$env:LOCALAPPDATA\Android\Sdk", 'D:\Runtime\Android\Sdk')) {
        if (Test-Path $candidate) { $SdkRoot = $candidate; break }
    }
}
if (-not $SdkRoot -or -not (Test-Path $SdkRoot)) {
    throw "Android SDK not found. Pass -SdkRoot or set ANDROID_HOME."
}

$platform = Get-ChildItem (Join-Path $SdkRoot 'platforms') -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName 'android.jar') } |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $platform) { throw "No android.jar found under $SdkRoot\platforms." }

$androidJar = Join-Path $platform.FullName 'android.jar'
$srcDir = Join-Path $repoRoot 'local-maven-src\expansion-compat'
$outDir = Join-Path $env:TEMP ("expansion-compat-" + [guid]::NewGuid().ToString('N'))
$artifactDir = Join-Path $repoRoot 'local-maven\com\sevtinge\hyperceiler\hyperceiler-expansion\1.0.0'
$artifact = Join-Path $artifactDir 'hyperceiler-expansion-1.0.0.jar'

New-Item -ItemType Directory -Force -Path $outDir, $artifactDir | Out-Null

$sources = Get-ChildItem $srcDir -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
Write-Host "android.jar : $androidJar"
Write-Host "sources     : $($sources.Count)"

& $Javac -nowarn -source 17 -target 17 -cp $androidJar -d $outDir $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed." }

& $Jar --create --file $artifact -C $outDir .
if ($LASTEXITCODE -ne 0) { throw "jar failed." }

Remove-Item -Recurse -Force $outDir
Write-Host "artifact    : $artifact"
