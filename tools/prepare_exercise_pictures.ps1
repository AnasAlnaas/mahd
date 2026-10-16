<#
Prepares the exercise pictures for the app.

1. Put the pictures (PNG, JPG or WebP) in drafts\exercise_pictures, each named as in exercises.json "images"
   (e.g. preg_cat_cow_1.png). That folder is not saved in git: keep your originals backed up elsewhere too.
2. Run:  powershell -ExecutionPolicy Bypass -File tools\prepare_exercise_pictures.ps1
   (or pass another folder: -Source C:\path\to\pictures)

Each picture is shrunk to at most 1000 px wide, its near-white background made pure white, and saved as WebP (transparency kept) in app\src\main\assets\exercises.
The script then lists the pictures still missing. Needs ffmpeg (winget install Gyan.FFmpeg).
After it, run gradlew testDebugUnitTest: ExercisesTest checks every file in that folder belongs to an exercise.
#>
param(
    [string]$Source = (Join-Path $PSScriptRoot "..\drafts\exercise_pictures"),
    [string]$Output = (Join-Path $PSScriptRoot "..\app\src\main\assets\exercises"),
    [int]$MaxWidth = 1000,
    [int]$Quality = 85
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

# The picture names the app expects, in order.
$json = Get-Content (Join-Path $root "app\src\main\assets\content\exercises.json") -Raw -Encoding UTF8 | ConvertFrom-Json
$expected = @()
foreach ($part in @($json.pregnancy, $json.postpartum)) {
    foreach ($stage in $part.stages) {
        foreach ($exercise in $stage.exercises) { $expected += $exercise.images }
    }
}
$expected = @($expected | Select-Object -Unique)

$ffmpeg = Get-Command ffmpeg -ErrorAction SilentlyContinue
if (-not $ffmpeg) { Write-Host "ffmpeg not found. Install it with:  winget install Gyan.FFmpeg"; exit 1 }
if (-not (Test-Path $Source)) { Write-Host "Folder not found: $Source"; exit 1 }
New-Item -ItemType Directory -Force $Output | Out-Null

$done = 0
$unknown = @()
$files = Get-ChildItem $Source -File | Where-Object { $_.Extension -match '^\.(png|jpe?g|webp)$' }
foreach ($file in $files) {
    $name = $file.BaseName.ToLowerInvariant()
    if ($expected -notcontains $name) { $unknown += $file.Name; continue }
    $target = Join-Path $Output "$name.webp"
    # Near-white becomes pure white: JPEG noise would otherwise show as faint specks on the app's white picture panel.
    $white = "if(gt(val,245),255,val)"
    & $ffmpeg.Source -hide_banner -loglevel error -y -i $file.FullName `
        -vf "scale='min($MaxWidth,iw)':-2,lutrgb=r='$white':g='$white':b='$white'" `
        -frames:v 1 -c:v libwebp -quality $Quality -pix_fmt yuva420p $target
    if ($LASTEXITCODE -ne 0) { throw "ffmpeg failed on $($file.Name)" }
    $kb = [math]::Round((Get-Item $target).Length / 1KB)
    Write-Host ("OK       {0,-26} {1} KB" -f $name, $kb)
    $done++
}

Write-Host ""
Write-Host "$done picture(s) prepared in $Output"
foreach ($u in $unknown) { Write-Host "UNKNOWN  $u  (no exercise uses this name: check the spelling)" }
$missing = @($expected | Where-Object { -not (Test-Path (Join-Path $Output "$_.webp")) })
foreach ($m in $missing) { Write-Host "MISSING  $m" }
if ($missing.Count -eq 0) { Write-Host "All $($expected.Count) pictures are in place." }
