package by.chemerisuk.cordova.firebase;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


public class AlertActivity extends Activity {
    public static int isAlertShown = 0;
    private static final String mLayoutName = "activity_alert";
    private static final String mAudioName = "audio";
    private Context mContext;
    private String mPackageName;
    private Vibrator mVibrator;
    private Ringtone mRingtone;

    private Utils utils;

    public void onCreate(Bundle state) {
        super.onCreate(state);
        mContext = getApplicationContext();
        utils = new Utils(mContext,this);
        mPackageName = mContext.getPackageName();
        setLayoutParams();
        setContentView(getLayout());
        isAlertShown++;
        utils.showAlert(true);
    }

    public void setLayoutParams() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);
    }

    public int getLayout() {
        int layout = mContext.getResources().getIdentifier(mLayoutName, "layout", mPackageName);
        return layout;
    }

    public void onDestroy() {
        isAlertShown--;
        super.onDestroy();
    }
}
