package com.crust87.motionpicturegenerator.player;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.MediaController;

import com.crust87.motionpicturegenerator.EventLogger;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioCapabilitiesReceiver;
import com.google.android.exoplayer.metadata.GeobMetadata;
import com.google.android.exoplayer.metadata.PrivMetadata;
import com.google.android.exoplayer.metadata.TxxxMetadata;
import com.google.android.exoplayer.util.PlayerControl;
import com.google.android.exoplayer.util.Util;

import java.util.Map;

/**
 * Created by mabi on 2015. 12. 30..
 */
public class ExoVideoView extends SurfaceView implements SurfaceHolder.Callback,
        ExoMediaPlayer.Listener,
        ExoMediaPlayer.Id3MetadataListener,
        MediaController.MediaPlayerControl,
        AudioCapabilitiesReceiver.Listener {

    private static String TAG ="ExoVideoView";

    // View Components
    private Context mContext;

    // Media Player Components
    private ExoMediaPlayer mMediaPlayer;
    private PlayerControl mPlayerControl;
    private EventLogger eventLogger;
    private AudioCapabilitiesReceiver audioCapabilitiesReceiver;

    // Listeners
    private ExoMediaPlayer.Listener mListener;

    // Attributes
    private Uri mContentUri;
    private boolean playerNeedsPrepare;

    // Constructors
    public ExoVideoView(Context context) {
        super(context);

        mContext = context;

        init();
    }

    public ExoVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;

        init();
    }

    public ExoVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mContext = context;

        init();
    }

    private void init() {
        getHolder().addCallback(this);

        audioCapabilitiesReceiver = new AudioCapabilitiesReceiver(mContext, this);
        audioCapabilitiesReceiver.register();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        audioCapabilitiesReceiver.unregister();
    }

    public void setContentUri(Uri contentUri) {
        mContentUri = contentUri;

        preparePlayer(true);
    }

    public void preparePlayer(boolean playWhenReady) {
        if (mMediaPlayer == null) {
            mMediaPlayer = new ExoMediaPlayer(getRendererBuilder());
            mPlayerControl = mMediaPlayer.getPlayerControl();
            mMediaPlayer.addListener(this);
            mMediaPlayer.setMetadataListener(this);
            playerNeedsPrepare = true;
            eventLogger = new EventLogger();
            eventLogger.startSession();
            mMediaPlayer.addListener(eventLogger);
            mMediaPlayer.setInfoListener(eventLogger);
            mMediaPlayer.setInternalErrorListener(eventLogger);
        }

        if (playerNeedsPrepare) {
            mMediaPlayer.prepare();
            playerNeedsPrepare = false;
        }

        mMediaPlayer.setSurface(getHolder().getSurface());
        mMediaPlayer.setPlayWhenReady(playWhenReady);
    }

    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
            mPlayerControl = null;
            eventLogger.endSession();
            eventLogger = null;
        }
    }

    // Internal methods
    private ExoMediaPlayer.RendererBuilder getRendererBuilder() {
        String userAgent = Util.getUserAgent(mContext, "MotionPictureGenerator");
        return new ExtractorRendererBuilder(mContext, userAgent, mContentUri);
    }

    // AudioCapabilitiesReceiver.Listener methods
    @Override
    public void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities) {
        boolean backgrounded = mMediaPlayer.getBackgrounded();
        boolean playWhenReady = mMediaPlayer.getPlayWhenReady();
        stopPlayback();
        preparePlayer(playWhenReady);
        mMediaPlayer.setBackgrounded(backgrounded);
    }

    // MediaController.MediaPlayerControl implementation
    @Override
    public void start() {
        if(mPlayerControl != null) {
            mPlayerControl.start();
        }
    }

    @Override
    public void pause() {
        if(mPlayerControl != null) {
            mPlayerControl.pause();
        }
    }

    @Override
    public int getDuration() {
        if(mPlayerControl != null) {
            return mPlayerControl.getDuration();
        } else {
            return 0;
        }
    }

    @Override
    public int getCurrentPosition() {
        if(mPlayerControl != null) {
            return mPlayerControl.getCurrentPosition();
        } else {
            return 0;
        }
    }

    @Override
    public void seekTo(int pos) {
        if(mPlayerControl != null) {
            mPlayerControl.seekTo(pos);
        }
    }

    @Override
    public boolean isPlaying() {
        if(mPlayerControl != null) {
            return mPlayerControl.isPlaying();
        } else {
            return false;
        }
    }

    @Override
    public int getBufferPercentage() {
        if(mPlayerControl != null) {
            return mPlayerControl.getBufferPercentage();
        } else {
            return 0;
        }
    }

    @Override
    public boolean canPause() {
        if(mPlayerControl != null) {
            return mPlayerControl.canPause();
        } else {
            return false;
        }
    }

    @Override
    public boolean canSeekBackward() {
        if(mPlayerControl != null) {
            return mPlayerControl.canSeekBackward();
        } else {
            return false;
        }
    }

    @Override
    public boolean canSeekForward() {
        if(mPlayerControl != null) {
            return mPlayerControl.canSeekForward();
        } else {
            return false;
        }
    }

    @Override
    public int getAudioSessionId() {
        if(mPlayerControl != null) {
            return mPlayerControl.getAudioSessionId();
        } else {
            return 0;
        }
    }

    // ExoMediaPlayer.MetadataListener implementation
    @Override
    public void onId3Metadata(Map<String, Object> metadata) {
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (TxxxMetadata.TYPE.equals(entry.getKey())) {
                TxxxMetadata txxxMetadata = (TxxxMetadata) entry.getValue();
                Log.i(TAG, String.format("ID3 TimedMetadata %s: description=%s, value=%s",
                        TxxxMetadata.TYPE, txxxMetadata.description, txxxMetadata.value));
            } else if (PrivMetadata.TYPE.equals(entry.getKey())) {
                PrivMetadata privMetadata = (PrivMetadata) entry.getValue();
                Log.i(TAG, String.format("ID3 TimedMetadata %s: owner=%s",
                        PrivMetadata.TYPE, privMetadata.owner));
            } else if (GeobMetadata.TYPE.equals(entry.getKey())) {
                GeobMetadata geobMetadata = (GeobMetadata) entry.getValue();
                Log.i(TAG, String.format("ID3 TimedMetadata %s: mimeType=%s, filename=%s, description=%s",
                        GeobMetadata.TYPE, geobMetadata.mimeType, geobMetadata.filename,
                        geobMetadata.description));
            } else {
                Log.i(TAG, String.format("ID3 TimedMetadata %s", entry.getKey()));
            }
        }
    }

    // ExoMediaPlayer.Listener implementation
    @Override
    public void onStateChanged(boolean playWhenReady, int playbackState) {
        if(mListener != null) {
            mListener.onStateChanged(playWhenReady, playbackState);
        }
    }

    @Override
    public void onError(Exception e) {
        if(mListener != null) {
            mListener.onError(e);
        }
        playerNeedsPrepare = true;
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        if(mListener != null) {
            mListener.onVideoSizeChanged(width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
        }
    }

    // SurfaceHolder.Callback implementation
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setSurface(holder.getSurface());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Do nothing.
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mMediaPlayer != null) {
            mMediaPlayer.blockingClearSurface();
        }
    }

    // Getters and Setters
    public void setExoPlayerListener(ExoMediaPlayer.Listener listener) {
        mListener = listener;
    }
}
