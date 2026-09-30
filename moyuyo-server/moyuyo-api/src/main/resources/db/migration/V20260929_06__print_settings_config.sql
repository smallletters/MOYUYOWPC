-- 打印设置持久化：把前端 localStorage 中的 OrderPrint 纸张/份数/方向/边距保存到数据库，
-- 多设备 / 多浏览器 / 跨浏览器缓存清理场景下设置不再丢失。
-- 复用 mo_system_config（key/value 结构，无需新建表）。
-- key 命名约定：order_print_settings（与前端 SETTINGS_STORAGE_KEY 一致）。

INSERT IGNORE INTO mo_system_config (id, config_key, config_value, remark, create_time, update_time)
VALUES (
  UNIX_TIMESTAMP() * 1000,
  'order_print_settings',
  JSON_OBJECT(
    'paperSize', 'a4',
    'copies', 1,
    'orientation', 'portrait',
    'duplex', false,
    'marginY', 5,
    'marginX', 5
  ),
  'OrderPrint 页面默认打印设置（纸张/份数/方向/边距）',
  NOW(),
  NOW()
);