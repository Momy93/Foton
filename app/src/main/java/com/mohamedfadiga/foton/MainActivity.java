package com.mohamedfadiga.foton;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.io.IOException;

import io.particle.android.sdk.cloud.ParticleCloud;
import io.particle.android.sdk.cloud.ParticleCloudException;
import io.particle.android.sdk.cloud.ParticleCloudSDK;
import io.particle.android.sdk.cloud.ParticleDevice;
import io.particle.android.sdk.utils.Async;

public class MainActivity extends AppCompatActivity implements FotonService.ServiceCallbacks {

    private FotonService fotonService;
    private boolean bound = false;
    private ProgressDialog loading;
    private Handler timerHandler = new Handler();
    private ParticleDevice device;
    private TextView tempValueView;
    private TextView humidValueView;
    private Runnable timerRunnable;
    private TextView lastMotionView;
    private CheckBox motionCheckBox;
    private LocalBroadcastManager localBroadcastManager;
    private SharedPreferences sharedData;

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("close")) {
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("close");
        localBroadcastManager.registerReceiver(broadcastReceiver, intentFilter);
        sharedData = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        motionCheckBox = (CheckBox) findViewById(R.id.motionCheckBox);
        motionCheckBox.setChecked(sharedData.getBoolean("showNotification", true));
        tempValueView = (TextView) findViewById(R.id.tempValueView);
        humidValueView = (TextView) findViewById(R.id.humValueView);
        lastMotionView = (TextView) findViewById(R.id.lastMotion);
        lastMotionView.setText("Last: " + sharedData.getString("lastMotion", "none"));
        Intent intent = new Intent(this, FotonService.class);
        if (!isServiceRunning(FotonService.class)) {
            loading = ProgressDialog.show(this, "Loading", "please wait", true);
        }

        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (timerRunnable != null) timerRunnable.run();
    }


    @Override
    protected void onStop() {
        super.onStop();
        timerHandler.removeCallbacks(timerRunnable);
        if (bound) {
            unbindService(connection);
            bound = false;
        }
    }


    @Override
    public void motionDetected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                lastMotionView.setText("Last: " + sharedData.getString("lastMotion", "none"));
            }
        });
    }


    @Override
    public void fotonServiceReady() {
        device = fotonService.getDevice();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                Async.executeAsync(ParticleCloudSDK.getCloud(), new Async.ApiWork<ParticleCloud, Object>() {
                    @Override
                    public Object callApi(ParticleCloud c) throws ParticleCloudException, IOException {
                        double temp = 0, humid = 0;
                        try {
                            temp = device.getDoubleVariable("temp");
                            humid = device.getDoubleVariable("humid");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        double[] data = new double[2];
                        data[0] = temp;
                        data[1] = humid;
                        return data;
                    }

                    @Override
                    public void onSuccess(final Object value) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                double[] data = (double[]) value;
                                tempValueView.setText("" + String.format("%.2f", data[0]) + "°C");
                                humidValueView.setText("" + String.format("%.2f", data[1]) + "%");
                            }
                        });
                    }

                    @Override
                    public void onFailure(ParticleCloudException e) {
                        e.printStackTrace();
                    }
                });
                timerHandler.postDelayed(this, 5000);
            }
        };
        timerRunnable.run();
        motionCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                fotonService.showNotification(isChecked);
            }
        });
        if (loading != null) loading.dismiss();
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        localBroadcastManager.unregisterReceiver(broadcastReceiver);
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            FotonService.LocalBinder binder = (FotonService.LocalBinder) service;
            fotonService = binder.getService();
            bound = true;
            fotonService.setCallbacks(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            bound = false;
        }
    };
}
