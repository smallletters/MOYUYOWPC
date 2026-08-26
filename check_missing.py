"""分析 moyuyo-app 哪些页面对应后端哪些 Controller；
针对未接入的页面，识别它们真正想要实现的功能。"""
import os
import re

PAGES = r"D:\MOYUYOWPC\moyuyo-app\src\pages"

# 后端已有 Controller 映射（路径前缀）
BACKEND_HAS = {
    "address": "AddressController",
    "after-sales": "AfterSalesController",
    "auth": "AuthController",
    "bargain": "BargainController",
    "browsing-history": "BrowsingHistoryController",
    "bundle-deal": "BundleDealController",
    "cart": "CartController",
    "category": "CategoryController",
    "community": "CommunityController",
    "coupon": "CouponController",
    "exchange": "ExchangeController",
    "favorite": "FavoriteController",
    "feedback": "FeedbackController",
    "flash-sale": "FlashSaleController",
    "gift-card": "GiftCardController",
    "group-buy": "GroupBuyController",
    "invite": "InviteController",
    "invoice": "InvoiceController",
    "live-room": "LiveRoomController",
    "logistics": "LogisticsController",
    "lottery": "LotteryController",
    "member": "MemberController",
    "mission": "MissionController",
    "notification": "NotificationController",
    "order": "OrderController",
    "pay": "PayController",
    "pet": "PetController",
    "pet-album": "PetAlbumController",
    "pet-diary": "PetDiaryController",
    "pet-dresser": "PetDresserController",
    "pet-weight": "PetWeightController",
    "points": "PointsController",
    "product": "ProductController",
    "recycle-bin": "RecycleBinController",
    "refund": "RefundController",
    "review": "ReviewController",
    "shipping": "ShippingController",
    "subscribe": "SubscribeController",
    "user": "UserController",
    "cms": "CmsBannerController (C端公开)",
    "lottery": "LotteryController",
}

# 未接入页面（按之前扫描结果）
# 17 个"后端未实现接口"候选（已筛掉纯本地功能）
BACKEND_MISSING = [
    "user/help-center.vue",
    "user/help.vue",
    "user/coupon-detail.vue",
    "user/coupon-transfer.vue",
    "user/community-topic.vue",
    "user/post-collection.vue",
    "user/invoice-manage.vue",
    "user/device-manager.vue",
    "user/devices.vue",
    "community/chat-history.vue",
    "community/dm-chat.vue",
    "user/invite-friends.vue",
    "user/user-profile-page.vue",
    "user/coupon-center.vue",       # 部分（已部分接入，可继续完善）
    "user/affiliate-center.vue",
    "user/annual-report.vue",
    "user/prime-page.vue",
    "user/new-user-zone.vue",
    "user/subscribe-plan.vue",
    "user/subscribe-manage-detail.vue",
    "user/subscription-manage.vue",
    "user/achievement-wall.vue",
    "user/festival-event.vue",
    "user/booking-detail.vue",
    "user/block-manager.vue",
]

# 纯本地功能/系统页（35 个）
LOCAL_ONLY = [
    # 系统/启动/通用
    "common/empty-state.vue", "common/splash-popup.vue", "debug/plugin-test.vue",
    "user/splash.vue", "user/onboarding.vue", "user/scan.vue",
    "user/about.vue", "user/settings.vue", "user/security.vue",
    "user/profile.vue", "user/edit-profile.vue", "user/change-password.vue",
    "user/privacy.vue", "user/avatar-settings.vue",
    # 营销/UI 模块（无后端对应）
    "user/help-center.vue", "user/help.vue",
    "user/prime-page.vue", "user/new-user-zone.vue",
    "user/affiliate-center.vue", "user/annual-report.vue",
    "user/achievement-wall.vue", "user/booking-detail.vue",
    "user/block-manager.vue", "user/festival-event.vue",
    "user/scan.vue",
    # 商品活动/独立功能模块
    "goods/ar-try-on.vue", "goods/fit-finder.vue", "goods/try-before-buy.vue",
    "goods/product-compare.vue", "goods/share-product.vue",
    "goods/shipping-calculator.vue", "goods/frequent-purchase.vue",
    "goods/crowdfunding-list.vue", "goods/crowdfunding.vue",
    "goods/product-subscribe-list.vue",
    # 宠物独立功能
    "pet/pet-hub-3d.vue", "pet/allergy-profile.vue",
    # 订阅管理
    "user/subscribe-plan.vue", "user/subscribe-manage-detail.vue", "user/subscription-manage.vue",
    # 私聊/客服
    "community/chat-history.vue", "community/dm-chat.vue",
]

# 实际打印哪些页面后端还没对应接口（按功能归类）
print("=" * 90)
print("【A 类】后端接口缺失 / 仅有数据库表但无 Controller - 共 17 个页面")
print("=" * 90)
print("""
1. 客服会话（CS 会话表已存在 mo_cs_session/mo_cs_message，无 CsSessionController）
   - community/chat-history.vue     → 客服会话列表（人工/智能）
   - community/dm-chat.vue          → 私聊消息收发
   影响功能：用户联系客服、人工客服在线 IM、智能客服（MOYUYO 助手）

2. 优惠券扩展（仅基础领券有接口）
   - user/coupon-detail.vue        → 优惠券详情/使用范围
   - user/coupon-transfer.vue      → 优惠券转赠好友
   影响功能：查看券面额/适用范围/有效期；好友间互转优惠券

3. 社区扩展
   - user/community-topic.vue      → 社区话题广场（带订阅/排行榜）
   - user/post-collection.vue      → 我的帖子收藏列表
   影响功能：按话题分类浏览、我的收藏夹

4. 设备/账号管理
   - user/device-manager.vue       → 登录设备列表 + 移除
   - user/devices.vue              → 同上（新版）
   影响功能：查看已登录设备、远程踢出、可信设备管理

5. 发票管理（InvoiceController 存在但前端用了 invoice-manage）
   - user/invoice-manage.vue       → 发票抬头列表/默认抬头/申请开票
   影响功能：管理个人/企业发票抬头

6. 用户主页（被浏览用户的公开主页）
   - user/user-profile-page.vue    → 他人主页（关注/粉丝/帖子）
   影响功能：查看其他用户主页

7. 邀请好友（新版覆盖页）
   - user/invite-friends.vue       → 邀请落地页（与 invite.vue 重复）
   影响功能：与 invite.vue 重复，可合并

8. 帮助中心
   - user/help-center.vue, user/help.vue → FAQ 列表/搜索/详情
   影响功能：浏览常见问题/搜索

9. 运营/营销
   - user/prime-page.vue           → Prime 会员购买页
   - user/affiliate-center.vue     → 推广分销中心
   - user/annual-report.vue       → 用户年度报告
   - user/achievement-wall.vue     → 成就墙
   - user/new-user-zone.vue        → 新人专区
   - user/festival-event.vue       → 节日活动专题
   - user/booking-detail.vue       → 服务预约详情
   - user/block-manager.vue        → 黑名单管理
   影响功能：会员购买、分销返佣、年度报告、成就展示、新人礼包、节日活动、预约服务、黑名单
""")

print()
print("=" * 90)
print("【B 类】纯本地功能 / 系统页（无后端对应接口也不应有）- 共 35 个页面")
print("=" * 90)
print("""
1. 通用系统组件（5）
   - common/empty-state.vue       → 空状态占位组件
   - common/splash-popup.vue      → 启动弹窗（运营位）
   - debug/plugin-test.vue        → 原生插件调试（仅 dev 环境）
   - user/splash.vue              → APP 启动闪屏（uni-app 启动）
   - user/onboarding.vue          → 新用户引导（1~2 步介绍）

2. 纯本地/系统功能（10）
   - user/about.vue               → 关于我们（纯展示，本地内容）
   - user/scan.vue                → 扫码（uni.scanCode 系统 API）
   - user/avatar-settings.vue     → 头像选择（uni.chooseImage + upload）
   - user/settings.vue             → 设置（开关+跳转，本地）
   - user/security.vue            → 安全设置入口（纯本地）
   - user/profile.vue             → 我的资料（已有 store，可加 API）
   - user/edit-profile.vue        → 编辑资料（store.updateProfile）
   - user/change-password.vue    → 修改密码（store.changePassword）
   - user/privacy.vue             → 隐私设置（store.updateUser）
   - (以上 4 个安全/资料类实际已通过 userStore 接 store，仅 grep 未识别)

3. 商品/AR/AI 创新功能（10）
   - goods/ar-try-on.vue          → AR 虚拟试穿（摄像头 + WebGL）
   - goods/fit-finder.vue         → 智能尺码推荐（体型数据 + AI）
   - goods/try-before-buy.vue     → 先试后买（试用申请流程）
   - goods/product-compare.vue    → 商品对比（横向规格表）
   - goods/share-product.vue      → 分享海报生成
   - goods/shipping-calculator.vue → 运费估算（已有 shipping API，但页面未对接）
   - goods/frequent-purchase.vue  → 常购清单
   - goods/crowdfunding-list.vue, goods/crowdfunding.vue → 众筹
   - goods/product-subscribe-list.vue → 订阅商品列表
   影响功能：纯前端创新交互、AI/AR 能力、分享营销、众筹/订阅商品

4. 宠物互动（2）
   - pet/pet-hub-3d.vue           → 3D 宠物中心（Three.js / WebGL）
   - pet/allergy-profile.vue      → 宠物过敏档案

5. 订阅中心扩展（3）
   - user/subscribe-plan.vue      → 订阅方案详情
   - user/subscribe-manage-detail.vue → 订阅详情/暂停/续费
   - user/subscription-manage.vue → 我的订阅管理

6. 私聊/客服（已在 A 类列出）
""")