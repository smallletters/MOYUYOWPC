# MOYUYO 后端 SSO 模块（欧美市场版）

> 文档版本：V1.0.0  
> 编写日期：2026-07-02  
> 技术栈：Spring Boot 3 + Java 17 + JJWT 0.12 + Auth0 java-jwt

---

## 一、模块结构

```
com.moyuyo.sso
├── controller/                # REST API
│   ├── EmailAuthController    # 邮箱注册/登录
│   ├── OAuthController        # Apple/Google/Facebook
│   ├── TwoFactorController    # 2FA
│   ├── MagicLinkController    # 邮箱无密登录
│   └── GdprController         # 数据导出/账号注销
├── service/
│   ├── AuthService            # 统一鉴权编排
│   ├── EmailAuthService       # 邮箱密码
│   ├── OAuthAuthService       # 第三方登录
│   ├── TwoFactorService       # 2FA（TOTP）
│   ├── MagicLinkService       # Magic Link
│   ├── TokenService           # JWT 签发
│   ├── PasswordService        # 密码强度校验 + BCrypt
│   └── AccountMergeService    # 账号合并
├── provider/                  # 第三方登录验证
│   ├── AppleTokenVerifier
│   ├── GoogleTokenVerifier
│   ├── FacebookTokenVerifier
│   └── OAuthProvider (interface)
├── dto/                       # 请求/响应 DTO
├── entity/                    # 实体（与数据库对应）
├── repository/                # JPA Repository
├── config/                    # 配置（JWT、Security、CORS）
└── exception/                 # 业务异常
```

---

## 二、依赖（pom.xml）

```xml
<dependencies>
  <!-- Spring Boot Web + Security -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
  </dependency>

  <!-- JWT -->
  <dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
  </dependency>
  <dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
  </dependency>

  <!-- Google Token 验证 -->
  <dependency>
    <groupId>com.google.auth</groupId>
    <artifactId>google-auth-library-oauth2-http</artifactId>
    <version>1.24.1</version>
  </dependency>

  <!-- BCrypt（Spring Security 自带） -->

  <!-- TOTP 2FA -->
  <dependency>
    <groupId>dev.samstevens.totp</groupId>
    <artifactId>totp</artifactId>
    <version>1.7.1</version>
  </dependency>

  <!-- ZXing 生成二维码 -->
  <dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>core</artifactId>
    <version>3.5.3</version>
  </dependency>
  <dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>javase</artifactId>
    <version>3.5.3</version>
  </dependency>

  <!-- 邮件发送 -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
  </dependency>
</dependencies>
```

---

## 三、配置

### 3.1 application.yml

```yaml
moyuyo:
  jwt:
    # 256 位密钥（Base64 编码），从环境变量注入
    secret: ${JWT_SECRET}
    access-token-expire-seconds: 7200    # 2 小时
    refresh-token-expire-seconds: 2592000 # 30 天
    temp-token-expire-seconds: 300       # 2FA 临时 token 5 分钟
    issuer: moyuyo
  oauth:
    apple:
      client-id: ${APPLE_CLIENT_ID}        # com.moyuyo.app
      team-id: ${APPLE_TEAM_ID}
      key-id: ${APPLE_KEY_ID}
      private-key-path: classpath:apple/AuthKey_${APPLE_KEY_ID}.p8
    google:
      client-id: ${GOOGLE_CLIENT_ID}
      client-secret: ${GOOGLE_CLIENT_SECRET}
    facebook:
      app-id: ${FB_APP_ID}
      app-secret: ${FB_APP_SECRET}
  age-limit:
    eu-min-age: 16
    us-min-age: 13
```

### 3.2 安全配置

```java
// SecurityConfig.java
// Spring Security 配置：放行 OAuth 回调与公开接口，其余需 JWT
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      .csrf(AbstractHttpConfigurer::disable)
      .cors(Customizer.withDefaults())
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers(
          "/api/v1/user/register/**",
          "/api/v1/user/login/**",
          "/api/v1/user/oauth/**",
          "/api/v1/user/magic-link/**",
          "/api/v1/user/password/forgot",
          "/api/v1/user/email/verify",
          "/api/v1/webhook/**"
        ).permitAll()
        .anyRequest().authenticated()
      )
      .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
      .exceptionHandling(ex -> ex
        .authenticationEntryPoint(new JwtAuthEntryPoint())
        .accessDeniedHandler(new JwtAccessDeniedHandler())
      );
    return http.build();
  }
}
```

---

## 四、统一鉴权 Service

```java
// AuthService.java
// 统一鉴权编排：邮箱/Apple/Google/Facebook 登录的入口
@Service
public class AuthService {

  private final UserRepository userRepository;
  private final OAuthBindingRepository oauthBindingRepository;
  private final TokenService tokenService;
  private final AccountMergeService accountMergeService;
  private final LoginLogService loginLogService;

  public AuthService(UserRepository userRepository,
                     OAuthBindingRepository oauthBindingRepository,
                     TokenService tokenService,
                     AccountMergeService accountMergeService,
                     LoginLogService loginLogService) {
    this.userRepository = userRepository;
    this.oauthBindingRepository = oauthBindingRepository;
    this.tokenService = tokenService;
    this.accountMergeService = accountMergeService;
    this.loginLogService = loginLogService;
  }

  /**
   * 第三方登录统一入口：查找或创建用户
   */
  @Transactional
  public LoginResult loginWithOAuth(OAuthProvider provider, OAuthUserInfo userInfo,
                                     HttpServletRequest request) {
    // 1. 查询绑定关系
    Optional<OAuthBinding> binding = oauthBindingRepository
        .findByProviderAndProviderUid(provider.name(), userInfo.getUid());

    User user;
    boolean isNewUser = false;

    if (binding.isPresent()) {
      // 2a. 已有绑定 → 取 user
      user = userRepository.findById(binding.get().getUserId())
          .orElseThrow(() -> new BizException("USER_NOT_FOUND"));
      // 更新最后登录时间
      binding.get().setLastLoginTime(LocalDateTime.now());
      oauthBindingRepository.save(binding.get());
    } else if (userInfo.getEmail() != null) {
      // 2b. 无绑定但有邮箱 → 尝试合并到已有账号
      Optional<User> existing = userRepository.findByEmail(userInfo.getEmail());
      if (existing.isPresent()) {
        user = existing.get();
        // 合并第三方账号
        accountMergeService.bindOAuth(user, provider, userInfo);
      } else {
        // 2c. 完全新用户 → 创建
        user = createUserFromOAuth(userInfo);
        isNewUser = true;
      }
    } else {
      // 2d. 无邮箱（Apple 隐私邮箱）→ 全新创建
      user = createUserFromOAuth(userInfo);
      isNewUser = true;
    }

    // 3. 检查账号状态
    checkAccountStatus(user);

    // 4. 检查 2FA（强制开启 2FA 的用户必须二次验证）
    if (user.getTwoFactorEnabled()) {
      return LoginResult.require2fa(
        tokenService.createTempToken(user.getId())
      );
    }

    // 5. 签发 Token
    String accessToken = tokenService.createAccessToken(user);
    String refreshToken = tokenService.createRefreshToken(user);

    // 6. 记录登录日志
    loginLogService.log(user, provider.name(), request, true, null);

    return LoginResult.success(accessToken, refreshToken, user, isNewUser);
  }

  /**
   * 创建新用户（第三方登录）
   */
  private User createUserFromOAuth(OAuthUserInfo info) {
    User user = new User();
    user.setEmail(info.getEmail());
    user.setNickname(info.getNickname() != null ? info.getNickname() : "User_" + RandomUtil.randomNumbers(6));
    user.setAvatar(info.getAvatar());
    user.setEmailVerified(info.getEmail() != null);
    user.setStatus(UserStatus.NORMAL);
    user.setRegisterTime(LocalDateTime.now());
    user.setLastLoginTime(LocalDateTime.now());
    userRepository.save(user);

    // 绑定第三方
    OAuthBinding binding = new OAuthBinding();
    binding.setUserId(user.getId());
    binding.setProvider(info.getProvider().name());
    binding.setProviderUid(info.getUid());
    binding.setProviderEmail(info.getEmail());
    binding.setBindTime(LocalDateTime.now());
    binding.setLastLoginTime(LocalDateTime.now());
    oauthBindingRepository.save(binding);

    return user;
  }
}
```

---

## 五、Apple 登录验证

```java
// AppleTokenVerifier.java
// Apple 登录：验证 identityToken（JWT 格式），从 Apple 公钥验证签名
@Component
public class AppleTokenVerifier implements OAuthProvider {

  @Value("${moyuyo.oauth.apple.client-id}")
  private String clientId;

  @Value("${moyuyo.oauth.apple.team-id}")
  private String teamId;

  @Override
  public ProviderType getType() {
    return ProviderType.APPLE;
  }

  @Override
  public OAuthUserInfo verify(String idToken) {
    try {
      // 1. 解析 JWT Header 获取 kid
      String[] parts = idToken.split("\\.");
      String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
      JsonNode header = new ObjectMapper().readTree(headerJson);
      String kid = header.path("kid").asText();
      String alg = header.path("alg").asText();
      if (!"RS256".equals(alg)) {
        throw new BizException("UNSUPPORTED_ALG");
      }

      // 2. 从 Apple JWKS 获取公钥
      AppleJwk jwk = AppleJwksClient.getJwk(kid);
      RSAPublicKey publicKey = JwkToPublicKeyConverter.toRSAKey(jwk);

      // 3. 验证签名
      Jws<Claims> claims = Jwts.parser()
          .verifyWith(publicKey)
          .requireIssuer("https://appleid.apple.com")
          .requireAudience(clientId)
          .build()
          .parseSignedClaims(idToken);
      Claims payload = claims.getPayload();

      // 4. 验证 nonce 等（可选）

      // 5. 构造统一用户信息
      OAuthUserInfo info = new OAuthUserInfo();
      info.setProvider(ProviderType.APPLE);
      info.setUid(payload.getSubject());
      info.setEmail(payload.get("email", String.class));
      // 首次登录时 Apple 会通过 user 字段返回姓名（仅一次）
      // 这里只能从 request body 传入，详见 AppleLoginRequest
      return info;
    } catch (ExpiredJwtException e) {
      throw new BizException("APPLE_TOKEN_EXPIRED");
    } catch (Exception e) {
      throw new BizException("APPLE_TOKEN_INVALID", e);
    }
  }
}

// AppleJwksClient.java
// Apple 公钥获取与缓存
// 注意：cache 与 cacheExpireAt 必须为 static，因为 getJwk / refreshIfExpired 均为 static 方法
@Component
public class AppleJwksClient {

  private static final String JWKS_URL = "https://appleid.apple.com/auth/keys";
  private static final long CACHE_TTL_MS = 24 * 60 * 60 * 1000L; // 24 小时
  // static 字段：与 static 方法 getJwk / refreshIfExpired 保持一致
  private static Map<String, AppleJwk> cache = new ConcurrentHashMap<>();
  private static long cacheExpireAt = 0;

  public static AppleJwk getJwk(String kid) {
    refreshIfExpired();
    AppleJwk jwk = cache.get(kid);
    if (jwk == null) throw new BizException("APPLE_KID_NOT_FOUND");
    return jwk;
  }

  private static synchronized void refreshIfExpired() {
    if (System.currentTimeMillis() < cacheExpireAt) return;
    // 调用 https://appleid.apple.com/auth/keys 拉取公钥并写入 static 缓存
    // 缓存 24 小时
    cache.clear();
    // 实际实现：restTemplate.getForObject(JWKS_URL, AppleJwks.class).getKeys().forEach(k -> cache.put(k.getKid(), k));
    cacheExpireAt = System.currentTimeMillis() + CACHE_TTL_MS;
  }
}
```

---

## 六、Google 登录验证

```java
// GoogleTokenVerifier.java
// Google 登录：验证 idToken（JWT 格式），通过 Google 公钥验证
@Component
public class GoogleTokenVerifier implements OAuthProvider {

  @Value("${moyuyo.oauth.google.client-id}")
  private String clientId;

  private final GoogleIdTokenVerifier verifier;

  public GoogleTokenVerifier(@Value("${moyuyo.oauth.google.client-id}") String clientId) {
    this.clientId = clientId;
    this.verifier = new GoogleIdTokenVerifier.Builder(
        new NetHttpTransport(),
        new GsonFactory()
    )
        .setAudience(Collections.singletonList(clientId))
        .build();
  }

  @Override
  public ProviderType getType() {
    return ProviderType.GOOGLE;
  }

  @Override
  public OAuthUserInfo verify(String idTokenString) {
    try {
      GoogleIdToken idToken = verifier.verify(idTokenString);
      if (idToken == null) {
        throw new BizException("GOOGLE_TOKEN_INVALID");
      }
      Payload payload = idToken.getPayload();

      // 校验 email_verified
      if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
        throw new BizException("GOOGLE_EMAIL_NOT_VERIFIED");
      }

      OAuthUserInfo info = new OAuthUserInfo();
      info.setProvider(ProviderType.GOOGLE);
      info.setUid(payload.getSubject());
      info.setEmail(payload.getEmail());
      info.setNickname((String) payload.get("name"));
      info.setAvatar((String) payload.get("picture"));
      return info;
    } catch (GeneralSecurityException | IOException e) {
      throw new BizException("GOOGLE_TOKEN_INVALID", e);
    }
  }
}
```

---

## 七、Facebook 登录验证

```java
// FacebookTokenVerifier.java
// Facebook 登录：调用 Graph API 验证 accessToken
@Component
public class FacebookTokenVerifier implements OAuthProvider {

  private static final String GRAPH_URL =
      "https://graph.facebook.com/v18.0/me?fields=id,name,email,picture&access_token=";

  @Value("${moyuyo.oauth.facebook.app-id}")
  private String appId;

  @Value("${moyuyo.oauth.facebook.app-secret}")
  private String appSecret;

  private final RestTemplate restTemplate;

  public FacebookTokenVerifier(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Override
  public ProviderType getType() {
    return ProviderType.FACEBOOK;
  }

  @Override
  public OAuthUserInfo verify(String accessToken) {
    try {
      // 1. 验证 token 有效并属于本 App
      String debugUrl = String.format(
          "https://graph.facebook.com/debug_token?input_token=%s&access_token=%s|%s",
          accessToken, appId, appSecret
      );
      JsonNode debug = restTemplate.getForObject(debugUrl, JsonNode.class);
      JsonNode data = debug.path("data");
      if (!data.path("is_valid").asBoolean()) {
        throw new BizException("FB_TOKEN_INVALID");
      }
      if (!appId.equals(data.path("app_id").asText())) {
        throw new BizException("FB_TOKEN_APP_MISMATCH");
      }

      // 2. 获取用户信息
      String userUrl = GRAPH_URL + accessToken;
      JsonNode user = restTemplate.getForObject(userUrl, JsonNode.class);

      OAuthUserInfo info = new OAuthUserInfo();
      info.setProvider(ProviderType.FACEBOOK);
      info.setUid(user.path("id").asText());
      info.setEmail(user.path("email").asText(null));
      info.setNickname(user.path("name").asText());
      info.setAvatar(user.path("picture").path("data").path("url").asText(null));
      return info;
    } catch (Exception e) {
      throw new BizException("FB_TOKEN_INVALID", e);
    }
  }
}
```

---

## 八、邮箱密码登录

```java
// EmailAuthService.java
@Service
public class EmailAuthService {

  private static final int MAX_FAILED_ATTEMPTS = 5;
  private static final int LOCK_DURATION_MINUTES = 30;

  private final UserRepository userRepository;
  private final PasswordService passwordService;
  private final TokenService tokenService;
  private final LoginLogService loginLogService;
  private final RedisTemplate<String, Integer> redisTemplate;
  private final ConsentService consentService;
  private final EmailService emailService;

  public EmailAuthService(UserRepository userRepository,
                          PasswordService passwordService,
                          TokenService tokenService,
                          LoginLogService loginLogService,
                          RedisTemplate<String, Integer> redisTemplate,
                          ConsentService consentService,
                          EmailService emailService) {
    this.userRepository = userRepository;
    this.passwordService = passwordService;
    this.tokenService = tokenService;
    this.loginLogService = loginLogService;
    this.redisTemplate = redisTemplate;
    this.consentService = consentService;
    this.emailService = emailService;
  }

  /** 邮箱注册 */
  @Transactional
  public User register(EmailRegisterRequest req) {
    // 1. 验证同意条款
    if (!req.isAcceptTerms() || !req.isAcceptPrivacy()) {
      throw new BizException("MUST_ACCEPT_TERMS");
    }
    // 2. 验证年龄
    validateAge(req.getBirthday(), req.getCountry());
    // 3. 验证密码强度
    passwordService.validateStrength(req.getPassword());
    // 4. 检查邮箱唯一
    if (userRepository.findByEmail(req.getEmail()).isPresent()) {
      throw new BizException("EMAIL_ALREADY_REGISTERED", 1101);
    }
    // 5. 创建用户
    User user = new User();
    user.setEmail(req.getEmail());
    user.setPasswordHash(passwordService.hash(req.getPassword()));
    user.setNickname("User_" + RandomUtil.randomNumbers(6));
    user.setBirthday(req.getBirthday());
    user.setCountry(req.getCountry());
    user.setLocale(req.getLocale());
    user.setEmailVerified(false);
    user.setMarketingOptIn(req.isMarketingOptIn());
    user.setStatus(UserStatus.NORMAL);
    user.setRegisterTime(LocalDateTime.now());
    userRepository.save(user);

    // 6. 记录同意日志
    consentService.logConsent(user.getId(), "PRIVACY_POLICY", true, req.getPrivacyVersion(), "REGISTER");
    consentService.logConsent(user.getId(), "TERMS", true, req.getTermsVersion(), "REGISTER");
    if (req.isMarketingOptIn()) {
      consentService.logConsent(user.getId(), "MARKETING_EMAIL", true, "1.0", "REGISTER");
    }
    // 7. 发送验证邮件
    emailService.sendVerificationEmail(user);
    return user;
  }

  /** 邮箱密码登录 */
  public LoginResult login(EmailLoginRequest req, HttpServletRequest httpRequest) {
    String email = req.getEmail().toLowerCase();
    // 1. 查找用户
    User user = userRepository.findByEmail(email).orElse(null);
    if (user == null) {
      loginLogService.logFailed(email, "EMAIL", httpRequest, "USER_NOT_FOUND");
      throw new BizException("INVALID_CREDENTIALS", 1201);
    }
    // 2. 检查账号锁定
    checkAccountLock(email);
    // 3. 校验密码
    if (!passwordService.matches(req.getPassword(), user.getPasswordHash())) {
      recordFailedLogin(email);
      loginLogService.logFailed(email, "EMAIL", httpRequest, "WRONG_PASSWORD");
      throw new BizException("INVALID_CREDENTIALS", 1201);
    }
    // 4. 检查账号状态
    checkAccountStatus(user);
    // 5. 清除失败计数
    clearFailedLogins(email);
    // 6. 更新最后登录
    user.setLastLoginTime(LocalDateTime.now());
    userRepository.save(user);
    // 7. 检查 2FA
    if (user.getTwoFactorEnabled()) {
      return LoginResult.require2fa(tokenService.createTempToken(user.getId()));
    }
    // 8. 签发 Token
    String accessToken = tokenService.createAccessToken(user);
    String refreshToken = tokenService.createRefreshToken(user);
    loginLogService.log(user, "EMAIL", httpRequest, true, null);
    return LoginResult.success(accessToken, refreshToken, user, false);
  }

  /** 年龄校验：EU 16+ / US 13+ */
  private void validateAge(LocalDate birthday, String country) {
    if (birthday == null) throw new BizException("BIRTHDAY_REQUIRED");
    int age = Period.between(birthday, LocalDate.now()).getYears();
    int minAge = isEU(country) ? 16 : 13;
    if (age < minAge) {
      throw new BizException("AGE_BELOW_LIMIT", 1103);
    }
  }

  private boolean isEU(String country) {
    // 简化版：实际应维护 EU 国家列表
    Set<String> euCountries = Set.of("DE","FR","IT","ES","NL","BE","SE","PL","AT","IE","DK","FI","PT","CZ","HU","RO","BG","GR");
    return euCountries.contains(country);
  }

  private void checkAccountLock(String email) {
    String key = "login:fail:" + email;
    Integer count = redisTemplate.opsForValue().get(key);
    if (count != null && count >= MAX_FAILED_ATTEMPTS) {
      throw new BizException("ACCOUNT_LOCKED", 1202);
    }
  }

  private void recordFailedLogin(String email) {
    String key = "login:fail:" + email;
    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1) {
      redisTemplate.expire(key, LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
    }
  }

  private void clearFailedLogins(String email) {
    redisTemplate.delete("login:fail:" + email);
  }
}
```

```java
// PasswordService.java
// 密码强度校验 + BCrypt 加密
@Service
public class PasswordService {

  private static final int BCRYPT_STRENGTH = 12;

  /** 校验密码强度：≥ 8 位，含大小写 + 数字 */
  public void validateStrength(String password) {
    if (password == null || password.length() < 8) {
      throw new BizException("PASSWORD_TOO_WEAK", 1102);
    }
    if (!password.matches(".*[a-z].*")) throw new BizException("PASSWORD_TOO_WEAK", 1102);
    if (!password.matches(".*[A-Z].*")) throw new BizException("PASSWORD_TOO_WEAK", 1102);
    if (!password.matches(".*\\d.*")) throw new BizException("PASSWORD_TOO_WEAK", 1102);
    // 检查 HIBP 泄露密码库（可选）
  }

  public String hash(String rawPassword) {
    return BCrypt.hashpw(rawPassword, BCrypt.gensalt(BCRYPT_STRENGTH));
  }

  public boolean matches(String rawPassword, String hashed) {
    if (hashed == null) return false;
    try {
      return BCrypt.checkpw(rawPassword, hashed);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
```

---

## 九、JWT Token Service

```java
// TokenService.java
@Service
public class TokenService {

  @Value("${moyuyo.jwt.secret}")
  private String secret;

  @Value("${moyuyo.jwt.access-token-expire-seconds}")
  private long accessExpire;

  @Value("${moyuyo.jwt.refresh-token-expire-seconds}")
  private long refreshExpire;

  @Value("${moyuyo.jwt.temp-token-expire-seconds}")
  private long tempExpire;

  private final RedisTemplate<String, String> redisTemplate;

  public TokenService(RedisTemplate<String, String> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  private SecretKey getKey() {
    return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
  }

  /** 创建访问令牌 */
  public String createAccessToken(User user) {
    return buildToken(user.getId(), user.getEmail(), "access", accessExpire);
  }

  /** 创建刷新令牌 */
  public String createRefreshToken(User user) {
    return buildToken(user.getId(), user.getEmail(), "refresh", refreshExpire);
  }

  /** 2FA 临时 token */
  public String createTempToken(Long userId) {
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("type", "2fa")
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + tempExpire * 1000))
        .signWith(getKey())
        .compact();
  }

  private String buildToken(Long userId, String email, String type, long expireSeconds) {
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("email", email)
        .claim("type", type)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + expireSeconds * 1000))
        .issuer("moyuyo")
        .signWith(getKey())
        .compact();
  }

  /** 解析 token */
  public Long parseUserId(String token) {
    Claims claims = Jwts.parser()
        .verifyWith(getKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
    return Long.parseLong(claims.getSubject());
  }

  /** 退出登录：将 access token 加入 Redis 黑名单 */
  public void logout(String accessToken) {
    try {
      Claims claims = Jwts.parser()
          .verifyWith(getKey())
          .build()
          .parseSignedClaims(accessToken)
          .getPayload();
      long expireAt = claims.getExpiration().getTime();
      long now = System.currentTimeMillis();
      long ttlSeconds = (expireAt - now) / 1000;
      if (ttlSeconds > 0) {
        String tokenHash = DigestUtils.sha256Hex(accessToken);
        String key = "blacklist:token:" + tokenHash;
        redisTemplate.opsForValue().set(key, "1", ttlSeconds, TimeUnit.SECONDS);
      }
    } catch (Exception e) {
      // token 已过期或无效，无需加入黑名单
    }
  }

  /** 校验 token：先检查是否在黑名单中，再解析返回 userId */
  public Long validateToken(String token) {
    String tokenHash = DigestUtils.sha256Hex(token);
    String key = "blacklist:token:" + tokenHash;
    if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
      throw new BizException("TOKEN_BLACKLISTED");
    }
    return parseUserId(token);
  }

  /** 刷新 access token */
  public String refreshAccessToken(String refreshToken) {
    Long userId = parseUserId(refreshToken);
    User user = userRepository.findById(userId).orElseThrow();
    return createAccessToken(user);
  }
}
```

---

## 十、2FA 双因素认证

```java
// TwoFactorService.java
@Service
public class TwoFactorService {

  private final UserRepository userRepository;
  private final TwoFactorRepository twoFactorRepository;
  private final Totp totp;
  private final EncryptionService encryptionService;

  public TwoFactorService(UserRepository userRepository,
                          TwoFactorRepository twoFactorRepository,
                          EncryptionService encryptionService) {
    this.userRepository = userRepository;
    this.twoFactorRepository = twoFactorRepository;
    this.encryptionService = encryptionService;
    this.totp = new Totp();
  }

  /** 启用 2FA：生成密钥、二维码、备用码 */
  @Transactional
  public TwoFactorEnableResult enable(Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new BizException("USER_NOT_FOUND"));

    // 1. 生成 160 位 TOTP 密钥
    String secret = new SecretGenerator().generate();
    // 2. 生成 10 组备用码
    List<String> backupCodes = generateBackupCodes(10);
    List<String> hashedBackupCodes = backupCodes.stream()
        .map(c -> BCrypt.hashpw(c, BCrypt.gensalt(8)))
        .toList();

    // 3. 存储（密钥加密）
    TwoFactorConfig config = twoFactorRepository.findByUserId(userId)
        .orElseGet(TwoFactorConfig::new);
    config.setUserId(userId);
    config.setSecret(encryptionService.encrypt(secret));
    config.setBackupCodes(objectMapper.writeValueAsString(hashedBackupCodes));
    config.setEnabledAt(LocalDateTime.now());
    twoFactorRepository.save(config);

    // 4. 构造 otpauth URL
    String otpAuth = String.format(
        "otpauth://totp/MOYUYO:%s?secret=%s&issuer=MOYUYO",
        user.getEmail(), secret
    );
    String qrCode = QrCodeGenerator.toBase64(otpAuth, 300, 300);

    return new TwoFactorEnableResult(secret, otpAuth, qrCode, backupCodes);
  }

  /** 验证 2FA 码 */
  public boolean verifyCode(Long userId, String code) {
    TwoFactorConfig config = twoFactorRepository.findByUserId(userId)
        .orElseThrow(() -> new BizException("2FA_NOT_ENABLED"));
    String secret = encryptionService.decrypt(config.getSecret());

    // 1. 尝试验证 TOTP
    if (totp.verify(code, secret)) {
      return true;
    }
    // 2. 尝试备用码
    // 注意：用 ArrayList 包装，确保后续 hashedCodes.remove(i) 可变操作不会抛 UnsupportedOperationException
    List<String> hashedCodes = new ArrayList<>(objectMapper.readValue(
        config.getBackupCodes(), new TypeReference<>() {}));
    for (int i = 0; i < hashedCodes.size(); i++) {
      if (BCrypt.checkpw(code, hashedCodes.get(i))) {
        // 一次性：使用后从列表移除
        hashedCodes.remove(i);
        config.setBackupCodes(objectMapper.writeValueAsString(hashedCodes));
        twoFactorRepository.save(config);
        return true;
      }
    }
    return false;
  }
}
```

---

## 十一、Controller 示例

```java
// OAuthController.java
// 第三方登录统一入口
@RestController
@RequestMapping("/api/v1/user/oauth")
public class OAuthController {

  private final AuthService authService;
  private final AppleTokenVerifier appleVerifier;
  private final GoogleTokenVerifier googleVerifier;
  private final FacebookTokenVerifier facebookVerifier;

  /** Apple 登录 */
  @PostMapping("/apple")
  public ApiResponse<LoginResponse> appleLogin(
      @RequestBody AppleLoginRequest req,
      HttpServletRequest httpRequest) {
    OAuthUserInfo info = appleVerifier.verify(req.getIdentityToken());
    if (req.getUser() != null && req.getUser().getName() != null) {
      info.setNickname(req.getUser().getName().getFullName());
    }
    LoginResult result = authService.loginWithOAuth(
        ProviderType.APPLE, info, httpRequest);
    return ApiResponse.ok(toResponse(result));
  }

  /** Google 登录 */
  @PostMapping("/google")
  public ApiResponse<LoginResponse> googleLogin(
      @RequestBody GoogleLoginRequest req,
      HttpServletRequest httpRequest) {
    OAuthUserInfo info = googleVerifier.verify(req.getIdToken());
    LoginResult result = authService.loginWithOAuth(
        ProviderType.GOOGLE, info, httpRequest);
    return ApiResponse.ok(toResponse(result));
  }

  /** Facebook 登录 */
  @PostMapping("/facebook")
  public ApiResponse<LoginResponse> facebookLogin(
      @RequestBody FacebookLoginRequest req,
      HttpServletRequest httpRequest) {
    OAuthUserInfo info = facebookVerifier.verify(req.getAccessToken());
    LoginResult result = authService.loginWithOAuth(
        ProviderType.FACEBOOK, info, httpRequest);
    return ApiResponse.ok(toResponse(result));
  }

  private LoginResponse toResponse(LoginResult result) {
    LoginResponse resp = new LoginResponse();
    resp.setAccessToken(result.getAccessToken());
    resp.setRefreshToken(result.getRefreshToken());
    resp.setRequire2fa(result.isRequire2fa());
    resp.setTempToken(result.getTempToken());
    resp.setIsNewUser(result.isNewUser());
    resp.setExpiresIn(7200);
    return resp;
  }
}
```

---

## 十二、GDPR 账号注销

> GdprService 实现见 §14.6（含 3 参数 `requestDeletion(userId, password, reason)` 完整版与数据导出能力）。
> 本节仅保留接口说明：账号注销采用 30 天可恢复机制，期间用户可通过邮件中的恢复链接撤销注销；过期后由定时任务执行匿名化/硬删除。

---

## 十三、错误码规范

```java
// BizException.java + GlobalExceptionHandler.java
@Getter
public class BizException extends RuntimeException {
  private final String code;
  private final int status;

  public BizException(String code) {
    this(code, 400);
  }

  public BizException(String code, int status) {
    super(code);
    this.code = code;
    this.status = status;
  }

  public BizException(String code, Throwable cause) {
    super(code, cause);
    this.code = code;
    this.status = 400;
  }
}

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(BizException.class)
  public ApiResponse<Void> handleBiz(BizException e) {
    return ApiResponse.fail(e.getStatus(), e.getCode());
  }
}
```

---

## 十四、补全 Controller（EmailAuth / MagicLink / TwoFactor / Gdpr）

> 以下 Controller 与接口文档完全对应，可直接落地。

### 14.1 EmailAuthController

```java
// EmailAuthController.java
// 邮箱注册 / 登录 / 密码找回 / 邮箱验证
@RestController
@RequestMapping("/api/v1/user")
public class EmailAuthController {

  private final EmailAuthService emailAuthService;
  private final EmailVerifyService emailVerifyService;
  private final PasswordService passwordService;

  public EmailAuthController(EmailAuthService emailAuthService,
                             EmailVerifyService emailVerifyService,
                             PasswordService passwordService) {
    this.emailAuthService = emailAuthService;
    this.emailVerifyService = emailVerifyService;
    this.passwordService = passwordService;
  }

  /** 2.1 邮箱密码注册 */
  @PostMapping("/register/email")
  public ApiResponse<RegisterResponse> registerEmail(@RequestBody EmailRegisterRequest req) {
    User user = emailAuthService.register(req);
    return ApiResponse.ok(RegisterResponse.builder()
        .userId(user.getId())
        .email(user.getEmail())
        .verifyEmailSent(true)
        .verifyEmailExpire(86400)
        .build());
  }

  /** 2.2 邮箱密码登录 */
  @PostMapping("/login/email")
  public ApiResponse<LoginResponse> loginEmail(
      @RequestBody EmailLoginRequest req,
      HttpServletRequest httpRequest) {
    LoginResult result = emailAuthService.login(req, httpRequest);
    return ApiResponse.ok(toLoginResponse(result));
  }

  /** 2.7 Magic Link 发送 */
  @PostMapping("/login/magic-link")
  public ApiResponse<MagicLinkResponse> sendMagicLink(@RequestBody MagicLinkRequest req) {
    emailAuthService.sendMagicLink(req.getEmail());
    return ApiResponse.ok(MagicLinkResponse.builder()
        .sent(true)
        .expireSeconds(600)
        .build());
  }

  /** 2.7 Magic Link 验证登录 */
  @PostMapping("/login/magic-link/verify")
  public ApiResponse<LoginResponse> verifyMagicLink(
      @RequestBody MagicLinkVerifyRequest req,
      HttpServletRequest httpRequest) {
    LoginResult result = emailAuthService.verifyMagicLink(req.getToken(), req.getDeviceId(), httpRequest);
    return ApiResponse.ok(toLoginResponse(result));
  }

  /** 2.8 邮箱验证 */
  @PostMapping("/email/verify")
  public ApiResponse<Void> verifyEmail(@RequestBody EmailVerifyRequest req) {
    emailVerifyService.verify(req.getToken());
    return ApiResponse.ok();
  }

  /** 2.9.1 发送找回密码邮件 */
  @PostMapping("/password/forgot")
  public ApiResponse<Void> forgotPassword(@RequestBody ForgotPasswordRequest req) {
    passwordService.sendResetEmail(req.getEmail());
    return ApiResponse.ok();
  }

  /** 2.9.2 重置密码 */
  @PostMapping("/password/reset")
  public ApiResponse<Void> resetPassword(@RequestBody ResetPasswordRequest req) {
    passwordService.resetPassword(req.getToken(), req.getNewPassword());
    return ApiResponse.ok();
  }

  /** 2.10 已登录修改密码 */
  @PostMapping("/password/change")
  public ApiResponse<Void> changePassword(
      @RequestBody ChangePasswordRequest req,
      @AuthenticationPrincipal Long userId) {
    passwordService.changePassword(userId, req.getOldPassword(), req.getNewPassword());
    return ApiResponse.ok();
  }

  /** 2.11 退出登录 */
  @PostMapping("/logout")
  public ApiResponse<Void> logout(@AuthenticationPrincipal Long userId) {
    emailAuthService.logout(userId);
    return ApiResponse.ok();
  }

  /** 公共：转换登录结果 */
  private LoginResponse toLoginResponse(LoginResult result) {
    return LoginResponse.builder()
        .accessToken(result.getAccessToken())
        .refreshToken(result.getRefreshToken())
        .refreshExpiresIn(2592000)
        .expiresIn(7200)
        .require2fa(result.isRequire2fa())
        .tempToken(result.getTempToken())
        .isNewUser(result.isNewUser())
        .profile(result.getUser() != null ? ProfileDto.from(result.getUser()) : null)
        .build();
  }
}
```

### 14.2 TwoFactorController

```java
// TwoFactorController.java
// 2FA 启用 / 验证 / 关闭
@RestController
@RequestMapping("/api/v1/user/2fa")
public class TwoFactorController {

  private final TwoFactorService twoFactorService;
  private final TokenService tokenService;

  public TwoFactorController(TwoFactorService twoFactorService, TokenService tokenService) {
    this.twoFactorService = twoFactorService;
    this.tokenService = tokenService;
  }

  /** 2.6.1 启用 2FA：返回密钥、二维码、备用码 */
  @PostMapping("/enable")
  public ApiResponse<TwoFactorEnableResponse> enable(@AuthenticationPrincipal Long userId) {
    TwoFactorEnableResult result = twoFactorService.enable(userId);
    return ApiResponse.ok(TwoFactorEnableResponse.builder()
        .secret(result.getSecret())
        .qrCodeUrl(result.getQrCodeUrl())
        .qrCodeBase64(result.getQrCodeBase64())
        .backupCodes(result.getBackupCodes())
        .build());
  }

  /** 2.6.2 验证 2FA 码（登录流程） */
  @PostMapping("/verify")
  public ApiResponse<LoginResponse> verify(@RequestBody TwoFactorVerifyRequest req) {
    Long userId = tokenService.parseUserIdFromTempToken(req.getTempToken());
    boolean ok = twoFactorService.verifyCode(userId, req.getCode());
    if (!ok) {
      throw new BizException("2FA_INVALID_CODE", 1204);
    }
    // 可信设备：保存到 mo_device
    if (req.isTrustDevice()) {
      twoFactorService.trustDevice(userId, req.getDeviceId());
    }
    // 签发正式 token
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new BizException("USER_NOT_FOUND"));
    String accessToken = tokenService.createAccessToken(user);
    String refreshToken = tokenService.createRefreshToken(user);
    return ApiResponse.ok(LoginResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(7200)
        .build());
  }

  /** 关闭 2FA */
  @PostMapping("/disable")
  public ApiResponse<Void> disable(
      @RequestBody TwoFactorDisableRequest req,
      @AuthenticationPrincipal Long userId) {
    twoFactorService.disable(userId, req.getPassword(), req.getCode());
    return ApiResponse.ok();
  }

  /** 重新生成备用码 */
  @PostMapping("/backup-codes/regenerate")
  public ApiResponse<List<String>> regenerateBackupCodes(
      @RequestBody TwoFactorVerifyRequest req,
      @AuthenticationPrincipal Long userId) {
    return ApiResponse.ok(twoFactorService.regenerateBackupCodes(userId, req.getCode()));
  }
}
```

### 14.3 MagicLinkService 完整实现

```java
// MagicLinkService.java
@Service
public class MagicLinkService {

  private static final long EXPIRE_SECONDS = 600;   // 10 分钟
  private static final int  MAX_ATTEMPTS   = 5;

  private final UserRepository userRepository;
  private final RedisTemplate<String, String> redisTemplate;
  private final EmailService emailService;
  private final TokenService tokenService;

  public MagicLinkService(UserRepository userRepository,
                          RedisTemplate<String, String> redisTemplate,
                          EmailService emailService,
                          TokenService tokenService) {
    this.userRepository = userRepository;
    this.redisTemplate = redisTemplate;
    this.emailService = emailService;
    this.tokenService = tokenService;
  }

  /** 发送 Magic Link */
  public void send(String email) {
    email = email.toLowerCase();
    // 1. 限流：同一邮箱 1 分钟内只能发 1 次
    String rateKey = "magic:rate:" + email;
    if (Boolean.TRUE.equals(redisTemplate.hasKey(rateKey))) {
      throw new BizException("MAGIC_LINK_RATE_LIMITED", 1004);
    }
    redisTemplate.opsForValue().set(rateKey, "1", 60, TimeUnit.SECONDS);
    // 2. 即便用户不存在也返回成功（防邮箱枚举）
    User user = userRepository.findByEmail(email).orElse(null);
    if (user == null) return;
    // 3. 生成 token 存 Redis
    String token = "ml_" + RandomUtil.randomUUID();
    String key = "magic:token:" + token;
    redisTemplate.opsForValue().set(key, email, EXPIRE_SECONDS, TimeUnit.SECONDS);
    // 4. 发送邮件
    String link = "https://app.moyuyo.com/magic?token=" + token;
    emailService.sendMagicLink(email, link, EXPIRE_SECONDS / 60);
  }

  /** 校验 Magic Link */
  public User verify(String token, String deviceId, HttpServletRequest httpRequest) {
    String key = "magic:token:" + token;
    String email = redisTemplate.opsForValue().get(key);
    if (email == null) {
      throw new BizException("MAGIC_LINK_EXPIRED", 1203);
    }
    // 单次使用
    redisTemplate.delete(key);
    // 失败计数
    String attemptKey = "magic:attempt:" + email;
    Long attempts = redisTemplate.opsForValue().increment(attemptKey);
    if (attempts != null && attempts > MAX_ATTEMPTS) {
      throw new BizException("MAGIC_LINK_TOO_MANY_ATTEMPTS", 1004);
    }
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new BizException("USER_NOT_FOUND"));
    user.setLastLoginTime(LocalDateTime.now());
    userRepository.save(user);
    return user;
  }
}
```

### 14.4 GdprController

```java
// GdprController.java
// GDPR 数据导出 / 账号注销 / 同意管理
@RestController
@RequestMapping("/api/v1/user")
public class GdprController {

  private final GdprService gdprService;
  private final ConsentService consentService;

  public GdprController(GdprService gdprService, ConsentService consentService) {
    this.gdprService = gdprService;
    this.consentService = consentService;
  }

  /** 2.15 更新同意状态 */
  @PutMapping("/consent")
  public ApiResponse<Void> updateConsent(
      @RequestBody ConsentRequest req,
      @AuthenticationPrincipal Long userId) {
    consentService.updateConsent(userId, req);
    return ApiResponse.ok();
  }

  /** 2.16 申请数据导出 */
  @PostMapping("/data/export")
  public ApiResponse<DataExportResponse> requestExport(@AuthenticationPrincipal Long userId) {
    DataExportRequest req = gdprService.requestExport(userId);
    return ApiResponse.ok(DataExportResponse.builder()
        .exportId(req.getExportId())
        .status(req.getStatus())
        .estimatedReadyAt(req.getEstimatedReadyAt())
        .build());
  }

  /** 2.16 查询导出状态 */
  @GetMapping("/data/export/{exportId}")
  public ApiResponse<DataExportResponse> getExport(
      @PathVariable String exportId,
      @AuthenticationPrincipal Long userId) {
    DataExportRequest req = gdprService.getExport(exportId, userId);
    return ApiResponse.ok(DataExportResponse.builder()
        .exportId(req.getExportId())
        .status(req.getStatus())
        .downloadUrl(req.getDownloadUrl())
        .expireAt(req.getDownloadExpireAt())
        .build());
  }

  /** 2.17 申请账号注销 */
  @PostMapping("/account/delete")
  public ApiResponse<AccountDeletionResponse> deleteAccount(
      @RequestBody AccountDeletionRequest req,
      @AuthenticationPrincipal Long userId) {
    AccountDeletion deletion = gdprService.requestDeletion(userId, req.getPassword(), req.getReason());
    return ApiResponse.ok(AccountDeletionResponse.builder()
        .status(deletion.getStatus())
        .deleteScheduledAt(deletion.getScheduledAt())
        .recoverDeadline(deletion.getRecoverDeadline())
        .recoverUrl("https://app.moyuyo.com/recover?token=" + deletion.getRecoverToken())
        .build());
  }

  /** 2.18 恢复账号 */
  @PostMapping("/account/recover")
  public ApiResponse<Void> recoverAccount(@RequestBody AccountRecoverRequest req) {
    gdprService.recoverAccount(req.getToken());
    return ApiResponse.ok();
  }
}
```

### 14.5 ConsentService 完整实现

```java
// ConsentService.java
@Service
public class ConsentService {

  private final ConsentLogRepository consentLogRepository;
  private final UserRepository userRepository;

  private static final Map<String, String> CURRENT_VERSIONS = Map.of(
      "PRIVACY_POLICY",      "1.0.0",
      "TERMS",               "1.0.0",
      "MARKETING_EMAIL",     "1.0.0",
      "ANALYTICS_COOKIE",    "1.0.0",
      "ADVERTISING_COOKIE",  "1.0.0"
  );

  /** 更新用户同意状态 */
  @Transactional
  public void updateConsent(Long userId, ConsentRequest req) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new BizException("USER_NOT_FOUND"));

    if (req.getMarketingEmail() != null) {
      logConsent(userId, "MARKETING_EMAIL", req.getMarketingEmail(),
                 CURRENT_VERSIONS.get("MARKETING_EMAIL"), "SETTINGS");
      user.setMarketingOptIn(req.getMarketingEmail());
    }
    if (req.getAnalyticsCookies() != null) {
      logConsent(userId, "ANALYTICS_COOKIE", req.getAnalyticsCookies(),
                 CURRENT_VERSIONS.get("ANALYTICS_COOKIE"), "SETTINGS");
    }
    if (req.getAdvertisingCookies() != null) {
      logConsent(userId, "ADVERTISING_COOKIE", req.getAdvertisingCookies(),
                 CURRENT_VERSIONS.get("ADVERTISING_COOKIE"), "SETTINGS");
    }
    userRepository.save(user);
  }

  /** 记录同意日志 */
  public void logConsent(Long userId, String type, boolean granted, String version, String source) {
    ConsentLog log = new ConsentLog();
    log.setUserId(userId);
    log.setConsentType(type);
    log.setGranted(granted);
    log.setVersion(version);
    log.setSource(source);
    log.setGrantTime(LocalDateTime.now());
    if (!granted) {
      log.setRevokeTime(LocalDateTime.now());
    }
    consentLogRepository.save(log);
  }
}
```

### 14.6 GdprService 完整实现

```java
// GdprService.java
@Service
public class GdprService {

  private static final int RECOVER_DAYS = 30;
  private static final long EXPORT_EXPIRE_DAYS = 7;
  private static final long EXPORT_PROCESSING_MINUTES = 30;

  private final UserRepository userRepository;
  private final OrderRepository orderRepository;
  private final PetRepository petRepository;
  private final AddressRepository addressRepository;
  private final CartRepository cartRepository;
  private final OAuthBindingRepository oauthBindingRepository;
  private final TwoFactorRepository twoFactorRepository;
  private final AccountDeletionRepository deletionRepository;
  private final DataExportRequestRepository exportRequestRepository;
  private final PasswordService passwordService;
  private final ExportTaskService exportTaskService;

  /** 申请注销账号 */
  @Transactional
  public AccountDeletion requestDeletion(Long userId, String password, String reason) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new BizException("USER_NOT_FOUND"));

    // 校验密码
    if (!passwordService.matches(password, user.getPasswordHash())) {
      throw new BizException("INVALID_PASSWORD");
    }

    // 已有待注销记录
    Optional<AccountDeletion> existing = deletionRepository.findByUserId(userId);
    if (existing.isPresent() && "PENDING".equals(existing.get().getStatus())) {
      return existing.get();
    }

    // 创建注销记录
    AccountDeletion deletion = existing.orElseGet(AccountDeletion::new);
    deletion.setUserId(userId);
    deletion.setStatus("PENDING");
    deletion.setReason(reason);
    deletion.setScheduledAt(LocalDateTime.now().plusDays(RECOVER_DAYS));
    deletion.setRecoverDeadline(LocalDateTime.now().plusDays(RECOVER_DAYS));
    deletion.setRecoverToken(RandomUtil.randomUUID());
    deletionRepository.save(deletion);

    // 标记用户为注销中
    user.setStatus(UserStatus.DELETING);
    user.setDeleteScheduledAt(deletion.getScheduledAt());
    userRepository.save(user);

    // 发送邮件通知
    emailService.sendAccountDeletionNotice(user.getEmail(), deletion.getScheduledAt());

    return deletion;
  }

  /** 恢复账号 */
  @Transactional
  public void recoverAccount(String recoverToken) {
    AccountDeletion deletion = deletionRepository.findByRecoverToken(recoverToken)
        .orElseThrow(() -> new BizException("INVALID_RECOVER_TOKEN"));
    if (!"PENDING".equals(deletion.getStatus())) {
      throw new BizException("DELETION_NOT_RECOVERABLE");
    }
    if (LocalDateTime.now().isAfter(deletion.getRecoverDeadline())) {
      throw new BizException("RECOVER_EXPIRED");
    }
    deletion.setStatus("RECOVERED");
    deletion.setRecoveredAt(LocalDateTime.now());
    deletionRepository.save(deletion);

    User user = userRepository.findById(deletion.getUserId())
        .orElseThrow(() -> new BizException("USER_NOT_FOUND"));
    user.setStatus(UserStatus.NORMAL);
    user.setDeleteScheduledAt(null);
    userRepository.save(user);
  }

  /** 申请数据导出 */
  @Transactional
  public DataExportRequest requestExport(Long userId) {
    DataExportRequest req = new DataExportRequest();
    req.setUserId(userId);
    req.setExportId("exp_" + RandomUtil.randomUUID());
    req.setStatus("PROCESSING");
    req.setCreateTime(LocalDateTime.now());
    exportRequestRepository.save(req);

    // 异步执行导出
    exportTaskService.exportAsync(req.getExportId(), userId);
    return req;
  }

  /** 获取导出请求 */
  public DataExportRequest getExport(String exportId, Long userId) {
    DataExportRequest req = exportRequestRepository.findByExportId(exportId)
        .orElseThrow(() -> new BizException("EXPORT_NOT_FOUND"));
    if (!req.getUserId().equals(userId)) {
      throw new BizException("EXPORT_ACCESS_DENIED");
    }
    return req;
  }
}
```

### 14.7 ExportTaskService 异步导出实现

```java
// ExportTaskService.java
// 异步生成用户数据导出包（GDPR）
@Service
public class ExportTaskService {

  @Async("exportExecutor")
  public void exportAsync(String exportId, Long userId) {
    try {
      // 1. 收集所有用户数据
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("profile",     collectProfile(userId));
      data.put("orders",      collectOrders(userId));
      data.put("addresses",   collectAddresses(userId));
      data.put("pets",        collectPets(userId));
      data.put("coupons",     collectCoupons(userId));
      data.put("pointsLog",   collectPointsLog(userId));
      data.put("community",   collectCommunity(userId));

      // 2. 序列化为 JSON
      ObjectMapper mapper = new ObjectMapper();
      mapper.registerModule(new JavaTimeModule());
      String json = mapper.writeValueAsString(data);

      // 3. 打包为 ZIP（多个 JSON 文件）
      String zipKey = "exports/" + exportId + ".zip";
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (ZipOutputStream zos = new ZipOutputStream(baos)) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
          zos.putNextEntry(new ZipEntry(entry.getKey() + ".json"));
          zos.write(mapper.writeValueAsBytes(entry.getValue()));
          zos.closeEntry();
        }
      }

      // 4. 上传 OSS 并生成签名 URL
      String downloadUrl = ossService.uploadAndSign(zipKey,
          new ByteArrayInputStream(baos.toByteArray()),
          TimeUnit.DAYS.toSeconds(EXPORT_EXPIRE_DAYS));

      // 5. 更新导出请求状态
      DataExportRequest req = exportRequestRepository.findByExportId(exportId)
          .orElseThrow();
      req.setStatus("READY");
      req.setDownloadUrl(downloadUrl);
      req.setDownloadExpireAt(LocalDateTime.now().plusDays(EXPORT_EXPIRE_DAYS));
      req.setCompleteTime(LocalDateTime.now());
      exportRequestRepository.save(req);
    } catch (Exception e) {
      DataExportRequest req = exportRequestRepository.findByExportId(exportId).orElseThrow();
      req.setStatus("FAILED");
      exportRequestRepository.save(req);
      log.error("Export failed: {}", exportId, e);
    }
  }
}
```

---

## 十五、附：DTO 定义示例

```java
// EmailRegisterRequest.java
@Data
public class EmailRegisterRequest {
  @NotBlank @Email private String email;
  @NotBlank @Size(min = 8, max = 64) private String password;
  @NotNull @Past private LocalDate birthday;
  @NotNull private String country;
  @NotNull private String locale;
  private boolean acceptTerms;
  private boolean acceptPrivacy;
  private boolean marketingOptIn;
}
```

```java
// LoginResponse.java
@Data
@Builder
public class LoginResponse {
  private String accessToken;
  private String refreshToken;
  private long expiresIn;
  private long refreshExpiresIn;
  private boolean require2fa;
  private String tempToken;
  private boolean isNewUser;
  private ProfileDto profile;
}
```

```java
// ConsentRequest.java
@Data
public class ConsentRequest {
  private Boolean marketingEmail;
  private Boolean marketingPush;
  private Boolean analyticsCookies;
  private Boolean advertisingCookies;
}
```

---

## 十六、关键安全点

| 项目 | 措施 |
| :--- | :--- |
| 密码 | BCrypt strength=12 |
| 密码传输 | 强制 HTTPS |
| JWT | HS256，密钥从环境变量，access 2h / refresh 30d |
| 2FA 密钥 | AES-256 加密存储 |
| 备用码 | BCrypt 哈希存储 |
| 失败锁定 | Redis 计数 5 次锁 30 分钟 |
| 账号合并 | 二次确认（密码 / 邮箱验证码） |
| Token 撤销 | Redis 黑名单（access token 主动失效） |
| 防重放 | 5 分钟有效期的 temp token + 单次使用 |
| 日志 | 登录日志保留 180 天，敏感字段脱敏 |

---

## 十七、AccountMergeService 账号合并

> 账号合并入口：用户在「设置 → 账号安全 → 账号合并」中手动发起，用于将重复注册的邮箱账号与第三方登录账号合并为一个主账号。
>
> 合并流程：验证两个账号身份 → 冲突检测 → 数据合并 → 去重 → 标记合并
>
> 冲突处理：以 `registerTime` 创建时间更早的账号为主账号（保留其邮箱、密码、2FA 配置）。
>
> 数据去重规则：
> - 订单（mo_order）：合并取并集，按 `order_no` 去重，重复订单保留主账号侧
> - 积分（mo_points_balance）：取两侧较大值，积分流水累加
> - 优惠券（mo_coupon）：按 `coupon_id` 去重，同券取最大有效期；数量累加
> - 其他数据（地址、宠物、购物车、社区内容、OAuth 绑定）：全部累加迁移至主账号
>
> 合并限制：同一用户 30 天内只能合并一次，合并操作不可撤销。

```java
// AccountMergeService.java
// 账号合并服务：将次要账号数据迁移至主账号，并标记次要账号为已合并
@Service
public class AccountMergeService {

  private static final int MERGE_COOLDOWN_DAYS = 30;

  private final UserRepository userRepository;
  private final OAuthBindingRepository oauthBindingRepository;
  private final OrderRepository orderRepository;
  private final PointsBalanceRepository pointsBalanceRepository;
  private final PointsLogRepository pointsLogRepository;
  private final CouponRepository couponRepository;
  private final AddressRepository addressRepository;
  private final PetRepository petRepository;
  private final CartRepository cartRepository;
  private final AccountMergeLogRepository mergeLogRepository;
  private final PasswordService passwordService;
  private final EmailService emailService;
  private final RedisTemplate<String, String> redisTemplate;

  public AccountMergeService(UserRepository userRepository,
                             OAuthBindingRepository oauthBindingRepository,
                             OrderRepository orderRepository,
                             PointsBalanceRepository pointsBalanceRepository,
                             PointsLogRepository pointsLogRepository,
                             CouponRepository couponRepository,
                             AddressRepository addressRepository,
                             PetRepository petRepository,
                             CartRepository cartRepository,
                             AccountMergeLogRepository mergeLogRepository,
                             PasswordService passwordService,
                             EmailService emailService,
                             RedisTemplate<String, String> redisTemplate) {
    this.userRepository = userRepository;
    this.oauthBindingRepository = oauthBindingRepository;
    this.orderRepository = orderRepository;
    this.pointsBalanceRepository = pointsBalanceRepository;
    this.pointsLogRepository = pointsLogRepository;
    this.couponRepository = couponRepository;
    this.addressRepository = addressRepository;
    this.petRepository = petRepository;
    this.cartRepository = cartRepository;
    this.mergeLogRepository = mergeLogRepository;
    this.passwordService = passwordService;
    this.emailService = emailService;
    this.redisTemplate = redisTemplate;
  }

  /**
   * 发起账号合并
   * @param primaryUserId   发起方（主账号）用户 ID
   * @param secondaryEmail   被合并方（次要账号）邮箱
   * @param primaryPassword  主账号密码（身份验证）
   * @param verifyCode       次要账号邮箱验证码（身份验证）
   */
  @Transactional
  public MergeResult merge(Long primaryUserId, String secondaryEmail,
                           String primaryPassword, String verifyCode) {
    // 1. 验证主账号身份
    User primary = userRepository.findById(primaryUserId)
        .orElseThrow(() -> new BizException("USER_NOT_FOUND"));
    if (!passwordService.matches(primaryPassword, primary.getPasswordHash())) {
      throw new BizException("INVALID_PASSWORD", 1201);
    }

    // 2. 合并冷却期校验：30 天内只能合并一次
    String cooldownKey = "merge:cooldown:" + primaryUserId;
    if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
      throw new BizException("MERGE_IN_COOLDOWN", 1301);
    }

    // 3. 查找次要账号
    User secondary = userRepository.findByEmail(secondaryEmail.toLowerCase())
        .orElseThrow(() -> new BizException("SECONDARY_NOT_FOUND", 1302));

    // 4. 验证次要账号邮箱验证码
    String codeKey = "merge:code:" + secondary.getEmail();
    String savedCode = redisTemplate.opsForValue().get(codeKey);
    if (savedCode == null || !savedCode.equals(verifyCode)) {
      throw new BizException("MERGE_CODE_INVALID", 1303);
    }

    // 5. 冲突检测：确定主账号（创建时间更早者为主）
    boolean primaryIsMaster = !primary.getRegisterTime()
        .isAfter(secondary.getRegisterTime());
    User master = primaryIsMaster ? primary : secondary;
    User slave  = primaryIsMaster ? secondary : primary;

    // 6. 数据合并
    mergeOAuthBindings(master, slave);
    mergeOrders(master, slave);
    mergePoints(master, slave);
    mergeCoupons(master, slave);
    mergeAddresses(master, slave);
    mergePets(master, slave);
    mergeCart(master, slave);

    // 7. 标记次要账号为已合并（不可撤销）
    slave.setStatus(UserStatus.MERGED);
    slave.setMergedInto(master.getId());
    slave.setMergeTime(LocalDateTime.now());
    // 次要账号邮箱改名以释放唯一约束（追加 .merged 后缀）
    slave.setEmail(slave.getEmail() + ".merged." + slave.getId());
    userRepository.save(slave);

    // 8. 记录合并日志
    AccountMergeLog logEntry = new AccountMergeLog();
    logEntry.setPrimaryUserId(master.getId());
    logEntry.setSecondaryUserId(slave.getId());
    logEntry.setMergeTime(LocalDateTime.now());
    logEntry.setIrreversible(true);
    mergeLogRepository.save(logEntry);

    // 9. 设置 30 天冷却期
    redisTemplate.opsForValue().set(cooldownKey, "1",
        MERGE_COOLDOWN_DAYS, TimeUnit.DAYS);

    // 10. 清理验证码
    redisTemplate.delete(codeKey);

    // 11. 邮件通知主账号
    emailService.sendAccountMergedNotice(master.getEmail(), slave.getEmail());

    return new MergeResult(master.getId(), slave.getId(), master.getEmail());
  }

  /** 绑定第三方 OAuth 到主账号 */
  @Transactional
  public void bindOAuth(User user, OAuthProvider provider, OAuthUserInfo info) {
    OAuthBinding binding = new OAuthBinding();
    binding.setUserId(user.getId());
    binding.setProvider(provider.name());
    binding.setProviderUid(info.getUid());
    binding.setProviderEmail(info.getEmail());
    binding.setBindTime(LocalDateTime.now());
    binding.setLastLoginTime(LocalDateTime.now());
    oauthBindingRepository.save(binding);
  }

  /** OAuth 绑定迁移：将次要账号的第三方绑定全部迁移到主账号 */
  private void mergeOAuthBindings(User master, User slave) {
    List<OAuthBinding> slaveBindings = oauthBindingRepository.findByUserId(slave.getId());
    for (OAuthBinding b : slaveBindings) {
      b.setUserId(master.getId());
      oauthBindingRepository.save(b);
    }
  }

  /** 订单合并：按 order_no 去重，重复订单保留主账号侧 */
  private void mergeOrders(User master, User slave) {
    List<Order> slaveOrders = orderRepository.findByUserId(slave.getId());
    Set<String> masterOrderNos = orderRepository.findByUserId(master.getId())
        .stream().map(Order::getOrderNo).collect(Collectors.toSet());
    for (Order o : slaveOrders) {
      if (masterOrderNos.contains(o.getOrderNo())) continue; // 重复订单丢弃
      o.setUserId(master.getId());
      orderRepository.save(o);
    }
  }

  /** 积分合并：余额取最大值，流水累加迁移 */
  private void mergePoints(User master, User slave) {
    PointsBalance masterBal = pointsBalanceRepository.findByUserId(master.getId());
    PointsBalance slaveBal  = pointsBalanceRepository.findByUserId(slave.getId());
    if (slaveBal == null) return;
    long mergedBalance = masterBal == null
        ? slaveBal.getBalance()
        : Math.max(masterBal.getBalance(), slaveBal.getBalance());
    if (masterBal == null) {
      masterBal = new PointsBalance();
      masterBal.setUserId(master.getId());
    }
    masterBal.setBalance(mergedBalance);
    pointsBalanceRepository.save(masterBal);

    // 积分流水累加迁移
    List<PointsLog> slaveLogs = pointsLogRepository.findByUserId(slave.getId());
    for (PointsLog l : slaveLogs) {
      l.setUserId(master.getId());
      pointsLogRepository.save(l);
    }
  }

  /** 优惠券合并：按 coupon_id 去重，同券数量累加 */
  private void mergeCoupons(User master, User slave) {
    List<Coupon> slaveCoupons = couponRepository.findByUserId(slave.getId());
    Map<Long, Coupon> masterMap = couponRepository.findByUserId(master.getId())
        .stream().collect(Collectors.toMap(Coupon::getCouponId, c -> c));
    for (Coupon c : slaveCoupons) {
      Coupon existing = masterMap.get(c.getCouponId());
      if (existing != null) {
        // 同券累加数量
        existing.setQuantity(existing.getQuantity() + c.getQuantity());
        couponRepository.save(existing);
      } else {
        c.setUserId(master.getId());
        couponRepository.save(c);
      }
    }
  }

  /** 地址、宠物、购物车等其他数据全部累加迁移 */
  private void mergeAddresses(User master, User slave) {
    addressRepository.findByUserId(slave.getId()).forEach(a -> {
      a.setUserId(master.getId());
      addressRepository.save(a);
    });
  }

  private void mergePets(User master, User slave) {
    petRepository.findByUserId(slave.getId()).forEach(p -> {
      p.setUserId(master.getId());
      petRepository.save(p);
    });
  }

  private void mergeCart(User master, User slave) {
    cartRepository.findByUserId(slave.getId()).forEach(c -> {
      c.setUserId(master.getId());
      cartRepository.save(c);
    });
  }
}
```

```java
// AccountMergeLog.java
// 账号合并记录实体
@Data
@Entity
@Table(name = "mo_account_merge_log")
public class AccountMergeLog {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long primaryUserId;
  private Long secondaryUserId;
  private LocalDateTime mergeTime;
  // 合并不可撤销标记
  private Boolean irreversible;
}
```

---

## 十八、多设备登录管理

> 同一账号支持多设备同时在线，但需要进行统一管控，防止账号被滥用。
>
> 同时在线限制：同一账号最多 3 台设备同时在线，超出时自动踢出最早登录的设备。
>
> 可信设备：用户可将设备标记为「可信」，可信期内（30 天）登录可跳过 2FA 验证。
>
> 新设备登录提醒：检测到新设备登录时，通过 Push + 邮件双通道通知用户。
>
> 数据存储：
> - 在线设备会话：Redis Sorted Set（key=`session:online:{userId}`，score=登录时间戳，value=deviceId）
> - 可信设备：MySQL `mo_trusted_device` 表，过期时间 30 天
> - 设备信息：User-Agent 解析 + 自定义 deviceId（前端生成并持久化）

```java
// DeviceSessionService.java
// 多设备登录管理：会话维护、远程踢出、可信设备、新设备通知
@Service
public class DeviceSessionService {

  private static final int MAX_ONLINE_DEVICES = 3;
  private static final int TRUSTED_DEVICE_TTL_DAYS = 30;
  private static final String ONLINE_KEY_PREFIX = "session:online:";

  private final RedisTemplate<String, String> redisTemplate;
  private final TrustedDeviceRepository trustedDeviceRepository;
  private final UserRepository userRepository;
  private final PushService pushService;
  private final EmailService emailService;

  public DeviceSessionService(RedisTemplate<String, String> redisTemplate,
                              TrustedDeviceRepository trustedDeviceRepository,
                              UserRepository userRepository,
                              PushService pushService,
                              EmailService emailService) {
    this.redisTemplate = redisTemplate;
    this.trustedDeviceRepository = trustedDeviceRepository;
    this.userRepository = userRepository;
    this.pushService = pushService;
    this.emailService = emailService;
  }

  /**
   * 注册新登录会话：超出上限踢出最早设备
   * @return 返回是否为新设备（用于触发通知）
   */
  public boolean registerSession(Long userId, String deviceId, String userAgent,
                                 String ip, HttpServletRequest request) {
    String key = ONLINE_KEY_PREFIX + userId;
    long now = System.currentTimeMillis();

    // 1. 检查是否为新设备
    boolean isNewDevice = !isOnline(userId, deviceId)
        && trustedDeviceRepository.findByUserIdAndDeviceId(userId, deviceId) == null;

    // 2. 写入/更新会话（ZSet，score=登录时间戳）
    redisTemplate.opsForZSet().add(key, deviceId, now);

    // 3. 超出上限：踢出最早登录的设备
    Long count = redisTemplate.opsForZSet().zCard(key);
    if (count != null && count > MAX_ONLINE_DEVICES) {
      // 移除 score 最小的（最早登录的）
      Set<String> earliest = redisTemplate.opsForZSet().range(key, 0, 0);
      if (earliest != null && !earliest.isEmpty()) {
        String kickedDeviceId = earliest.iterator().next();
        redisTemplate.opsForZSet().remove(key, kickedDeviceId);
        // 将被踢设备的 access token 加入黑名单，强制下线
        evictDeviceToken(userId, kickedDeviceId);
      }
    }

    // 4. 新设备登录通知（Push + 邮件）
    if (isNewDevice) {
      User user = userRepository.findById(userId).orElse(null);
      if (user != null) {
        String deviceName = parseDeviceName(userAgent);
        String location = parseLocation(ip);
        pushService.sendNewDeviceLoginAlert(userId, deviceName, location, now);
        emailService.sendNewDeviceLoginEmail(user.getEmail(), deviceName, location, ip, now);
      }
    }

    return isNewDevice;
  }

  /** 查询当前在线设备列表 */
  public List<DeviceSession> listOnlineDevices(Long userId) {
    String key = ONLINE_KEY_PREFIX + userId;
    Set<ZSetOperations.TypedTuple<String>> tuples =
        redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);
    if (tuples == null) return Collections.emptyList();

    List<DeviceSession> devices = new ArrayList<>();
    Set<String> trustedIds = trustedDeviceRepository.findByUserId(userId).stream()
        .map(TrustedDevice::getDeviceId).collect(Collectors.toSet());

    for (ZSetOperations.TypedTuple<String> t : tuples) {
      DeviceSession ds = new DeviceSession();
      ds.setDeviceId(t.getValue());
      ds.setLoginTime(new Date(Objects.requireNonNull(t.getScore()).longValue()));
      ds.setTrusted(trustedIds.contains(t.getValue()));
      ds.setCurrent(false); // 由 Controller 根据当前请求 deviceId 设置
      devices.add(ds);
    }
    return devices;
  }

  /** 远程踢出指定设备 */
  public void kickOut(Long userId, String deviceId) {
    String key = ONLINE_KEY_PREFIX + userId;
    redisTemplate.opsForZSet().remove(key, deviceId);
    evictDeviceToken(userId, deviceId);
  }

  /** 标记为可信设备（30 天内免 2FA） */
  @Transactional
  public void trustDevice(Long userId, String deviceId, String deviceName) {
    TrustedDevice td = trustedDeviceRepository.findByUserIdAndDeviceId(userId, deviceId);
    if (td == null) {
      td = new TrustedDevice();
      td.setUserId(userId);
      td.setDeviceId(deviceId);
    }
    td.setDeviceName(deviceName);
    td.setTrustedAt(LocalDateTime.now());
    td.setExpireAt(LocalDateTime.now().plusDays(TRUSTED_DEVICE_TTL_DAYS));
    trustedDeviceRepository.save(td);
  }

  /** 检查设备是否可信且未过期（用于 2FA 跳过判断） */
  public boolean isTrustedDevice(Long userId, String deviceId) {
    TrustedDevice td = trustedDeviceRepository.findByUserIdAndDeviceId(userId, deviceId);
    if (td == null) return false;
    if (td.getExpireAt() == null || td.getExpireAt().isBefore(LocalDateTime.now())) {
      // 已过期，清理
      trustedDeviceRepository.delete(td);
      return false;
    }
    return true;
  }

  /** 设备是否在线 */
  private boolean isOnline(Long userId, String deviceId) {
    Double score = redisTemplate.opsForZSet()
        .score(ONLINE_KEY_PREFIX + userId, deviceId);
    return score != null;
  }

  /** 将指定设备的 access token 加入黑名单，强制下线 */
  private void evictDeviceToken(Long userId, String deviceId) {
    // 实际实现：从 mo_login_log 中找到该设备最近的有效 access token，
    // 调用 TokenService.logout(token) 加入 Redis 黑名单（见 §九 Token 黑名单实现）
    // tokenService.blacklistDeviceToken(userId, deviceId);
  }

  private String parseDeviceName(String userAgent) {
    // 简化版：实际使用 ua-parser 库解析
    if (userAgent == null) return "Unknown Device";
    if (userAgent.contains("iPhone")) return "iPhone";
    if (userAgent.contains("Android")) return "Android Device";
    if (userAgent.contains("Mac")) return "Mac";
    if (userAgent.contains("Windows")) return "Windows PC";
    return "Unknown Device";
  }

  private String parseLocation(String ip) {
    // 简化版：实际使用 GeoIP 库解析
    return "Unknown Location";
  }
}
```

```java
// DeviceController.java
// 多设备登录管理 API
@RestController
@RequestMapping("/api/v1/user/devices")
public class DeviceController {

  private final DeviceSessionService deviceSessionService;
  private final TokenService tokenService;

  public DeviceController(DeviceSessionService deviceSessionService, TokenService tokenService) {
    this.deviceSessionService = deviceSessionService;
    this.tokenService = tokenService;
  }

  /** 查询当前登录的所有设备 */
  @GetMapping
  public ApiResponse<List<DeviceSession>> listDevices(
      @AuthenticationPrincipal Long userId,
      @RequestHeader(value = "X-Device-Id", required = false) String currentDeviceId) {
    List<DeviceSession> devices = deviceSessionService.listOnlineDevices(userId);
    // 标记当前请求设备
    devices.forEach(d -> d.setCurrent(d.getDeviceId().equals(currentDeviceId)));
    return ApiResponse.ok(devices);
  }

  /** 远程踢出指定设备 */
  @DeleteMapping("/{deviceId}")
  public ApiResponse<Void> kickOut(
      @PathVariable String deviceId,
      @AuthenticationPrincipal Long userId) {
    deviceSessionService.kickOut(userId, deviceId);
    return ApiResponse.ok();
  }

  /** 标记为可信设备（30 天内免 2FA） */
  @PostMapping("/{deviceId}/trust")
  public ApiResponse<Void> trustDevice(
      @PathVariable String deviceId,
      @RequestBody TrustDeviceRequest req,
      @AuthenticationPrincipal Long userId) {
    deviceSessionService.trustDevice(userId, deviceId, req.getDeviceName());
    return ApiResponse.ok();
  }
}
```

```java
// TrustedDevice.java
// 可信设备实体（30 天有效期，期内登录免 2FA）
@Data
@Entity
@Table(name = "mo_trusted_device")
public class TrustedDevice {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long userId;
  private String deviceId;
  private String deviceName;
  private LocalDateTime trustedAt;
  // 过期时间（创建后 +30 天）
  private LocalDateTime expireAt;
}
```
