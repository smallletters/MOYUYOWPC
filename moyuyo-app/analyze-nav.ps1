# 提取 12 个有冲突页面的 nav-header 结构
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
  $content = Get-Content $f -Raw
  # 找 nav-header 范围
  $start = $content.IndexOf('<view class="nav-header"')
  if($start -lt 0) {
    Write-Host "NO NAV-HEADER: $f"
    continue
  }
  # 找结束 </view>，按层级匹配
  $depth = 0
  $i = $start
  $end = -1
  while($i -lt $content.Length) {
    $openIdx = $content.IndexOf('<view', $i)
    $closeIdx = $content.IndexOf('</view>', $i)
    if($closeIdx -lt 0) { break }
    if($openIdx -gt 0 -and $openIdx -lt $closeIdx) {
      $depth++
      $i = $openIdx + 1
    } else {
      $depth--
      if ($depth -eq 0) {
        $end = $closeIdx + 7
        break
      }
      $i = $closeIdx + 1
    }
  }
  Write-Host "==== $f ===="
  Write-Host "  nav-header: $start..$end"
  Write-Host "  $(($content.Substring($start, [Math]::Min(200, $end-$start))) -replace '\n', ' ')"
}