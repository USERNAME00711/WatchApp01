/*
 * Copyright 2022 Samsung Electronics Co., Ltd. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.lastone;

import static android.content.pm.PackageManager.PERMISSION_DENIED;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.icu.text.SimpleDateFormat;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

//import com.chaquo.python.PyObject;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.example.lastone.databinding.ActivityMainBinding;

//import com.chaquo.python.Python;
//import com.chaquo.python.android.AndroidPlatform;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

    private final static String APP_TAG = "MainActivity";
    private final static int MEASUREMENT_DURATION = 10000;
    private final static int MEASUREMENT_TICK = 200;

    private final AtomicBoolean isMeasurementRunning = new AtomicBoolean(false);
    Thread uiUpdateThread = null;
    private TextView txtHeartRate;
    private TextView txtStatus;
    private TextView txtClassification;
    private Button butStart;
    private CircularProgressIndicator measurementProgress = null;



    private SensorManager mSensorManager;
    private Sensor ppg;

    private static PPGvalue ppgValue;
    FirebaseDatabase firebaseDatabase;
    DatabaseReference databaseReference;

    private static int count;
    private static int id;
    private static double time = -0.02;
    private static int measureNumber = 0;

    List<Float> ppgSignal = new ArrayList<Float>();
    Python py;
    private static String bgl;

    final CountDownTimer countDownTimer = new CountDownTimer(MEASUREMENT_DURATION, MEASUREMENT_TICK) {
        @Override
        public void onTick(long timeLeft) {
            if (isMeasurementRunning.get()) {
                txtStatus.setText("Measuring...");
                runOnUiThread(() ->
                        measurementProgress.setProgress(measurementProgress.getProgress() + 1, true));
            } else
                if(measurementProgress.getProgress() == MEASUREMENT_DURATION) {
             }
                else {
                cancel();
             }
        }

        @Override
        public void onFinish() {

            Log.i(APP_TAG, "Finish measurement");
            runOnUiThread(() ->
            {
                mSensorManager.unregisterListener(mLightSensorListener);
                txtStatus.setText("Press Measure to Initiate Measurement");
                txtStatus.invalidate();
                getBGLvalue();
                butStart.setText(R.string.StartLabel);
                measurementProgress.invalidate();
            });
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            isMeasurementRunning.set(false);
        }
    };

    public void runPython(){
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }

        Log.d("PYTHON", "executing ----"); //Don't ignore potential errors!

        py = Python.getInstance();
        Object[] objects = ppgSignal.toArray();
        PyObject array = PyObject.fromJava(objects);
        String textPy = py.getModule("script").callAttr("process_signal", array).toString();
        bgl = textPy;
       // Float prediction = Float.parseFloat(textPy.substring(1,textPy.length()-1));
       // bgl = prediction.intValue();
        Log.d("PYTHON", String.valueOf(bgl)); //Don't ignore potential errors!
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        txtHeartRate = binding.txtHeartRate;
        txtStatus = binding.txtStatus;
        txtClassification = binding.txtClassification;
        butStart = binding.butStart;
        measurementProgress = binding.progressBar;


        id = 0;
        count = 0;

        adjustProgressBar(measurementProgress);

        mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));


        firebaseDatabase = FirebaseDatabase.getInstance();

        databaseReference = firebaseDatabase.getReference();

        Query query = databaseReference.child("users").child("zmjxaSlF0CZOWZdIprw1AMjAEFF2")
                .child("measures").orderByKey().limitToLast(1);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for(DataSnapshot ds : dataSnapshot.getChildren()) {
                    String key = ds.getKey();
                    measureNumber = Integer.valueOf(key);
                    Log.d("MEASURE", String.valueOf(measureNumber+1)); //Don't ignore potential errors!
                    databaseReference.child("users").child("zmjxaSlF0CZOWZdIprw1AMjAEFF2")
                            .child("measures").child(String.valueOf(measureNumber+1))
                            .child("measureNumber").setValue(String.valueOf(measureNumber+1));
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d("TAG", databaseError.getMessage()); //Don't ignore potential errors!
            }
        });






        if (ActivityCompat.checkSelfPermission(getApplicationContext(), getString(R.string.BodySensors)) == PackageManager.PERMISSION_DENIED)
            requestPermissions(new String[]{Manifest.permission.BODY_SENSORS}, 0);


    }

    @Override
    protected void onResume(){
        super.onResume();
        id = 0;
        count = 0;
        time = -0.02;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    void adjustProgressBar(CircularProgressIndicator progressBar) {
        DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();
        int pxWidth = displayMetrics.widthPixels;
        int padding = 1;
        progressBar.setPadding(padding, padding, padding, padding);
        int trackThickness = progressBar.getTrackThickness();

        int progressBarSize = pxWidth - trackThickness - 2 * padding;
        progressBar.setIndicatorSize(progressBarSize);
    }

    public void getBGLvalue(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSS");
        Date now = new Date();
        String strDate = sdf.format(now);

        databaseReference.child("users").child("zmjxaSlF0CZOWZdIprw1AMjAEFF2")
                .child("measures").child(String.valueOf(measureNumber+1))
                .child("BGL").setValue(String.valueOf(bgl));

        databaseReference.child("users").child("zmjxaSlF0CZOWZdIprw1AMjAEFF2")
                .child("measures").child(String.valueOf(measureNumber+1))
                .child("Date").setValue(strDate);

        txtHeartRate.setText(String.valueOf(bgl));
        txtClassification.setText(bgl);
        txtClassification.setTextColor(Color.parseColor("#00ff00"));

        /*Query BGLquery = databaseReference.child("users").child("zmjxaSlF0CZOWZdIprw1AMjAEFF2")
                .child("measures").child(String.valueOf(measureNumber+1));
        BGLquery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for(DataSnapshot ds : dataSnapshot.getChildren()) {
                    int value = ds.child("BGL").getValue(Integer.class);
                    txtHeartRate.setText(String.valueOf(value));

                    isMeasurementRunning.set(false);
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d("TAG", databaseError.getMessage()); //Don't ignore potential errors!
            }
        });*/

    }


    public void performMeasurement(View view) {

        id = 0;
        count = 0;
        time = -0.02;

        if (isPermissionsOrConnectionInvalid()) { //1
            return;
        }

        if (!isMeasurementRunning.get()) {
            butStart.setText(R.string.StopLabel);
            measurementProgress.setProgress(0);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            isMeasurementRunning.set(true);
            
            uiUpdateThread = new Thread(countDownTimer::start); //2
            uiUpdateThread.start();
            SensorManager mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));
            ppg = mSensorManager.getDefaultSensor(65537);
            //databaseReference.child("PPGsignal").removeValue();
            mSensorManager.registerListener(mLightSensorListener, ppg, SensorManager.SENSOR_DELAY_GAME);

        } else {
            butStart.setEnabled(false);
            isMeasurementRunning.set(false);
            mSensorManager.unregisterListener(mLightSensorListener);

            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Handler progressHandler = new Handler(Looper.getMainLooper());
            progressHandler.postDelayed(() ->
                    {
                        butStart.setText(R.string.StartLabel);
                        txtStatus.setText("Press Measure to initiate Measurement");
                        mSensorManager.unregisterListener(mLightSensorListener);
                        measurementProgress.setProgress(0);
                        butStart.setEnabled(true);
                    }, MEASUREMENT_TICK * 2
            );
        }
    }

    // Send the sensor data to the PC over the network
    private void sendSensorData(Float value, String time) {
        ppgValue = new PPGvalue();
        ppgValue.setValue(value);
        ppgValue.setTime(time);
        ppgSignal.add(value);
        databaseReference.child("users").child("zmjxaSlF0CZOWZdIprw1AMjAEFF2")
                .child("measures").child(String.valueOf(measureNumber+1))
                .child("PPG").child(String.valueOf(id)).setValue(ppgValue);

        id++;
    }

    private SensorEventListener mLightSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {


            Float value = event.values[2];
            time += 0.02;


            if(count > 50) {
                sendSensorData(value, String.valueOf(time));
                //Log.d("MY_PPG", value + "");
            };
            count++;

            if(count==600) {
                runPython();
                mSensorManager.unregisterListener(mLightSensorListener);
            }
        }


        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            //Log.d("MY_APP", sensor.toString() + " - " + accuracy);
        }
    };


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 0) {
            for (int i = 0; i < permissions.length; ++i) {
                if (grantResults[i] == PERMISSION_DENIED) {
                    //User denied permissions twice - permanent denial:
                    if (!shouldShowRequestPermissionRationale(permissions[i]))
                        Toast.makeText(getApplicationContext(), getString(R.string.PermissionDeniedPermanently), Toast.LENGTH_LONG).show();
                        //User denied permissions once:
                    else
                        Toast.makeText(getApplicationContext(), getString(R.string.PermissionDeniedRationale), Toast.LENGTH_LONG).show();
                    break;
                }
            }

        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private boolean isPermissionsOrConnectionInvalid() {
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), getString(R.string.BodySensors)) == PackageManager.PERMISSION_DENIED)
            requestPermissions(new String[]{Manifest.permission.BODY_SENSORS}, 0);

        return false;
    }
}