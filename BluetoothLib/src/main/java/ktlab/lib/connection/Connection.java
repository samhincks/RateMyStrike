package ktlab.lib.connection;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeoutException;

public abstract class Connection extends Handler {

    private static final String TAG = "Connection";

    // Event
    public static final int EVENT_CONNECT_COMPLETE = 1;
    public static final int EVENT_DATA_RECEIVED = 2;
    public static final int EVENT_DATA_SEND_COMPLETE = 3;
    public static final int EVENT_CONNECT_PING = 4;
    public static final int EVENT_CONNECTION_FAIL = 101;
    public static final int EVENT_CONNECTION_SEND_FAIL = 102;

    private static final long PING_INTERVAL = 1000;
    private static final long PING_LATE_TIME = 15000;
    private static final long PING_WORRY_TIME = 10000;

    // Event
    private static byte PING = Byte.MAX_VALUE;
    private static int PING_ID = Integer.MAX_VALUE;

    protected ConnectionCallback mCallback;

    // communication thread
    protected ConnectionThread mConnectionThread;
    protected CommandReceiveThread mReceiveThread;
    protected CommandSendThread mSendThread;

    // stream
    protected InputStream mInput;
    protected OutputStream mOutput;

    // send/close flag
    private boolean isSending = false;
    private boolean forceStop = false;

    // send data queue
    private final boolean canQueueing;
    private Queue<PendingData> mQueue = null;
    private final ByteOrder mOrder;

    // ping
    private long mLastActivity;
    private boolean hasOpenConnection = false;
    private boolean hasWorkingConnection = false;

    @Override
    public void handleMessage(Message msg) {
        boolean processed = true;

        switch (msg.what) {
            case EVENT_DATA_RECEIVED:
                Log.i(TAG, "data received");
                mLastActivity = System.currentTimeMillis();

                if (!hasWorkingConnection) {
                    hasWorkingConnection = true;
                    mCallback.onConnectComplete();
                }

                ConnectionCommand cmd = (ConnectionCommand) msg.obj;
                if (cmd.type == PING) {
                    Log.v(TAG, "data received: ping");
                } else {
                    mCallback.onCommandReceived(cmd);
                }

                if (!forceStop) {
                    // receive thread starting
                    mReceiveThread = null;
                    mReceiveThread = new CommandReceiveThread(mInput, obtainMessage(EVENT_DATA_RECEIVED),
                            mOrder);
                    mReceiveThread.start();
                }

                break;

            case EVENT_DATA_SEND_COMPLETE:
                int id = msg.arg1;

                if (id != PING_ID) {
                    Log.i(TAG, "data send complete, id : " + id);
                } else {
                    Log.v(TAG, "data send complete, id : " + id);
                }

                mSendThread = null;
                isSending = false;

                mLastActivity = System.currentTimeMillis();
                if (!hasWorkingConnection) {
                    hasWorkingConnection = true;
                    mCallback.onConnectComplete();
                }

                if (id != PING_ID) {
                    mCallback.onDataSendComplete(id);
                } else {
                    Log.v(TAG, "send complete: ping");
                }

                if (!forceStop) {
                    // if queueing data exists, send first data
                    sendPendingData();
                }

                break;

            default:
                processed = false;
        }

        if (!processed ) {
            if (forceStop) {
                mConnectionThread.close();
                return;
            }

            switch (msg.what) {
                case EVENT_CONNECT_COMPLETE:
                    Log.i(TAG, "pre-connect complete");
                    mInput = mConnectionThread.getInputStream();
                    mOutput = mConnectionThread.getOutputStream();
                    //mCallback.onConnectComplete();

                    hasOpenConnection = true;

                    // receive thread starting
                    mReceiveThread = new CommandReceiveThread(mInput, obtainMessage(EVENT_DATA_RECEIVED),
                            mOrder);
                    mReceiveThread.start();

                    mLastActivity = System.currentTimeMillis();
                    sendEmptyMessageDelayed(EVENT_CONNECT_PING, 0);

                    // if queueing data exists, send first data
                    sendPendingData();

                    break;

                case EVENT_CONNECT_PING:
                    try {
                        long timeSinceActivity = System.currentTimeMillis() - mLastActivity;
                        if (timeSinceActivity > PING_LATE_TIME) {
                            throw new TimeoutException("late ping");
                        } else if (!isSending && mQueue.size() == 0
                                && (!hasWorkingConnection || timeSinceActivity > PING_WORRY_TIME)) {
                            Log.v(TAG, "send ping");
                            mInput.available();
                            mOutput.flush();
                            sendData(PING, PING_ID);
                        }
                        sendEmptyMessageDelayed(EVENT_CONNECT_PING, PING_INTERVAL);
                    } catch (Exception e) {
                        mSendThread = null;
                        isSending = false;
                        if (!hasWorkingConnection) {
                            Log.e(TAG, "connection failed", e);
                            mCallback.onConnectionFailed();
                        } else {
                            Log.e(TAG, "connection lost", e);
                            mCallback.onConnectionLost();
                        }
                    }
                    break;

                case EVENT_CONNECTION_FAIL:
                case EVENT_CONNECTION_SEND_FAIL:
                    if (msg.what == EVENT_CONNECTION_SEND_FAIL || !isSending) {
                        mSendThread = null;
                        isSending = false;
                        if (!hasWorkingConnection) {
                            Log.e(TAG, "connection failed");
                            mCallback.onConnectionFailed();
                            break;
                        } else {
                            Log.e(TAG, "connection lost");
                            mCallback.onConnectionLost();
                        }
                    }
                    break;

                default:
                    Log.e(TAG, "Unknown Event:" + msg.what);
            }
        }
    }

    /**
     * Constructor
     *
     * @param cb          callback for communication result
     * @param canQueueing true if can queue sending data
     * @param order       byte order of the destination
     */
    protected Connection(ConnectionCallback cb, boolean canQueueing, ByteOrder order) {
        mCallback = cb;

        this.canQueueing = canQueueing;
        this.mQueue = new LinkedList<PendingData>();

        mOrder = order;
    }

    /**
     * stop connection. this method must be called when application will stop
     * connection
     */
    public void stopConnection() {
        forceStop = true;

        // stop connection thread
        mConnectionThread.close();

        // stop receive thread
        if (mReceiveThread != null) {
            mReceiveThread.forceStop();
            mReceiveThread.interrupt();
            mReceiveThread = null;
        }

        // stop send thread
        mSendThread = null;
        clearQueuedData();

        mInput = null;
        mOutput = null;
        hasWorkingConnection = false;
        hasOpenConnection = false;
        System.gc();
    }

    /**
     * @param type command type
     * @param data option data
     * @param id   send id
     * @return return true if success sending or queueing data. if "canQueueing"
     * is false and sending any data, return false.
     */
    public boolean sendData(byte type, byte[] data, int id) {

        // if sending data, queueing...
        if (type == PING && !hasOpenConnection) {
            Log.i(TAG, "sendData(PING), not queuing...");
            return false;
        } else if (isSending || ( !hasWorkingConnection && type != PING )) {
            if (canQueueing && type != PING) {
                synchronized (mQueue) {
                    PendingData p = new PendingData(id, new ConnectionCommand(type, data));
                    mQueue.offer(p);
                }
                Log.i(TAG, "sendData(), pending...");
                return true;
            } else {
                Log.i(TAG, "sendData(), not queuing...");
                return false;
            }
        }

        Log.v(TAG, "sendData(" + id + ")");
        Message msg = obtainMessage(EVENT_DATA_SEND_COMPLETE);
        msg.arg1 = id;

        //.. Here finally, it is, the point where we instantiate the ConnectionCommand from data
        //.. Next step: see who calls sendData, how does it create the data byte array?
        //.. Once we have the data byte array, we can simply put our message in whatever format we want in it
        msg.obj = new ConnectionCommand(type, data);
        Bundle b = new Bundle();
        b.putString("fitta", "hora");
        msg.setData(b);
        Log.d(TAG, "fittslem " + mOutput.toString() + " " +msg.getData().size() + msg.getData().get("fitta") + " " + msg.getData().size() + msg.getData().get("hora"));
        mSendThread = new CommandSendThread(mOutput, msg, mOrder);
        mSendThread.start();

        isSending = true;
        return true;
    }

    /**
     * @param type command type
     * @param id   send id
     * @return return true if success sending or queueing data. if "canQueueing"
     * is false and sending any data, return false.
     */
    public boolean sendData(byte type, int id) {

        // if sending data, queueing...
        if (type == PING && !hasOpenConnection) {
            Log.i(TAG, "sendData(PING), not queuing...");
            return false;
        } else if (isSending || ( !hasWorkingConnection && type != PING )) {
            if (canQueueing && type != PING) {
                synchronized (mQueue) {
                    PendingData p = new PendingData(id, new ConnectionCommand(type));
                    mQueue.offer(p);
                }
                Log.i(TAG, "sendData(), pending...");
                return true;
            } else {
                Log.i(TAG, "sendData(), not queuing...");
                return false;
            }
        }

        Log.v(TAG, "sendData(" + id + ")");
        Message msg = obtainMessage(EVENT_DATA_SEND_COMPLETE);
        msg.arg1 = id;
        msg.obj = new ConnectionCommand(type);
        mSendThread = new CommandSendThread(mOutput, msg, mOrder);
        mSendThread.start();

        isSending = true;
        return true;
    }

    /**
     * send data internal.
     *
     * @param pendingData pending data
     * @return always true
     * @hide
     */
    private boolean sendData(PendingData pendingData) {

        Log.i(TAG, "send PendingData");
        Message msg = obtainMessage(EVENT_DATA_SEND_COMPLETE);
        msg.arg1 = pendingData.id;
        msg.obj = pendingData.command;
        mSendThread = new CommandSendThread(mOutput, msg, mOrder);
        mSendThread.start();

        isSending = true;
        return true;
    }

    /**
     * send pending data if exists.
     *
     * @hide
     */
    private void sendPendingData() {
        PendingData pendingData = null;
        synchronized (mQueue) {
            if (mQueue.size() > 0) {
                pendingData = mQueue.poll();
            }
        }
        if (pendingData != null) {
            sendData(pendingData);
        }
    }

    /**
     * clear queue data
     *
     * @hide
     */
    private void clearQueuedData() {
        synchronized (mQueue) {
            mQueue.clear();
        }
    }

    public boolean isSending() {
        return isSending;
    }

    public boolean hasPending() {
        synchronized (mQueue) {
            return mQueue.size() > 0;
        }
    }

    abstract public void startConnection();

    public class PendingData {
        final int id;
        final ConnectionCommand command;

        PendingData(int id, ConnectionCommand command) {
            this.id = id;
            this.command = command;
        }
    }
}
