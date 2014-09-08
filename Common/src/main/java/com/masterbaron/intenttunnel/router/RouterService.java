package com.masterbaron.intenttunnel.router;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import java.util.Iterator;
import java.util.LinkedList;

import samshared.MessengerClient;
import samshared.MessengerService;

/**
 * Created by Van Etten on 12/9/13.
 */
public class RouterService extends Service implements Handler.Callback {
    private static final String TAG = RouterService.class.getSimpleName();

    protected static final int ROUTER_MESSAGE_BROADCAST_INTENT = 1000;
    protected static final int ROUTER_MESSAGE_STARTSERVICE_INTENT = 1001;
    protected static final int ROUTER_MESSAGE_STARTACTIVITY_INTENT = 1002;
    protected static final int ROUTER_ACCELEROMETER = 1003;


    protected static final int ROUTER_MESSAGE_SEND_QUEUED_MESSAGES = 1100;

    private static Boolean isGlass = null;
    private static RouterService service;

    private final Messenger mMessenger = new Messenger(new IncomingHandler());
    private final LinkedList<Packet> mPackets = new LinkedList<Packet>();

    protected Handler mHandler;
    private ClientService mClientService;
    private ServerService mServerService;
    private long lastClientError;

    /**
     * Check if the service is still active
     *
     * @return
     */
    public static boolean isServicesRunning() {
        return service != null;
    }

    /**
     * Get the current status of the client bluetooth connection
     *
     * @return
     */
    public static String getClientStatus() {
        RouterService routerService = service;
        return routerService != null ? routerService.mClientService.getStatus() : "Stopped";
    }

    /**
     * Get the current status of the server bluetooth connection
     *
     * @return
     */
    public static String getServerStatus() {
        RouterService routerService = service;
        return routerService != null ? routerService.mServerService.getStatus() : "Stopped";
    }

    /**
     * Setup work for the start of the router service
     */
    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        super.onCreate();
        service = this;

        getPreferences(this).registerOnSharedPreferenceChangeListener(mPreferenceHandler);

        startService(new Intent(this, RouterService.class));

        // setup handler and messenger
        mHandler = new Handler(this);

        mClientService = new ClientService(this);
        mServerService = new ServerService(this);
        mServerService.startConnection();

        //.. Sam: start the connection to my interprocess messenger
        // Bind to the service. This is gonna go off in a separate thread, so it is naive to think, that we can send a message at instantiatoin
        bindService(new Intent(this, MessengerService.class), mConnection,
                Context.BIND_AUTO_CREATE);
    }

    /**
     * cleanup the router service
     */
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");

        getPreferences(this).unregisterOnSharedPreferenceChangeListener(mPreferenceHandler);
        mClientService.stop();
        mServerService.stop();
        service = null;

        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        super.onDestroy();
    }

    /**
     * Process incoming intents to the service
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                Log.d("onStartCommand", "intent=" + intent.toUri(0));
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_ON) {
                    mServerService.stopConnection();
                    mServerService.startConnection();
                } else if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    mServerService.stopConnection();

                }
            }
        }
        return START_STICKY; // "prevent" this service from stopping!
    }

    /**
     * Return the binder for the router
     *
     * @param intent
     * @return
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    /**
     * Route a message to a bluetooth service.
     * If the bluetooth server is connected, send to that service
     * otherwise, send to the client bluetooth to be sent
     */
    protected void sentToService() {
        try {
            expirePackets();
            if ( mPackets.size() > 0 && isBluetoothEnabled() ) {
                if (mServerService.isConnected()) {
                    if ( !mServerService.isSending() ) {
                        Log.d(TAG, "sentToService: serviceService running");
                        mServerService.sendIntent(mPackets.poll());
                    }
                } else {
                    if (!mClientService.isRunning()) {
                        if ( lastClientError + 5000 < System.currentTimeMillis() ) {
                            Log.d(TAG, "sentToService: clientService not running");
                            mClientService.startConnection();
                        } else {
                            Log.d(TAG, "sentToService: clientService error delay");
                            mHandler.sendEmptyMessageDelayed(ROUTER_MESSAGE_SEND_QUEUED_MESSAGES, 1000);
                        }
                    } else if ( !mServerService.isSending() ) {
                        Log.d(TAG, "sentToService: sending to clientService");

                        //.. We go in here, after initiating the send, and mPackets
                        mClientService.sendIntent(mPackets.poll());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to route message", e);
        }
    }

    private void expirePackets() {
        // now expire old ones
        Iterator<Packet> iterator = mPackets.iterator();
        while ( iterator.hasNext() ) {
            if ( iterator.next().isExpired() ) {
                iterator.remove();
            }
        }

        // cap size of queue
        while ( mPackets.size() > 100 ) {
            mPackets.removeFirst();
        }
    }

    /**
     * Hande messages coming from a 3rd party that has bound to the router.
     * @param msg
     * @return
     */
    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == ROUTER_MESSAGE_SEND_QUEUED_MESSAGES) {
            sentToService();
            return true;
        }
        return false;
    }

    private void processQueue() {
        mHandler.sendEmptyMessage(ROUTER_MESSAGE_SEND_QUEUED_MESSAGES);
    }

    protected void onIntentSendComplete(BluetoothService bluetoothService, Packet packet) {
        processQueue();
    }

    protected void onIntentSendFail(BluetoothService bluetoothService, Packet packet) {
        if ( !bluetoothService.isBTServer() ) {
            lastClientError = System.currentTimeMillis();
            if ( !mServerService.isConnected() ) {
                mServerService.onConnectionLost();
            }
        }
        if( packet != null ) {
            mPackets.addFirst(packet);
        }
        processQueue();
    }

    protected void onConnectComplete(BluetoothService bluetoothService) {
        processQueue();
    }

    public static void pushRecentMessage(String message) {
    }

    /**
     * Handle all internal and external messages*/
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "send to service message: " + msg.what);
            if (msg.what == ROUTER_MESSAGE_BROADCAST_INTENT || msg.what == ROUTER_MESSAGE_STARTSERVICE_INTENT
                    || msg.what == ROUTER_MESSAGE_STARTACTIVITY_INTENT || msg.what == MessengerService.MSG_ACCELEROMETER ||
                    msg.what == MessengerService.MSG_TRIGGER_GLASS_CAMERA) {
                if ( msg.obj instanceof  Intent ) {
                    expirePackets();

                    Intent intent = (Intent) msg.obj;
                    Log.d(TAG + "balle", "send message: " + intent.toUri(0) + " " +intent.getAction() + " , " + msg.what);
                    mPackets.add( new Packet(msg.what, intent) );
                    processQueue();
                }
            }
            
        }
    }

    public boolean isBluetoothEnabled() {
        // if no bluetooth or off, then just stop since we cant do anything
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();

        if (defaultAdapter == null || !defaultAdapter.isEnabled()) {
            Log.d(TAG, "bluetooth adapter disabled");
            return false;
        }
        return true;
    }

    public static boolean isGlass() {
        if( isGlass == null ) {
            try {
                RouterService.class.getClassLoader().loadClass("com.google.android.glass.timeline.LiveCard");
                isGlass = true;
            } catch (Exception e) {
                isGlass = false;
            }
        }

        return isGlass;
    }

    public static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences("Router", Context.MODE_PRIVATE);
    }

    public static void setDeviceAddress(Context context, String address) {
        SharedPreferences.Editor edit = getPreferences(context).edit();
        edit.putString("bt.device.address", address);
        edit.commit();
    }
    public static String getDeviceAddress(Context context) {
        return getPreferences(context).getString("bt.device.address", null);
    }

    private SharedPreferences.OnSharedPreferenceChangeListener mPreferenceHandler = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if( "bt.device.address".equals(key)) {
                mClientService.onConnectionLost();
            }
        }
    };

    /**-------------------
     * Sam Hincks code follows. Mainly code for passing inter-process messages that hav been intercepted by bluetooth
     *
     * -------------------**/
    /** Messenger for communicating with the service. */
    Messenger mService = null;

    /** Flag indicating whether we have called bind on the service. */
    boolean mBound;

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */


    public void sendAccelerometerMessage(String message) {
        if (!mBound) return;

        // Create and send a message to the service, using a supported 'what' value
        Message msg = Message.obtain(null, MessengerService.STRING_MSG, 0, 0);
        Bundle b = new Bundle(); b.putString(MessengerService.STRING_KEY, message);
        msg.setData(b);

        try {
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }



    public void sendCameraMessage() {
        if (!mBound) return;
        // Create and send a message to the service, using a supported 'what' value
        Message msg = Message.obtain(null, MessengerService.MSG_RECEIVE_GLASS_TRIGGER, 0, 0);

        try {
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            mService = new Messenger(service);
            mBound = true;
            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                Message msg = Message.obtain(null,
                        MessengerService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger; //.. replies will go up to the one defined already
                msg.arg1 = 0;
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
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
            mBound = false;
        }
    };
}
