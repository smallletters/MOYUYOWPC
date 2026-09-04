USE moyuyo_dev;
SELECT id, parent_id, name, sort, level FROM mo_category WHERE level = 1 ORDER BY sort, id;