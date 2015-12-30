package com.crust87.motionpicturegenerator.player;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.MediaController;
import android.widget.Toast;

import com.crust87.motionpicturegenerator.EventLogger;
import com.crust87.motionpicturegenerator.R;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaCodecUtil;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioCapabilitiesReceiver;
import com.google.android.exoplayer.drm.UnsupportedDrmException;
import com.google.android.exoplayer.metadata.GeobMetadata;
import com.google.android.exoplayer.metadata.PrivMetadata;
import com.google.android.exoplayer.metadata.TxxxMetadata;
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

    private Context mContext;

    private ExoMediaPlayer player;

    private EventLogger eventLogger;

    private AudioCapabilitiesReceiver audioCapabilitiesReceiver;

    private Uri mContentUri;

    private long playerPosition;

    private boolean playerNeedsPrepare;

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

    public void release() {
        releasePlayer();
        playerPosition = 0;
    }

    public void setContentUri(Uri contentUri) {
        mContentUri = contentUri;

        preparePlayer(true);
//        if (player == null) {
//            if (!maybeRequestPermission()) {
//                preparePlayer(true);
//            }
//        } else {
//            player.setBackgrounded(false);
//        }
    }

    public void preparePlayer(boolean playWhenReady) {
        if (player == null) {
            player = new ExoMediaPlayer(getRendererBuilder());
            player.addListener(this);
            player.setMetadataListener(this);
            player.seekTo(playerPosition);
            playerNeedsPrepare = true;
            eventLogger = new EventLogger();
            eventLogger.startSession();
            player.addListener(eventLogger);
            player.setInfoListener(eventLogger);
            player.setInternalErrorListener(eventLogger);
        }
        if (playerNeedsPrepare) {
            player.prepare();
            playerNeedsPrepare = false;
        }
        player.setSurface(getHolder().getSurface());
        player.setPlayWhenReady(playWhenReady);
    }

    private void releasePlayer() {
        if (player != null) {
            playerPosition = player.getCurrentPosition();
            player.release();
            player = null;
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
        boolean backgrounded = player.getBackgrounded();
        boolean playWhenReady = player.getPlayWhenReady();
        releasePlayer();
        preparePlayer(playWhenReady);
        player.setBackgrounded(backgrounded);
    }

    // MediaController.MediaPlayerControl implementation
    @Override
    public void start() {
        player.getPlayerControl().start();
    }

    @Override
    public void pause() {
        player.getPlayerControl().pause();
    }

    @Override
    public int getDuration() {
        return player.getPlayerControl().getDuration();
    }

    @Override
    public int getCurrentPosition() {
        return player.getPlayerControl().getCurrentPosition();
    }

    @Override
    public void seekTo(int pos) {
        player.getPlayerControl().seekTo(pos);
    }

    @Override
    public boolean isPlaying() {
        return player.getPlayerControl().isPlaying();
    }

    @Override
    public int getBufferPercentage() {
        return player.getPlayerControl().getBufferPercentage();
    }

    @Override
    public boolean canPause() {
        return player.getPlayerControl().canPause();
    }

    @Override
    public boolean canSeekBackward() {
        return player.getPlayerControl().canSeekBackward();
    }

    @Override
    public boolean canSeekForward() {
        return player.getPlayerControl().canSeekForward();
    }

    @Override
    public int getAudioSessionId() {
        return player.getPlayerControl().getAudioSessionId();
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
        if (playbackState == com.google.android.exoplayer.ExoPlayer.STATE_ENDED) {
        }
    }

    @Override
    public void onError(Exception e) {
        String errorString = null;
        if (e instanceof UnsupportedDrmException) {
            // Special case DRM failures.
            UnsupportedDrmException unsupportedDrmException = (UnsupportedDrmException) e;
            errorString = mContext.getString(Util.SDK_INT < 18 ? R.string.error_drm_not_supported
                    : unsupportedDrmException.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
                    ? R.string.error_drm_unsupported_scheme : R.string.error_drm_unknown);
        } else if (e instanceof ExoPlaybackException
                && e.getCause() instanceof MediaCodecTrackRenderer.DecoderInitializationException) {
            // Special case for decoder initialization failures.
            MediaCodecTrackRenderer.DecoderInitializationException decoderInitializationException =
                    (MediaCodecTrackRenderer.DecoderInitializationException) e.getCause();
            if (decoderInitializationException.decoderName == null) {
                if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
                    errorString = mContext.getString(R.string.error_querying_decoders);
                } else if (decoderInitializationException.secureDecoderRequired) {
                    errorString = mContext.getString(R.string.error_no_secure_decoder,
                            decoderInitializationException.mimeType);
                } else {
                    errorString = mContext.getString(R.string.error_no_decoder,
                            decoderInitializationException.mimeType);
                }
            } else {
                errorString = mContext.getString(R.string.error_instantiating_decoder,
                        decoderInitializationException.decoderName);
            }
        }
        if (errorString != null) {
            Toast.makeText(mContext, errorString, Toast.LENGTH_LONG).show();
        }
        playerNeedsPrepare = true;
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        videoFrame.setAspectRatio(height == 0 ? 1 : (width * pixelWidthHeightRatio) / height);
    }

    // SurfaceHolder.Callback implementation
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (player != null) {
            player.setSurface(holder.getSurface());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Do nothing.
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (player != null) {
            player.blockingClearSurface();
        }
    }
}
