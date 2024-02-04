package by.chemerisuk.cordova.firebase;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import static android.app.Notification.EXTRA_NOTIFICATION_ID;
import static android.content.ContentResolver.SCHEME_ANDROID_RESOURCE;

import static by.chemerisuk.cordova.firebase.FirebaseMessagingPlugin.mActivity;

import java.util.List;


public class FirebaseMessagingPluginService extends FirebaseMessagingService {
    private static final String TAG = "FCMPluginService";

    public static final String ACTION_FCM_MESSAGE = "by.chemerisuk.cordova.firebase.ACTION_FCM_MESSAGE";
    public static final String EXTRA_FCM_MESSAGE = "by.chemerisuk.cordova.firebase.EXTRA_FCM_MESSAGE";
    public static final String ACTION_FCM_TOKEN = "by.chemerisuk.cordova.firebase.ACTION_FCM_TOKEN";
    public static final String EXTRA_FCM_TOKEN = "by.chemerisuk.cordova.firebase.EXTRA_FCM_TOKEN";
    public final static String NOTIFICATION_ICON_KEY = "com.google.firebase.messaging.default_notification_icon";
    public final static String NOTIFICATION_COLOR_KEY = "com.google.firebase.messaging.default_notification_color";
    public final static String NOTIFICATION_CHANNEL_KEY = "com.google.firebase.messaging.default_notification_channel_id";

    private LocalBroadcastManager broadcastManager;
    private NotificationManagerCompat notificationManager;
    private int defaultNotificationIcon;
    private int defaultNotificationColor;
    private String defaultNotificationChannel;

    private Utils utils;

  public static  String NOTIFICATION_CHANNEL_ID="order_alerts";
  public static int NOTIFICATION_ID = 5;

    @Override
    public void onCreate() {
        broadcastManager = LocalBroadcastManager.getInstance(this);
        notificationManager = NotificationManagerCompat.from(this);
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getApplicationContext().getPackageName(), PackageManager.GET_META_DATA);
            defaultNotificationIcon = ai.metaData.getInt(NOTIFICATION_ICON_KEY, ai.icon);
            defaultNotificationChannel = ai.metaData.getString(NOTIFICATION_CHANNEL_KEY, "default");
            defaultNotificationColor = ContextCompat.getColor(this, ai.metaData.getInt(NOTIFICATION_COLOR_KEY));
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Failed to load meta-data", e);
        } catch(Resources.NotFoundException e) {
            Log.d(TAG, "Failed to load notification color", e);
        }
        // On Android O or greater we need to create a new notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel defaultChannel = notificationManager.getNotificationChannel(defaultNotificationChannel);
            if (defaultChannel == null) {
                notificationManager.createNotificationChannel(
                        new NotificationChannel(defaultNotificationChannel, "Firebase", NotificationManager.IMPORTANCE_HIGH));
            }
        }
      createNotificationChannel();
      utils = new Utils(getApplicationContext(),mActivity);
    }

    public void createNotificationChannel(){
      NotificationChannelCompat channel = new NotificationChannelCompat.Builder(
        NOTIFICATION_CHANNEL_ID,
        NotificationManagerCompat.IMPORTANCE_HIGH
      ).setName("Order alerts")
        .setDescription("New order alerts")
        .build();
      notificationManager.createNotificationChannel(channel);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        FirebaseMessagingPlugin.sendToken(token);

        Intent intent = new Intent(ACTION_FCM_TOKEN);
        intent.putExtra(EXTRA_FCM_TOKEN, token);
        broadcastManager.sendBroadcast(intent);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        FirebaseMessagingPlugin.sendNotification(remoteMessage);
        Intent intent = new Intent(ACTION_FCM_MESSAGE);
        intent.putExtra(EXTRA_FCM_MESSAGE, remoteMessage);
        broadcastManager.sendBroadcast(intent);
        if (FirebaseMessagingPlugin.isForceShow()) {
            RemoteMessage.Notification notification = remoteMessage.getNotification();
            if (notification != null) {
                showAlert(notification);
            }
        }
      if (!isMainAppForeground()) {
        utils.startActivity();
        Log.i(TAG, "sendMessage: " + "App not active");
      }
    }

  @SuppressLint("MissingPermission")
  private void showNotification() {

      Context context = getApplicationContext();
    String ACTION_SNOOZE = "snooze";
    Intent viewIntent = new Intent(this, NotificationReceiver.class);
    viewIntent.setAction(ACTION_SNOOZE);
    viewIntent.putExtra(EXTRA_NOTIFICATION_ID, 0);
    PendingIntent snoozePendingIntent =
      PendingIntent.getBroadcast(this, 0, viewIntent, PendingIntent.FLAG_IMMUTABLE);

    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
        this,
        NOTIFICATION_CHANNEL_ID
      )
      .setSmallIcon(defaultNotificationIcon)
      .setColor(defaultNotificationColor)
      .setContentTitle("Eatie")
      .setContentText("New order")
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION).addAction(0,"View",snoozePendingIntent);
    Notification notification = notificationBuilder.build();
    notificationManager.notify(NOTIFICATION_ID, notification);
  }

    @SuppressLint("MissingPermission")
    private void showAlert(RemoteMessage.Notification notification) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getNotificationChannel(notification))
                .setSound(getNotificationSound(notification.getSound()))
                .setContentTitle(notification.getTitle())
                .setContentText(notification.getBody())
                .setGroup(notification.getTag())
                .setSmallIcon(defaultNotificationIcon)
                .setColor(defaultNotificationColor)
                // must set priority to make sure forceShow works properly
                .setPriority(1);

        notificationManager.notify(0, builder.build());
        // dismiss notification to hide icon from status bar automatically
        new Handler(getMainLooper()).postDelayed(() -> {
            notificationManager.cancel(0);
        }, 3000);
    }

    private String getNotificationChannel(RemoteMessage.Notification notification) {
        String channel = notification.getChannelId();
        if (channel == null) {
            return defaultNotificationChannel;
        } else {
            return channel;
        }
    }

    private Uri getNotificationSound(String soundName) {
        if (soundName == null || soundName.isEmpty()) {
            return null;
        } else if (soundName.equals("default")) {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        } else {
            return Uri.parse(SCHEME_ANDROID_RESOURCE + "://" + getApplicationContext().getPackageName() + "/raw/" + soundName);
        }
    }

  private boolean isMainAppForeground() {
    boolean isMainAppForeground = false;
//    Context context = FirebaseMessagingPlugin.mApplicationContext;
    Context context = getApplicationContext();
    Activity activity = mActivity;
    KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);

    boolean isPhoneLocked = km.inKeyguardRestrictedInputMode();
    boolean isSceenAwake = (Build.VERSION.SDK_INT < 20 ? pm.isScreenOn() : pm.isInteractive());

    List<ActivityManager.RunningAppProcessInfo> runningProcessInfo = activityManager.getRunningAppProcesses();
    if (runningProcessInfo != null && !Utils.isAlertActive) {
      for (ActivityManager.RunningAppProcessInfo appProcess : runningProcessInfo) {
        Log.d("NotificationService", String.valueOf(appProcess.importance));
        if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
          && appProcess.processName.equals(activity.getApplication().getPackageName())
          && !isPhoneLocked && isSceenAwake) {
          isMainAppForeground = true;
          break;
        }
      }
    }
    return isMainAppForeground;
  }
}
