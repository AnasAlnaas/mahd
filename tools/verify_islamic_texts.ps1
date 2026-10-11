# Checks every Qur'an verse and hadith in app/src/main/assets/content/islamic_advice.json against primary sources.
#  - Qur'an: the exact verse(s) named in "source", from api.quran.com (imla'i script).
#  - Hadith: the full Arabic text of Sahih al-Bukhari (Fath al-Bari numbers) and Sahih Muslim (Abd al-Baqi numbers),
#    from the open fawazahmed0/hadith-api dataset (downloaded once into -Cache).
# A verse must match letter for letter; a hadith's WHOLE text must appear in the narration with that number
# (diacritics, punctuation and honorifics such as ﷺ / رضي الله عنه are ignored). Only an opening attribution
# ("عن فلان قال:" or "قال رسول الله ﷺ:") may be ours, and "…" marks a shortening.
# This checks the WORDING only. Whether each reflection ("advice") matches the meaning must still be read by a person.
#
# Run from the project folder (Windows PowerShell):  powershell -ExecutionPolicy Bypass -File tools\verify_islamic_texts.ps1
# Saved as UTF-8 WITH a byte-order mark: Windows PowerShell 5.1 misreads the Arabic in this file otherwise.
param(
    [string]$Advice = "app\src\main\assets\content\islamic_advice.json",
    [string]$Cache = (Join-Path $env:TEMP "atwar-verify")
)
$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force $Cache | Out-Null
foreach ($book in "bukhari", "muslim") {
    $file = Join-Path $Cache "$book.json"
    if (-not (Test-Path $file)) {
        Invoke-WebRequest "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions/ara-$book.min.json" -OutFile $file -UseBasicParsing
    }
}
$surahs = @{ "البقرة" = 2; "آل عمران" = 3; "النساء" = 4; "يوسف" = 12; "إبراهيم" = 14; "الرعد" = 13; "الكهف" = 18; "مريم" = 19;
    "طه" = 20; "الحج" = 22; "المؤمنون" = 23; "الفرقان" = 25; "القصص" = 28; "الروم" = 30; "لقمان" = 31; "الأحقاف" = 46;
    "الطلاق" = 65; "التحريم" = 66; "نوح" = 71; "الشرح" = 94; "القدر" = 97 }

function Norm([string]$s) {
    if ($null -eq $s) { return "" }
    $s = [regex]::Replace($s, "[ً-ٰٟۖ-ۭـ]", "")
    $s = [regex]::Replace($s, "[آأإٱ]", [string][char]0x0627)
    # "ﷺ" in our texts, the words in full in the collections: drop both.
    $s = $s.Replace([string][char]0xFDFA, " ")
    $s = $s.Replace([char]0x0649, [char]0x064A).Replace([char]0x0629, [char]0x0647)
    $s = $s.Replace([char]0x0624, [char]0x0648).Replace([char]0x0626, [char]0x064A)
    $s = [regex]::Replace($s, "[^ء-ي ]", " ")
    $s = [regex]::Replace($s, "\s+", " ")
    $s = (" " + $s + " ").Replace(" صلي الله عليه وسلم ", " ")
    # Honorifics after a Companion's name are added freely by narrators and editors.
    foreach ($h in " رضي الله عنهما ", " رضي الله عنها ", " رضي الله عنه ", " عليها السلام ", " عليه السلام ") { $s = $s.Replace($h, " ") }
    return $s.Trim()
}

$items = (Get-Content $Advice -Raw -Encoding UTF8 | ConvertFrom-Json).items
$bukhari = @{}
foreach ($h in (Get-Content (Join-Path $Cache "bukhari.json") -Raw -Encoding UTF8 | ConvertFrom-Json).hadiths) { $bukhari[[int]$h.hadithnumber] = $h.text }
$muslim = @{}
foreach ($h in (Get-Content (Join-Path $Cache "muslim.json") -Raw -Encoding UTF8 | ConvertFrom-Json).hadiths) {
    $n = [int][math]::Floor([double]$h.arabicnumber)
    if ($n -gt 0) { $muslim[$n] = "$($muslim[$n]) $($h.text)" }
}

$out = New-Object System.Collections.Generic.List[string]
foreach ($a in $items) {
    if ($a.kind -eq "quran") {
        $m = [regex]::Match($a.source, "^\S+\s+(.+?):\s*(\d+)(?:\s*[–-]\s*(\d+))?$")
        $name = $m.Groups[1].Value.Trim()
        $num = $surahs[$name]
        if (-not $num) { $out.Add("XX $($a.id): add sura '$name' to the list in this script"); continue }
        $from = [int]$m.Groups[2].Value; $to = if ($m.Groups[3].Success) { [int]$m.Groups[3].Value } else { $from }
        $verse = ""
        for ($v = $from; $v -le $to; $v++) {
            $j = Invoke-RestMethod "https://api.quran.com/api/v4/quran/verses/imlaei?verse_key=$($num):$v"
            $verse += " " + $j.verses[0].text_imlaei
        }
        $ok = (Norm $verse).Contains((Norm $a.text))
        $out.Add("$(if ($ok) {'OK'} else {'XX'}) $($a.id) [$($a.source)]")
        if (-not $ok) { $out.Add("   ours:   $($a.text)"); $out.Add("   mushaf: $($verse.Trim())") }
    } else {
        $src = ""
        foreach ($bm in [regex]::Matches($a.source, "البخاري\s*\(([\d، ]+)\)")) {
            foreach ($n in ($bm.Groups[1].Value -split "[، ]+" | Where-Object { $_ })) { $src += " " + $bukhari[[int]$n] }
        }
        foreach ($mm in [regex]::Matches($a.source, "مسلم\s*\((\d+)\)")) { $src += " " + $muslim[[int]$mm.Groups[1].Value] }
        # The WHOLE text must be in the source. Only an opening attribution may be ours:
        # "عن فلان (قال/قالت):" or "قال رسول الله ﷺ:" / "قال النبي ﷺ:". "…" marks a shortening.
        $t = $a.text
        if ($t.StartsWith("عن ")) { $t = $t.Substring($t.IndexOf(":") + 1) }
        foreach ($p0 in "قال رسول الله ﷺ:", "قال النبي ﷺ:") { if ($t.StartsWith($p0)) { $t = $t.Substring($p0.Length) } }
        $nsrc = Norm $src
        $bad = @()
        if (-not $src.Trim()) { $bad += "(no source text found for this reference)" }
        foreach ($part in ($t -split "\.\.\.|…")) {
            $p = Norm $part
            if ($p.Length -gt 0 -and -not $nsrc.Contains($p)) { $bad += $part.Trim() }
        }
        $out.Add("$(if (-not $bad) {'OK'} else {'XX'}) $($a.id) [$($a.source)]")
        foreach ($b in $bad) { $out.Add("   not found: $b") }
        if ($bad) { $out.Add("   source: " + ($src.Trim() -replace "\s+", " ")) }
    }
}
$out | ForEach-Object { $_ }
$failed = @($out | Where-Object { $_.StartsWith("XX") }).Count
"$($items.Count) items, $failed failed"
if ($failed -gt 0) { exit 1 }
