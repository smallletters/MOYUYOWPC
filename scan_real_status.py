"""扫描 APP 页面是否真正接入真实数据。
识别三种接入方式：
1. 直接调用 api.X
2. 通过 store.X() 间接调用 API
3. 通过 Pinia / uni.$emit 等其他方式
"""
import os
import re

PAGES = r"D:\MOYUYOWPC\moyuyo-app\src\pages"

# 列出所有 "纯静态/独立模块/无后端接口" 的页面（即使接入 store 也没意义）
PURE_STATIC = {
    "common/empty-state.vue",   # 通用空状态
    "common/splash-popup.vue",   # 启动弹窗（通常本地配置）
    "debug/plugin-test.vue",     # 调试页面
    "user/splash.vue",           # 启动页（本地）
    "user/onboarding.vue",       # 引导页（本地）
    "user/scan.vue",             # 扫码（系统 API）
    "user/about.vue",            # 关于（本地）
    "user/avatar-settings.vue",  # 头像设置（系统 API）
    "user/change-password.vue",  # 修改密码（store 接口）
    "user/edit-profile.vue",     # 编辑资料（store 接口）
    "user/security.vue",         # 安全（本地）
    "user/privacy.vue",          # 隐私（store 接口）
    "user/settings.vue",          # 设置（本地）
    "user/help.vue",              # 帮助（旧版，本地）
    "user/help-center.vue",      # 帮助中心（本地）
    "user/login.vue",             # 登录（store.login）
    "user/register.vue",          # 注册（store.register）
    "user/forgot.vue",            # 忘记密码（store）
    "user/magic-link.vue",        # Magic Link（store）
    "user/two-factor.vue",        # 2FA（store）
    "user/user-profile-page.vue", # 他人主页（需拉真实数据，但接口未实现）
    "user/new-user-zone.vue",    # 新人专区（本地）
    "user/prime-page.vue",        # Prime 会员（本地）
    "user/achievement-wall.vue", # 成就墙（本地）
    "user/affiliate-center.vue", # 推广中心（本地）
    "user/annual-report.vue",    # 年报（本地）
    "user/block-manager.vue",     # 黑名单（本地）
    "user/booking-detail.vue",    # 预约详情（本地）
    "user/device-manager.vue",    # 设备管理（旧版，本地）
    "user/devices.vue",           # 设备管理（store 接口）
    "user/festival-event.vue",   # 节日活动（本地）
    "user/invoice-manage.vue",   # 发票管理（已用 api）
    "user/invite-friends.vue",   # 邀请好友（已用 invite.vue）
    "user/coupon-detail.vue",    # 优惠券详情（已用 api）
    "user/coupon-transfer.vue",  # 优惠券转赠（本地）
    "user/community-topic.vue",   # 话题（本地）
    "user/post-collection.vue",   # 帖子收藏（本地）
    "user/messages.vue",          # 消息（本地）
    "user/profile.vue",           # 我的资料（本地）
    "user/subscribe-manage-detail.vue",  # 订阅详情（本地）
    "user/subscribe-plan.vue",   # 订阅方案（已用 api）
    "user/subscription-manage.vue",  # 订阅管理（本地）
    "goods/ar-try-on.vue",        # AR 试穿（本地 3D）
    "goods/fit-finder.vue",      # 智能尺码（本地）
    "goods/try-before-buy.vue",   # 先试后买（本地）
    "goods/product-compare.vue",  # 商品对比（本地）
    "goods/share-product.vue",    # 商品分享（本地）
    "goods/shipping-calculator.vue", # 运费计算（本地）
    "goods/qa.vue",               # 商品问答（已用 communityApi 加评论；qa 部分本地）
    "goods/frequent-purchase.vue", # 常购清单（本地）
    "goods/crowdfunding-list.vue", # 众筹列表（本地）
    "goods/crowdfunding.vue",     # 众筹详情（本地）
    "goods/product-subscribe.vue", # 商品订阅（本地）
    "goods/product-subscribe-list.vue", # 订阅列表（本地）
    "goods/flash-sale-detail.vue", # 限时秒杀详情（本地）
    "pet/pet-hub-3d.vue",        # 3D 宠物中心（本地 3D）
    "pet/allergy-profile.vue",   # 过敏档案（本地）
    "community/chat-history.vue", # 私聊列表（本地）
    "community/dm-chat.vue",     # 私聊详情（本地）
    "community/post-create.vue", # 发布帖子（本地）
    "order/cs-rating.vue",       # 客服评价（本地）
    "order/payment-success.vue", # 支付成功（本地）
    "order/tariff-detail.vue",   # 关税详情（本地）
    "tabbar/pet.vue",            # Pet Hub（已用 store 接 petApi.loadPets）
    "cart/index.vue",            # 购物车（已用 store 接 cartApi）
}

# 真正需要补全真实接口接入的页面
NEED_REAL_API = {
    "tabbar/pet.vue": "已有 store 接 petApi，但页面部分功能硬编码（活动、徽章）",
    "community/chat-history.vue": "需要 communityApi 私信会话列表",
    "community/dm-chat.vue":     "需要 communityApi 消息收发",
    "community/post-create.vue": "需要 communityApi 发帖/上传",
    "pet/pet-hub-3d.vue":        "可接入 petApi",
    "pet/allergy-profile.vue":   "可接入 petApi",
    "order/cs-rating.vue":       "需要评价接口（reviewApi 可用）",
    "order/payment-success.vue": "需要支付结果查询（payApi）",
    "order/tariff-detail.vue":   "关税（orderApi 已含）",
    "goods/qa.vue":              "已用 communityApi；qa 详情可加 productApi",
    "goods/flash-sale-detail.vue": "需要 flashSaleApi 详情",
    "goods/crowdfunding-list.vue": "需要 crowdfundingApi 列表",
    "goods/crowdfunding.vue":    "需要 crowdfundingApi 详情",
    "goods/frequent-purchase.vue": "可加 orderApi 历史购买",
    "goods/product-subscribe.vue": "需要 subscribeApi",
    "goods/product-subscribe-list.vue": "需要 subscribeApi",
    "goods/product-compare.vue":  "需要 productApi 多 sku 详情",
    "goods/share-product.vue":   "需要 productApi + 分享",
    "goods/shipping-calculator.vue": "需要 shippingApi 估算",
    "goods/ar-try-on.vue":       "AR 功能纯本地",
    "goods/fit-finder.vue":      "尺码推荐纯本地",
    "goods/try-before-buy.vue":   "试用流程纯本地",
    "user/affiliate-center.vue": "可加 invitationApi",
    "user/annual-report.vue":    "可加 orderApi 聚合",
    "user/achievement-wall.vue": "可加 missionApi 已完成成就",
    "user/booking-detail.vue":   "可加 petApi 服务预约",
    "user/festival-event.vue":   "可加 cmsApi 活动",
    "user/post-collection.vue":  "可加 communityApi 收藏列表",
    "user/community-topic.vue":   "可加 communityApi 话题",
    "user/messages.vue":          "可加 notificationApi + communityApi",
    "user/invite-friends.vue":   "已用 invite.vue",
    "user/coupon-transfer.vue":  "可加 couponApi 转赠",
    "user/subscribe-manage-detail.vue": "可加 subscribeApi",
    "user/subscription-manage.vue": "可加 subscribeApi",
    "user/user-profile-page.vue": "可加 userApi 公共信息",
    "user/help.vue":              "旧版帮助",
    "user/block-manager.vue":     "可加 userApi 黑名单",
    "user/device-manager.vue":    "老版设备管理",
    "user/prime-page.vue":        "可加 memberApi Prime",
    "user/new-user-zone.vue":    # 新人专区",
    "user/scan.vue":              "扫一扫（uni.scanCode 系统 API）",
}

# 简单列出每个文件的接入情况
print("=" * 80)
print("APP 页面接入真实数据盘点")
print("=" * 80)
for root, _, files in os.walk(PAGES):
    for f in files:
        if not f.endswith(".vue"):
            continue
        path = os.path.join(root, f)
        rel = path.replace(PAGES + "\\", "")
        with open(path, "r", encoding="utf-8") as fh:
            content = fh.read()
        has_api = "@/api" in content
        has_store = ("useCartStore" in content or "useUserStore" in content
                      or "usePetStore" in content or "useProductStore" in content)
        if has_api or has_store:
            status = "✅"
            notes = ""
        else:
            status = "❌"
            notes = "  <-- 需要补"
        print(f"{status} {rel}")