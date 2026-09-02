# 批量清理12个文件的双标题栏问题
# 每个文件做 3 件事:
# 1) 删模板中 <view class="nav-header">...</view> 整块
# 2) 删 goBack() 方法 (如果存在且未被其他代码调用)
# 3) 删 .nav-header / .nav-back / .back-icon / .nav-title / .nav-placeholder 整段 CSS

$files = @(
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\bargain.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\bundle-deal.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\charity-donation.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\crowdfunding.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\fit-finder.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\flash-sale.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\frequent-purchase.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\group-buy.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\product-compare.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\shipping-calculator.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\try-before-buy.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\invoice.vue'
)

foreach($f in $files) {
  if(-not (Test-Path $f)) {
    Write-Host "SKIP(not found): $f"
    continue
  }
  $content = Get-Content $f -Raw
  $before = $content

  # 1) 删 nav-header 块 (跨多行)
  $content = [regex]::Replace($content,
    '\s*<view\s+class="nav-header">[\s\S]*?</view>\s*(?=<!--|\r?\n\s*<view|\r?\n\s*<scroll-view)','',
    [System.Text.RegularExpressions.RegexOptions]::Singleline)

  # 2) 删 goBack() 方法 - 用空白边界
  $content = [regex]::Replace($content,
    '\s*goBack\(\)\s*\{\s*uni\.navigateBack\(\)\s*\},\s*','',
    [System.Text.RegularExpressions.RegexOptions]::Singleline)

  # 3) 删 nav-* + back-icon 整段 CSS (从 .nav-header { 到对应闭合 }，逐个匹配)
  $cssClasses = @('\.nav-header', '\.nav-back', '\.back-icon', '\.nav-title', '\.nav-placeholder')
  foreach($cls in $cssClasses) {
    # 匹配 ".\n.cls { ... }" 整段,非贪婪
    $pattern = '\s*\n\s*' + $cls + '\s*\{[^}]*\}\s*\n'
    $content = [regex]::Replace($content, $pattern, "`n", [System.Text.RegularExpressions.RegexOptions]::Singleline)
  }

  if($content -ne $before) {
    Set-Content -Path $f -Value $content -Encoding UTF8 -NoNewline
    Write-Host "FIXED: $($f -replace 'd:\\MOYUYOWPC\\moyuyo-app\\src\\', '')"
  } else {
    Write-Host "NO CHANGE: $($f -replace 'd:\\MOYUYOWPC\\moyuyo-app\\src\\', '')"
  }
}