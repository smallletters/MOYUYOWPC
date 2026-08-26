-- 插入示例 CMS Banner 用于 APP 首页联调
INSERT INTO mo_cms_content (id, title, type, content, image_url, link_url, location, status, sort_order, start_time, end_time, create_time)
VALUES
  (1001, 'LIMITED · Festive Picks', 'BANNER', 'Up to 30% off selected care & gear', 'https://picsum.photos/seed/moyuyo-banner-1/750/360', '/pages/goods/list?categoryId=1', '首页', 'ACTIVE', 1, NULL, NULL, NOW()),
  (1002, 'NEW · MILO Explorer Kit', 'BANNER', 'MILO 同款户外探险套装上新', 'https://picsum.photos/seed/moyuyo-banner-2/750/360', '/pages/goods/list?categoryId=2', '首页', 'ACTIVE', 2, NULL, NULL, NOW())
ON DUPLICATE KEY UPDATE image_url = VALUES(image_url), update_time = NOW();