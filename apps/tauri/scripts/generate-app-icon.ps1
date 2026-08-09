# 从官方 SVG（对齐 Android ic_kuayun_cloud）生成全平台图标
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

node scripts/generate-app-icon.mjs
if ($LASTEXITCODE -ne 0) { throw "generate-app-icon.mjs failed" }
Write-Host "Done. Source of truth: assets/app-icon.svg (= Android vector path)"
