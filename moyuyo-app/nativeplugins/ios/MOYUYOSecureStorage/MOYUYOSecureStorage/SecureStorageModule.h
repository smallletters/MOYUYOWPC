#import <Foundation/Foundation.h>
#import <DCUniModule/DCUniBaseModule.h>

NS_ASSUME_NONNULL_BEGIN

/// 加密存储原生插件（Keychain）
/// 仅用于存放长效凭证（refreshToken），不覆盖业务普通缓存
@interface SecureStorageModule : DCUniBaseModule
@end

NS_ASSUME_NONNULL_END
