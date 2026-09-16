-- 帮助中心 FAQ 种子数据
-- V20260915_01
-- 仅在表为空时插入,避免与运营手动添加的冲突
-- 内容围绕当前 APP 功能模块编写(账号/订单/支付/Pet Hub/社区/会员)

-- ============================================================
-- 分类(mo_help_category)
-- ============================================================
INSERT IGNORE INTO mo_help_category (id, name, icon, sort_order, active, create_time)
VALUES
    (1, 'Account & Sign-in',  'user',            10, 1, NOW()),
    (2, 'Orders & Shipping',  'shopping-bag',    20, 1, NOW()),
    (3, 'Payment & Coupons',  'credit-card',     30, 1, NOW()),
    (4, 'Pet Hub',            'paw-print',       40, 1, NOW()),
    (5, 'Community',          'message-square',  50, 1, NOW()),
    (6, 'MOYUYO+ Membership', 'star',            60, 1, NOW());

-- ============================================================
-- 文章(mo_help_article)
-- ============================================================
INSERT IGNORE INTO mo_help_article (id, category_id, title, content, tags, view_count, helpful_count, status, sort_order, create_time)
VALUES
-- ---------- 1. Account & Sign-in ----------
(101, 1, 'How do I create a MOYUYO account?',
 'Tap the "Sign in" button on the home page or user center, then choose Email or Phone Number. Enter your email/phone, set a password (at least 8 characters including letters and numbers), and confirm. Your account is ready immediately — no email verification is required for sign-up.',
 'register,signup,email,phone,password', 0, 0, 1, 10, NOW()),

(102, 1, 'I forgot my password. How can I reset it?',
 'On the sign-in page tap "Forgot password". Enter the email associated with your account and we''ll send a 6-digit verification code. Use the code to set a new password. The code expires in 10 minutes. If you don''t receive it, check your spam folder or tap "Resend".',
 'password,reset,forgot,verify', 0, 0, 1, 20, NOW()),

(103, 1, 'What is Two-Factor Authentication and do I need it?',
 'Two-Factor Authentication (2FA) adds an extra 6-digit code step on top of your password when signing in from a new device. We strongly recommend enabling it under Settings > Account & Security. It protects your account if your password is leaked. You can disable 2FA any time.',
 '2fa,two-factor,security,login', 0, 0, 1, 30, NOW()),

(104, 1, 'How do I manage my login devices?',
 'Go to Settings > Account & Security > Devices. You''ll see every device currently signed in to your account. To remove an old device, tap "Sign out" next to it. You can be signed in on up to 3 devices at the same time.',
 'devices,sessions,security,sign-out', 0, 0, 1, 40, NOW()),

(105, 1, 'How do I change my email or phone number?',
 'Go to Settings > Edit Profile. Tap your email or phone field, enter the new value, and confirm with the verification code we send. Note: changing your email/phone does NOT transfer your points, coupons, or orders — they stay with the original account.',
 'email,phone,change,profile', 0, 0, 1, 50, NOW()),

-- ---------- 2. Orders & Shipping ----------
(201, 2, 'How do I place an order?',
 'Add items to your cart, tap the cart icon, then tap "Checkout". Choose your shipping address, payment method, and any coupons. Review the order summary and tap "Place Order". You''ll receive an order confirmation notification immediately.',
 'order,checkout,place-order,cart', 0, 0, 1, 10, NOW()),

(202, 2, 'How long does shipping take?',
 'Standard shipping takes 3–5 business days within the continental US. Express shipping arrives within 1–2 business days. You''ll receive a tracking number by email and under Orders > Order Details once the order ships.',
 'shipping,delivery,eta,tracking', 0, 0, 1, 20, NOW()),

(203, 2, 'How do I track my order?',
 'Tap "Orders" on the user center, find your order, and tap "View Logistics". You''ll see the carrier, tracking number, and current shipping status. We update the tracking page automatically as your package moves.',
 'track,logistics,order-status', 0, 0, 1, 30, NOW()),

(204, 2, 'Can I cancel or change an order after placing it?',
 'You can cancel an order for free within 30 minutes of placing it, as long as it hasn''t been shipped yet. Go to Orders > Order Details > Cancel Order. After 30 minutes or once the order ships, you''ll need to use our returns process instead.',
 'cancel,change-order,modify', 0, 0, 1, 40, NOW()),

(205, 2, 'What is your return policy?',
 'Most items can be returned within 30 days of delivery for a full refund. Items must be unused and in original packaging. Pet food and personalized items are non-returnable. To start a return, go to Orders > Order Details > Apply for Refund.',
 'return,refund,policy', 0, 0, 1, 50, NOW()),

-- ---------- 3. Payment & Coupons ----------
(301, 3, 'What payment methods do you accept?',
 'We accept Visa, Mastercard, American Express, Discover, Apple Pay, Google Pay, and PayPal. Payment methods may vary by region. You can save multiple payment methods under Settings > Wallet > Payment Methods.',
 'payment,visa,mastercard,apple-pay,paypal', 0, 0, 1, 10, NOW()),

(302, 3, 'How do I use a coupon at checkout?',
 'On the checkout page, tap "Coupon" and select one from your available list. The discount is applied to the order total automatically. Coupons cannot be combined unless explicitly stated. Expired or used coupons won''t appear in the selection.',
 'coupon,discount,checkout', 0, 0, 1, 20, NOW()),

(303, 3, 'How does the points system work?',
 'You earn points on every purchase (typically 1 point per $1 spent, more at higher membership tiers). You can also earn points by signing in daily, writing reviews, completing missions, and inviting friends. Points can be redeemed for coupons or used as partial payment (100 points = $1, up to 30% of an order).',
 'points,rewards,loyalty', 0, 0, 1, 30, NOW()),

(304, 3, 'How do I apply for a refund?',
 'Go to Orders > Order Details > Apply for Refund, choose a reason, and submit. We review refund requests within 1–3 business days. Once approved, the refund is sent back to your original payment method and usually arrives within 5–10 business days.',
 'refund,return-money', 0, 0, 1, 40, NOW()),

(305, 3, 'Can I get an invoice for my order?',
 'Yes. Go to Order Details > Apply for Invoice, fill in your company name, tax ID, and email, and we''ll send a PDF invoice within 3 business days. Personal invoices are issued under your account name.',
 'invoice,receipt,tax', 0, 0, 1, 50, NOW()),

-- ---------- 4. Pet Hub ----------
(401, 4, 'What is Pet Hub?',
 'Pet Hub is your personalized pet space inside MOYUYO. You can dress up your pet avatar (Labrador, Shepherd, Bulldog, Maine Coon, or your own photo / 3D model), pick a scene background, and view your pet''s health records, care reminders, and weight chart all in one place.',
 'pet-hub,overview,features', 0, 0, 1, 10, NOW()),

(402, 4, 'How do I add a pet profile?',
 'Go to Pet Hub and tap the "+" button. Enter your pet''s name, species, breed, gender, birthday, and (optional) weight. You can add multiple pets and switch between them from the avatar carousel at the top of Pet Hub.',
 'add-pet,profile,create', 0, 0, 1, 20, NOW()),

(403, 4, 'How do care reminders work?',
 'After you add a pet, Pet Hub suggests four default care types: Bath, Vaccine, Deworm, and Check-up. Set a cycle (e.g. every 30 days for Bath) and Pet Hub will remind you when each is due. You can also log a care record directly without setting a reminder.',
 'care,reminder,bath,vaccine,deworm', 0, 0, 1, 30, NOW()),

(404, 4, 'Can I upload my own 3D model as my pet avatar?',
 'Yes. In the Dress Up popup, tap "Upload 3D" and select a GLB or FBX file (max 30 MB). The model is saved locally and used in the scene preview. Custom 3D models are visible only on your device.',
 '3d,model,glb,fbx,upload,avatar', 0, 0, 1, 40, NOW()),

(405, 4, 'How is my pet''s weight tracked?',
 'Go to Pet Hub > Weight Chart and log each weighing with date and weight (kg/lb). Pet Hub draws a trend line so you can see growth or weight-loss progress. You can edit or delete any entry by tapping it.',
 'weight,chart,track,growth', 0, 0, 1, 50, NOW()),

-- ---------- 5. Community ----------
(501, 5, 'How do I create a post?',
 'Tap the "+" button on the Community tab. Add photos (up to 9) or a video (up to 60 seconds), write a caption, optionally tag a topic, and choose visibility (public / friends / only me). Tap "Publish" to share.',
 'post,create,publish', 0, 0, 1, 10, NOW()),

(502, 5, 'Why was my post flagged as sensitive?',
 'Our moderation system blocks posts containing restricted keywords, personal contact info, or links to external sites. When triggered, the system highlights the sensitive words so you can edit and resubmit. Posts about pet safety, recalls, or medical advice are reviewed manually within 24 hours.',
 'sensitive,flag,moderation,review', 0, 0, 1, 20, NOW()),

(503, 5, 'How do I follow another user or topic?',
 'On any post or user profile, tap the "Follow" button. To see all the topics you follow, go to Community > Follow tab. You can also unfollow from the same place; unfollowed topics stop appearing in your feed.',
 'follow,topic,user,feed', 0, 0, 1, 30, NOW()),

(504, 5, 'How do I link a post to my pet?',
 'When creating a post, tap "Link Pet" at the top of the editor and pick the pet you want to associate. The post will then appear on that pet''s profile in Pet Hub, and other users can tap your pet''s name to see all posts you''ve linked.',
 'link-pet,associate,profile', 0, 0, 1, 40, NOW()),

(505, 5, 'Can I delete or edit a post after publishing?',
 'Yes. Open the post, tap the "..." menu, and choose "Delete" or "Edit". Deleted posts cannot be recovered. Edits show a small "edited" tag so other users know the content was changed.',
 'delete,edit,remove', 0, 0, 1, 50, NOW()),

-- ---------- 6. MOYUYO+ Membership ----------
(601, 6, 'What is MOYUYO+?',
 'MOYUYO+ is our paid membership program. Members get free shipping on every order, member-only discounts (5–10% off), early access to new products, free returns, priority customer support, a monthly $10 points allowance, and exclusive access to all Pet Hub 3D scenes.',
 'membership,plus,subscription', 0, 0, 1, 10, NOW()),

(602, 6, 'How much does MOYUYO+ cost?',
 'We offer three plans: Monthly ($9.99), Quarterly ($24.99, save ~17%), and Yearly ($89.99, save ~25%). All plans auto-renew unless cancelled. You can cancel anytime in Settings > Manage Subscription and keep your benefits until the end of the current billing period.',
 'price,cost,plan,monthly,yearly', 0, 0, 1, 20, NOW()),

(603, 6, 'How does the points multiplier work?',
 'MOYUYO+ members earn 2× points on every purchase. Gold and Platinum tiers earn up to 5×. Points post to your account within 24 hours of order delivery. Points earned on refund-eligible orders are reversed if the refund is granted.',
 'points,multiplier,bonus', 0, 0, 1, 30, NOW()),

(604, 6, 'Can I pause or cancel my subscription?',
 'Yes. Go to Settings > Manage Subscription > [Your Plan] > Pause or Cancel. Pausing holds your membership for up to 3 months; cancelling stops the auto-renewal at the end of the current period. Either change takes effect immediately and you keep your benefits until then.',
 'pause,cancel,subscription', 0, 0, 1, 40, NOW()),

(605, 6, 'How do I redeem my free monthly points?',
 'MOYUYO+ members receive $10 (1000 points) credited automatically on the 1st of each month. Look under Wallet > Points for the "MOYUYO+ Allowance" entry. The allowance expires at the end of the month if unused.',
 'redeem,monthly-points,allowance', 0, 0, 1, 50, NOW());
