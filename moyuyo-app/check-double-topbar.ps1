# 检查每个有自定义 topbar 的页面是否同时在 pages.json 配置了 navigationBarTitleText
$topbarFiles = @(
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\register.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\tabbar\user.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\order\pay.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\about.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\annual-report.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\coupon-center.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\customer-service.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\common\splash-popup.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\bundle-deal.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\charity-donation.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\crowdfunding-list.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\detail.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\flash-sale-detail.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\flash-sale.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\live-room.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\product-subscribe-list.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\qa.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\share-product.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\try-before-buy.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\order\after-sales-policy.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\gift-card-manage.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\gift-cards.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\invite-friends.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\invite.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\new-user-zone.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\settings.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\subscribe-plan.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\subscription-manage.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\terms-document.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\prime-page.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\onboarding.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\membership.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\membership-rule.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\user\splash.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\crowdfunding.vue',
  'd:\MOYUYOWPC\moyuyo-app\src\pages\goods\bargain.vue'
)
$pagesJson = Get-Content 'd:\MOYUYOWPC\moyuyo-app\src\pages.json' -Raw
foreach($f in $topbarFiles) {
  if(Test-Path $f) {
    $rel = $f -replace 'd:\\MOYUYOWPC\\moyuyo-app\\src\\', ''
    $pagePath = ($rel -replace '\.vue$', '') -replace '\\', '/'
    # 找该 page path 在 pages.json 中是否配置了 navigationBarTitleText
    $escapedPath = [regex]::Escape($pagePath)
    $pattern = '"path"\s*:\s*"' + $escapedPath + '"[\s\S]*?"navigationBarTitleText"\s*:\s*"([^"]+)"'
    $match = [regex]::Match($pagesJson, $pattern)
    $title = ''
    if($match.Success) { $title = $match.Groups[1].Value }
    # 自定义 topbar 中的 brand text
    $content = Get-Content $f -Raw
    $brandMatch = [regex]::Match($content, 'topbar-brand[^>]*>([^<]+)<')
    $brand = ''
    if($brandMatch.Success) { $brand = $brandMatch.Groups[1].Value.Trim() }
    $hasNativeTitle = $title -ne ''
    $hasCustomTopbar = ($content -match 'class="topbar"')
    $conflict = $hasNativeTitle -and $hasCustomTopbar
    Write-Host ("{0,-55} | native='{1}' | brand='{2}' | conflict={3}" -f $rel, $title, $brand, $conflict)
  }
}