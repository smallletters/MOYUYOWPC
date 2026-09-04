USE moyuyo_dev;
SELECT id, name, CHAR_LENGTH(name) AS len, HEX(SUBSTRING(name,1,4)) AS first_4_bytes_hex FROM mo_category ORDER BY id DESC LIMIT 30;