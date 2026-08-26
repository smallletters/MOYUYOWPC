"""按后端接口存在性，分页哪些 APP 页面应该接入真实数据。
基于 moyuyo-server 后端 controller 与 moyuyo-app 前端页面名映射。"""
import os

# 后端已实现接口的领域 (路由前缀)
BACKEND_DOMAINS = {
    "address": "/api/v1/address",
    "afterSales": "/api/v1/refunds",
    "auth": "/api/v1/auth",
    "bargain": "/api/v1/bargains",
    "browsingHistory": "/api/v1/browsing-history",
    "bundleDeal": "/api/v1/bundle-deals",
    "cart": "/api/v1/cart",
    "category": "/api/v1/categories",
    "community": "/api/v1/community",
    "coupon": "/api/v1/coupons",
    "favorite": "/api/v1/favorites",
    "feedback": "/api/v1/feedback",
    "flashSale": "/api/v1/flash-sales",
    "giftCard": "/api/v1/gift-cards",
    "groupBuy": "/api/v1/group-buys",
    "invite": "/api/v1/invites",
    "invoice": "/api/v1/invoices",
    "liveRoom": "/api/v1/live-rooms",
    "lottery": "/api/v1/lotteries",
    "member": "/api/v1/member",
    "mission": "/api/v1/missions",
    "notification": "/api/v1/notifications",
    "order": "/api/v1/orders",
    "pay": "/api/v1/payments",
    "pet": "/api/v1/pets",
    "petAlbum": "/api/v1/pets/{petId}/album",
    "petDiary": "/api/v1/pets/{petId}/diary",
    "petDresser": "/api/v1/pets/{petId}/dresser",
    "petWeight": "/api/v1/pets/{petId}/weights",
    "points": "/api/v1/points",
    "product": "/api/v1/products",
    "recycleBin": "/api/v1/recycle-bin",
    "review": "/api/v1/reviews",
    "shipping": "/api/v1/shipping",
    "subscribe": "/api/v1/subscribe",
    "user": "/api/v1/users",
    "cms": "/api/v1/cms",
}

# APP 没接入 API 的页面
no_api = [
    "cart\\index.vue",
    "community\\chat-history.vue",
    "community\\dm-chat.vue",
    "community\\post-create.vue",
    "goods\\ar-try-on.vue",
    "goods\\crowdfunding-list.vue",
    "goods\\crowdfunding.vue",
    "goods\\fit-finder.vue",
    "goods\\flash-sale-detail.vue",
    "goods\\frequent-purchase.vue",
    "goods\\product-compare.vue",
    "goods\\product-subscribe-list.vue",
    "goods\\product-subscribe.vue",
    "goods\\qa.vue",
    "goods\\share-product.vue",
    "goods\\shipping-calculator.vue",
    "goods\\try-before-buy.vue",
    "order\\cs-rating.vue",
    "order\\payment-success.vue",
    "order\\tariff-detail.vue",
    "pet\\allergy-profile.vue",
    "pet\\pet-hub-3d.vue",
    "tabbar\\pet.vue",
    "user\\about.vue",
    "user\\achievement-wall.vue",
    "user\\affiliate-center.vue",
    "user\\annual-report.vue",
    "user\\avatar-settings.vue",
    "user\\block-manager.vue",
    "user\\booking-detail.vue",
    "user\\change-password.vue",
    "user\\community-topic.vue",
    "user\\coupon-detail.vue",
    "user\\coupon-transfer.vue",
    "user\\device-manager.vue",
    "user\\devices.vue",
    "user\\festival-event.vue",
    "user\\forgot.vue",
    "user\\help-center.vue",
    "user\\help.vue",
    "user\\invite-friends.vue",
    "user\\invoice-manage.vue",
    "user\\login.vue",
    "user\\magic-link.vue",
    "user\\messages.vue",
    "user\\new-user-zone.vue",
    "user\\onboarding.vue",
    "user\\post-collection.vue",
    "user\\prime-page.vue",
    "user\\profile.vue",
    "user\\register.vue",
    "user\\scan.vue",
    "user\\security.vue",
    "user\\settings.vue",
    "user\\splash.vue",
    "user\\subscribe-manage-detail.vue",
    "user\\subscribe-plan.vue",
    "user\\subscription-manage.vue",
    "user\\two-factor.vue",
    "user\\user-profile-page.vue",
    "common\\empty-state.vue",
    "common\\splash-popup.vue",
    "debug\\plugin-test.vue",
    "user\\edit-profile.vue",
    "user\\customer-service.vue",
]

# 按文件名关键词分类
import re
CATEGORIZE = [
    ("login", ["login", "register", "forgot", "magic-link", "two-factor"]),
    ("auth/me", ["profile", "edit-profile", "settings", "security", "privacy", "change-password", "about", "splash", "onboarding", "scan"]),
    ("address", ["address"]),
    ("community", ["community-topic", "chat-history", "dm-chat", "post-create", "post-collection"]),
    ("pet", ["pet-hub-3d", "allergy-profile", "tabbar/pet"]),
    ("product-feature", ["ar-try-on", "fit-finder", "try-before-buy", "product-compare", "share-product", "shipping-calculator", "qa", "frequent-purchase", "crowdfunding", "product-subscribe"]),
    ("marketing", ["coupon-detail", "coupon-transfer", "festival-event", "help", "help-center", "block-manager", "booking-detail", "device-manager", "devices"]),
    ("me-static", ["achievement-wall", "affiliate-center", "annual-report", "avatar-settings", "new-user-zone", "prime-page", "user-profile-page", "payment-success", "tariff-detail", "cs-rating", "empty-state", "splash-popup", "plugin-test", "invite-friends", "invoice-manage", "subscribe-manage-detail", "subscribe-plan", "subscription-manage"]),
    ("cart", ["cart/index", "cart/checkout"]),
    ("goods-flash-sale", ["flash-sale-detail"]),
]

print("=" * 80)
print(f"未接入 API 的页面总数: {len(no_api)}")
print("=" * 80)

for cat_name, patterns in CATEGORIZE:
    matched = []
    for p in no_api:
        for pat in patterns:
            if pat in p:
                matched.append(p)
                break
    if matched:
        print(f"\n【{cat_name}】（{len(matched)} 个）")
        for m in matched:
            print(f"  - {m}")