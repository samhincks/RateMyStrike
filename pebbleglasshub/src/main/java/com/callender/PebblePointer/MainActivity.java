package com.callender.PebblePointer;

import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.masterbaron.intenttunnel.android.AndroidConfigureActivity;

import samshared.MessengerClient;
import samshared.MessengerService;

public class MainActivity extends ListActivity {

    /** Messenger for communicating with the service. */
    Messenger mService = null;

    /** Flag indicating whether we have called bind on the service. */
    boolean mBound;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String[] menu = {
                "Pebble Accelerometer Vectors",
                "Glass Accelerometer Vectors",
                "Glass Gravity Vectors",
                "Glass Linear Accelerometer Vectors",
                "Glass Gyroscope Vectors"
        };

        setListAdapter(new ArrayAdapter<String>(this, R.layout.simple_list_item_1, menu));
        startActivityForResult(new Intent(this, AndroidConfigureActivity.class ), 2);

    }

    @Override
    protected void onListItemClick(final ListView l, final View v, final int position, final long id) {
        Intent intent = null;

        switch (position) {
            case 0:
                intent = new Intent(this, AccelerometerActivity.class);
                intent.setAction("PA");
                break;
            case 1:
                intent = new Intent(this, AccelerometerActivity.class);
                intent.setAction("GA");
                break;
            case 2:
                intent = new Intent(this, AccelerometerActivity.class);
                intent.setAction("GG");
                break;
            case 3:
                intent = new Intent(this, AccelerometerActivity.class);
                intent.setAction("GLA");
                break;
            case 4:
                intent = new Intent(this, AccelerometerActivity.class);
                intent.setAction("GGY");
                break;
        }

        if (intent != null) {
            startActivity(intent);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to the service
       // bindService(new Intent(this, MessengerService.class), mConnection,
         //       Context.BIND_AUTO_CREATE);
       /* Intent i = new Intent(this, MessengerClient.class);
        i.putExtra(MessengerClient.EXTRA_MESSAGE, "du ar en fitta");
        i.putExtra(MessengerClient.TER_STRING,"1");
        startActivityForResult(i,3);*/


    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("samhincks2", "back here");
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
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
            mBound = false;
        }
    };
}
