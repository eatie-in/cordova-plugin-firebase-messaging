package by.chemerisuk.cordova.firebase;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class Utils {
    private static final String TAG = Utils.class.getName();
    private static final String mAudioName = "audio";
    private Context mContext;
    private Activity mActivity;
    private String mPackageName;
    private Vibrator mVibrator;
    private Ringtone mRingtone;
    public static Boolean isAlertActive = false;

    Utils(Context context, Activity activity) {
        mContext = context;
        mActivity = activity;
        mPackageName = mContext.getPackageName();
        getServices();
    }

    private void vibrate(long seconds) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mVibrator.vibrate(VibrationEffect.createOneShot(seconds, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            mVibrator.vibrate(seconds);
        }
    }

    private void getServices() {
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        getSound();
    }

    private void getSound() {
        int checkExistence = mContext.getResources().getIdentifier("audio", "raw", mPackageName);
        if (checkExistence != 0) {
            Uri soundPath = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + mPackageName + "/raw/" + mAudioName);
            mRingtone = RingtoneManager.getRingtone(mContext, soundPath);
        } else {
            // ringtone
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            mRingtone = RingtoneManager.getRingtone(mContext, notification);
        }
    }


    private AlertDialog createAlert(boolean launchApp) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle("You have new order");
        builder.setMessage("Tap ok to view the order");
        builder.setCancelable(false);
        builder.setIcon(mContext.getResources().getIdentifier("ic_launcher", "mipmap", mPackageName));
        builder.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int which) {
                        mVibrator.cancel();
                        mRingtone.stop();
                        if (launchApp) {
                            launchApp();
                        }else {
                            isAlertActive = false;
                        }
                    }
                });

        return builder.create();
    }

    void showAlert(Boolean lauchApp) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if(isAlertActive){
                    Log.w(TAG, "run: " + "already alert active" );
                    return;
                }
                AlertDialog alert = createAlert(lauchApp);
                vibrate(5000);
                mRingtone.play();
                alert.show();
                isAlertActive = true;
            }
        };
        mActivity.runOnUiThread(runnable);
    }

    void startActivity() {
        if (isAlertActive) {
            Log.i(TAG, "showAlert: " + "alert active");
            return;
        }
        Context context = mContext;
        Intent alertIntent = new Intent(context, AlertActivity.class);
        alertIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        //alertIntent.putExtra("alertMessage", alertMessage);
        context.startActivity(alertIntent);
    }


    private void launchApp() {
        PackageManager pm = mContext.getPackageManager();
        Intent notificationIntent = pm.getLaunchIntentForPackage(mPackageName);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        mContext.startActivity(notificationIntent);
    }

    public static boolean isJSONValid(String test) {
        try {
            new JSONObject(test);
        } catch (JSONException ex) {
            try {
                new JSONArray(test);
            } catch (JSONException ex1) {
                return false;
            }
        }
        return true;
    }
}
