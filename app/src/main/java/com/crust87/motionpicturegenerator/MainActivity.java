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
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.crust87.ffmpegexecutor.FFmpegExecutor;
import com.crust87.motionpicturegenerator.player.ExoVideoView;
import com.crust87.videotrackview.VideoTrackView;
import com.google.android.exoplayer.AspectRatioFrameLayout;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaCodecUtil;
import com.google.android.exoplayer.drm.UnsupportedDrmException;
import com.google.android.exoplayer.util.Util;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int MENU_GROUP_TRACKS = 1;
    private static final int ID_OFFSET = 2;

    private AspectRatioFrameLayout videoFrame;

    private VideoTrackView mAnchorVideoTrackView;
    private ExoVideoView mVideoView;
    private AnchorOverlay mAnchorOverlay;

    // Attributes
    private String originalPath;

    // Component
    private FFmpegExecutor mExecutor;

    private ProgressDialog mProgressDialog;


    // Working Variables
    private int mVideoSeek;			// generated video seek
    private int mVideoDuration;		// generated video duration

    // Activity lifecycle
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setElevation(0);

        setContentView(R.layout.main_activity);

        initFFmpeg();

        videoFrame = (AspectRatioFrameLayout) findViewById(R.id.video_frame);
        mVideoView = (ExoVideoView) findViewById(R.id.videoView);
        mAnchorVideoTrackView = (VideoTrackView) findViewById(R.id.anchorVideoTrackView);

        mAnchorOverlay = new AnchorOverlay(getApplicationContext());
        mAnchorVideoTrackView.setVideoTrackOverlay(mAnchorOverlay);

        mVideoView.addListener(mExoListener);

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

        mExecutor.setOnReadProcessLineListener(new FFmpegExecutor.OnReadProcessLineListener() {
            @Override
            public void onReadProcessLine(String line) {
                Message message = Message.obtain();
                message.obj = line;
                message.setTarget(mMessageHandler);
                message.sendToTarget();
            }
        });
    }

    private void initFFmpeg() {
        try {
            InputStream ffmpegFileStream = getApplicationContext().getAssets().open("ffmpeg");
            mExecutor = new FFmpegExecutor(getApplicationContext(), ffmpegFileStream);
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Fail FFmpeg Setting", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);

        return true;
    }

    public void openVideo() {
        Intent lIntent = new Intent(Intent.ACTION_PICK);
        lIntent.setType("video/*");
        lIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivityForResult(lIntent, 1000);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_open:
                openVideo();
                return true;
            case R.id.action_crop:
                cropVideo();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private Handler mMessageHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            String message = (String) msg.obj;
            if(mProgressDialog != null) {
                mProgressDialog.setMessage(message);
            }
        }
    };

    public void cropVideo() {
        mVideoView.pause();

        new AsyncTask<Void, Void, Void>() {

            float scale;
            int viewWidth;
            int viewHeight;
            int width;
            int height;
            int positionX;
            int positionY;
            int videoWidth;
            int videoHeight;
            int rotate;
            String start;
            String dur;

            @Override
            protected void onPreExecute() {
                mExecutor.init();
                mProgressDialog = ProgressDialog.show(MainActivity.this, null, "execute....", true);

                int startMinute = mVideoSeek / 60000;
                int startSeconds = mVideoSeek - startMinute*60000;
                start = String.format("00:%02d:%02.2f", startMinute, startSeconds/1000f);
                dur = String.format("00:00:%02.2f", mVideoDuration/1000f);
            }

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    mExecutor.putCommand("-y")
                            .putCommand("-i")
                            .putCommand(originalPath)
                            .putCommand("-vcodec")
                            .putCommand("libx264")
                            .putCommand("-profile:v")
                            .putCommand("baseline")
                            .putCommand("-level")
                            .putCommand("3.1")
                            .putCommand("-b:v")
                            .putCommand("1000k")
                            .putCommand("-ss")
                            .putCommand(start)
                            .putCommand("-t")
                            .putCommand(dur)
                            .putCommand("-c:a")
                            .putCommand("copy")
                            .putCommand(Environment.getExternalStorageDirectory().getAbsolutePath() + "/result.mp4")
                            .executeCommand();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }

        }.execute();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1000 && resultCode == RESULT_OK) {
            mVideoView.stopPlayback();

            Uri contentUri = data.getData();
            setOriginalVideo(contentUri);
        }
    }

    // Initialization original video
    private void setOriginalVideo(Uri uri) {
        originalPath = getRealPathFromURI(uri);
        mVideoView.setContentUri(uri);
        mAnchorVideoTrackView.setVideo(originalPath);
    }

    private ExoVideoView.Listener mExoListener = new ExoVideoView.Listener() {
        @Override
        public void onStateChanged(boolean playWhenReady, int playbackState) {
            if (playbackState == com.google.android.exoplayer.ExoPlayer.STATE_ENDED) {
            }
        }

        @Override
        public void onError(Exception e) {
            String errorString = null;
            if (e instanceof UnsupportedDrmException) {
                // Special case DRM failures.
                UnsupportedDrmException unsupportedDrmException = (UnsupportedDrmException) e;
                errorString = getString(Util.SDK_INT < 18 ? R.string.error_drm_not_supported : unsupportedDrmException.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME ? R.string.error_drm_unsupported_scheme : R.string.error_drm_unknown);
            } else if (e instanceof ExoPlaybackException && e.getCause() instanceof MediaCodecTrackRenderer.DecoderInitializationException) {
                // Special case for decoder initialization failures.
                MediaCodecTrackRenderer.DecoderInitializationException decoderInitializationException = (MediaCodecTrackRenderer.DecoderInitializationException) e.getCause();
                if (decoderInitializationException.decoderName == null) {
                    if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
                        errorString = getString(R.string.error_querying_decoders);
                    } else if (decoderInitializationException.secureDecoderRequired) {
                        errorString = getString(R.string.error_no_secure_decoder, decoderInitializationException.mimeType);
                    } else {
                        errorString = getString(R.string.error_no_decoder, decoderInitializationException.mimeType);
                    }
                } else {
                    errorString = getString(R.string.error_instantiating_decoder, decoderInitializationException.decoderName);
                }
            }

            if (errorString != null) {
                Toast.makeText(getApplicationContext(), errorString, Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
            videoFrame.setAspectRatio(height == 0 ? 1 : (width * pixelWidthHeightRatio) / height);
        }
    };

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

    @Override
    public void onPause() {
        super.onPause();
        mVideoView.pause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mVideoView.stopPlayback();
    }

}
