
package by.chemerisuk.cordova.firebase;

import static android.content.Context.POWER_SERVICE;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import by.chemerisuk.cordova.support.CordovaMethod;
import by.chemerisuk.cordova.support.ReflectiveCordovaPlugin;
import me.leolin.shortcutbadger.ShortcutBadger;

import static androidx.core.content.ContextCompat.getSystemService;
import static com.google.android.gms.tasks.Tasks.await;
import static by.chemerisuk.cordova.support.ExecutionThread.WORKER;

public class FirebaseMessagingPlugin extends ReflectiveCordovaPlugin {
    private static final String TAG = "FCMPlugin";
    public static final String READ = Manifest.permission.SYSTEM_ALERT_WINDOW;
    public static final int SEARCH_REQ_CODE = 0;
    public static Activity mActivity;
  public static Context mApplicationContext;
    private static final int OVERLAY_REQUEST_CODE = 5;
//    public static Utils utils;

    private JSONObject lastBundle;
    private boolean isBackground = false;
    private boolean forceShow = false;
    private CallbackContext tokenRefreshCallback;
    private CallbackContext foregroundCallback;
    private CallbackContext backgroundCallback;
    private static FirebaseMessagingPlugin instance;
    private NotificationManager notificationManager;
    private FirebaseMessaging firebaseMessaging;
    private CallbackContext requestPermissionCallback;

    @Override
    protected void pluginInitialize() {
        FirebaseMessagingPlugin.instance = this;

        firebaseMessaging = FirebaseMessaging.getInstance();
        notificationManager = getSystemService(cordova.getActivity(), NotificationManager.class);
        lastBundle = getNotificationData(cordova.getActivity().getIntent());

        mActivity = cordova.getActivity();
        mApplicationContext = mActivity.getApplicationContext();
//        utils = new Utils(mApplicationContext,mActivity);
        // reset this as activity and context are available
        Utils.isAlertActive = false;
    }

    @CordovaMethod(WORKER)
    private void subscribe(CordovaArgs args, final CallbackContext callbackContext) throws Exception {
        String topic = args.getString(0);
        await(firebaseMessaging.subscribeToTopic(topic));
        callbackContext.success();
    }

    @CordovaMethod(WORKER)
    private void unsubscribe(CordovaArgs args, CallbackContext callbackContext) throws Exception {
        String topic = args.getString(0);
        await(firebaseMessaging.unsubscribeFromTopic(topic));
        callbackContext.success();
    }

    @CordovaMethod
    private void clearNotifications(CallbackContext callbackContext) {
        notificationManager.cancelAll();
        callbackContext.success();
    }

    @CordovaMethod(WORKER)
    private void deleteToken(CallbackContext callbackContext) throws Exception {
        await(firebaseMessaging.deleteToken());
        callbackContext.success();
    }

    @CordovaMethod(WORKER)
    private void getToken(CordovaArgs args, CallbackContext callbackContext) throws Exception {
        String type = args.getString(0);
        if (!type.isEmpty()) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, (String) null));
        } else {
            String fcmToken = await(firebaseMessaging.getToken());
            callbackContext.success(fcmToken);
        }
    }

    @CordovaMethod
    private void onTokenRefresh(CallbackContext callbackContext) {
        instance.tokenRefreshCallback = callbackContext;
    }

    @CordovaMethod
    private void onMessage(CallbackContext callbackContext) {
        instance.foregroundCallback = callbackContext;
    }

    @CordovaMethod
    private void onBackgroundMessage(CallbackContext callbackContext) {
        instance.backgroundCallback = callbackContext;

        if (lastBundle != null) {
            sendNotification(lastBundle, callbackContext);
            lastBundle = null;
        }
    }

    @CordovaMethod
    private void setBadge(CordovaArgs args, CallbackContext callbackContext) throws JSONException {
        int value = args.getInt(0);
        if (value >= 0) {
            Context context = cordova.getActivity().getApplicationContext();
            ShortcutBadger.applyCount(context, value);
            callbackContext.success();
        } else {
            callbackContext.error("Badge value can't be negative");
        }
    }

    @CordovaMethod
    private void getBadge(CallbackContext callbackContext) {
        Context context = cordova.getActivity();
        SharedPreferences settings = context.getSharedPreferences("badge", Context.MODE_PRIVATE);
        callbackContext.success(settings.getInt("badge", 0));
    }

    @CordovaMethod
    private void requestPermission(CordovaArgs args, CallbackContext callbackContext) throws JSONException {
        JSONObject options = args.getJSONObject(0);
        Context context = cordova.getActivity().getApplicationContext();
        forceShow = options.optBoolean("forceShow");
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            callbackContext.success(1);
        } else if (SDK_INT >= 33) {
            requestPermissionCallback = callbackContext;
            PermissionHelper.requestPermission(this, 0, Manifest.permission.POST_NOTIFICATIONS);
        } else {
            callbackContext.error("Notifications permission is not granted");
        }
    }


  @CordovaMethod
    private void areNotificationsEnabled(CordovaArgs args, CallbackContext callbackContext){
      Context context = cordova.getActivity().getApplicationContext();
      boolean areNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled();
      callbackContext.success(areNotificationsEnabled?1:0);
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_DENIED) {
                requestPermissionCallback.error("Notifications permission is not granted");
                return;
            }
        }
        requestPermissionCallback.success(1);
    }

    @Override
    public void onNewIntent(Intent intent) {
        JSONObject notificationData = getNotificationData(intent);
        if (instance != null && notificationData != null) {
            sendNotification(notificationData, instance.backgroundCallback);
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        this.isBackground = true;
    }

    @Override
    public void onResume(boolean multitasking) {
        this.isBackground = false;
    }

    static void sendNotification(RemoteMessage remoteMessage) {
        JSONObject notificationData = new JSONObject(remoteMessage.getData());
        RemoteMessage.Notification notification = remoteMessage.getNotification();
        try {
            if (notification != null) {
                notificationData.put("gcm", toJSON(notification));
            }
            notificationData.put("google.message_id", remoteMessage.getMessageId());
            notificationData.put("google.sent_time", remoteMessage.getSentTime());

            if (instance != null) {
                CallbackContext callbackContext = instance.isBackground ? instance.backgroundCallback
                        : instance.foregroundCallback;
                instance.sendNotification(notificationData, callbackContext);
            }
        } catch (JSONException e) {
            Log.e(TAG, "sendNotification", e);
        }
    }

    static void sendToken(String instanceId) {
        if (instance != null) {
            if (instance.tokenRefreshCallback != null && instanceId != null) {
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, instanceId);
                pluginResult.setKeepCallback(true);
                instance.tokenRefreshCallback.sendPluginResult(pluginResult);
            }
        }
    }

    static boolean isForceShow() {
        return instance != null && instance.forceShow;
    }

    private void sendNotification(JSONObject notificationData, CallbackContext callbackContext) {
        if (callbackContext != null) {
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, notificationData);
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
        }
    }

    private JSONObject getNotificationData(Intent intent) {
        Bundle bundle = intent.getExtras();

        if (bundle == null) {
            return null;
        }

        if (!bundle.containsKey("google.message_id") && !bundle.containsKey("google.sent_time")) {
            return null;
        }

        try {
            JSONObject notificationData = new JSONObject();
            Set<String> keys = bundle.keySet();
            for (String key : keys) {
                notificationData.put(key, bundle.get(key));
            }
            return notificationData;
        } catch (JSONException e) {
            Log.e(TAG, "getNotificationData", e);
            return null;
        }
    }

    private static JSONObject toJSON(RemoteMessage.Notification notification) throws JSONException {
        JSONObject result = new JSONObject()
                .put("body", notification.getBody())
                .put("title", notification.getTitle())
                .put("sound", notification.getSound())
                .put("icon", notification.getIcon())
                .put("tag", notification.getTag())
                .put("color", notification.getColor())
                .put("clickAction", notification.getClickAction());

        Uri imageUri = notification.getImageUrl();
        if (imageUri != null) {
            result.put("imageUrl", imageUri.toString());
        }

        return result;
    }

  @CordovaMethod(WORKER)
  private void hasOverlayPermission(CordovaArgs args, CallbackContext callbackContext) throws JSONException {
    Boolean hasPermission = false;
    if (SDK_INT >= M) {
      hasPermission = Settings.canDrawOverlays(mApplicationContext);
    }
    Log.i(TAG, "hasoverlayPermission: " + hasPermission);
    PluginResult res = new PluginResult(PluginResult.Status.OK, hasPermission);
    callbackContext.sendPluginResult(res);
  }


  @CordovaMethod(WORKER)
  private void requestOverlayPermission(CordovaArgs args, CallbackContext callbackContext) throws JSONException   {
    if (SDK_INT >= M) {
      if (Settings.canDrawOverlays(mApplicationContext)) {
        return;
      }
      String pkgName = mActivity.getPackageName();
      Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + pkgName));
      mActivity.startActivity(intent);
    }
  }

  @CordovaMethod(WORKER)
  private void isIgnoringBatteryOptimizations(CordovaArgs args, CallbackContext callbackContext) {
    if (SDK_INT < M)
      return;
    Activity activity = cordova.getActivity();
    String pkgName = activity.getPackageName();
    PowerManager pm = (PowerManager) getService(POWER_SERVICE);
    boolean isIgnoring = pm.isIgnoringBatteryOptimizations(pkgName);
    PluginResult res = new PluginResult(PluginResult.Status.OK, isIgnoring);
    callbackContext.sendPluginResult(res);
  }

  private Object getService(String name) {
    return mActivity.getSystemService(name);
  }


  @CordovaMethod(WORKER)
  private void openBatterySettings(CordovaArgs args, CallbackContext callbackContext) {
    if (SDK_INT < M)
      return;
    Intent intent = new Intent(ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
    mActivity.startActivity(intent);
  }

  @CordovaMethod
  private void openNotificationSetting(CordovaArgs args, CallbackContext callbackContext){
    Activity activity = cordova.getActivity();
    Context context = cordova.getActivity().getApplicationContext();
    Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
      .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
    activity.startActivity(intent);
  }


  @CordovaMethod(WORKER)
  private void openAppStart(CordovaArgs args, CallbackContext callbackContext) {
    PackageManager pm = mActivity.getPackageManager();
    for (Intent intent : getAppStartIntents()) {
      if (pm.resolveActivity(intent, MATCH_DEFAULT_ONLY) != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mActivity.startActivity(intent);
        break;
      }
    }
  }

  /**
   * Returns list of all possible intents to present the app start settings.
   */
  private List<Intent> getAppStartIntents() {
    return Arrays.asList(
      new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
      new Intent().setComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
      new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
      new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
      new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
      new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
      new Intent().setComponent(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
      new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
      new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
      new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
      new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")),
      new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")).setData(android.net.Uri.parse("mobilemanager://function/entry/AutoStart")),
      new Intent().setAction("com.letv.android.permissionautoboot"),
      new Intent().setComponent(new ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.ram.AutoRunActivity")),
      new Intent().setComponent(ComponentName.unflattenFromString("com.iqoo.secure/.MainActivity")),
      new Intent().setComponent(ComponentName.unflattenFromString("com.meizu.safe/.permission.SmartBGActivity")),
      new Intent().setComponent(new ComponentName("com.yulong.android.coolsafe", ".ui.activity.autorun.AutoRunListActivity")),
      new Intent().setComponent(new ComponentName("cn.nubia.security2", "cn.nubia.security.appmanage.selfstart.ui.SelfStartActivity")),
      new Intent().setComponent(new ComponentName("com.zui.safecenter", "com.lenovo.safecenter.MainTab.LeSafeMainActivity"))
    );
  }
}
