#!/usr/bin/env pwsh
# Downloads the pre-built NCNN Android SDK for native inference
$ErrorActionPreference = "Stop"

$version = "20240820"
$url = "https://github.com/Tencent/ncnn/releases/download/$version/ncnn-$version-android.zip"
$cppDir = Join-Path $PSScriptRoot "app/src/main/cpp"
$sdkDir = Join-Path $cppDir "ncnn-sdk"
$zipFile = Join-Path $cppDir "ncnn-sdk.zip"

if (Test-Path $sdkDir) {
    Write-Host "NCNN SDK already exists at $sdkDir — skipping download."
    exit 0
}

Write-Host "Downloading NCNN Android SDK v$version ..."
Invoke-WebRequest -Uri $url -OutFile $zipFile -UseBasicParsing

Write-Host "Extracting ..."
Expand-Archive -Path $zipFile -DestinationPath $cppDir -Force

# The archive extracts to ncnn-<version>-android/
$extracted = Join-Path $cppDir "ncnn-$version-android"
if (Test-Path $extracted) {
    Rename-Item $extracted $sdkDir
}
Remove-Item $zipFile -ErrorAction SilentlyContinue

Write-Host "Done! NCNN SDK installed at $sdkDir"
Write-Host "Supported ABIs: arm64-v8a, armeabi-v7a, x86, x86_64"
