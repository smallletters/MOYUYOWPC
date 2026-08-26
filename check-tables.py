import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='dev123456', database='moyuyo_dev')
cur = conn.cursor()
for t in ['mo_community_post', 'mo_community_comment', 'mo_community_like', 'mo_community_topic_v2', 'mo_sensitive_word', 'mo_content_review', 'mo_community_collect']:
    try:
        cur.execute(f"SELECT COUNT(*) FROM {t}")
        n = cur.fetchone()[0]
        print(f"{t}: {n} rows")
    except Exception as e:
        print(f"{t}: ERROR {e}")
conn.close()