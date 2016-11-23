/*
 Copyright 2009-2011 Urban Airship Inc. All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:

 1. Redistributions of source code must retain the above copyright notice, this
 list of conditions and the following disclaimer.

 2. Redistributions in binaryform must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided withthe distribution.

 THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#import "PushPlugin.h"

@implementation PushPlugin

@synthesize notificationMessage;
@synthesize isInline;

@synthesize callbackId;
@synthesize notificationCallbackId;
@synthesize callback;


- (void)unregister:(CDVInvokedUrlCommand*)command;
{
  self.callbackId = command.callbackId;
  
  [[UIApplication sharedApplication] unregisterForRemoteNotifications];
  [self successWithMessage:@"unregistered"];
}

- (void)areNotificationsEnabled:(CDVInvokedUrlCommand*)command;
{
  self.callbackId = command.callbackId;
  BOOL registered;
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
  if ([[UIApplication sharedApplication] respondsToSelector:@selector(isRegisteredForRemoteNotifications)]) {
    registered = [[UIApplication sharedApplication] isRegisteredForRemoteNotifications];
  } else {
    UIRemoteNotificationType types = [[UIApplication sharedApplication] enabledRemoteNotificationTypes];
    registered = types != UIRemoteNotificationTypeNone;
  }
#else
  UIRemoteNotificationType types = [[UIApplication sharedApplication] enabledRemoteNotificationTypes];
  registered = types != UIRemoteNotificationTypeNone;
#endif
  CDVPluginResult *commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:registered];
  [self.commandDelegate sendPluginResult:commandResult callbackId:self.callbackId];
}

- (void)registerUserNotificationSettings:(CDVInvokedUrlCommand*)command;
{
  self.callbackId = command.callbackId;
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
    if (![[UIApplication sharedApplication]respondsToSelector:@selector(registerUserNotificationSettings:)]) {
        [self successWithMessage:[NSString stringWithFormat:@"%@", @"user notifications not supported for this ios version."]];
        return;
    }
    
  NSDictionary *options = [command.arguments objectAtIndex:0];
  NSArray *categories = [options objectForKey:@"categories"];
  if (categories == nil) {
    [self failWithMessage:@"No categories specified" withError:nil];
    return;
  }
  NSMutableArray *nsCategories = [[NSMutableArray alloc] initWithCapacity:[categories count]];

  for (NSDictionary *category in categories) {
    // ** 1. create the actions for this category
    NSMutableArray *nsActionsForDefaultContext = [[NSMutableArray alloc] initWithCapacity:4];
    NSArray *actionsForDefaultContext = [category objectForKey:@"actionsForDefaultContext"];
    if (actionsForDefaultContext == nil) {
      [self failWithMessage:@"Category doesn't contain actionsForDefaultContext" withError:nil];
      return;
    }
    if (![self createNotificationAction:category actions:actionsForDefaultContext nsActions:nsActionsForDefaultContext]) {
      return;
    }

    NSMutableArray *nsActionsForMinimalContext = [[NSMutableArray alloc] initWithCapacity:2];
    NSArray *actionsForMinimalContext = [category objectForKey:@"actionsForMinimalContext"];
    if (actionsForMinimalContext == nil) {
      [self failWithMessage:@"Category doesn't contain actionsForMinimalContext" withError:nil];
      return;
    }
    if (![self createNotificationAction:category actions:actionsForMinimalContext nsActions:nsActionsForMinimalContext]) {
      return;
    }

    // ** 2. create the category
    UIMutableUserNotificationCategory *nsCategory = [[UIMutableUserNotificationCategory alloc] init];
    // Identifier to include in your push payload and local notification
    NSString *identifier = [category objectForKey:@"identifier"];
    if (identifier == nil) {
      [self failWithMessage:@"Category doesn't contain identifier" withError:nil];
      return;
    }
    nsCategory.identifier = identifier;
    // Add the actions to the category and set the action context
    [nsCategory setActions:nsActionsForDefaultContext forContext:UIUserNotificationActionContextDefault];
    // Set the actions to present in a minimal context
    [nsCategory setActions:nsActionsForMinimalContext forContext:UIUserNotificationActionContextMinimal];
    [nsCategories addObject:nsCategory];
  }

  // ** 3. Determine the notification types
  NSArray *types = [options objectForKey:@"types"];
  if (types == nil) {
    [self failWithMessage:@"No types specified" withError:nil];
    return;
  }
  UIUserNotificationType nsTypes = UIUserNotificationTypeNone;
  for (NSString *type in types) {
    if ([type isEqualToString:@"badge"]) {
      nsTypes |= UIUserNotificationTypeBadge;
    } else if ([type isEqualToString:@"alert"]) {
      nsTypes |= UIUserNotificationTypeAlert;
    } else if ([type isEqualToString:@"sound"]) {
      nsTypes |= UIUserNotificationTypeSound;
    } else {
      [self failWithMessage:[NSString stringWithFormat:@"Unsupported type: %@, use one of badge, alert, sound", type] withError:nil];
    }
  }

  // ** 4. Register the notification categories
  NSSet *nsCategorySet = [NSSet setWithArray:nsCategories];
  UIUserNotificationSettings *settings = [UIUserNotificationSettings settingsForTypes:nsTypes categories:nsCategorySet];
  [[UIApplication sharedApplication] registerUserNotificationSettings:settings];
#endif
  [self successWithMessage:[NSString stringWithFormat:@"%@", @"user notifications registered"]];
}

#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
- (BOOL)createNotificationAction:(NSDictionary *)category
                         actions:(NSArray *) actions
                       nsActions:(NSMutableArray *)nsActions
{
  for (NSDictionary *action in actions) {
    UIMutableUserNotificationAction *nsAction = [[UIMutableUserNotificationAction alloc] init];
    // Define an ID string to be passed back to your app when you handle the action
    NSString *identifier = [action objectForKey:@"identifier"];
    if (identifier == nil) {
      [self failWithMessage:@"Action doesn't contain identifier" withError:nil];
      return NO;
    }
    nsAction.identifier = identifier;
    // Localized text displayed in the action button
    NSString *title = [action objectForKey:@"title"];
    if (title == nil) {
      [self failWithMessage:@"Action doesn't contain title" withError:nil];
      return NO;
    }
    nsAction.title = title;
    // If you need to show UI, choose foreground (background gives your app a few seconds to run)
    BOOL isForeground = [@"foreground" isEqualToString:[action objectForKey:@"activationMode"]];
    nsAction.activationMode = isForeground ? UIUserNotificationActivationModeForeground : UIUserNotificationActivationModeBackground;
    // Destructive actions display in red
    BOOL isDestructive = [[action objectForKey:@"destructive"] isEqual:[NSNumber numberWithBool:YES]];
    nsAction.destructive = isDestructive;
    // Set whether the action requires the user to authenticate
    BOOL isAuthRequired = [[action objectForKey:@"authenticationRequired"] isEqual:[NSNumber numberWithBool:YES]];
    nsAction.authenticationRequired = isAuthRequired;
    [nsActions addObject:nsAction];
  }
  return YES;
}
#endif

- (void)register:(CDVInvokedUrlCommand*)command;
{
  self.callbackId = command.callbackId;
  
  NSMutableDictionary* options = [command.arguments objectAtIndex:0];
  
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
    UIUserNotificationType UserNotificationTypes = UIUserNotificationTypeNone;
#endif
  UIRemoteNotificationType notificationTypes = UIRemoteNotificationTypeNone;
  
  id badgeArg = [options objectForKey:@"badge"];
  id soundArg = [options objectForKey:@"sound"];
  id alertArg = [options objectForKey:@"alert"];
  
  if ([badgeArg isKindOfClass:[NSString class]])
  {
    if ([badgeArg isEqualToString:@"true"]) {
      notificationTypes |= UIRemoteNotificationTypeBadge;
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
      UserNotificationTypes |= UIUserNotificationTypeBadge;
#endif
    }
  }
  else if ([badgeArg boolValue]) {
    notificationTypes |= UIRemoteNotificationTypeBadge;
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
    UserNotificationTypes |= UIUserNotificationTypeBadge;
#endif
  }
  
  if ([soundArg isKindOfClass:[NSString class]])
  {
    if ([soundArg isEqualToString:@"true"]) {
      notificationTypes |= UIRemoteNotificationTypeSound;
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
      UserNotificationTypes |= UIUserNotificationTypeSound;
#endif
    }
  }
  else if ([soundArg boolValue]) {
    notificationTypes |= UIRemoteNotificationTypeSound;
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
    UserNotificationTypes |= UIUserNotificationTypeSound;
#endif
  }
  
  if ([alertArg isKindOfClass:[NSString class]])
  {
    if ([alertArg isEqualToString:@"true"]) {
      notificationTypes |= UIRemoteNotificationTypeAlert;
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
      UserNotificationTypes |= UIUserNotificationTypeAlert;
#endif
    }
  }
  else if ([alertArg boolValue]) {
    notificationTypes |= UIRemoteNotificationTypeAlert;
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
    UserNotificationTypes |= UIUserNotificationTypeAlert;
#endif
  }
  
  notificationTypes |= UIRemoteNotificationTypeNewsstandContentAvailability;
//#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
//  UserNotificationTypes |= UIUserNotificationActivationModeBackground;
//#endif
  
  self.callback = [options objectForKey:@"ecb"];
  
  if (notificationTypes == UIRemoteNotificationTypeNone)
    NSLog(@"PushPlugin.register: Push notification type is set to none");
  
  isInline = NO;
  
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
  if ([[UIApplication sharedApplication]respondsToSelector:@selector(registerUserNotificationSettings:)]) {
    UIUserNotificationSettings *settings = [UIUserNotificationSettings settingsForTypes:UserNotificationTypes categories:nil];
    [[UIApplication sharedApplication] registerUserNotificationSettings:settings];
    [[UIApplication sharedApplication] registerForRemoteNotifications];
  } else {
    [[UIApplication sharedApplication] registerForRemoteNotificationTypes:notificationTypes];
  }
#else
    [[UIApplication sharedApplication] registerForRemoteNotificationTypes:notificationTypes];
#endif
  
  if (notificationMessage)      // if there is a pending startup notification
    [self notificationReceived];  // go ahead and process it
}

/*
 - (void)isEnabled:(NSMutableArray *)arguments withDict:(NSMutableDictionary *)options {
 UIRemoteNotificationType type = [[UIApplication sharedApplication] enabledRemoteNotificationTypes];
 NSString *jsStatement = [NSString stringWithFormat:@"navigator.PushPlugin.isEnabled = %d;", type != UIRemoteNotificationTypeNone];
 NSLog(@"JSStatement %@",jsStatement);
 }
 */

- (void)didRegisterForRemoteNotificationsWithDeviceToken:(NSData *)deviceToken {
  
  NSMutableDictionary *results = [NSMutableDictionary dictionary];
  NSString *token = [[[[deviceToken description] stringByReplacingOccurrencesOfString:@"<"withString:@""]
                      stringByReplacingOccurrencesOfString:@">" withString:@""]
                     stringByReplacingOccurrencesOfString: @" " withString: @""];
  [results setValue:token forKey:@"deviceToken"];
  
#if !TARGET_IPHONE_SIMULATOR
  // Get Bundle Info for Remote Registration (handy if you have more than one app)
  [results setValue:[[[NSBundle mainBundle] infoDictionary] objectForKey:@"CFBundleDisplayName"] forKey:@"appName"];
  [results setValue:[[[NSBundle mainBundle] infoDictionary] objectForKey:@"CFBundleVersion"] forKey:@"appVersion"];
  
  // Check what Notifications the user has turned on.  We registered for all three, but they may have manually disabled some or all of them.
  NSUInteger rntypes;
  #if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
    if([UIUserNotificationSettings class]){
      rntypes = [[[UIApplication sharedApplication] currentUserNotificationSettings] types];
    } else {
        rntypes = [[UIApplication sharedApplication] enabledRemoteNotificationTypes];
    }
  #else
      rntypes = [[UIApplication sharedApplication] enabledRemoteNotificationTypes]; 
  #endif
  
  // Set the defaults to disabled unless we find otherwise...
  NSString *pushBadge = @"disabled";
  NSString *pushAlert = @"disabled";
  NSString *pushSound = @"disabled";
  
  // Check what Registered Types are turned on. This is a bit tricky since if two are enabled, and one is off, it will return a number 2... not telling you which
  // one is actually disabled. So we are literally checking to see if rnTypes matches what is turned on, instead of by number. The "tricky" part is that the
  // single notification types will only match if they are the ONLY one enabled.  Likewise, when we are checking for a pair of notifications, it will only be
  // true if those two notifications are on.  This is why the code is written this way
  if(rntypes & UIRemoteNotificationTypeBadge){
    pushBadge = @"enabled";
  }
  if(rntypes & UIRemoteNotificationTypeAlert) {
    pushAlert = @"enabled";
  }
  if(rntypes & UIRemoteNotificationTypeSound) {
    pushSound = @"enabled";
  }
  
  [results setValue:pushBadge forKey:@"pushBadge"];
  [results setValue:pushAlert forKey:@"pushAlert"];
  [results setValue:pushSound forKey:@"pushSound"];
  
  // Get the users Device Model, Display Name, Token & Version Number
  UIDevice *dev = [UIDevice currentDevice];
  [results setValue:dev.name forKey:@"deviceName"];
  [results setValue:dev.model forKey:@"deviceModel"];
  [results setValue:dev.systemVersion forKey:@"deviceSystemVersion"];
  
    [self successWithMessage:[NSString stringWithFormat:@"%@", token]];
#endif
}

- (void)didFailToRegisterForRemoteNotificationsWithError:(NSError *)error
{
  [self failWithMessage:@"" withError:error];
}

- (void)notificationReceived {
  NSLog(@"Notification received");
  
  if (notificationMessage && self.callback)
  {
    NSMutableString *jsonStr = [NSMutableString stringWithString:@"{"];
    
    [self parseDictionary:notificationMessage intoJSON:jsonStr];
    
    if (isInline)
    {
      [jsonStr appendFormat:@"foreground:\"%d\"", 1];
      isInline = NO;
    }
    else
      [jsonStr appendFormat:@"foreground:\"%d\"", 0];
    
    [jsonStr appendString:@"}"];
    
    NSLog(@"Msg: %@", jsonStr);
    
    NSString * jsCallBack = [NSString stringWithFormat:@"%@(%@);", self.callback, jsonStr];

    if ([self.webView respondsToSelector:@selector(stringByEvaluatingJavaScriptFromString:)]) {
        // Cordova-iOS pre-4
        [self.webView performSelectorOnMainThread:@selector(stringByEvaluatingJavaScriptFromString:) withObject:jsCallBack waitUntilDone:NO];
    } else {
        // Cordova-iOS 4+
        [self.webView performSelectorOnMainThread:@selector(evaluateJavaScript:completionHandler:) withObject:jsCallBack waitUntilDone:NO];
    }

    self.notificationMessage = nil;
  }
}

// reentrant method to drill down and surface all sub-dictionaries' key/value pairs into the top level json
-(void)parseDictionary:(NSDictionary *)inDictionary intoJSON:(NSMutableString *)jsonString
{
  NSArray         *keys = [inDictionary allKeys];
  NSString        *key;
  
  for (key in keys)
  {
    id thisObject = [inDictionary objectForKey:key];
    
    if ([thisObject isKindOfClass:[NSDictionary class]])
      [self parseDictionary:thisObject intoJSON:jsonString];
    else if ([thisObject isKindOfClass:[NSString class]])
      [jsonString appendFormat:@"\"%@\":\"%@\",",
       key,
       [[[[inDictionary objectForKey:key]
          stringByReplacingOccurrencesOfString:@"\\" withString:@"\\\\"]
         stringByReplacingOccurrencesOfString:@"\"" withString:@"\\\""]
        stringByReplacingOccurrencesOfString:@"\n" withString:@"\\n"]];
    else {
      [jsonString appendFormat:@"\"%@\":\"%@\",", key, [inDictionary objectForKey:key]];
    }
  }
}

- (void)setApplicationIconBadgeNumber:(CDVInvokedUrlCommand *)command {
  
  self.callbackId = command.callbackId;
  
  NSMutableDictionary* options = [command.arguments objectAtIndex:0];
  int badge = [[options objectForKey:@"badge"] intValue] ?: 0;
  
  [[UIApplication sharedApplication] setApplicationIconBadgeNumber:badge];
  
  [self successWithMessage:[NSString stringWithFormat:@"app badge count set to %d", badge]];
}
-(void)successWithMessage:(NSString *)message
{
  if (self.callbackId != nil)
  {
    CDVPluginResult *commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:message];
    [self.commandDelegate sendPluginResult:commandResult callbackId:self.callbackId];
  }
}

-(void)failWithMessage:(NSString *)message withError:(NSError *)error
{
  NSString        *errorMessage = (error) ? [NSString stringWithFormat:@"%@ - %@", message, [error localizedDescription]] : message;
  CDVPluginResult *commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errorMessage];
  
  [self.commandDelegate sendPluginResult:commandResult callbackId:self.callbackId];
}

@end
