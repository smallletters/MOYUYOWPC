# Re-diagnose with UTF8 encoding
$root = "d:\Code\MOYUYO"
$mdFiles = Get-ChildItem "$root\*.md"
$byComment = 0
$byEllipsis = 0
$byStringDots = 0
$byOther = 0
$commentDetails = @()
$ellipsisDetails = @()
$otherDetails = @()

foreach ($f in $mdFiles) {
  $content = Get-Content $f.FullName -Raw -Encoding UTF8
  $blocks = [regex]::Matches($content, '```json\s*([\s\S]*?)```')
  $idx = 0
  foreach ($b in $blocks) {
    $idx++
    $jsonStr = $b.Groups[1].Value.Trim()
    
    $origFail = $false
    try { ConvertFrom-Json $jsonStr -ErrorAction Stop | Out-Null } catch { $origFail = $true }
    if (-not $origFail) { continue }

    # Try removing comments
    $noComment = $jsonStr -replace '//[^\r\n]*', ''
    $cPass = $false
    try { ConvertFrom-Json $noComment -ErrorAction Stop | Out-Null; $cPass = $true } catch {}
    if ($cPass) { $byComment++; $commentDetails += "$($f.Name)#$idx"; continue }

    # Try removing ... (unquoted) 
    $noEllipsis = $noComment -replace '(?<!")\.\.\.(?!")', ''
    $ePass = $false
    try { ConvertFrom-Json $noEllipsis -ErrorAction Stop | Out-Null; $ePass = $true } catch {}
    if ($ePass) { $byEllipsis++; $ellipsisDetails += "$($f.Name)#$idx"; continue }

    # Check if only issue is "xxx..." in strings
    $noStringDots = $noComment -replace '"[^"]*\.\.\.([^"]*)"', '"truncated"'
    $sPass = $false
    try { ConvertFrom-Json $noStringDots -ErrorAction Stop | Out-Null; $sPass = $true } catch {}
    if ($sPass) { $byStringDots++; $otherDetails += "$($f.Name)#$idx (string-dots)"; continue }

    $byOther++
    $otherDetails += "$($f.Name)#$idx (real-error)"
  }
}

Write-Output "=== JSON failure re-diagnosis (UTF8) ==="
Write-Output " By // comments only:       $byComment"
Write-Output " By unquoted ... only:      $byEllipsis"
Write-Output " By string 'xxx...' only:   $byStringDots"
Write-Output " Real errors:               $byOther"
Write-Output ""
if ($commentDetails.Count -gt 0) {
  Write-Output "-- by comments --"
  $commentDetails | ForEach-Object { Write-Output "  $_" }
}
if ($ellipsisDetails.Count -gt 0) {
  Write-Output "-- by ellipsis --"
  $ellipsisDetails | ForEach-Object { Write-Output "  $_" }
}
if ($otherDetails.Count -gt 0) {
  Write-Output "-- by string-dots / real errors --"
  $otherDetails | ForEach-Object { Write-Output "  $_" }
}
