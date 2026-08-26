import sys
with open('D:/MOYUYOWPC/moyuyo-server/moyuyo-api/logs/moyuyo-error.log', 'r', encoding='utf-8', errors='replace') as f:
    content = f.read()
# 写到一个新文件便于直接读
with open('D:/MOYUYOWPC/_err_log.txt', 'w', encoding='utf-8') as f:
    # 取最后 3000 字符（包含最新错误）
    f.write(content[-3000:])
print('wrote', len(content), 'chars')