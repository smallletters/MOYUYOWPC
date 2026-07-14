# MOYUYO Document Executability Validation Script
# Usage: powershell -ExecutionPolicy Bypass -File validate.ps1

$root = "d:\Code\MOYUYO"
$pass = 0
$fail = 0

Write-Output "========================================"
Write-Output " MOYUYO Document Executability Validation"
Write-Output "========================================"

# ====== 1. JSON syntax validation ======
Write-Output ""
Write-Output "[1/6] JSON syntax validation"
$mdFiles = Get-ChildItem "$root\*.md"
$jsonTotal = 0
$jsonFail = 0
foreach ($f in $mdFiles) {
  $content = Get-Content $f.FullName -Raw -Encoding UTF8
  $blocks = [regex]::Matches($content, '```json\s*([\s\S]*?)```')
  $idx = 0
  foreach ($b in $blocks) {
    $idx++
    $jsonStr = $b.Groups[1].Value.Trim()
    # 移除 // 行注释（不匹配 URL 中的 //）
    $cleaned = $jsonStr -replace '(?<!:)//[^\r\n]*', ''
    # 替换文档占位符 ... 为合法 JSON 值
    $cleaned = $cleaned -replace '\{[ \t\r\n]*\.\.\.[ \t\r\n]*\}', '{}'
    $cleaned = $cleaned -replace '\[[ \t\r\n]*\.\.\.[ \t\r\n]*\]', '[]'
    $cleaned = $cleaned -replace ':\s*\.\.\.', ': null'
    try {
      ConvertFrom-Json $cleaned -ErrorAction Stop | Out-Null
      $jsonTotal++
    } catch {
      $jsonTotal++
      $jsonFail++
      $emsg = $_.Exception.Message
      $short = $emsg.Substring(0, [Math]::Min(50, $emsg.Length))
      Write-Output "  FAIL  $($f.Name)  json#$idx  $short"
    }
  }
}
if ($jsonFail -eq 0) {
  Write-Output "  OK    $jsonTotal JSON blocks validated, 0 errors"
  $pass++
} else {
  Write-Output "  RESULT  $jsonFail / $jsonTotal JSON blocks failed"
  $fail++
}

# ====== 2. SQL bracket / semicolon check ======
Write-Output ""
Write-Output "[2/6] SQL syntax basic check"
$sqlFiles = Get-ChildItem "$root\db-migration\*.sql"
$sqlOk = 0
$sqlBad = 0
foreach ($f in $sqlFiles) {
  $c = Get-Content $f.FullName -Raw -Encoding UTF8
  $open = ($c.ToCharArray() | Where-Object { $_ -eq '(' }).Count
  $close = ($c.ToCharArray() | Where-Object { $_ -eq ')' }).Count
  $semi = ($c.ToCharArray() | Where-Object { $_ -eq ';' }).Count
  if ($open -ne $close) {
    Write-Output "  FAIL  $($f.Name)  paren mismatch open=$open close=$close"
    $sqlBad++
  } else {
    Write-Output "  OK    $($f.Name)  paren=$open semicolons=$semi"
    $sqlOk++
  }
}
if ($sqlBad -eq 0) { $pass++ } else { $fail++ }

# ====== 3. HTTP endpoint format validation ======
Write-Output ""
Write-Output "[3/6] HTTP endpoint format validation"
$endpoints = @()
foreach ($f in $mdFiles) {
  $c = Get-Content $f.FullName -Raw -Encoding UTF8
  $matches = [regex]::Matches($c, '(?m)^(GET|POST|PUT|DELETE|PATCH)\s+/api/v1/\S+')
  foreach ($m in $matches) {
    $endpoints += $m.Value
  }
}
$malformed = @()
foreach ($ep in $endpoints) {
  if ($ep -notmatch '^(GET|POST|PUT|DELETE|PATCH)\s+/api/v1/[a-z][a-z0-9-]+') {
    $malformed += $ep
  }
}
Write-Output "  total=$($endpoints.Count) malformed=$($malformed.Count)"
if ($malformed.Count -gt 0) {
  $malformed | ForEach-Object { Write-Output "    MALFORMED: $_" }
  $fail++
} else {
  Write-Output "  OK    $($endpoints.Count) endpoints, 0 malformed"
  $pass++
}

# ====== 4. Placeholder scan ======
Write-Output ""
Write-Output "[4/6] Placeholder / TODO scan"
$phTotal = 0
foreach ($f in $mdFiles) {
  $c = Get-Content $f.FullName -Raw -Encoding UTF8
  $ph = [regex]::Matches($c, '(\{\{[a-zA-Z_]+\}\}|<your_[a-z_]+>|your_[a-z_]+_here|REPLACE_ME|xxx_here)')
  if ($ph.Count -gt 0) {
    Write-Output "  WARN  $($f.Name)  $($ph.Count) placeholders"
    $ph | ForEach-Object { Write-Output "    -> $($_.Value)" }
    $phTotal += $ph.Count
  }
}
if ($phTotal -eq 0) {
  Write-Output "  OK    0 unreplaced placeholders"
  $pass++
} else {
  Write-Output "  RESULT  $phTotal placeholders found"
  $fail++
}

# ====== 5. Markdown internal link existence ======
Write-Output ""
Write-Output "[5/6] Markdown internal link existence"
$linkTotal = 0
$linkBad = 0
foreach ($f in $mdFiles) {
  $c = Get-Content $f.FullName -Raw -Encoding UTF8
  $links = [regex]::Matches($c, '\[([^\]]+)\]\((file:///[^)]+)\)')
  foreach ($l in $links) {
    $linkTotal++
    $url = $l.Groups[2].Value
    $path = $url -replace '^file:///', '' -replace '#L\d+(-\d+)?$', ''
    $path = $path -replace '/', '\'
    $path = $path -replace 'd:\\Code\\Code\\', 'd:\Code\'
    if (-not (Test-Path $path)) {
      Write-Output "  FAIL  $($f.Name) -> $path"
      $linkBad++
    }
  }
}
if ($linkBad -eq 0) {
  Write-Output "  OK    $linkTotal links checked, 0 broken"
  $pass++
} else {
  Write-Output "  RESULT  $linkBad / $linkTotal broken links"
  $fail++
}

# ====== 6. Code block closure check ======
Write-Output ""
Write-Output "[6/6] Code block closure check"
$cbBad = 0
foreach ($f in $mdFiles) {
  $c = Get-Content $f.FullName -Raw -Encoding UTF8
  $fences = ([regex]::Matches($c, '```')).Count
  if ($fences % 2 -ne 0) {
    Write-Output "  FAIL  $($f.Name)  $fences fences - odd, unclosed"
    $cbBad++
  }
}
if ($cbBad -eq 0) {
  Write-Output "  OK    all code blocks properly closed"
  $pass++
} else {
  $fail++
}

# ====== Summary ======
Write-Output ""
Write-Output "========================================"
Write-Output " Summary"
Write-Output "========================================"
Write-Output " JSON blocks:   $jsonTotal validated, $jsonFail failed"
Write-Output " SQL files:     $sqlOk OK, $sqlBad failed"
Write-Output " HTTP endpoints: $($endpoints.Count) total, $($malformed.Count) malformed"
Write-Output " Placeholders:  $phTotal found"
Write-Output " Links:         $linkTotal checked, $linkBad broken"
Write-Output " Code blocks:   $cbBad unclosed"
Write-Output ""
Write-Output " Dimensions passed: $pass / 6"
Write-Output "========================================"
