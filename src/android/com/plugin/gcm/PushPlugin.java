package com.plugin.gcm;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class PushPlugin extends CordovaPlugin {
	public static final String TAG = "PushPlugin";

	public static final String REGISTER = "register";
	public static final String UNREGISTER = "unregister";
	public static final String ARE_NOTIFICATIONS_ENABLED = "areNotificationsEnabled";
	public static final String PROPERTY_REG_ID = "registration_id";
	public static final String PROPERTY_APPVERSION = "app_version";
	public static final String EXTRA_MESSAGE = "message";
	public static final String EXIT = "exit";
	private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

	private static CordovaWebView gWebView;
	private static String gECB;
	private static String gSenderID;
	private static Bundle gCachedExtras = null;
	private static boolean gForeground = false;

	/**
	 * Gets the application context from cordova's main activity.
	 *
	 * @return the application context
	 */
	private Context getApplicationContext() {
		return this.cordova.getActivity().getApplicationContext();
	}

	private Activity getApplicationActivity() {
		return this.cordova.getActivity();
	}

	GoogleCloudMessaging gcm;
	String regid;
	Context context;

	@Override
	public boolean execute(final String action, final JSONArray data, final CallbackContext callbackContext) {

		Log.v(TAG, "execute: action=" + action);

		if (REGISTER.equals(action)) {

			Log.v(TAG, "execute: data=" + data.toString());

			try {
				JSONObject jo = data.getJSONObject(0);

				gWebView = this.webView;
				Log.v(TAG, "execute: jo=" + jo.toString());

				gECB = (String) jo.get("ecb");
				gSenderID = (String) jo.get("senderID");

				Log.v(TAG, "execute: ECB=" + gECB + " senderID=" + gSenderID);

				context = getApplicationContext();

				regid = getRegistrationId(getApplicationContext());

				if (regid.isEmpty()) {
					new AsyncRegister().execute(callbackContext);
				} else {
					sendJavascript(new JSONObject().put("event", "registered").put("regid", regid));
					callbackContext.success(regid);
				}
			} catch (JSONException e) {
				Log.e(TAG, "execute: Got JSON Exception " + e.getMessage());
				callbackContext.error(e.getMessage());
			}

			if (gCachedExtras != null) {
				Log.v(TAG, "sending cached extras");
				sendExtras(gCachedExtras);
				gCachedExtras = null;
			}
			return true;

		} else if (ARE_NOTIFICATIONS_ENABLED.equals(action)) {

			Log.v(TAG, "ARE_NOTIFICATIONS_ENABLED");
			final boolean registered = !getRegistrationId(getApplicationContext()).isEmpty();
			Log.d(TAG, "areNotificationsEnabled? " + registered);
			callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, registered));
			return true;

		} else if (UNREGISTER.equals(action)) {

			cordova.getThreadPool().execute(new Runnable() {
				public void run() {
					GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(getApplicationContext());
					try {
						gcm.unregister();
						removeRegistrationId(getApplicationContext());
					} catch (IOException exception) {
						Log.d(TAG, "IOException!");
					}

					Log.v(TAG, "UNREGISTER");

					callbackContext.success();
				}
			});
			return true;

		} else {
			Log.e(TAG, "Invalid action : " + action);
			callbackContext.error("Invalid action : " + action);
			return false;
		}
	}

	/**
	 * Gets the current registration ID for application on GCM service.
	 * <p/>
	 * If result is empty, the app needs to register.
	 *
	 * @return registration ID, or empty string if there is no existing
	 * registration ID.
	 */
	private String getRegistrationId(Context context) {
		final SharedPreferences prefs = getGCMPreferences(context);
		String registrationId = prefs.getString(PROPERTY_REG_ID, "");
		if (registrationId.isEmpty()) {
			Log.i(TAG, "Registration not found");
			return "";
		}
		// Check if the app was updated; if so, it must clear the registration ID
		// since the existing regID is not guaranteed to work with the new
		// app version
		String registeredVersion = prefs.getString(PROPERTY_APPVERSION, "");
		String currentVersion = getVersion();
		if (registeredVersion.equals(currentVersion)) {
			Log.i(TAG, "Got registrationId from cache");
			return registrationId;
		}
		return "";
	}

	private class AsyncRegister extends AsyncTask<CallbackContext, Void, Void> {
		@Override
		protected Void doInBackground(CallbackContext... callbackContext) {
			try {
				if (gcm == null) {
					gcm = GoogleCloudMessaging.getInstance(getApplicationContext());
				}
				regid = gcm.register(gSenderID);
				Log.v(TAG, "Device registered, registration ID=" + regid);
				storeRegistrationId(getApplicationContext(), regid);
				sendJavascript(new JSONObject().put("event", "registered").put("regid", regid));
				callbackContext[0].success(regid);
			} catch (Exception ex) {
				Log.d(TAG, "Got Exception on registerInBackground", ex);
				callbackContext[0].error(ex.getMessage());
			}
			return null;
		}
	}

	/**
	 * @return Application's {@code SharedPreferences}.
	 */
	private SharedPreferences getGCMPreferences(Context context) {
		// This sample app persists the registration ID in shared preferences, but
		// how you store the regID in your app is up to you.
		Log.d(TAG, "Activity: " + getApplicationActivity().toString());
		return context.getSharedPreferences(getApplicationActivity().toString(), Context.MODE_PRIVATE);
	}

	/**
	 * Stores the registration ID and app versionCode in the application's
	 * {@code SharedPreferences}.
	 *
	 * @param context application's context.
	 * @param regId   registration ID
	 */
	private void storeRegistrationId(Context context, String regId) {
		final SharedPreferences prefs = getGCMPreferences(context);
		String appVersion = getVersion();
		Log.i(TAG, "Saving registrationId on version " + appVersion);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(PROPERTY_REG_ID, regId);
		editor.putString(PROPERTY_APPVERSION, appVersion);
		editor.apply();
	}

	private void removeRegistrationId(Context context) {
		final SharedPreferences prefs = getGCMPreferences(context);
		Log.i(TAG, "Clearing registrationId");
		SharedPreferences.Editor editor = prefs.edit();
		editor.remove(PROPERTY_REG_ID);
		editor.apply();
	}

	/*
   * Sends a json object to the client as parameter to a method which is defined in gECB.
   */
	public static void sendJavascript(JSONObject _json) {
		String _d = "javascript:" + gECB + "(" + _json.toString() + ")";
		Log.v(TAG, "sendJavascript: " + _d);

		if (gECB != null && gWebView != null) {
			gWebView.sendJavascript(_d);
		}
	}

	public String getVersion() {
		try {
			PackageManager packageManager = getApplicationActivity().getPackageManager();
			return packageManager.getPackageInfo(getApplicationActivity().getPackageName(), 0).versionName;
		} catch (NameNotFoundException exception) {
			Log.d(TAG, "NameNotFoundException!");
			return "";
		}
	}

	/*
   * Sends the pushbundle extras to the client application.
   * If the client application isn't currently active, it is cached for later processing.
   */
	public static void sendExtras(Bundle extras) {
		if (extras != null) {
			if (gECB != null && gWebView != null) {
				sendJavascript(convertBundleToJson(extras));
			} else {
				Log.v(TAG, "sendExtras: caching extras to send at a later time.");
				gCachedExtras = extras;
			}
		}
	}

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		gForeground = true;
		checkPlayServices();

		// Register a receiver for action with doNothing=true
		IntentFilter filter = new IntentFilter();
		filter.addAction(CordovaGCMBroadcastReceiver.DO_NOTHING);
		getApplicationContext().registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				NotificationManager manager = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);
				String appName = PushHandlerActivity.getAppName(getApplicationContext());
				manager.cancel(appName, intent.getIntExtra(PushHandlerActivity.NOTIFICATION_ID, -1));
			}
		}, filter);
	}

	@Override
	public void onPause(boolean multitasking) {
		super.onPause(multitasking);
		gForeground = false;
		final NotificationManager notificationManager = (NotificationManager) cordova.getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
//    notificationManager.cancelAll();
	}

	@Override
	public void onResume(boolean multitasking) {
		super.onResume(multitasking);
		gForeground = true;
		checkPlayServices();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		gForeground = false;
		gECB = null;
		gWebView = null;
	}

	private boolean checkPlayServices() {
		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getApplicationContext());
		if (resultCode != ConnectionResult.SUCCESS) {
			if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
				GooglePlayServicesUtil.getErrorDialog(resultCode, this.cordova.getActivity(), PLAY_SERVICES_RESOLUTION_REQUEST).show();
			} else {
				Log.i(TAG, "This device does not support Play Services.");
				// finish();
			}
			return false;
		}
		return true;
	}

	/*
   * serializes a bundle to JSON.
   */
	private static JSONObject convertBundleToJson(Bundle extras) {
		try {
			JSONObject json;
			json = new JSONObject().put("event", "message");

			JSONObject jsondata = new JSONObject();
			for (String key : extras.keySet()) {
				Object value = extras.get(key);

				// System data from Android
				if (key.equals("from") || key.equals("collapse_key")) {
					json.put(key, value);
				} else if (key.equals("foreground")) {
					json.put(key, extras.getBoolean("foreground"));
				} else if (key.equals("coldstart")) {
					json.put(key, extras.getBoolean("coldstart"));
				} else {
					// Maintain backwards compatibility
					if (key.equals("message") || key.equals("msgcnt") || key.equals("soundname")) {
						json.put(key, value);
					}

					if (value instanceof String) {
						// Try to figure out if the value is another JSON object

						String strValue = (String) value;
						if (strValue.startsWith("{")) {
							try {
								JSONObject json2 = new JSONObject(strValue);
								jsondata.put(key, json2);
							} catch (Exception e) {
								jsondata.put(key, value);
							}
							// Try to figure out if the value is another JSON array
						} else if (strValue.startsWith("[")) {
							try {
								JSONArray json2 = new JSONArray(strValue);
								jsondata.put(key, json2);
							} catch (Exception e) {
								jsondata.put(key, value);
							}
						} else {
							jsondata.put(key, value);
						}
					}
				}
			} // while
			json.put("payload", jsondata);

			Log.v(TAG, "extrasToJSON: " + json.toString());

			return json;
		} catch (JSONException e) {
			Log.e(TAG, "extrasToJSON: JSON exception");
		}
		return null;
	}

	public static boolean isInForeground() {
		return gForeground;
	}

	public static boolean isActive() {
		return gWebView != null;
	}
}
