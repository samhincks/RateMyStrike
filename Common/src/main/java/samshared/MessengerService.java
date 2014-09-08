package samshared;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

/** ----------------------------------------------------------
 * A class for intercepting messages between android processes. When a message is to
 * be sent to and from com.masterbaron.intenttunnel, this is its first or final point of interception.
 * In order to pass messages to a class like RouterService, you must first make sure it is running in the background
 * somewhere, even if you don't store its point of access, so that it pings this Messenger to register
 * Created by samhincks on 9/2/14.
 -------------------------------*/

/** What do you need to do when we want to pass along new bluetooth data?
 *  - Add two things to this class:
 *      - a code for intercepting it immediately, and what to do with it
 *      - a code for what to do once the signal has come back onto the other device
 *  - Add two things to RouterService
 *      - In incomingHandler, add the code we pass in the msg.what to be acceptable to do the same thing
 *      - A function we will call from BluetoothService, which will send back a message to this on the other end
 *  - Add two things to BluetoothService
 *      - In onCommandReceived, the code for rerouting the freshly arrived bluetooth packet to RouterService
 *      - in Incominghandler, the code for resending the data onto Connection.
 *
 *   -----
 *   In reality, next time I want to pass along a message, I should simply do the following:
 *   -one code for here for data-in
 *   -one code here for data-out
 *   - what differs will be the content in the string MSG_KEY
 *   This can simply be hacked together from the existing stuff, ie STRING_MSG and RECEIVE_GLASS should be the same
 *   and TRIGGER_GLASS and MSG_ACCELEROMETER should be the same
 *   -----
 *
 * And here's an interesting bug, or not a bug, depending on how you look at it. Everything that is sent
 * ultimately returns to it; we have ourselves registered both as clients and as servers of the bluetooth;
 * when we send a message, one recipient of it will be the phone if glass and glass if phone; the other
 * will be ourselves. I don't know how to fix this, but we should make sure, at least that situation
 * does not cause a circuit; we can't send off a trigger to start the camera, which sends off a trigger
 * that ultimately circles back to MessengerService, which - blindly - broadcasts a command to all its clients
 *
 * So my solution will be to write code that says - essentially - these codes go to these clients;
 * as a client, we register to subscribe to a code. Is this too complicated? How else will we know who to respond to?
 * Don't do this. Make so that we have to identities for a registered client; external or internal. If you're internal, you
 * get messages from triggers that involve interception from bluetooth. If you're external, you get messages from
 * local messages that should go through bluetooth. We should also tweak our MSG id's. A message either goes in or out.
 **/
 public class MessengerService extends Service {

    /** Command to the service to display a message */
    public static final int STRING_MSG = 0; //.. ?
    public static final int MSG_SAY_HELLO = 1; //.. ?
    public static final int MSG_REGISTER_CLIENT = 2; //.. tag to register a new client
    public static final int MSG_SET_VALUE = 3; //.. ?
    public static final int MSG_UNREGISTER_CLIENT = 4; //..  tag to unregister client
    public static final int MSG_TRIGGER_GLASS_CAMERA = 5; //.. tag to deliver a bt message totrigger the camera
    public static final int MSG_ACCELEROMETER = 6; //.. tag to pass along accelerometer data
    public static final int MSG_RECEIVE_GLASS_TRIGGER = 7; //.. tag to start the camera locally

    static ArrayList<Messenger> mClients = new ArrayList<Messenger>();
    static ArrayList<Boolean> mClientsExternal = new ArrayList<Boolean>();
    int mValue = 0;

    public static final String STRING_KEY = "STRING_KEY"; //.. where in the data any message are sent

        /** Handler of incoming messages from clients.
         * SUPER IMPORTANT AND OBSCURE FACT:
         *  -mClient.get(k).arg1 = 0 means recipient of outward bound communication and 1 means inward bound.
         * */
        class IncomingHandler extends Handler {
            @Override
            public void handleMessage(Message msg) {
                Log.i("samhincks", " MessengerService handling " + msg.what);
                switch (msg.what) {

                    //.. ?
                    case MSG_SAY_HELLO:
                        Toast.makeText(getApplicationContext(), "hello!", Toast.LENGTH_SHORT).show();
                        break;

                    //.. I think this is incoming accelerometer data
                    case STRING_MSG:
                        String mess = msg.getData().getString(STRING_KEY);
                        for (int i=mClients.size()-1; i>=0; i--) {
                            try {
                                Message retMessage = Message.obtain(null, STRING_MSG, mValue, 0);
                                Bundle b = new Bundle(); b.putString(STRING_KEY,mess);
                                retMessage.setData(b);
                                if (!mClientsExternal.get(i))
                                    mClients.get(i).send(retMessage);
                            } catch (RemoteException e) {
                                mClients.remove(i);
                                mClientsExternal.remove(i);
                            }
                        }
                        break;

                    //..Accelerometer data coming from glass, and being relayed to all clients
                    case MSG_ACCELEROMETER:
                        String messg = msg.getData().getString(STRING_KEY);
                        for (int i=mClients.size()-1; i>=0; i--) {
                            try {
                                Intent action  = new Intent("Bluetooth-Accelerometer");
                                action.setAction(messg);
                                Message mesg = Message.obtain(null, MSG_ACCELEROMETER, action);

                                if (mClientsExternal.get(i));
                                    mClients.get(i).send(mesg);
                            } catch (RemoteException e) {
                                mClients.remove(i);
                                mClientsExternal.remove(i);
                            }
                        }
                        break; //.. dont forget breaks you dumb cunt

                    //.. A trigger to start the glass camera coming from nexus
                    case MSG_TRIGGER_GLASS_CAMERA:
                        for (int i=mClients.size()-1; i>=0; i--) {
                            try {
                                Intent action  = new Intent("Bluetooth-Fake-Intention");
                                Message mesg = Message.obtain(null, MSG_TRIGGER_GLASS_CAMERA, action);
                                if (mClientsExternal.get(i))
                                    mClients.get(i).send(mesg);
                            } catch (RemoteException e) {
                                mClients.remove(i);
                                mClientsExternal.remove(i);
                            }
                        }
                        break;

                    //.. We received a trigger to start the camera, relay it to all local clients
                    case MSG_RECEIVE_GLASS_TRIGGER:
                        for (int i=mClients.size()-1; i>=0; i--) {
                            try {
                                Intent action  = new Intent("Glass-Trigger-Camera");
                                Message mesg = Message.obtain(null, MSG_RECEIVE_GLASS_TRIGGER, action);
                                if (!(mClientsExternal.get(i)))
                                    mClients.get(i).send(mesg);
                            } catch (RemoteException e) {
                                mClients.remove(i);
                                mClientsExternal.remove(i);
                            }
                        }
                        break;

                    //.. Register new client
                    case MSG_REGISTER_CLIENT:
                        Log.i("samhincks", "registering client " + msg.replyTo + " and arg1 = " +msg.arg1);
                        mClients.add(msg.replyTo);
                        mClientsExternal.add(msg.arg1 == 0);
                        break;

                    //.. Unregister client
                    case MSG_UNREGISTER_CLIENT:
                        Log.d("samhinckssnor", "unregistering client " + msg.replyTo);
                        mClients.remove(msg.replyTo);
                        break;

                    //..?
                    case MSG_SET_VALUE:
                        mValue = msg.arg1;
                        Log.d("samhincks", "MSG_SET_VALUE");
                        for (int i=mClients.size()-1; i>=0; i--) {
                            try {
                                Log.d("samhincks", "sending data to client " +i);
                                mClients.get(i).send(Message.obtain(null,
                                        MSG_SAY_HELLO, mValue, 0));
                            } catch (RemoteException e) {
                                // The client is dead.  Remove it from the list;
                                // we are going through the list from back to front
                                // so this is safe to do inside the loop.
                                mClients.remove(i);
                            }
                        }
                        break;
                    default:
                        super.handleMessage(msg);
                }
            }
        }

        /**
         * Target we publish for clients to send messages to IncomingHandler.
         */
        final Messenger mMessenger = new Messenger(new IncomingHandler());

        /**
         * When binding to the service, we return an interface to our messenger
         * for sending messages to the service.
         */
        @Override
        public IBinder onBind(Intent intent) {
            Toast.makeText(getApplicationContext(), "binding", Toast.LENGTH_SHORT).show();
            return mMessenger.getBinder();
        }
}
