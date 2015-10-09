package com.plugin.gcm;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import org.json.JSONException;
import org.json.JSONObject;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.util.Random;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
/*
 * Implementation of GCMBroadcastReceiver that hard-wires the intent service to be 
 * com.plugin.gcm.GcmntentService, instead of your_package.GcmIntentService 
 */
public class CordovaGCMBroadcastReceiver extends WakefulBroadcastReceiver {
	private static final String TAG = "GcmIntentService";

	@Override
	public void onReceive(Context context, Intent intent) {

		Log.d(TAG, "onHandleIntent - context: " + context);

		// Extract the payload from the message
		Bundle extras = intent.getExtras();
		GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
		String messageType = gcm.getMessageType(intent);

		if (extras != null) {
			try {
				if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
					JSONObject json = new JSONObject();

					json.put("event", "error");
					json.put("message", extras.toString());
					PushPlugin.sendJavascript(json);
				} else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
					JSONObject json = new JSONObject();
					json.put("event", "deleted");
					json.put("message", extras.toString());
					PushPlugin.sendJavascript(json);
				} else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
					// if we are in the foreground, just surface the payload, else post it to the statusbar
					if (PushPlugin.isInForeground()) {
						extras.putBoolean("foreground", true);
						PushPlugin.sendExtras(extras);
					} else {
						extras.putBoolean("foreground", false);

						// Send a notification if there is a message
						if (extras.getString("message") != null && extras.getString("message").length() != 0) {
							createNotification(context, extras);
						}
					}
				}
			} catch (JSONException exception) {
				Log.d(TAG, "JSON Exception was had!");
			}
		}
	}

	public void createNotification(Context context, Bundle extras) {
		int notId = 0;

		try {
			notId = Integer.parseInt(extras.getString("notId"));
		} catch (NumberFormatException e) {
			Log.e(TAG, "Number format exception - Error parsing Notification ID: " + e.getMessage());
		} catch (Exception e) {
			Log.e(TAG, "Number format exception - Error parsing Notification ID" + e.getMessage());
		}
		if (notId == 0) {
			// no notId passed, so assume we want to show all notifications, so make it a random number
			notId = new Random().nextInt(100000);
			Log.d(TAG, "Generated random notId: " + notId);
		} else {
			Log.d(TAG, "Received notId: " + notId);
		}


		NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		String appName = getAppName(context);

		Intent notificationIntent = new Intent(context, PushHandlerActivity.class);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		notificationIntent.putExtra("pushBundle", extras);

    PendingIntent contentIntent = PendingIntent.getActivity(context, notId, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

		int defaults = Notification.DEFAULT_ALL;

		if (extras.getString("defaults") != null) {
			try {
				defaults = Integer.parseInt(extras.getString("defaults"));
			} catch (NumberFormatException ignore) {
			}
		}

		String message = extras.getString("message");
        Bitmap image = null;
        String url = extras.getString("imageUrl");
        if(url!=null){
            try {
                URL imageUrl = new URL(url);
                image = BitmapFactory.decodeStream(imageUrl.openConnection().getInputStream());
            }catch (MalformedURLException e){
                Log.w("GCMIntentService", "Incorrect URL : " + url );
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context)
                        .setPriority(Notification.PRIORITY_MAX)
                        .setDefaults(defaults)
                        .setSmallIcon(context.getApplicationInfo().icon)
                        .setWhen(System.currentTimeMillis())
                        .setContentTitle(extras.getString("title"))
                        .setTicker(extras.getString("title"))
                        .setContentIntent(contentIntent)
                        .setAutoCancel(true);
        if (image != null) {
            mBuilder.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(image).setSummaryText(message));
        } else{
            mBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(message));
        }
		if (message != null) {
			mBuilder.setContentText(message);
		} else {
			mBuilder.setContentText("<missing message content>");
		}

		String msgcnt = extras.getString("msgcnt");
		if (msgcnt != null) {
			mBuilder.setNumber(Integer.parseInt(msgcnt));
		}

		String soundName = extras.getString("sound");
		if (soundName != null) {
			Resources r = context.getResources();
			int resourceId = r.getIdentifier(soundName, "raw", context.getPackageName());
			Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + resourceId);
			mBuilder.setSound(soundUri);
			defaults &= ~Notification.DEFAULT_SOUND;
			mBuilder.setDefaults(defaults);
		}

		final Notification notification = mBuilder.build();
		final int largeIcon = getLargeIcon(context, extras);
		if (largeIcon > -1) {
			notification.contentView.setImageViewResource(android.R.id.icon, largeIcon);
		}

		mNotificationManager.notify(appName, notId, notification);
	}

	private static String getAppName(Context context) {
		CharSequence appName =
				context
						.getPackageManager()
						.getApplicationLabel(context.getApplicationInfo());

		return (String) appName;
	}

  private int getColor(Bundle extras) {
    int theColor = 0; // default, transparent
    final String passedColor = extras.getString("color"); // something like "#FFFF0000", or "red"
    if (passedColor != null) {
      try {
        theColor = Color.parseColor(passedColor);
      } catch (IllegalArgumentException ignore) {}
    }
    return theColor;
  }

	private int getSmallIcon(Context context, Bundle extras) {

		int icon = -1;

		// first try an iconname possible passed in the server payload
		final String iconNameFromServer = extras.getString("smallIcon");
		if (iconNameFromServer != null) {
			icon = getIconValue(context.getPackageName(), iconNameFromServer);
		}

		// try a custom included icon in our bundle named ic_stat_notify(.png)
		if (icon == -1) {
			icon = getIconValue(context.getPackageName(), "ic_stat_notify");
		}

		// fall back to the regular app icon
		if (icon == -1) {
			icon = context.getApplicationInfo().icon;
		}

		return icon;
	}

	private int getLargeIcon(Context context, Bundle extras) {

		int icon = -1;

		// first try an iconname possible passed in the server payload
		final String iconNameFromServer = extras.getString("largeIcon");
		if (iconNameFromServer != null) {
			icon = getIconValue(context.getPackageName(), iconNameFromServer);
		}

		// try a custom included icon in our bundle named ic_stat_notify(.png)
		if (icon == -1) {
			icon = getIconValue(context.getPackageName(), "ic_notify");
		}

		// fall back to the regular app icon
		if (icon == -1) {
			icon = context.getApplicationInfo().icon;
		}

		return icon;
	}

	private int getIconValue(String className, String iconName) {
		try {
			Class<?> clazz  = Class.forName(className + ".R$drawable");
			return (Integer) clazz.getDeclaredField(iconName).get(Integer.class);
		} catch (Exception ignore) {}
		return -1;
	}
}
