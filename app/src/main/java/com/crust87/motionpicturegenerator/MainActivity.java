/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.crust87.motionpicturegenerator;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;

import com.crust87.motionpicturegenerator.player.ExoMediaPlayer;
import com.crust87.motionpicturegenerator.player.ExoVideoView;
import com.crust87.videotrackview.VideoTrackView;
import com.google.android.exoplayer.AspectRatioFrameLayout;

/**
 * An activity that plays media using {@link ExoMediaPlayer}.
 */
public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int MENU_GROUP_TRACKS = 1;
    private static final int ID_OFFSET = 2;

    private AspectRatioFrameLayout videoFrame;

    private VideoTrackView mAnchorVideoTrackView;
    private ExoVideoView mVideoView;
    private AnchorOverlay mAnchorOverlay;


    // Working Variables
    private int mVideoSeek;			// generated video seek
    private int mVideoDuration;		// generated video duration

    // Activity lifecycle

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);

        videoFrame = (AspectRatioFrameLayout) findViewById(R.id.video_frame);
        mAnchorVideoTrackView = (VideoTrackView) findViewById(R.id.anchorVideoTrackView);

        mAnchorOverlay = new AnchorOverlay(getApplicationContext());
        mAnchorVideoTrackView.setVideoTrackOverlay(mAnchorOverlay);

        mAnchorOverlay.setOnUpdateAnchorListener(new AnchorOverlay.OnUpdateAnchorListener() {
            @Override
            public void onUpdatePositionStart() {
                mVideoView.pause();
            }

            @Override
            public void onUpdatePosition(int seek, int duration) {
                mVideoSeek = seek;
                mVideoDuration = duration;
            }

            @Override
            public void onUpdatePositionEnd(int seek, int duration) {
                mVideoSeek = seek;
                mVideoDuration = duration;

                mVideoView.seekTo(mVideoSeek);
                mVideoView.start();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1000 && resultCode == RESULT_OK) {
            mVideoView.release();

            Uri contentUri = data.getData();
            mVideoView.setContentUri(contentUri);

            String originalPath = getRealPathFromURI(contentUri);
            mAnchorVideoTrackView.setVideo(originalPath);
        }
    }

    public String getRealPathFromURI(Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = { MediaStore.Images.Media.DATA };
            cursor = getApplicationContext().getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void onLoadButtonClicked(View view) {
        Intent lIntent = new Intent(Intent.ACTION_PICK);
        lIntent.setType("video/*");
        lIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivityForResult(lIntent, 1000);
    }

    @Override
    public void onPause() {
        super.onPause();
        mVideoView.pause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mVideoView.pause();
        mVideoView.release();
    }

}
