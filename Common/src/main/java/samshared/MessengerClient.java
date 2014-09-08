package samshared;

import android.app.Activity;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.masterbaron.intenttunnel.common.R;

import samshared.MessengerService;

/** Both extensible and callable as an intent; if you want to use it as a quick and dirty way to send messages,
 * start the Intent with the a value of the extra TER_STRING as an activity for a result; that way you will return
 * to wherever you like when you are done
 * **/
public class MessengerClient extends Activity {
    /** Messenger for communicating with the service. */
    Messenger mService = null;

    /** Flag indicating whether we have called bind on the service. */
    boolean mBound;
    boolean hasMessage = false;

    public final static String EXTRA_MESSAGE = "BT";
    public final static String TER_STRING = "TS"; //. if there's a string here, we will terminate this activity upon starting it


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
                    Log.d("d","Received from service: " + msg.arg1);
                    break;
                case MessengerService.STRING_MSG:
                    String mess = msg.getData().getString(MessengerService.STRING_KEY);
                    Toast.makeText(getApplicationContext(), mess, Toast.LENGTH_SHORT).show();
                    Log.d("string", "Received mess " + mess);
                    break;
                default:
                    super.handleMessage(msg);
            }
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
            sendMessage();

            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                Message msg = Message.obtain(null,
                        MessengerService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
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

    /** This is how a message is sent, and it's awkward.
     * MessengerClient, is a class an ordinary activity can extend in order to be able to send Messages.
     * When it's instantiated, it's going to try to send any messages put inside the extra string
     * with the tag BT (see static variable) at the point of onResume(), so any clients extending this
     * can simply add the extra string you want sent at the point, onStart(). It will
     * try to send the message both at the point of binding and at the point of resuming. This awkwardness
     * is an attempt to make a service and an activity be able to send a messsage with a common interface.
     **/
    public void sendMessage() {
        if (!mBound) return;
        if (!hasMessage) return;
        Intent intent = getIntent(); //.. get the intent that instatiated us
        String message = intent.getExtras().getString(EXTRA_MESSAGE);

        // Create and send a message to the service, using a supported 'what' value
        Message msg = Message.obtain(null, MessengerService.STRING_MSG, 0, 0);
        Bundle b = new Bundle(); b.putString(MessengerService.STRING_KEY, message);
        msg.setData(b);

        try {
            mService.send(msg);
            hasMessage = false;
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        String ter = intent.getExtras().getString(TER_STRING);
        if (ter !=null)
            this.finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent(); //.. get the intent that instatiated us
        String message = intent.getStringExtra(EXTRA_MESSAGE);
        if (message != null) {
            hasMessage = true;
            sendMessage();
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to the service. This is gonna go off in a separate thread, so it is naive to think, that we can send a message at instantiatoin
        bindService(new Intent(this, MessengerService.class), mConnection,
                Context.BIND_AUTO_CREATE);

    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }
}