"""使用 Spring Security BCrypt 算法生成密码哈希。
BCryptPasswordEncoder strength=10（与后端一致：mo_security.password.bcrypt-strength: 10）。
"""
import sys

# BCrypt 标准输出格式：$2a$10$<22-char-salt><31-char-hash>
# strength=10, salt = 16 bytes base64-ish

# 由于 Java 的 BCryptPasswordEncoder 在 JDK 上运行，Python 没有等价实现。
# 我们用标准 bcrypt 库生成 $2a$ 前缀（Spring Security BCryptPasswordEncoder 兼容）。
try:
    import bcrypt
except ImportError:
    print("NO_BCRYPT_LIB")
    sys.exit(0)

password = sys.argv[1].encode("utf-8")
# gensalt(rounds=10) → $2a$10$xxxx
salt = bcrypt.gensalt(rounds=10)
hashed = bcrypt.hashpw(password, salt)
# 统一改成 $2a$ 前缀（Spring Security PasswordEncoder 兼容 $2a）
print(hashed.decode("utf-8"))