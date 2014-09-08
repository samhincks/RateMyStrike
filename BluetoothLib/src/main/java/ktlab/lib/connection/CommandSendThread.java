package ktlab.lib.connection;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;

import android.content.Intent;
import android.os.Message;
import android.util.Log;

public class CommandSendThread extends Thread {

    private OutputStream mOut;
    private Message mMessage;
    private ConnectionCommand mCommand;
    private ByteOrder mOrder;

    public CommandSendThread(OutputStream out, Message msg, ByteOrder order) {
        mOut = out;
        //.. At what point is it converted to a ConnectionCommand!? :S
        mCommand = (ConnectionCommand) msg.obj;
        mMessage = msg;
        mOrder = order;
    }

    @Override
    public void run() {
        Log.v("CommandSendThread", "write: " + mMessage.arg1);
        try {
            //.. So we're packing the msg.obj into this ConnectionCommand thing
            //.. 2 Questions: 1. Is msg.obj and Intent? 2. Does it retain everything we packed into the Intent?
            //.. 3 Could we potentially cast something else into a msg.obj, does it need to be Parcelable?

            //.. We should check what's coming over on the other end, at the singular point where its intercepting data, and
            //.. decide whether the crap we put inside is coming out
            mOut.write(ConnectionCommand.toByteArray(mCommand, mOrder));
        } catch (Exception e) {
            Log.e("CommandSendThread", "error", e);
            mMessage.what = Connection.EVENT_CONNECTION_SEND_FAIL;
        }
        //.. so which is sending? mMessage, or mOut. So this one just changes the status of the message
        //.. which is contained in mMessage.what
        mMessage.sendToTarget();
    }
}
