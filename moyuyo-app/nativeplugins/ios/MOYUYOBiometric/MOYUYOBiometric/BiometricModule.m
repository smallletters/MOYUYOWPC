#import "BiometricModule.h"
#import <LocalAuthentication/LocalAuthentication.h>

@implementation BiometricModule

UNI_EXPORT_METHOD(@selector(isAvailable:callback:))

- (void)isAvailable:(NSDictionary *)params callback:(UniModuleKeepAliveCallback)callback {
    LAContext *context = [[LAContext alloc] init];
    NSError *error = nil;
    BOOL available = [context canEvaluatePolicy:LAPolicyDeviceOwnerAuthenticationWithBiometrics error:&error];

    NSString *type = @"NONE";
    if (available) {
        if (@available(iOS 11.0, *)) {
            type = context.biometryType == LABiometryTypeFaceID ? @"FACE_ID" : @"TOUCH_ID";
        } else {
            type = @"TOUCH_ID";
        }
    }
    callback(@{@"available": @(available), @"biometricType": type}, NO);
}

UNI_EXPORT_METHOD(@selector(authenticate:callback:))

- (void)authenticate:(NSDictionary *)params callback:(UniModuleKeepAliveCallback)callback {
    LAContext *context = [[LAContext alloc] init];
    NSString *reason = params[@"reason"] ?: @"验证身份以继续";

    [context evaluatePolicy:LAPolicyDeviceOwnerAuthenticationWithBiometrics
        localizedReason:reason
        reply:^(BOOL success, NSError * _Nullable error) {
            if (success) {
                callback(@{@"success": @YES}, NO);
            } else {
                callback(@{@"success": @NO, @"message": error.localizedDescription ?: @"身份验证失败"}, NO);
            }
        }];
}

UNI_EXPORT_METHOD(@selector(isEnrolled:callback:))

- (void)isEnrolled:(NSDictionary *)params callback:(UniModuleKeepAliveCallback)callback {
    LAContext *context = [[LAContext alloc] init];
    NSError *error = nil;
    BOOL available = [context canEvaluatePolicy:LAPolicyDeviceOwnerAuthenticationWithBiometrics error:&error];
    callback(@{@"enrolled": @(available && error == nil)}, NO);
}

@end
