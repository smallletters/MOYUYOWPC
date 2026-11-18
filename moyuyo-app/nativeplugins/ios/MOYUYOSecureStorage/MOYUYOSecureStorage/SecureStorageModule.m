#import "SecureStorageModule.h"
#import <Security/Security.h>

/// Keychain service 标识：与 App Bundle ID 绑定，重装/升级不会变化
static NSString *const kSecureService = @"com.moyuyoshop.app.secure";
/// 安装标记：Keychain 条目在卸载后默认保留，靠该标记判断是否为新装并清空残留
static NSString *const kInstalledFlagKey = @"moyuyo_secure_installed_v1";

#pragma mark - Keychain 基础操作

static NSMutableDictionary *MYBaseQuery(NSString * _Nullable account) {
  NSMutableDictionary *query = [NSMutableDictionary dictionary];
  query[(__bridge id)kSecClass] = (__bridge id)kSecClassGenericPassword;
  query[(__bridge id)kSecAttrService] = kSecureService;
  if (account.length > 0) {
    query[(__bridge id)kSecAttrAccount] = account;
  }
  return query;
}

/// 首次启动（未写入安装标记）时清空 Keychain 残留：
/// iOS 卸载 App 不会删除 Keychain 条目，否则重装后会读到上一份已失效的 refreshToken
static void MYEnsureFreshInstall(void) {
  NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
  if ([defaults boolForKey:kInstalledFlagKey]) {
    return;
  }
  SecItemDelete((__bridge CFDictionaryRef)MYBaseQuery(nil));
  [defaults setBool:YES forKey:kInstalledFlagKey];
  [defaults synchronize];
}

static OSStatus MYSetItem(NSString *account, NSString *value) {
  // 先删后写，保证覆盖语义（Keychain 不支持原地覆盖同 account 条目）
  SecItemDelete((__bridge CFDictionaryRef)MYBaseQuery(account));
  NSData *data = [value dataUsingEncoding:NSUTF8StringEncoding];
  if (data == nil) {
    return errSecParam;
  }
  NSMutableDictionary *query = MYBaseQuery(account);
  query[(__bridge id)kSecValueData] = data;
  // AfterFirstUnlockThisDeviceOnly：不随 iCloud/iTunes 备份迁移，仅本机可解
  query[(__bridge id)kSecAttrAccessible] =
      (__bridge id)kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly;
  return SecItemAdd((__bridge CFDictionaryRef)query, NULL);
}

static NSString * _Nullable MYGetItem(NSString *account) {
  NSMutableDictionary *query = MYBaseQuery(account);
  query[(__bridge id)kSecReturnData] = @YES;
  query[(__bridge id)kSecMatchLimit] = (__bridge id)kSecMatchLimitOne;
  CFTypeRef result = NULL;
  OSStatus status = SecItemCopyMatching((__bridge CFDictionaryRef)query, &result);
  if (status != errSecSuccess || result == NULL) {
    return nil;
  }
  NSData *data = (__bridge_transfer NSData *)result;
  return [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
}

static BOOL MYRemoveItem(NSString * _Nullable account) {
  OSStatus status = SecItemDelete((__bridge CFDictionaryRef)MYBaseQuery(account));
  return status == errSecSuccess || status == errSecItemNotFound;
}

static NSDictionary *MYResult(BOOL success, NSString *message) {
  return @{@"success": @(success), @"message": message ?: @""};
}

@implementation SecureStorageModule

UNI_EXPORT_METHOD(@selector(setItem:callback:))

- (void)setItem:(NSDictionary *)params callback:(UniModuleKeepAliveCallback)callback {
  MYEnsureFreshInstall();
  NSString *key = params[@"key"];
  NSString *value = params[@"value"];
  if (key.length == 0 || value == nil) {
    callback(MYResult(NO, @"key/value 不能为空"), NO);
    return;
  }
  OSStatus status = MYSetItem(key, value);
  callback(MYResult(status == errSecSuccess, [NSString stringWithFormat:@"status=%d", (int)status]), NO);
}

UNI_EXPORT_METHOD(@selector(getItem:callback:))

- (void)getItem:(NSDictionary *)params callback:(UniModuleKeepAliveCallback)callback {
  MYEnsureFreshInstall();
  NSString *key = params[@"key"];
  if (key.length == 0) {
    callback(MYResult(NO, @"key 不能为空"), NO);
    return;
  }
  NSString *value = MYGetItem(key);
  callback(@{@"success": @YES, @"value": value ?: @""}, NO);
}

UNI_EXPORT_METHOD(@selector(removeItem:callback:))

- (void)removeItem:(NSDictionary *)params callback:(UniModuleKeepAliveCallback)callback {
  MYEnsureFreshInstall();
  NSString *key = params[@"key"];
  if (key.length == 0) {
    callback(MYResult(NO, @"key 不能为空"), NO);
    return;
  }
  callback(MYResult(MYRemoveItem(key), @""), NO);
}

UNI_EXPORT_METHOD(@selector(clear:callback:))

- (void)clear:(NSDictionary *)params callback:(UniModuleKeepAliveCallback)callback {
  MYEnsureFreshInstall();
  callback(MYResult(MYRemoveItem(nil), @""), NO);
}

/// 能力探测：前端据此决定是否启用加密链路
UNI_EXPORT_METHOD(@selector(isAvailable:callback:))

- (void)isAvailable:(NSDictionary *)params callback:(UniModuleKeepAliveCallback)callback {
  MYEnsureFreshInstall();
  callback(@{@"available": @YES, @"backend": @"Keychain"}, NO);
}

@end
