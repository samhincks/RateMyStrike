package com.callender.PebblePointer;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.util.Log;
import android.widget.Toast;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.SimpleXYSeries;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.UUID;

import com.callender.PebblePointer.R;

import samshared.MessengerClient;
import samshared.MessengerService;


/**
 *  Receive accelerometer vectors from Pebble watch via PebblePointer app.
 *
 *  @author robin.callender@gmail.com
 */
public class AccelerometerActivity extends Activity {

    private static final String TAG = "PebblePointer";

    // The tuple key corresponding to a vector received from the watch
    private static final int PP_KEY_CMD = 128;
    private static final int PP_KEY_X   = 1;
    private static final int PP_KEY_Y   = 2;
    private static final int PP_KEY_Z   = 3;

    @SuppressWarnings("unused")
    private static final int PP_CMD_INVALID = 0;
    private static final int PP_CMD_VECTOR  = 1;

    public static final int VECTOR_INDEX_X  = 0;
    public static final int VECTOR_INDEX_Y  = 1;
    public static final int VECTOR_INDEX_Z  = 2;

    private static int vector[] = new int[3];

    private PebbleKit.PebbleDataReceiver dataReceiver;

    // This UUID identifies the PebblePointer app.
    private static final UUID PEBBLEPOINTER_UUID = UUID.fromString("273761eb-97dc-4f08-b353-3384a2170902");

    private static final int SAMPLE_SIZE = 30;

    private XYPlot dynamicPlot = null;

    SimpleXYSeries xSeries = null;
    SimpleXYSeries ySeries = null;
    SimpleXYSeries zSeries = null;

    //.. Sam variables
    boolean glass =false;
    boolean pebble = false;
    String dataType = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "onCreate: ");

        setContentView(R.layout.activity_accelerometer);

        vector[VECTOR_INDEX_X] = 0;
        vector[VECTOR_INDEX_Y] = 0;
        vector[VECTOR_INDEX_Z] = 0;

        PebbleKit.startAppOnPebble(getApplicationContext(), PEBBLEPOINTER_UUID);


        dynamicPlot = (XYPlot) findViewById(R.id.dynamicPlot);


        dynamicPlot.getGraphWidget().getBackgroundPaint().setColor(Color.WHITE);
        dynamicPlot.getGraphWidget().getGridBackgroundPaint().setColor(Color.WHITE);

        dynamicPlot.getGraphWidget().setDomainValueFormat(new DecimalFormat("0.0"));
        dynamicPlot.getGraphWidget().setRangeValueFormat(new DecimalFormat("0"));

        dynamicPlot.getGraphWidget().getDomainLabelPaint().setColor(Color.BLACK);
        dynamicPlot.getGraphWidget().getRangeLabelPaint().setColor(Color.BLACK);

        dynamicPlot.getGraphWidget().getDomainOriginLabelPaint().setColor(Color.BLACK);
        dynamicPlot.getGraphWidget().getDomainOriginLinePaint().setColor(Color.BLACK);
        dynamicPlot.getGraphWidget().getRangeOriginLinePaint().setColor(Color.BLACK);

        dynamicPlot.setTicksPerDomainLabel(1);
        dynamicPlot.setTicksPerRangeLabel(1);

        dynamicPlot.getGraphWidget().getDomainLabelPaint().setTextSize(30);
        dynamicPlot.getGraphWidget().getRangeLabelPaint().setTextSize(30);

        dynamicPlot.getGraphWidget().setDomainLabelWidth(40);
        dynamicPlot.getGraphWidget().setRangeLabelWidth(80);

        dynamicPlot.setDomainLabel("time");
        dynamicPlot.getDomainLabelWidget().pack();

        dynamicPlot.setRangeLabel("G-force");
        dynamicPlot.getRangeLabelWidget().pack();

        dynamicPlot.setRangeBoundaries(-1024, 1024, BoundaryMode.FIXED);
        dynamicPlot.setDomainBoundaries(0, SAMPLE_SIZE, BoundaryMode.FIXED);


        xSeries = new SimpleXYSeries("X-axis");
        xSeries.useImplicitXVals();

        ySeries = new SimpleXYSeries("Y-axis");
        ySeries.useImplicitXVals();

        zSeries = new SimpleXYSeries("Z-axis");
        zSeries.useImplicitXVals();

        // Blue line for X axis.
        LineAndPointFormatter fmtX = new LineAndPointFormatter(Color.BLUE, null, null, null);
        dynamicPlot.addSeries(xSeries, fmtX);

        // Green line for Y axis.
        LineAndPointFormatter fmtY = new LineAndPointFormatter(Color.GREEN, null, null, null);;
        dynamicPlot.addSeries(ySeries, fmtY);

        // Red line for Z axis.
        LineAndPointFormatter fmtZ = new LineAndPointFormatter(Color.RED, null, null, null);
        dynamicPlot.addSeries(zSeries, fmtZ);

    }

    @Override
    public void onPause() {
        super.onPause();

        Log.i(TAG, "onPause: ");

        setContentView(R.layout.activity_accelerometer);

        if (dataReceiver != null) {
                unregisterReceiver(dataReceiver);
                dataReceiver = null;
        }
        PebbleKit.closeAppOnPebble(getApplicationContext(), PEBBLEPOINTER_UUID);

        // / Unbind from the service
        if (mBound) {
            try {
                Message msg = Message.obtain(null,
                        MessengerService.MSG_UNREGISTER_CLIENT, this.hashCode(), 0);
                msg.replyTo = mMessenger;

                mService.send(msg);
            }catch (RemoteException e) {Log.d("samhincks", "failed to unregister");}
            unbindService(mConnection);
            mBound = false;
        }
    }

    /* BUG: Occassionally, the on resume does not get triggered fast enough, so we're missing out on the pebble messages
     */
    @Override
    public void onResume() {
        super.onResume();
        glassCamTriggered = false;


        //... ------- Sam code
        Intent intent = getIntent();
        String action = intent.getAction();
        dataType = action;
        Toast.makeText(getApplicationContext(), dataType, Toast.LENGTH_SHORT).show();

        if(dataType.equals("PA")) pebble = true;
        else glass = true;

        if (glass) {
            // Bind to the service. This is gonna go off in a separate thread, so it is naive to think, that we can send a message at instantiatoin
            bindService(new Intent(this, MessengerService.class), mConnection,
                    Context.BIND_AUTO_CREATE);
        }
        //... -------- End Sam code
        Log.i(TAG, "onResume: ");

        final Handler handler = new Handler();
        if(true) { //.. used to be if pebble, but now want pebble to tirgger it no matter what
            Toast.makeText(getApplicationContext(), "PEBBLE", Toast.LENGTH_SHORT).show();

            dataReceiver = new PebbleKit.PebbleDataReceiver(PEBBLEPOINTER_UUID) {

                @Override
                public void receiveData(final Context context, final int transactionId, final PebbleDictionary dict) {

                    handler.post(new Runnable() {
                        @Override
                        public void run() {

                            PebbleKit.sendAckToPebble(context, transactionId);

                            final Long cmdValue = dict.getInteger(PP_KEY_CMD);
                            if (cmdValue == null) {
                                return;
                            }

                            if (cmdValue.intValue() == PP_CMD_VECTOR) {

                                // Capture the received vector.
                                final Long xValue = dict.getInteger(PP_KEY_X);
                                if (xValue != null) {
                                    vector[VECTOR_INDEX_X] = xValue.intValue();
                                }

                                final Long yValue = dict.getInteger(PP_KEY_Y);
                                if (yValue != null) {
                                    vector[VECTOR_INDEX_Y] = yValue.intValue();
                                }

                                final Long zValue = dict.getInteger(PP_KEY_Z);
                                if (zValue != null) {
                                    vector[VECTOR_INDEX_Z] = zValue.intValue();
                                }

                                // Update the user interface.
                                if (pebble) updateUI();
                            }
                        }
                    });
                }
            };

            PebbleKit.registerReceivedDataHandler(this, dataReceiver);
        }

    }

    public void updateUI() {

        final String x = String.format(Locale.getDefault(), "X: %d", vector[VECTOR_INDEX_X]);
        final String y = String.format(Locale.getDefault(), "Y: %d", vector[VECTOR_INDEX_Y]);
        final String z = String.format(Locale.getDefault(), "Z: %d", vector[VECTOR_INDEX_Z]);

        // Update the numerical fields

        TextView x_axis_tv = (TextView) findViewById(R.id.x_axis_Text);
        x_axis_tv.setText(x);

        TextView y_axis_tv = (TextView) findViewById(R.id.y_axis_Text);
        y_axis_tv.setText(y);

        TextView z_axis_tv = (TextView) findViewById(R.id.z_axis_Text);
        z_axis_tv.setText(z);

        // Update the Plot

        // Remove oldest vector data.
        if (xSeries.size() > SAMPLE_SIZE) {
            xSeries.removeFirst();
            ySeries.removeFirst();
            zSeries.removeFirst();
        }

        // Add the latest vector data.
        xSeries.addLast(null, vector[VECTOR_INDEX_X]);
        ySeries.addLast(null, vector[VECTOR_INDEX_Y]);
        zSeries.addLast(null, vector[VECTOR_INDEX_Z]);

        // Redraw the Plots.
        dynamicPlot.redraw();
    }


    /** Sam code----------------
     **/

    /** Messenger for communicating with the service. */
    Messenger mService = null;

    /** Flag indicating whether we have called bind on the service. */
    boolean mBound;
    boolean glassCamTriggered  = false;
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
                   // Toast.makeText(getApplicationContext(), mess, Toast.LENGTH_SHORT).show();
                    Log.d("string", "Received mess " + mess);
                    parseAccelerometerString(mess);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }


    /** This is a Glass accelerometer, and the user just shook pebble, so trigger the Glass camera
     *  in other words, see if we can pass a message back to the Glass Sensor, and have it initiate the camera
     **/
    private void triggerGlassCamera() {
        try {
            Message msg = Message.obtain(null,
                    MessengerService.MSG_TRIGGER_GLASS_CAMERA, this.hashCode(), 0);
            msg.replyTo = mMessenger;
            if(true/*glassCamTriggered == false*/) {
                    mService.send(msg);
                    glassCamTriggered = true;
            }
        }
        catch(RemoteException re) {Log.d("samhincks", "Cannot pass message");}
    }
    /** Assume data looks like this:
     Accelerometer:[1.496825, 9.44348, -2.3849242]
     Gravity:[1.5064018, 9.397092, -2.3654714]
     Linear Acceleration:[-0.009576807, 0.046387658, -0.019452889]
     Gyroscope:[3.3289514E-4, -0.0012650015, 1.3315806E-4]
     Rotation Vector:[0.68503845, 0.3890015, 0.38510332, 0.48072425, 0.06785742]
     * **/
    private void parseAccelerometerString(String mess) {
        try {
            String[] accData =null;
            if (dataType.equals("GA")) {
                accData = mess.split("Accelerometer");
            } else if (dataType.equals("GG")) {
                accData = mess.split("Gravity");

            } else if (dataType.equals("GLA")) {
                accData = mess.split("Linear Acceleration");

            } else if (dataType.equals("GGY")) {
                accData = mess.split("Gyroscope");
            }
            else throw new Exception("Unrecognized datatype");
            String[] remainingData = accData[1].split("\\[");
            String [] remainingDataB = remainingData[1].split("]");
            String [] remainingDataC = remainingDataB[0].split(",");
            //Toast.makeText(getApplicationContext(), remainingDataB[0] + " ----- " + remainingDataB[1] + " ----" + remainingDataC[0]  , Toast.LENGTH_SHORT).show();

            //.. Now add it to the GUI like the Pebble

            // Capture the received vector.
            //Toast.makeText(getApplicationContext(), "."+remainingDataC[0].trim()+".",Toast.LENGTH_SHORT);
            Float x = Float.parseFloat(remainingDataC[0].trim());
            Float y = Float.parseFloat(remainingDataC[1].trim());
            Float z = Float.parseFloat(remainingDataC[2].trim());

            if (x + y +z < 100) { x *=10;y*=10;z*=10;} //.. Take this out later probably

            //.. OK. Next step: shake your feet to trigger the camera intention in Glass!

            vector[VECTOR_INDEX_X] = Math.round(x);
            vector[VECTOR_INDEX_Y] = Math.round(y);
            vector[VECTOR_INDEX_Z] = Math.round(z);
            //Toast.makeText(getApplicationContext(), remainingDataC[0] + " , " + remainingDataC[1] + " , " + remainingDataC[2]  , Toast.LENGTH_SHORT).show();

            // Update the user interface.
            updateUI();
        }
        catch(Exception e) {Log.d("samhinkcs", "cant parse" + e.getMessage());}
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
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
            mBound = false;
        }
    };

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_settings:
                Log.i("samhincks", "Triggering camera from AccelerometerActivity");
                triggerGlassCamera();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}