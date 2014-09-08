package com.gdkdemo.sensor.motion.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.gdkdemo.sensor.motion.MotionSensorDemoActivity;
import com.gdkdemo.sensor.motion.R;
import com.gdkdemo.sensor.motion.common.SensorValueStruct;
import com.google.android.glass.media.CameraManager;
import com.google.android.glass.timeline.LiveCard;
import com.masterbaron.intenttunnel.android.AndroidConfigureActivity;
import com.masterbaron.intenttunnel.router.RouterService;

import java.net.BindException;

import samshared.MessengerService;

public class MotionSensorDemoLocalService extends Service implements SensorEventListener
{
    // Sensor manager
    private SensorManager mSensorManager = null;

    // Motion sensors
    private Sensor mSensorAccelerometer = null;
    private Sensor mSensorGravity = null;
    private Sensor mSensorLinearAcceleration = null;
    private Sensor mSensorGyroscope = null;
    private Sensor mSensorRotationVector = null;


    // Last known motionsensor values.
    private SensorValueStruct lastSensorValuesAccelerometer = null;
    private SensorValueStruct lastSensorValuesGravity = null;
    private SensorValueStruct lastSensorValuesLinearAcceleration = null;
    private SensorValueStruct lastSensorValuesGyroscope = null;
    private SensorValueStruct lastSensorValuesRotationVector = null;

    // TBD:
    // Need a timer, etc. to refresh the UI (LiveCard) ever x seconds, etc..
    // For now, we just use the "last timestamp".
    private long lastRefreshedTime = 0L;

    //.. Sam added these
    private static String TAG = MotionSensorDemoLocalService.class.getName();
    public Messenger mService; //.. a messenger service - we bind this to the router which passes bluetooth messages

    public boolean mBound; //.. true if bound
    private TextView mErrorText;

    // No need for IPC...
    public class LocalBinder extends Binder {
        public MotionSensorDemoLocalService getService() {
            return MotionSensorDemoLocalService.this;
        }
    }
    private final IBinder mBinder = new LocalBinder();


    @Override
    public void onCreate()
    {
        super.onCreate();
        initializeSensorManager();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.i("Received start id " + startId + ": " + intent);
        onServiceStart();

        try {

            //... THIS IS HOW WE WOULD BIND DIRECTLY WITH a PRELOADED LIBRARY
           /* Intent in = new Intent();
            in.setClassName("com.masterbaron.intenttunnel", "com.masterbaron.intenttunnel.router.RouterService");
            if ( !bindService(in, mConnection, Context.BIND_AUTO_CREATE) ) {
                throw new BindException("failed to bind");
            }*/



            //.. NOW THIS IS GETTING A BIT RETARDED, WE ARE BINDING OURSELVES TO MY MESSENGERSERVICE, WHICH IS
            //.. BOUND TO ROUTER SERVICE, WHICH WE ARE NOW BINDING OURSELVES TO. ROUTER SERVICE IS BOUND TO CLIENTSERVICE
            //.. WHICH IS BOUND TO THE BLUETOOTH CONNECTION CLASSES. AT SOME POINT, I SHOULD STRAIGHTEN THINGS OUT AND
            //.. INTEGRATE DIRECTLY WITH THE BLUETOOTH LIBRARIES .

            //.. THIS IS HOW WE BIND WITH SOMETHING WE HAVE LOADED OURSELVES
            // Bind to the service. This is gonna go off in a separate thread, so it is naive to think, that we can send a message at instantiatoin
            bindService(new Intent(this, MessengerService.class), mConnection,
                    Context.BIND_AUTO_CREATE);


        } catch (Exception e) {
            mErrorText.setText("Error: " + e.getMessage());
            android.util.Log.e(TAG, "onStart error", e);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        // ????
        onServiceStart();
        return mBinder;
    }

    @Override
    public void onDestroy()
    {
        if ( mBound ) {
            unbindService(mConnection);
            mBound = false;
        }
        onServiceStop();
        super.onDestroy();
    }


    @Override
    public void onSensorChanged(SensorEvent event)
    {
       // Log.d("onSensorChanged() called.");

        processMotionSensorData(event);

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {
       // Log.d("onAccuracyChanged() called.");

    }



    // Service state handlers.
    // ....

    private boolean onServiceStart()
    {
        Log.d("onServiceStart() called.");

        mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mSensorGravity, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mSensorLinearAcceleration, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mSensorGyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mSensorRotationVector, SensorManager.SENSOR_DELAY_NORMAL);

        // Publish live card...
        publishCard(this);

        return true;
    }

    private boolean onServicePause()
    {
        Log.d("onServicePause() called.");
        return true;
    }
    private boolean onServiceResume()
    {
        Log.d("onServiceResume() called.");
        return true;
    }

    private boolean onServiceStop()
    {
        Log.d("onServiceStop() called.");

        mSensorManager.unregisterListener(this);

        return true;
    }

    // For live cards...
    private void publishCard(Context context)
    {
        publishCard(context, false);
    }

    private void publishCard(Context context, boolean update) {
        if (Log.D) Log.d("publishCard() called: update = " + update);
        if ( update == true) {


            RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.livecard_motionsensordemo);
            String content = "";
            if (lastSensorValuesAccelerometer != null) {
                content += "Accelerometer:" + lastSensorValuesAccelerometer.toString() + "\n";
            }
            if (lastSensorValuesGravity != null) {
                content += "Gravity:" + lastSensorValuesGravity.toString() + "\n";
            }
            if (lastSensorValuesLinearAcceleration != null) {
                content += "Linear Acceleration:" + lastSensorValuesLinearAcceleration.toString() + "\n";
            }
            if (lastSensorValuesGyroscope != null) {
                content += "Gyroscope:" + lastSensorValuesGyroscope.toString() + "\n";
            }
            if (lastSensorValuesRotationVector != null) {
                content += "Rotation Vector:" + lastSensorValuesRotationVector.toString() + "\n";
            }

            //.. Sam added: ship over the sensor content as a string
            try {
                sendCommand(content);
            }
            catch(Exception e) {e.printStackTrace();}
        }
    }


    // MotionSensor methods
    private void initializeSensorManager()
    {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mSensorLinearAcceleration = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mSensorGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorRotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    private void processMotionSensorData(SensorEvent event)
    {
        long now = System.currentTimeMillis();

        Sensor sensor = event.sensor;
        int type = sensor.getType();
        long timestamp = event.timestamp;
        float[] values = event.values;
        int accuracy = event.accuracy;
        SensorValueStruct data = new SensorValueStruct(type, timestamp, values, accuracy);

        switch(type) {
            case Sensor.TYPE_ACCELEROMETER:
                lastSensorValuesAccelerometer = data;
                break;
            case Sensor.TYPE_GRAVITY:
                lastSensorValuesGravity = data;
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                lastSensorValuesLinearAcceleration = data;
                break;
            case Sensor.TYPE_GYROSCOPE:
                lastSensorValuesGyroscope = data;
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                lastSensorValuesRotationVector = data;
                break;
            default:
                Log.w("Unknown type: " + type);
        }


        final long delta = 5000L;   // every 5 seconds. make this 500L later
        if(lastRefreshedTime <= now - delta) {
            // if(liveCard != null && liveCard.isPublished()) {
                // Update...
                publishCard(this, true);
            // }
            lastRefreshedTime = now;
        }
    }

    /**Sam Hincks
     * Send a String message through bluetooth; so that we can hijack this other person's code,
     * we make it the message the action of a String
     * **/
    private void sendCommand(String message) throws RemoteException {
        Message msg = Message.obtain(null, MessengerService.MSG_ACCELEROMETER, 0,0);
        msg.replyTo = mMessenger;
        Bundle b = new Bundle();
        b.putString(MessengerService.STRING_KEY, message);
        msg.setData(b);
        Log.d("sending " + message);
        mService.send(msg);
    }



    /** Sam Hincks added this
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            android.util.Log.d(TAG, "onServiceConnected()");

            mService = new Messenger(service);
            mBound = true;

            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                Message msg = Message.obtain(null,
                        MessengerService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                msg.arg1 =1;
                mService.send(msg);

                // Give it some value as an example.
                msg = Message.obtain(null,
                        MessengerService.MSG_SET_VALUE, this.hashCode(), 0);
                mService.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }

        }

        public void onServiceDisconnected(ComponentName className) {
            android.util.Log.e(TAG, "onServiceDisconnected()");
            mService = null;
            mBound = false;
        }
    };

    /**
     * Break time!
     * Problem: this is a service, so we cant use startActivityForResult. Maybe here we want to
     * launch a new activity, that does this then reutrns to the service? Will we still be collecting data then?
     * **/
    private void triggerCamera() {
        Intent intent = new Intent(this, CameraActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        startActivity(intent);
    }


    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    /**
     * Handler of incoming messages from service.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MessengerService.MSG_SAY_HELLO:
                    android.util.Log.d("d", "Received from service: " + msg.arg1);
                    break;
                case MessengerService.STRING_MSG:
                    String mess = msg.getData().getString(MessengerService.STRING_KEY);
                    android.util.Log.d("string", "Received mess " + mess);
                    break;

                case MessengerService.MSG_RECEIVE_GLASS_TRIGGER:
                    Log.i("fittballe HERE, TRIGGER THE FUCKING CAMERA");
                    triggerCamera();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }


}
