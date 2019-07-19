package kr.ac.inha.nsl.mindforecaster;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.IOException;

public class MF_DataCollectorService extends Service {

    // region Constants
    private final IBinder mBinder = new LocalBinder();

    private static final int GPS_FASTEST_INTERVAL = 10000; // 10 seconds = 10000
    private static final int GPS_SLOWEST_INTERVAL = 60000; // 10 minutes = 60000
    private static final int ACTIVITY_RECOGNITION_INTERVAL = 1000; // 1 minute = 60000
    // endregion

    // region Variables
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private ActivityRecognitionClient activityRecognitionClient;
    private PendingIntent transitionPendingIntent;
    // endregion

    // region Override
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        Log.e("MF_DataCollectorService", "onCreate");

        final String GPS_LOCATIONS_FILENAME = "mf-locations-cache.txt";
        final String ACTIVITY_RECOGNITIONS_FILENAME = "mf-activity-recognitions-cache.txt";


        // set up location callbacks
        try {
            Tools.locationDataFile = new File(getApplicationContext().getFilesDir(), GPS_LOCATIONS_FILENAME);
            boolean fileAvailable = Tools.locationDataFile.exists() || Tools.locationDataFile.createNewFile();

            if (fileAvailable) {
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
                locationCallback = new MF_LocationCallback();
            } else
                Tools.locationDataFile = null;
        } catch (IOException e) {
            e.printStackTrace();
        }

        // set up activity recognition
        try {
            Tools.activityRecognitionDataFile = new File(getApplicationContext().getFilesDir(), ACTIVITY_RECOGNITIONS_FILENAME);

            boolean fileAvailable = Tools.activityRecognitionDataFile.exists() || Tools.activityRecognitionDataFile.createNewFile();
            if (fileAvailable) {
                activityRecognitionClient = ActivityRecognition.getClient(getApplicationContext());
                transitionPendingIntent = PendingIntent.getService(getApplicationContext(), 2, new Intent(getApplicationContext(), MF_ActivityRecognitionService.class), PendingIntent.FLAG_UPDATE_CURRENT);
            } else
                Tools.locationDataFile = null;
        } catch (IOException e) {
            e.printStackTrace();
        }

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.e("MF_DataCollectorService", "onDestroy");

        fusedLocationClient.removeLocationUpdates(locationCallback);
        activityRecognitionClient.removeActivityUpdates(transitionPendingIntent);

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("MF_DataCollectorService", "onStartCommand");

        if (activityRecognitionClient != null && fusedLocationClient != null && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationRequest locationRequest = new LocationRequest();
            locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
            locationRequest.setFastestInterval(GPS_FASTEST_INTERVAL); // fastest ==> 10 seconds
            locationRequest.setInterval(GPS_SLOWEST_INTERVAL); // slowest ==> 10 minutes
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
            activityRecognitionClient.requestActivityUpdates(ACTIVITY_RECOGNITION_INTERVAL, transitionPendingIntent);

            Log.e("MF_DataCollectorService", "Data collection started...");
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.e("MF_DataCollectorService", "onTaskRemoved: restarting service...");
        Intent intent = new Intent(getApplicationContext(), MF_DataCollectorService.class);
        intent.setPackage(Tools.PACKAGE_NAME);
        PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext(), 1, intent, PendingIntent.FLAG_ONE_SHOT);

        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 100, restartServicePendingIntent);
    }
    // endregion

    private class LocalBinder extends Binder {
        //MF_DataCollectorService getService() {
        //    return MF_DataCollectorService.this;
        //}
    }
}
