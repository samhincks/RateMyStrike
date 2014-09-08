package com.gdkdemo.sensor.motion;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.MotionEvent;

import com.gdkdemo.sensor.motion.service.MotionSensorDemoLocalService;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.masterbaron.intenttunnel.router.RouterService;

import samshared.MessengerService;

/** This is what's happening here.
 * We're going to reload the IntentTunnel onto the Android app. No - we don't need to?
 * We're going to reload the pebbleglasshub, which takes in the libraries it needs, onto Android
 * We're going to relaod Sensor onto Google Glass. That app is going to push its acceloremeter data onto bluetooth,
 * which is going to relay it to the RouterService, which finally is going to push it to our MessengerService. At
 * the Messenger Service, we will be happy to simply view the data that has arrived, but ultimately, we're going to
 * want our Accelerometer Activity listening in on this data, and in addition to supporting the possibility of displaying
 * its pebble data, display its glass data.
 **/
// The "main" activity...
public class MotionSensorDemoActivity extends Activity
{
    // For tap event
    private GestureDetector mGestureDetector;

    // Service to handle liveCard publishing, etc...
    private boolean mIsBound = false;
    private MotionSensorDemoLocalService motionSensorDemoLocalService;


    private ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d("onServiceConnected() called.");
            motionSensorDemoLocalService = ((MotionSensorDemoLocalService.LocalBinder)service).getService();
        }
        public void onServiceDisconnected(ComponentName className) {
            Log.d("onServiceDisconnected() called.");
            motionSensorDemoLocalService = null;
        }
    };
    private void doBindService()
    {
        bindService(new Intent(this, MotionSensorDemoLocalService.class), serviceConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }
    private void doUnbindService() {
        if (mIsBound) {
            unbindService(serviceConnection);
            mIsBound = false;
        }
    }
    private void doStartService()
    {
        startService(new Intent(this, MotionSensorDemoLocalService.class));
    }
    private void doStopService()
    {
        stopService(new Intent(this, MotionSensorDemoLocalService.class));
    }


    @Override
    protected void onDestroy()
    {
        doUnbindService();
        // doStopService();   // TBD: When do we call Stop service???
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Log.d("onCreate() called.");

        setContentView(R.layout.activity_motionsensordemo);

        // For gesture handling.
        mGestureDetector = createGestureDetector(this);
        //.. THIS hack worked, we initiate from here, just to get the Router Service to ping
        //.. the messenger service, so that this could be our interface for relaying messages
        bindService(new Intent(this, RouterService.class), mConnection,
                Context.BIND_AUTO_CREATE);        // bind does not work. We need to call start() explilicitly...
        // doBindService();
        doStartService();
        // TBD: We need to call doStopService() when user "closes" the app....
        // ...

    }

    /** Sam Hincks added this
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
        }

        public void onServiceDisconnected(ComponentName className) {
        }
    };

    @Override
    protected void onResume()
    {
        super.onResume();
        Log.d("onResume() called.");

    }



    // TBD:
    // Just use context menu instead of gesture ???
    // ...

    @Override
    public boolean onGenericMotionEvent(MotionEvent event)
    {
        if (mGestureDetector != null) {
            return mGestureDetector.onMotionEvent(event);
        }
        return false;
    }

    private GestureDetector createGestureDetector(Context context)
    {
        GestureDetector gestureDetector = new GestureDetector(context);
        //Create a base listener for generic gestures
        gestureDetector.setBaseListener( new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {
                if(Log.D) Log.d("gesture = " + gesture);
                if (gesture == Gesture.TAP) {
                    handleGestureTap();
                    return true;
                } else if (gesture == Gesture.TWO_TAP) {
                    handleGestureTwoTap();
                    return true;
                }
                return false;
            }
        });
        return gestureDetector;
    }

    private void handleGestureTap()
    {
        Log.d("handleGestureTap() called.");
        doStopService();
        finish();
    }

    private void handleGestureTwoTap()
    {
        Log.d("handleGestureTwoTap() called.");
    }


}
