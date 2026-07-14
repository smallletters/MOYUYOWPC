# Extract failing JSON blocks - iterate all md files
$root = "d:\Code\MOYUYO"
$targetIds = @(9, 10, 12, 14, 15, 31, 35, 36, 37, 38, 39, 42, 52, 60, 63)
$mdFiles = Get-ChildItem "$root\*.md"
$globalIdx = 0

foreach ($f in $mdFiles) {
  $content = Get-Content $f.FullName -Raw
  $blocks = [regex]::Matches($content, '```json\s*([\s\S]*?)```')
  foreach ($b in $blocks) {
    $globalIdx++
    if ($targetIds -notcontains $globalIdx) { continue }
    $jsonStr = $b.Groups[1].Value.Trim()
    Write-Output "===== json#$globalIdx in $($f.Name) ====="
    $lines = $jsonStr -split "`n"
    $shown = 0
    foreach ($line in $lines) {
      Write-Output "  $line"
      $shown++
      if ($shown -ge 10) {
        Write-Output "  ...(truncated)"
        break
      }
    }
    Write-Output ""
  }
}
