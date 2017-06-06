# Telerik Push Notifications Plugin

## Description

The Telerik Push Notifications is based on the [Phonegap Push Plugin](https://github.com/phonegap-build/PushPlugin).

It contains some bug fixes and new features and is easily integrated with [Telerik Backend Services](http://www.telerik.com/backend-services).

## Using with Telerik Backend Services

In order to use the plugin with Telerik Backend Services, which supports iOS, Android, WP8 and Windows 8, take a look at the official documentation:

- [Getting started with Push Notifications in a Hybrid Application with Telerik Backend Services](http://docs.telerik.com/platform/backend-services/getting-started/push-notifications/integrating-push-hybrid)

For further information you can take a look into the Backend Services hybrid push notifications samples:

- [Backend Services Push Hybrid simple application](https://github.com/telerik/backend-services-push-hybrid) - basic example of using Push Notifications provided by Telerik Backend Services in a hybrid app built with Telerik AppBuilder.

- [Backend Services Push Hybrid advanced application](https://github.com/telerik/backend-services-push-hybrid-advanced) - example of creating and receiving Push Notifications provided by Telerik Backend Services.

## Features

- Register a device for push notifications

         var deviceSpecificOptions = { ... }; // set the device specific options here
		 pushNotification.register(successHandler, errorHandler, deviceSpecificOptions);

- Unregister a device from push notifications

		pushNotification.unregister(successHandler, errorHandler, options);

		
- iOS 8 interactive push notifications support (available from v2.5 and above)
  
        // Get the push plugin instance
		var pushPlugin = window.plugins.pushNotification;

        // Define a new Read Action
      	var readAction = {
        	identifier: 'READ_IDENTIFIER', // mandatory
        	title: 'Read', // mandatory
        	activationMode: pushPlugin.UserNotificationActivationMode.Foreground, // default: Background
        	destructive: false, // default: false
        	authenticationRequired: true // default: false
      	};

      	// Define a new Ignore Action. Defaults are commented out
      	var ignoreAction = {
	        identifier: 'IGNORE_IDENTIFIER',
        	title: 'Ignore'
        	//activationMode: pushPlugin.UserNotificationActivationMode.Background,
        	//destructive: false,
        	//authenticationRequired: false
      	};

      	// Define a new Delete Action. Defaults are commented out.
      	var deleteAction = {
	        identifier: 'DELETE_IDENTIFIER',
        	title: 'Delete',
        	//activationMode: pushPlugin.UserNotificationActivationMode.Background,
        	destructive: true,
        	authenticationRequired: true
      	};

        // Define a read category with default and minimal context actions
      	var readCategory = {
        	identifier: 'READ_CATEGORY', // mandatory
        	actionsForDefaultContext: [readAction, ignoreAction, deleteAction], // mandatory
        	actionsForMinimalContext: [readAction, deleteAction]  // mandatory
      	};
 
        // Define another category, with different set of actions
      	var otherCategory = {
	        identifier: 'OTHER_CATEGORY', // mandatory
        	actionsForDefaultContext: [ignoreAction, deleteAction], // mandatory
        	actionsForMinimalContext: [deleteAction]  // mandatory
      	};
 
        // Register the category and the other interactive settings.
      	pushPlugin.registerUserNotificationSettings(
          	// the success callback which will immediately return (APNs is not contacted for this)
          	onUserNotificationSettingsReady,
          	// called in case the configuration is incorrect
          	errorHandler,
          	{
            		// asking permission for these features
            		types: [
              			pushPlugin.UserNotificationTypes.Alert,
        	      		pushPlugin.UserNotificationTypes.Badge,
	              		pushPlugin.UserNotificationTypes.Sound
            		],
            		// register these categories
            		categories: [
              			readCategory,
              			otherCategory
            		]
          	}
      	);


- Add notification actions (Android only)

		// Add actions to the data sent by the server. Everything in data
		// will be in the "payload" key of the ECB callback on the client,
		// so if you want to pass any extra data to the client, you can
		// add it here. We will also add an "action" key to the data passed
		// to the ECB callback with the "id" of the selected action as the
		// value. Add any icons you want to use for actions to
		// "res/drawable-<density>/" folders. If the "doNothing" key for an
		// action is set te true, selecting that action will simply dismiss
		// the notification and do nothing else.
		"data": {
			"title": "Hello",
			"message": "Do you want to do something?",
			"my_extra_data1": "some data1",
			"some_other_data2": "some data2",
			"actions": [{
				"id": "yes",
				"title": "Yes",
				"icon": "ic_yes"
			}, {
				"id": "no",
				"title": "No",
				"icon": "ic_no",
				"doNothing": true
			}]
		}

- iOS 9 text input support (available from v3.1 and above)

		// Define a Text Input Action
      	var replyAction = {
        	identifier: 'REPLY_IDENTIFIER',
        	title: 'Reply', 
        	activationMode: window.plugins.pushNotification.UserNotificationActivationMode.Background,
        	destructive: false,
        	authenticationRequired: true,
		behavior: window.plugins.pushNotification.ActionBehavior.TextInput
      	};

- **IMPORTANT**: When using interactive iOS push notifications with background activation mode, you **must** call the following function, once you are done processing the push notification object:

		pushPlugin.notificationProcessed()
 This way you'll be able to execute your javascript callback and then notify the operating system to put back your app in background, which is the correct approach to handle such notifications by iOS.  


- Set an application icon badge number (iOS only)

		// sets the application badge to the provided value 
		// if badge === 0 clears out the badge 
		pushNotification.setApplicationIconBadgeNumber(successCallback, errorCallback, badge)
 

- Check if the user has disabled push notifications on the device

		// Checks whether Push Notifications are enabled for this Application on the Device 
		pushNotification.areNotificationsEnabled(successCallback, errorCallback, options);

- Handling multiple notifications on Android devices - Since version 2.4.3 of this plugin, all new notifications are stacked in the notification panel and do not replace previous notifications by default. To change this behavior and control which notifications are replaced and which are not, pass a **notId** key in the notification payload.

  - To always stack new notifications, don't pass the **notId** value.

			"data": {
        		"title": "Hello",
	                "message": "Always stack the notification.", 
	    	}

  - To always replace existing notifications, use the same positive (> 0) **notId** value for all notifications.

			"data": {
	        	"title": "Hello",
    	        "message": "Always replace the notification.",
		        "notId": 1 // send the same notId every time 
	    	}

  - If your application supports different kinds of push notifications, you can use a mixed approach based on your business logic. Notifications that are sent with the same **notId** value are replaced automatically, so only the last one is visible. For the rest of the notifications which should be stacked, just do not send a **notId** key in the payload.

			"data": {
				"title": "Hello",
				"message": "This notification will be stacked."
				"notId": 5
			}

			"data": {
				"title": "Hello",
				"message": "This notification will be stacked."
				"notId": 6
			}

			"data": {
				"title": "Hello",
				"message": "This notification will replace the first one."
				"notId": 5
			}
 


##<a name="license"></a> LICENSE

	The MIT License

	Copyright (c) 2012 Adobe Systems, inc.
	portions Copyright (c) 2012 Olivier Louvignes

	Permission is hereby granted, free of charge, to any person obtaining a copy
	of this software and associated documentation files (the "Software"), to deal
	in the Software without restriction, including without limitation the rights
	to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
	copies of the Software, and to permit persons to whom the Software is
	furnished to do so, subject to the following conditions:

	The above copyright notice and this permission notice shall be included in
	all copies or substantial portions of the Software.

	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
	FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
	AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
	LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
	OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
	THE SOFTWARE.
