package com.crust87.motionpicturegenerator.player;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.MediaController;

import com.google.android.exoplayer.CodecCounters;
import com.google.android.exoplayer.DummyTrackRenderer;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.TimeRange;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioCapabilitiesReceiver;
import com.google.android.exoplayer.audio.AudioTrack;
import com.google.android.exoplayer.chunk.ChunkSampleSource;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.dash.DashChunkSource;
import com.google.android.exoplayer.drm.StreamingDrmSessionManager;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.metadata.GeobMetadata;
import com.google.android.exoplayer.metadata.MetadataTrackRenderer;
import com.google.android.exoplayer.metadata.PrivMetadata;
import com.google.android.exoplayer.metadata.TxxxMetadata;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.TextRenderer;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.util.PlayerControl;
import com.google.android.exoplayer.util.Util;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by mabi on 2015. 12. 30..
 */
public class ExoVideoView extends TextureView implements
        MediaController.MediaPlayerControl,
        ChunkSampleSource.EventListener,
        HlsSampleSource.EventListener,
        DefaultBandwidthMeter.EventListener,
        MediaCodecVideoTrackRenderer.EventListener,
        MediaCodecAudioTrackRenderer.EventListener,
        StreamingDrmSessionManager.EventListener,
        DashChunkSource.EventListener,
        TextRenderer,
        MetadataTrackRenderer.MetadataRenderer<Map<String, Object>> {

    private static String TAG ="ExoVideoView";

    // Constants pulled into this class for convenience.
    public static final int STATE_IDLE = com.google.android.exoplayer.ExoPlayer.STATE_IDLE;
    public static final int STATE_PREPARING = com.google.android.exoplayer.ExoPlayer.STATE_PREPARING;
    public static final int STATE_BUFFERING = com.google.android.exoplayer.ExoPlayer.STATE_BUFFERING;
    public static final int STATE_READY = com.google.android.exoplayer.ExoPlayer.STATE_READY;
    public static final int STATE_ENDED = com.google.android.exoplayer.ExoPlayer.STATE_ENDED;
    public static final int TRACK_DISABLED = com.google.android.exoplayer.ExoPlayer.TRACK_DISABLED;
    public static final int TRACK_DEFAULT = com.google.android.exoplayer.ExoPlayer.TRACK_DEFAULT;

    public static final int RENDERER_COUNT = 4;

    public static final int TYPE_VIDEO = 0;
    public static final int TYPE_AUDIO = 1;
    public static final int TYPE_TEXT = 2;
    public static final int TYPE_METADATA = 3;

    private static final int RENDERER_BUILDING_STATE_IDLE = 1;
    private static final int RENDERER_BUILDING_STATE_BUILDING = 2;
    private static final int RENDERER_BUILDING_STATE_BUILT = 3;

    // View Components
    private Context mContext;
    private Handler mHandler;
    private Surface mSurface;

    // ExoPlayer Components
    private RendererBuilder mRendererBuilder;
    private com.google.android.exoplayer.ExoPlayer mMediaPlayer;
    private CopyOnWriteArrayList<Listener> mListeners;
    private TrackRenderer mVideoRenderer;
    private CodecCounters mCodecCounters;
    private BandwidthMeter mBandwidthMeter;

    // Media Player Components
    private PlayerControl mPlayerControl;
    private AudioCapabilitiesReceiver mAudioCapabilitiesReceiver;

    // Listener
    private CaptionListener mCaptionListener;
    private InternalErrorListener mInternalErrorListener;
    private InfoListener infoListener;

    // Attributes
    private Uri mContentUri;
    private boolean playerNeedsPrepare;

    private int rendererBuildingState;
    private int lastReportedPlaybackState;
    private boolean lastReportedPlayWhenReady;

    private Format mVideoFormat;
    private int videoTrackToRestore;

    private boolean backgrounded;

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

    @Override
    protected void onDetachedFromWindow() {
        mAudioCapabilitiesReceiver.unregister();

        super.onDetachedFromWindow();
    }

    private void init() {
        mHandler = new Handler();
        mListeners = new CopyOnWriteArrayList<>();

        setSurfaceTextureListener(mSurfaceTextureListener);

        mAudioCapabilitiesReceiver = new AudioCapabilitiesReceiver(mContext, mAudioListener);
        mAudioCapabilitiesReceiver.register();
    }

    public void setContentUri(Uri contentUri) {
        mContentUri = contentUri;

        preparePlayer(true);
    }

    public void preparePlayer(boolean playWhenReady) {
        if (mMediaPlayer == null) {
            mRendererBuilder = getRendererBuilder();
            mMediaPlayer = com.google.android.exoplayer.ExoPlayer.Factory.newInstance(RENDERER_COUNT, 1000, 5000);
            mMediaPlayer.addListener(mExoPlayerListener);
            mPlayerControl = new PlayerControl(mMediaPlayer);
            lastReportedPlaybackState = STATE_IDLE;
            rendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
            // Disable text initially.
            mMediaPlayer.setSelectedTrack(TYPE_TEXT, TRACK_DISABLED);
            playerNeedsPrepare = true;
        }

        if (playerNeedsPrepare) {
            if (rendererBuildingState == RENDERER_BUILDING_STATE_BUILT) {
                mMediaPlayer.stop();
            }
            mRendererBuilder.cancel();
            mVideoFormat = null;
            mVideoRenderer = null;
            rendererBuildingState = RENDERER_BUILDING_STATE_BUILDING;
            maybeReportPlayerState();
            mRendererBuilder.buildRenderers(this);
            playerNeedsPrepare = false;
        }

        pushSurface(false);
        mMediaPlayer.setPlayWhenReady(playWhenReady);
    }

    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mRendererBuilder.cancel();
            rendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
            mMediaPlayer.release();
            mMediaPlayer = null;
            mPlayerControl = null;
        }
    }

    // Internal methods
    private RendererBuilder getRendererBuilder() {
        String userAgent = Util.getUserAgent(mContext, "MotionPictureGenerator");
        return new ExtractorRendererBuilder(mContext, userAgent, mContentUri);
    }

    // AudioCapabilitiesReceiver.Listener methods
    private AudioCapabilitiesReceiver.Listener mAudioListener = new AudioCapabilitiesReceiver.Listener() {
        @Override
        public void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities) {
            stopPlayback();
            preparePlayer(mMediaPlayer.getPlayWhenReady());
            setBackgrounded(backgrounded);
        }
    };

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
    private ExoVideoView.Id3MetadataListener mId3MetadataListener = new Id3MetadataListener() {
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
    };

    // ExoMediaPlayer.Listener implementation
    private ExoVideoView.Listener mListener = new Listener() {
        @Override
        public void onStateChanged(boolean playWhenReady, int playbackState) {
            for (Listener listener : mListeners) {
                listener.onStateChanged(playWhenReady, playbackState);
            }
        }

        @Override
        public void onError(Exception e) {
            for (Listener listener : mListeners) {
                listener.onError(e);
            }
            playerNeedsPrepare = true;
        }

        @Override
        public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
            for (Listener listener : mListeners) {
                listener.onVideoSizeChanged(width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
            }
        }
    };

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        mListener.onVideoSizeChanged(width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
    }

    private SurfaceTextureListener mSurfaceTextureListener = new SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, final int width, final int height) {
            mSurface = new Surface(surface);

            if (mMediaPlayer != null) {
                pushSurface(false);
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, final int width, final int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            if (mMediaPlayer != null) {
                pushSurface(true);
            }

            if (mSurface != null) {
                mSurface.release();
                mSurface = null;
            }

            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    public void blockingClearSurface() {
        pushSurface(true);
    }

    public int getSelectedTrack(int type) {
        return mMediaPlayer.getSelectedTrack(type);
    }

    public void setSelectedTrack(int type, int index) {
        mMediaPlayer.setSelectedTrack(type, index);
        if (type == TYPE_TEXT && index < 0 && mCaptionListener != null) {
            mCaptionListener.onCues(Collections.<Cue>emptyList());
        }
    }

    public void setBackgrounded(boolean backgrounded) {
        if (this.backgrounded == backgrounded) {
            return;
        }
        this.backgrounded = backgrounded;
        if (backgrounded) {
            videoTrackToRestore = getSelectedTrack(TYPE_VIDEO);
            setSelectedTrack(TYPE_VIDEO, TRACK_DISABLED);
            blockingClearSurface();
        } else {
            setSelectedTrack(TYPE_VIDEO, videoTrackToRestore);
        }
    }

    private void pushSurface(boolean blockForSurfacePush) {
        if (mVideoRenderer == null) {
            return;
        }

        if (blockForSurfacePush) {
            mMediaPlayer.blockingSendMessage(mVideoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, mSurface);
        } else {
            mMediaPlayer.sendMessage(mVideoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, mSurface);
        }
    }

    private void maybeReportPlayerState() {
        boolean playWhenReady = mMediaPlayer.getPlayWhenReady();
        int playbackState = getPlaybackState();
        if (lastReportedPlayWhenReady != playWhenReady || lastReportedPlaybackState != playbackState) {
            mListener.onStateChanged(playWhenReady, playbackState);
            lastReportedPlayWhenReady = playWhenReady;
            lastReportedPlaybackState = playbackState;
        }
    }

    public int getPlaybackState() {
        if (rendererBuildingState == RENDERER_BUILDING_STATE_BUILDING) {
            return STATE_PREPARING;
        }
        int playerState = mMediaPlayer.getPlaybackState();
        if (rendererBuildingState == RENDERER_BUILDING_STATE_BUILT && playerState == STATE_IDLE) {
            // This is an edge case where the renderers are built, but are still being passed to the
            // player's playback thread.
            return STATE_PREPARING;
        }
        return playerState;
    }

    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    public void setInternalErrorListener(InternalErrorListener listener) {
        mInternalErrorListener = listener;
    }

    public void setInfoListener(InfoListener listener) {
        infoListener = listener;
    }

    public void setCaptionListener(CaptionListener listener) {
        mCaptionListener = listener;
    }

    public int getTrackCount(int type) {
        return mMediaPlayer.getTrackCount(type);
    }

    public MediaFormat getTrackFormat(int type, int index) {
        return mMediaPlayer.getTrackFormat(type, index);
    }

    public boolean getBackgrounded() {
        return backgrounded;
    }


    /**
     * Invoked with the results from a {@link RendererBuilder}.
     *
     * @param renderers      Renderers indexed by {@link ExoVideoView} TYPE_* constants. An individual
     *                       element may be null if there do not exist tracks of the corresponding type.
     * @param bandwidthMeter Provides an estimate of the currently available bandwidth. May be null.
     */
  /* package */ void onRenderers(TrackRenderer[] renderers, BandwidthMeter bandwidthMeter) {
        for (int i = 0; i < RENDERER_COUNT; i++) {
            if (renderers[i] == null) {
                // Convert a null renderer to a dummy renderer.
                renderers[i] = new DummyTrackRenderer();
            }
        }
        // Complete preparation.
        mVideoRenderer = renderers[TYPE_VIDEO];
        mCodecCounters = mVideoRenderer instanceof MediaCodecTrackRenderer
                ? ((MediaCodecTrackRenderer) mVideoRenderer).codecCounters
                : renderers[TYPE_AUDIO] instanceof MediaCodecTrackRenderer
                ? ((MediaCodecTrackRenderer) renderers[TYPE_AUDIO]).codecCounters : null;
        mBandwidthMeter = bandwidthMeter;
        pushSurface(false);
        mMediaPlayer.prepare(renderers);
        rendererBuildingState = RENDERER_BUILDING_STATE_BUILT;
    }

    /**
     * Invoked if a {@link RendererBuilder} encounters an error.
     *
     * @param e Describes the error.
     */
  /* package */ void onRenderersError(Exception e) {
        if (mInternalErrorListener != null) {
            mInternalErrorListener.onRendererInitializationError(e);
        }
        mListener.onError(e);
        rendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
        maybeReportPlayerState();
    }

    public void setPlayWhenReady(boolean playWhenReady) {
        mMediaPlayer.setPlayWhenReady(playWhenReady);
    }

    public void seekTo(long positionMs) {
        mMediaPlayer.seekTo(positionMs);
    }

    public void release() {
        mRendererBuilder.cancel();
        rendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
        mMediaPlayer.release();
    }

    public int getBufferedPercentage() {
        return mMediaPlayer.getBufferedPercentage();
    }

    public boolean getPlayWhenReady() {
        return mMediaPlayer.getPlayWhenReady();
    }

    /* package */ Looper getPlaybackLooper() {
        return mMediaPlayer.getPlaybackLooper();
    }

    public Handler getMainHandler() {
        return mHandler;
    }

    // com.google.android.exoplayer.ExoPlayer.Listener
    private com.google.android.exoplayer.ExoPlayer.Listener mExoPlayerListener = new ExoPlayer.Listener() {
        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int state) {
            maybeReportPlayerState();
        }

        @Override
        public void onPlayWhenReadyCommitted() {
            // Do nothing.
        }

        @Override
        public void onPlayerError(ExoPlaybackException exception) {
            rendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
            mListener.onError(exception);
        }
    };

    @Override
    public void onDroppedFrames(int count, long elapsed) {
        if (infoListener != null) {
            infoListener.onDroppedFrames(count, elapsed);
        }
    }

    @Override
    public void onBandwidthSample(int elapsedMs, long bytes, long bitrateEstimate) {
        if (infoListener != null) {
            infoListener.onBandwidthSample(elapsedMs, bytes, bitrateEstimate);
        }
    }

    @Override
    public void onDownstreamFormatChanged(int sourceId, Format format, int trigger, long mediaTimeMs) {
        if (infoListener == null) {
            return;
        }
        if (sourceId == TYPE_VIDEO) {
            mVideoFormat = format;
            infoListener.onVideoFormatEnabled(format, trigger, mediaTimeMs);
        } else if (sourceId == TYPE_AUDIO) {
            infoListener.onAudioFormatEnabled(format, trigger, mediaTimeMs);
        }
    }

    @Override
    public void onDrmKeysLoaded() {
        // Do nothing.
    }

    @Override
    public void onDrmSessionManagerError(Exception e) {
        if (mInternalErrorListener != null) {
            mInternalErrorListener.onDrmSessionManagerError(e);
        }
    }

    @Override
    public void onDecoderInitializationError(MediaCodecTrackRenderer.DecoderInitializationException e) {
        if (mInternalErrorListener != null) {
            mInternalErrorListener.onDecoderInitializationError(e);
        }
    }

    @Override
    public void onAudioTrackInitializationError(AudioTrack.InitializationException e) {
        if (mInternalErrorListener != null) {
            mInternalErrorListener.onAudioTrackInitializationError(e);
        }
    }

    @Override
    public void onAudioTrackWriteError(AudioTrack.WriteException e) {
        if (mInternalErrorListener != null) {
            mInternalErrorListener.onAudioTrackWriteError(e);
        }
    }

    @Override
    public void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
        if (mInternalErrorListener != null) {
            mInternalErrorListener.onAudioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
        }
    }

    @Override
    public void onCryptoError(MediaCodec.CryptoException e) {
        if (mInternalErrorListener != null) {
            mInternalErrorListener.onCryptoError(e);
        }
    }

    @Override
    public void onDecoderInitialized(String decoderName, long elapsedRealtimeMs,
                                     long initializationDurationMs) {
        if (infoListener != null) {
            infoListener.onDecoderInitialized(decoderName, elapsedRealtimeMs, initializationDurationMs);
        }
    }

    @Override
    public void onLoadError(int sourceId, IOException e) {
        if (mInternalErrorListener != null) {
            mInternalErrorListener.onLoadError(sourceId, e);
        }
    }

    @Override
    public void onCues(List<Cue> cues) {
        if (mCaptionListener != null && getSelectedTrack(TYPE_TEXT) != TRACK_DISABLED) {
            mCaptionListener.onCues(cues);
        }
    }

    @Override
    public void onMetadata(Map<String, Object> metadata) {
        if (mId3MetadataListener != null && getSelectedTrack(TYPE_METADATA) != TRACK_DISABLED) {
            mId3MetadataListener.onId3Metadata(metadata);
        }
    }

    @Override
    public void onAvailableRangeChanged(TimeRange availableRange) {
        if (infoListener != null) {
            infoListener.onAvailableRangeChanged(availableRange);
        }
    }

    @Override
    public void onDrawnToSurface(Surface surface) {
        // Do nothing.
    }

    @Override
    public void onLoadStarted(int sourceId, long length, int type, int trigger, Format format,
                              long mediaStartTimeMs, long mediaEndTimeMs) {
        if (infoListener != null) {
            infoListener.onLoadStarted(sourceId, length, type, trigger, format, mediaStartTimeMs,
                    mediaEndTimeMs);
        }
    }

    @Override
    public void onLoadCompleted(int sourceId, long bytesLoaded, int type, int trigger, Format format,
                                long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs) {
        if (infoListener != null) {
            infoListener.onLoadCompleted(sourceId, bytesLoaded, type, trigger, format, mediaStartTimeMs,
                    mediaEndTimeMs, elapsedRealtimeMs, loadDurationMs);
        }
    }

    @Override
    public void onLoadCanceled(int sourceId, long bytesLoaded) {
        // Do nothing.
    }

    @Override
    public void onUpstreamDiscarded(int sourceId, long mediaStartTimeMs, long mediaEndTimeMs) {
        // Do nothing.
    }

    /**
     *
     */
    public interface RendererBuilder {
        void buildRenderers(ExoVideoView player);
        void cancel();
    }

    /**
     * A listener for core events.
     */
    public interface Listener {
        void onStateChanged(boolean playWhenReady, int playbackState);
        void onError(Exception e);
        void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio);
    }

    /**
     * A listener for internal errors.
     * <p>
     * These errors are not visible to the user, and hence this listener is provided for
     * informational purposes only. Note however that an internal error may cause a fatal
     * error if the player fails to recover. If this happens, {@link Listener#onError(Exception)}
     * will be invoked.
     */
    public interface InternalErrorListener {
        void onRendererInitializationError(Exception e);
        void onAudioTrackInitializationError(AudioTrack.InitializationException e);
        void onAudioTrackWriteError(AudioTrack.WriteException e);
        void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs);
        void onDecoderInitializationError(MediaCodecTrackRenderer.DecoderInitializationException e);
        void onCryptoError(MediaCodec.CryptoException e);
        void onLoadError(int sourceId, IOException e);
        void onDrmSessionManagerError(Exception e);
    }

    /**
     * A listener for debugging information.
     */
    public interface InfoListener {
        void onVideoFormatEnabled(Format format, int trigger, long mediaTimeMs);
        void onAudioFormatEnabled(Format format, int trigger, long mediaTimeMs);
        void onDroppedFrames(int count, long elapsed);
        void onBandwidthSample(int elapsedMs, long bytes, long bitrateEstimate);
        void onLoadStarted(int sourceId, long length, int type, int trigger, Format format, long mediaStartTimeMs, long mediaEndTimeMs);
        void onLoadCompleted(int sourceId, long bytesLoaded, int type, int trigger, Format format, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs);
        void onDecoderInitialized(String decoderName, long elapsedRealtimeMs, long initializationDurationMs);
        void onAvailableRangeChanged(TimeRange availableRange);
    }

    /**
     * A listener for receiving notifications of timed text.
     */
    public interface CaptionListener {
        void onCues(List<Cue> cues);
    }

    /**
     * A listener for receiving ID3 metadata parsed from the media stream.
     */
    public interface Id3MetadataListener {
        void onId3Metadata(Map<String, Object> metadata);
    }
}
