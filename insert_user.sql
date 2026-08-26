USE moyuyo_dev;
INSERT INTO mo_user
  (id, email, password_hash, nickname, country, locale, timezone, status, email_verified, two_factor_enabled, points, marketing_opt_in, deleted)
VALUES
  (200000001, 'test@moyuyo.com', '$2b$10$jEfCSIVoRt0yrVQ3dv2/VuR0i.aVrAOYMsXTCOY8k0sHKlJtLOKlm', '测试用户', 'US', 'en_US', 'UTC', 1, 1, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  nickname = VALUES(nickname),
  country = VALUES(country),
  locale = VALUES(locale),
  timezone = VALUES(timezone),
  status = 1,
  email_verified = 1,
  deleted = 0;

SELECT id, email, nickname, country, status, points FROM mo_user WHERE email = 'test@moyuyo.com';