package com.mohamedfadiga.foton;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
import android.widget.Toast;

import java.io.IOException;

import io.particle.android.sdk.cloud.ParticleCloud;
import io.particle.android.sdk.cloud.ParticleCloudException;
import io.particle.android.sdk.cloud.ParticleCloudSDK;
import io.particle.android.sdk.cloud.ParticleDevice;
import io.particle.android.sdk.cloud.ParticleEvent;
import io.particle.android.sdk.cloud.ParticleEventHandler;
import io.particle.android.sdk.utils.Async;

public class FotonService extends Service {
    private final IBinder binder = new LocalBinder();
    private ServiceCallbacks serviceCallbacks;
    private SharedPreferences sharedData;
    private ParticleDevice device;
    private ParticleCloud sparkCloud;
    private boolean showNotification;

    public void showNotification(boolean val) {
        showNotification = val;
        if (!val) stopForeground(true);
        else startForeground(93, new Notification());
        SharedPreferences.Editor editor = sharedData.edit();
        editor.putBoolean("showNotification", val);
        editor.commit();
    }

    public void subscribeMotionEvent() {
        try {
            sparkCloud.subscribeToDeviceEvents("Motion detected", "370031000f47343339383037", new ParticleEventHandler() {
                public void onEvent(String eventName, ParticleEvent event) {
                    SharedPreferences.Editor editor = sharedData.edit();
                    editor.putString("lastMotion", event.publishedAt.toString());
                    editor.commit();
                    serviceCallbacks.motionDetected();
                    if (showNotification) {
                        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                        NotificationCompat.Builder notificationBuilder = (NotificationCompat.Builder) new NotificationCompat.Builder(FotonService.this)
                                .setContentTitle("Motion detected!")
                                .setSound(alarmSound)
                                .setSmallIcon(R.drawable.logo)
                                .setContentText("Last detection at: " + sharedData.getString("lastMotion", "none"));

                        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                        notificationManager.notify(93, notificationBuilder.build());
                    }
                }

                public void onEventError(Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        sharedData = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        showNotification = sharedData.getBoolean("showNotification", true);
        ParticleCloudSDK.init(this);
        Async.executeAsync(ParticleCloudSDK.getCloud(), new Async.ApiWork<ParticleCloud, Object>() {
            @Override
            public Object callApi(ParticleCloud c) throws ParticleCloudException, IOException {
                try {
                    sparkCloud = c;
                    sparkCloud.logIn("yourmail", "yourpassword");
                    device = sparkCloud.getDevice("1234567890987654321");
                    subscribeMotionEvent();
                    if (serviceCallbacks != null) serviceCallbacks.fotonServiceReady();
                    if (showNotification) startForeground(93, new Notification());
                } catch (final ParticleCloudException e) {
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(FotonService.this, e.getBestMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                    LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(FotonService.this);
                    localBroadcastManager.sendBroadcast(new Intent("close"));
                    FotonService.this.stopSelf();
                }
                return -1;
            }

            @Override
            public void onSuccess(Object value) {

            }

            @Override
            public void onFailure(ParticleCloudException e) {
                Toast.makeText(FotonService.this, e.getBestMessage(), Toast.LENGTH_LONG);
            }
        });
        return Service.START_STICKY;
    }


    public class LocalBinder extends Binder {
        FotonService getService() {
            return FotonService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public ParticleDevice getDevice() {
        return device;
    }

    public interface ServiceCallbacks {
        void fotonServiceReady();

        void motionDetected();
    }

    public void setCallbacks(ServiceCallbacks callbacks) {
        serviceCallbacks = callbacks;
    }
}
