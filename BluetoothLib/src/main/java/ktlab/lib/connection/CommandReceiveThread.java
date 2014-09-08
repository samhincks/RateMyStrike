package ktlab.lib.connection;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.os.Message;
import android.util.Log;

public class CommandReceiveThread extends Thread {

    private boolean forceStop = false;

    private final InputStream mInput;
    private Message mMessage;
    private ByteOrder mOrder;

    public CommandReceiveThread(InputStream in, Message msg, ByteOrder order) {
        mInput = in;
        mMessage = msg;
        mOrder = order;
    }

    /** This is the singular point of interception, where we're getting the bytes
     **/
    public void run() {
        try {

            byte[] rawHeader = new byte[ConnectionCommand.HEADER_LENGTH];

            int receivedSize = 0;

            // receive header
            while (!forceStop && (receivedSize < ConnectionCommand.HEADER_LENGTH)) {
                int length = 0;
                try {
                    length = mInput.read(rawHeader, receivedSize,
                            ConnectionCommand.HEADER_LENGTH - receivedSize);
                } catch (IOException e) {
                    Log.e("CommandReceiveThread", "error", e);
                    mMessage.what = Connection.EVENT_CONNECTION_FAIL;
                    mMessage.sendToTarget();
                    return;
                }
                if (length != -1) {
                    receivedSize += length;
                }

                if (length == 0) {
                    try {
                        sleep(50);
                    } catch (InterruptedException e) {
                        // DO NOTHING
                    }
                }
            }

            ByteBuffer bb = ByteBuffer.wrap(rawHeader).order(mOrder);
            byte what = bb.get();
            int optionLen = bb.getInt();
            Log.v("CommandReceiveThread", "what=" + what + " / len = " + optionLen);
            byte[] rawOption = new byte[optionLen];
            receivedSize = 0;

            // receive option
            while (!forceStop && (receivedSize < optionLen)) {

                int length = 0;
                try {
                    length = mInput.read(rawOption, receivedSize, optionLen
                            - receivedSize);
                } catch (IOException e) {
                    Log.e("CommandReceiveThread", "error", e);
                    mMessage.what = Connection.EVENT_CONNECTION_FAIL;
                    mMessage.sendToTarget();
                    return;
                }
                if (length != -1) {
                    receivedSize += length;
                }
            }

            byte[] orderedOption = new byte[rawOption.length];
            ByteBuffer.wrap(rawOption).order(mOrder).get(orderedOption);

            ConnectionCommand command = ConnectionCommand.fromHeaderAndOption(
                    rawHeader, rawOption, mOrder);

            Log.v("CommandReceiverThread", "CommandReceiveThread=" + mMessage.what);
            mMessage.obj = command;
        }
        catch ( Exception e ) {
            Log.e("CommandReceiveThread", "error", e);
            mMessage.what = Connection.EVENT_CONNECTION_FAIL;
        }

        //. so we've unpacked the message, here and added the command back into the obj, now we pass things back
        mMessage.sendToTarget();
    }

    protected void forceStop() {
        forceStop = true;
    }
}
