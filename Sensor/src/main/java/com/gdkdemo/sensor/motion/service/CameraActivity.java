package com.gdkdemo.sensor.motion.service;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.*;


import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.glass.media.CameraManager;

/**Video works now, but it lasts only 10 seconds. Is this acceptable? Do we maybe want it to last just a little bit longer?
 * I think its probably fine. But what are we going to do with the video? Can we send it back to Android. I think this will be hard.
 * Instead, I should push it to the cloud? I don't want the screen on
 *
 * Saving Mp4 Files option:
 * Lazy method:
 * - Don't save them, keep them locally. Write a java script which, when both are plugged in, reads them, and synthesizes their data
 * according to timestamps. Let this be the back-end of a larger web-app for viewing and analyzing strike data. Build it in NodeJS. When
 * you have the app, explore the possibility of pushing it up to the web-server which you have hosted on amazon or something.
 * Created by samhincks on 9/5/14.
 *
 * It goes here 09-05 20:22:12.821    2226-2226/sambluetooth.sensor.sensor D/samhincks took video﹕ /storage/emulated/0/thumbnail_cache/t_thumb_20140905_202152_484.mp4


 Also, there is still a problem. We CANT have it send multiple TRIGGERS at teh same time it will mess things up

 ----- OK, then.
 Next step a bit of a departure, but will integrate a lot of real quality pieces. Get the PhotoHunt Sample working, build off it.
 Instead of passing images up, pass text and video. Or if passing video is not possible use it then remanufacture the web app so
 that it instead solicits these web compoonents from google plus
 */
public class CameraActivity extends Activity {
    boolean filming = false;
    private static final int TAKE_PICTURE_REQUEST = 1;

    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.util.Log.i("samhincks", "Taking a video");

        if (!filming) {
            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            startActivityForResult(intent, TAKE_PICTURE_REQUEST);
            filming= true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        filming = false;
        if (requestCode == TAKE_PICTURE_REQUEST && resultCode == RESULT_OK) {
            String picturePath = data.getStringExtra(
                    CameraManager.EXTRA_THUMBNAIL_FILE_PATH);

            //.. FIND the video, then push to Youtube. Is this even possible?


            Log.d("samhincks took video ", picturePath);
        }


        super.onActivityResult(requestCode, resultCode, data);
    }

}
