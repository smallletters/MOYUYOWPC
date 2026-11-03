#import "PushModule.h"
#import <UIKit/UIKit.h>
#import <UserNotifications/UserNotifications.h>
#import <objc/runtime.h>

static NSString *const kMOYUYOPushTokenKey = @"moyuyo_push_token";
static NSString *const kMOYUYOPushEnabledKey = @"moyuyo_push_enabled";

/// AppDelegate 原始实现的函数指针（用于 hook 后调用链）
static IMP gOriginalDidRegisterIMP = NULL;
static IMP gOriginalDidFailIMP = NULL;
static BOOL gHookInstalled = NO;

/// 拦截 didRegisterForRemoteNotificationsWithDeviceToken，保存 APNs deviceToken
static void MOYUYOPushDidRegister(id self, SEL _cmd, UIApplication *application, NSData *deviceToken) {
    [PushModule storeToken:deviceToken];
    if (gOriginalDidRegisterIMP) {
        ((void (*)(id, SEL, UIApplication *, NSData *))gOriginalDidRegisterIMP)(self, _cmd, application, deviceToken);
    }
}

/// 拦截 didFailToRegisterForRemoteNotificationsWithError
static void MOYUYOPushDidFail(id self, SEL _cmd, UIApplication *application, NSError *error) {
    if (gOriginalDidFailIMP) {
        ((void (*)(id, SEL, UIApplication *, NSError *))gOriginalDidFailIMP)(self, _cmd, application, error);
    }
}

@interface PushModule (MOYUYOPushInternal)
+ (NSString *)cachedToken;
+ (void)storeToken:(NSData *)deviceToken;
+ (void)installAppDelegateHook;
@end

@implementation PushModule

UNI_EXPORT_METHOD_SYNC(register, ^(NSDictionary *params, UniJSCallback callback) {
    [[NSUserDefaults standardUserDefaults] setBool:YES forKey:kMOYUYOPushEnabledKey];
    [PushModule installAppDelegateHook];

    UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];
    UNAuthorizationOptions options = UNAuthorizationOptionAlert | UNAuthorizationOptionBadge | UNAuthorizationOptionSound;
    [center requestAuthorizationWithOptions:options
                          completionHandler:^(BOOL granted, NSError * _Nullable error) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (granted) {
                [[UIApplication sharedApplication] registerForRemoteNotifications];
            }
            NSString *cached = [PushModule cachedToken];
            // deviceToken 回调是异步的：首次注册最多等 1.5 秒，避免每次都返回空
            if (cached.length > 0 || !granted) {
                callback(@{@"success": @YES, @"token": cached ?: @"", @"granted": @(granted)});
                return;
            }
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.5 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                callback(@{@"success": @YES, @"token": [PushModule cachedToken] ?: @"", @"granted": @(granted)});
            });
        });
    }];
})

UNI_EXPORT_METHOD_SYNC(getToken, ^(NSDictionary *params, UniJSCallback callback) {
    NSString *token = [PushModule cachedToken];
    callback(@{@"token": token ?: @""});
})

UNI_EXPORT_METHOD_SYNC(handleMessage, ^(NSDictionary *params, UniJSCallback callback) {
    // 前台展示由 UNUserNotificationCenter 控制，这里仅确认收到
    callback(@{@"success": @YES});
})

UNI_EXPORT_METHOD_SYNC(setBadge, ^(NSDictionary *params, UniJSCallback callback) {
    NSInteger count = [params[@"count"] integerValue];
    [UIApplication sharedApplication].applicationIconBadgeNumber = count;
    callback(@{@"success": @YES});
})

UNI_EXPORT_METHOD_SYNC(clearBadge, ^(NSDictionary *params, UniJSCallback callback) {
    [UIApplication sharedApplication].applicationIconBadgeNumber = 0;
    callback(@{@"success": @YES});
})

UNI_EXPORT_METHOD_SYNC(setEnabled, ^(NSDictionary *params, UniJSCallback callback) {
    BOOL enabled = [params[@"enabled"] boolValue];
    [[NSUserDefaults standardUserDefaults] setBool:enabled forKey:kMOYUYOPushEnabledKey];
    if (enabled) {
        [PushModule installAppDelegateHook];
        [[UIApplication sharedApplication] registerForRemoteNotifications];
    } else {
        [[UIApplication sharedApplication] unregisterForRemoteNotifications];
    }
    callback(@{@"success": @YES});
})

UNI_EXPORT_METHOD_SYNC(isEnabled, ^(NSDictionary *params, UniJSCallback callback) {
    NSNumber *value = [[NSUserDefaults standardUserDefaults] objectForKey:kMOYUYOPushEnabledKey];
    BOOL enabled = value == nil ? YES : value.boolValue;
    callback(@{@"enabled": @(enabled)});
})

#pragma mark - 内部实现

/// 读取缓存的 APNs deviceToken
+ (NSString *)cachedToken {
    NSString *token = [[NSUserDefaults standardUserDefaults] stringForKey:kMOYUYOPushTokenKey];
    return token ?: @"";
}

/// 把 NSData 形式的 deviceToken 转为十六进制字符串并持久化
+ (void)storeToken:(NSData *)deviceToken {
    if (deviceToken.length == 0) {
        return;
    }
    NSString *raw = deviceToken.description;
    NSString *token = [raw stringByReplacingOccurrencesOfString:@"<" withString:@""];
    token = [token stringByReplacingOccurrencesOfString:@">" withString:@""];
    token = [token stringByReplacingOccurrencesOfString:@" " withString:@""];
    if (token.length == 0) {
        return;
    }
    [[NSUserDefaults standardUserDefaults] setObject:token forKey:kMOYUYOPushTokenKey];
    [[NSUserDefaults standardUserDefaults] synchronize];
}

/// 替换 AppDelegate 的远程通知回调，拿到 deviceToken（只执行一次）
+ (void)installAppDelegateHook {
    if (gHookInstalled) {
        return;
    }
    id<UIApplicationDelegate> delegate = [UIApplication sharedApplication].delegate;
    Class cls = object_getClass(delegate);
    if (!cls) {
        return;
    }

    SEL registerSel = @selector(application:didRegisterForRemoteNotificationsWithDeviceToken:);
    Method originalRegister = class_getInstanceMethod(cls, registerSel);
    gOriginalDidRegisterIMP = originalRegister ? method_getImplementation(originalRegister) : NULL;
    class_replaceMethod(cls, registerSel, (IMP)MOYUYOPushDidRegister,
                        originalRegister ? method_getTypeEncoding(originalRegister) : "v@:@@");

    SEL failSel = @selector(application:didFailToRegisterForRemoteNotificationsWithError:);
    Method originalFail = class_getInstanceMethod(cls, failSel);
    gOriginalDidFailIMP = originalFail ? method_getImplementation(originalFail) : NULL;
    class_replaceMethod(cls, failSel, (IMP)MOYUYOPushDidFail,
                        originalFail ? method_getTypeEncoding(originalFail) : "v@:@@");

    gHookInstalled = YES;
}

@end
