# Extract specific failing JSON blocks
$root = "d:\Code\MOYUYO"
$mdFiles = Get-ChildItem "$root\*.md"
$targetIds = @(1, 38, 47)
$globalIdx = 0

foreach ($f in $mdFiles) {
  $content = Get-Content $f.FullName -Raw -Encoding UTF8
  $blocks = [regex]::Matches($content, '```json\s*([\s\S]*?)```')
  foreach ($b in $blocks) {
    $globalIdx++
    if ($targetIds -notcontains $globalIdx) { continue }
    $jsonStr = $b.Groups[1].Value.Trim()
    Write-Output "===== json#$globalIdx in $($f.Name) ====="
    Write-Output $jsonStr
    Write-Output "===== END ====="
    Write-Output ""
  }
}
