/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.opengl.GLSurfaceView;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.StrictMode;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.lifemedia.BatteryCollect;
import com.google.android.exoplayer2.lifemedia.ContextInformation;
import com.google.android.exoplayer2.lifemedia.HDMIConnectionEvent;
import com.google.android.exoplayer2.lifemedia.InetInfoCollect;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.DecoderInitializationException;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.DebugTextViewHelper;
import com.google.android.exoplayer2.ui.PlaybackControlView;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.demo.Samples.Sample;
import com.google.android.exoplayer2.util.VerboseLogUtil;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An activity that plays media using {@link SimpleExoPlayer}.
 */
public class PlayerActivity extends Activity implements OnClickListener, ExoPlayer.EventListener,
    PlaybackControlView.VisibilityListener {

  // For use within demo app code.
  public static final String CONTENT_ID_EXTRA = "content_id";
  public static final String CONTENT_TYPE_EXTRA = "content_type";
  public static final String PROVIDER_EXTRA = "provider";

  // For use when launching the demo app using adb.
  private static final String CONTENT_EXT_EXTRA = "type";

  private static final int QoSNotEnable = 1;
  private static final int QoSEnable = 2;
  private static final int QoSandContextEnable = 3;

  private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
  private static final CookieManager DEFAULT_COOKIE_MANAGER;

  private static long currentTimeInfo;

  static {
    DEFAULT_COOKIE_MANAGER = new CookieManager();
    DEFAULT_COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  }

  private Handler mainHandler;
  private EventLogger eventLogger;
  private SimpleExoPlayerView simpleExoPlayerView;
  private LinearLayout debugRootView;
  private TextView debugTextView;
  private Button retryButton;

  private DataSource.Factory mediaDataSourceFactory;
  private SimpleExoPlayer player;
  private DefaultTrackSelector trackSelector;
  private TrackSelectionHelper trackSelectionHelper;
  private DebugTextViewHelper debugViewHelper;
  private boolean needRetrySource;

  private Uri contentUri;
  private int contentType;
  private String contentId;
  private String provider;
  private boolean enableBackgroundAudio;
  private long playerPosition;

  private boolean shouldAutoPlay;
  private int resumeWindow;
  private long resumePosition;
  private static final String TAG_PLAY = "PlayerActivity";

  // LifeMedia
  private boolean CountDownActivated = false;
  private CountDownTimer cdTimer = null;
  private boolean CountDownCanceled = false;

  private boolean GetSaveTimeActivated = false;
  private String playerState = "stop";
  private String currentVideoURL;
  private String serverVideoURL;
  private String proxyVideoURL;
  private boolean streamChanged = false;
  private boolean enableProxySetting;
  private boolean streamNowChanging = false;
  private boolean stallingEvent = false;
  private String existingState = "";
  private Context context;

  private int lastReportedPlaybackState;
  private boolean lastReportedPlayWhenReady;
  private final CopyOnWriteArrayList<ExoPlayer.EventListener> listeners = new CopyOnWriteArrayList<>();

  private ContextInformation contextInfo;
  private BatteryCollect batteryInfo;
  private InetInfoCollect inetInfo;
  private HDMIConnectionEvent hdmiInfo;

  private Button proxyButton;
  private Button resolutionButton;
  private SurfaceView getSurface;
  private static int setSurfaceWidth;
  private static int setSurfaceHeight;

  // Activity lifecycle

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.d(TAG_PLAY,"onCreate");
    super.onCreate(savedInstanceState);

    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

    contextInfo = new ContextInformation();
    batteryInfo = new BatteryCollect(contextInfo);
    inetInfo = new InetInfoCollect(contextInfo);
    hdmiInfo = new HDMIConnectionEvent(contextInfo);
    registerReceiver(batteryInfo, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    registerReceiver(inetInfo, new IntentFilter(WifiManager.RSSI_CHANGED_ACTION));
    registerReceiver(inetInfo, new IntentFilter(Context.CONNECTIVITY_SERVICE));
    registerReceiver(hdmiInfo, new IntentFilter("android.intent.action.HDMI_PLUGGED"));

    shouldAutoPlay = true;
    clearResumePosition();
    mediaDataSourceFactory = buildDataSourceFactory(true);
    mainHandler = new Handler();

    if (CookieHandler.getDefault() != DEFAULT_COOKIE_MANAGER) {
      CookieHandler.setDefault(DEFAULT_COOKIE_MANAGER);
    }

    setContentView(R.layout.player_activity);
    View rootView = findViewById(R.id.root);
    rootView.setOnClickListener(this);

    debugRootView = (LinearLayout) findViewById(R.id.controls_root);
    debugTextView = (TextView) findViewById(R.id.debug_text_view);
    debugTextView.setVisibility(View.GONE);
    retryButton = (Button) findViewById(R.id.retry_button);
    retryButton.setOnClickListener(this);
    resolutionButton = (Button) findViewById(R.id.resolution_button);
    resolutionButton.setOnClickListener(this);

    proxyButton = (Button) findViewById(R.id.proxy_controls);

    simpleExoPlayerView = (SimpleExoPlayerView) findViewById(R.id.player_view);
    simpleExoPlayerView.setControllerVisibilityListener(this);
    simpleExoPlayerView.requestFocus();

    getSurface = (SurfaceView) simpleExoPlayerView.getVideoSurfaceView();
  }

  @Override
  public void onNewIntent(Intent intent) {
    Log.d(TAG_PLAY,"onNewIntent");
    releasePlayer();
    shouldAutoPlay = true;
    clearResumePosition();
    setIntent(intent);
  }

  @Override
  public void onStart() {
    super.onStart();
    if (Util.SDK_INT > 23) {
      Log.d(TAG_PLAY,"onStart");
      onShown();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if ((Util.SDK_INT <= 23 || player == null)) {
      Log.d(TAG_PLAY,"onResume");
      onShown();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (Util.SDK_INT <= 23) {
      Log.d(TAG_PLAY,"onPause");
      releasePlayer();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    if (Util.SDK_INT > 23) {
      Log.d(TAG_PLAY,"onStop");
      releasePlayer();
    }

    CountDownCanceled = true;
    sendState("stop");
    playerState = "stop";

    unregisterReceiver(batteryInfo);
    unregisterReceiver(inetInfo);
    unregisterReceiver(hdmiInfo);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      initializePlayer();
    }

    else {
      showToast(R.string.storage_permission_denied);
      finish();
    }
  }

  // Activity input

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    // Show the controls on any key event.
    simpleExoPlayerView.showController();
    // If the event was not handled then see if the player view can handle it as a media key event.
    return super.dispatchKeyEvent(event) || simpleExoPlayerView.dispatchMediaKeyEvent(event);
  }

  // OnClickListener methods

  @Override
  public void onClick(View view) {
    Log.d(TAG_PLAY,"onClick");
    if (view == retryButton) {
      setSaveTime(serverVideoURL, 0);
      setSaveTime(proxyVideoURL, 0);
      initializePlayer();
    }

   else if (view == resolutionButton){
     /* FrameLayout fr = (FrameLayout)findViewById(R.id.root);
      LinearLayout li = (LinearLayout)findViewById(R.id.abc);
      li.setLayoutParams(new FrameLayout.LayoutParams(30,60));*/
    }

    else if (view.getParent() == debugRootView) {
      MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
      if (mappedTrackInfo != null) {
        trackSelectionHelper.showSelectionDialog(this, ((Button) view).getText(),
            trackSelector.getCurrentMappedTrackInfo(), (int) view.getTag());
      }
    }
  }

  // PlaybackControlView.VisibilityListener implementation

  @Override
  public void onVisibilityChange(int visibility) {
    debugRootView.setVisibility(visibility);
  }

  // Internal methods

  private void initializePlayer() {
    Log.d(TAG_PLAY,"initializePlayer");
    Intent intent = getIntent();
    boolean needNewPlayer = player == null;

    if (needNewPlayer) {
      //boolean preferExtensionDecoders = intent.getBooleanExtra(PREFER_EXTENSION_DECODERS, false);
      //UUID drmSchemeUuid = intent.hasExtra(DRM_SCHEME_UUID_EXTRA) ? UUID.fromString(intent.getStringExtra(DRM_SCHEME_UUID_EXTRA)) : null;
      //DrmSessionManager<FrameworkMediaCrypto> drmSessionManager = null;

    /*  if (drmSchemeUuid != null) {
        String drmLicenseUrl = intent.getStringExtra(DRM_LICENSE_URL);
        String[] keyRequestPropertiesArray = intent.getStringArrayExtra(DRM_KEY_REQUEST_PROPERTIES);

        try {
          drmSessionManager = buildDrmSessionManager(drmSchemeUuid, drmLicenseUrl,
              keyRequestPropertiesArray);
        }

        catch (UnsupportedDrmException e) {
          int errorStringId = Util.SDK_INT < 18 ? R.string.error_drm_not_supported
              : (e.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
                  ? R.string.error_drm_unsupported_scheme : R.string.error_drm_unknown);
          showToast(errorStringId);

          return;
        }
      }*/

      /*@SimpleExoPlayer.ExtensionRendererMode int extensionRendererMode =
          ((DemoApplication) getApplication()).useExtensionRenderers()
              ? (preferExtensionDecoders ? SimpleExoPlayer.EXTENSION_RENDERER_MODE_PREFER
              : SimpleExoPlayer.EXTENSION_RENDERER_MODE_ON)
              : SimpleExoPlayer.EXTENSION_RENDERER_MODE_OFF;*/

      TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);
      trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
      trackSelectionHelper = new TrackSelectionHelper(trackSelector, videoTrackSelectionFactory);
      player = ExoPlayerFactory.newSimpleInstance(this, trackSelector, new DefaultLoadControl(), null, SimpleExoPlayer.EXTENSION_RENDERER_MODE_OFF);
      // drmSessionManager -> null , extensionRendererMode -> SimpleExoPlayer.EXTENSION_RENDERER_MODE_OFF
      player.addListener(this);

      eventLogger = new EventLogger(trackSelector);
      player.addListener(eventLogger);
      player.setAudioDebugListener(eventLogger);
      player.setVideoDebugListener(eventLogger);
      player.setMetadataOutput(eventLogger);

      simpleExoPlayerView.setPlayer(player);
      player.setPlayWhenReady(shouldAutoPlay);
      debugViewHelper = new DebugTextViewHelper(player, debugTextView);
      debugViewHelper.start();
    }

    if (needNewPlayer || needRetrySource) {
      /*String action = intent.getAction();
      Uri[] uris;
      String[] extensions;

      if (ACTION_VIEW.equals(action)) {
        uris = new Uri[] {intent.getData()};
        extensions = new String[] {intent.getStringExtra(EXTENSION_EXTRA)};
      }

      else if (ACTION_VIEW_LIST.equals(action)) {
        String[] uriStrings = intent.getStringArrayExtra(URI_LIST_EXTRA);
        uris = new Uri[uriStrings.length];

        for (int i = 0; i < uriStrings.length; i++) {
          uris[i] = Uri.parse(uriStrings[i]);
        }
        extensions = intent.getStringArrayExtra(EXTENSION_LIST_EXTRA);

        if (extensions == null) {
          extensions = new String[uriStrings.length];
        }
      }

      else {
        showToast(getString(R.string.unexpected_intent_action, action));
        return;
      }

      if (Util.maybeRequestReadExternalStoragePermission(this, uris)) {
        // The player will be reinitialized if the permission is granted.
        return;
      }

      MediaSource[] mediaSources = new MediaSource[uris.length];

      for (int i = 0; i < uris.length; i++) {
        mediaSources[i] = buildMediaSource(uris[i], extensions[i]);
      }

      MediaSource mediaSource = mediaSources.length == 1 ? mediaSources[0] : new ConcatenatingMediaSource(mediaSources);*/

      MediaSource mediaSource = buildMediaSource(intent.getData(), intent.getStringExtra(CONTENT_EXT_EXTRA));
      // Only one Uri arrived from LifeMedia Server

      boolean haveResumePosition = resumeWindow != C.INDEX_UNSET;

      if (haveResumePosition) {
        player.seekTo(resumeWindow, resumePosition);
      }

      player.prepare(mediaSource, !haveResumePosition, false);
      needRetrySource = false;
      updateButtonVisibilities();

    }

  }

  private MediaSource buildMediaSource(Uri uri, String overrideExtension) {
    Log.d(TAG_PLAY,"buildMediaSource");
    int type = TextUtils.isEmpty(overrideExtension) ? Util.inferContentType(uri)
        : Util.inferContentType("." + overrideExtension);

    Log.d(TAG_PLAY,"TYPE : " + String.valueOf(type));

    //Log.d(TAG_PLAY,"URI : " + uri.toString());

    switch (type) {
      case C.TYPE_SS:
        return new SsMediaSource(uri, buildDataSourceFactory(false),
            new DefaultSsChunkSource.Factory(mediaDataSourceFactory), mainHandler, eventLogger);
      case C.TYPE_DASH:
        return new DashMediaSource(uri, buildDataSourceFactory(false),
            new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler, eventLogger);
      case C.TYPE_HLS:
        return new HlsMediaSource(uri, mediaDataSourceFactory, mainHandler, eventLogger);
      case C.TYPE_OTHER:
        return new ExtractorMediaSource(uri, mediaDataSourceFactory, new DefaultExtractorsFactory(),
            mainHandler, eventLogger);
      default: {
        throw new IllegalStateException("Unsupported type: " + type);
      }
    }
  }

/*  private DrmSessionManager<FrameworkMediaCrypto> buildDrmSessionManager(UUID uuid,
      String licenseUrl, String[] keyRequestPropertiesArray) throws UnsupportedDrmException {

    if (Util.SDK_INT < 18) {
      return null;
    }

    HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(licenseUrl,
        buildHttpDataSourceFactory(false));

    if (keyRequestPropertiesArray != null) {
      for (int i = 0; i < keyRequestPropertiesArray.length - 1; i += 2) {
        drmCallback.setKeyRequestProperty(keyRequestPropertiesArray[i],
            keyRequestPropertiesArray[i + 1]);
      }
    }

    return new DefaultDrmSessionManager<>(uuid,
        FrameworkMediaDrm.newInstance(uuid), drmCallback, null, mainHandler, eventLogger);
  }*/

  private void releasePlayer() {

    Log.d(TAG_PLAY,"releasePlayer");

   /* unregisterReceiver(batteryInfo);
    unregisterReceiver(inetInfo);
    unregisterReceiver(hdmiInfo);*/

    if (player != null) {
      debugViewHelper.stop();
      debugViewHelper = null;
      shouldAutoPlay = player.getPlayWhenReady();
      updateResumePosition();
      setSaveTime(serverVideoURL, resumePosition);
      setSaveTime(proxyVideoURL, resumePosition);
      player.release();
      player = null;
      trackSelector = null;
      trackSelectionHelper = null;
      eventLogger = null;
    }
  }

  private void updateResumePosition() {
    Log.d(TAG_PLAY,"updateResumePosition");
    resumeWindow = player.getCurrentWindowIndex();
    resumePosition = player.isCurrentWindowSeekable() ? Math.max(0, player.getCurrentPosition()) : C.TIME_UNSET;
  }

  private void clearResumePosition() {
    Log.d(TAG_PLAY,"clearResumePosition");
    resumeWindow = C.INDEX_UNSET;
    resumePosition = C.TIME_UNSET;
  }

  /**
   * Returns a new DataSource factory.
   *
   * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
   *     DataSource factory.
   * @return A new DataSource factory.
   */
  private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
    return ((DemoApplication) getApplication()).buildDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
  }

  /**
   * Returns a new HttpDataSource factory.
   *
   * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
   *     DataSource factory.
   * @return A new HttpDataSource factory.
   */
  private HttpDataSource.Factory buildHttpDataSourceFactory(boolean useBandwidthMeter) {
    return ((DemoApplication) getApplication()).buildHttpDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
  }

  // ExoPlayer.EventListener implementation

  @Override
  public void onLoadingChanged(boolean isLoading) {
    // Do nothing.
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {

    Log.d(TAG_PLAY,"onPlayerStateChanged");

    if (playbackState == ExoPlayer.STATE_ENDED) {
      needRetrySource = true;
      showControls();
    }

    switch(playbackState){
      case ExoPlayer.STATE_BUFFERING:
          Log.d(TAG_PLAY,"STATE_BUFFERING");
        if(playerState.equals("start")){
          stallingEvent = true;
          sendState("stalling");
        }
        break;

      case ExoPlayer.STATE_ENDED:
          Log.d(TAG_PLAY,"STATE_ENDED");
        retry();
        if(!playerState.equals("end")){
          sendState("end");
          playerState = "end";
        }
        break;

      case ExoPlayer.STATE_IDLE:
          Log.d(TAG_PLAY,"STATE_IDLE");
        break;

      case ExoPlayer.STATE_READY:
          Log.d(TAG_PLAY,"STATE_READY");
        if(GetSaveTimeActivated && !playerState.equals("init")){
          // Pause
            Log.d(TAG_PLAY,"init");
          sendState("init");
          playerState = "init";
        }

        else if(player.getPlayWhenReady() && !playerState.equals("start")){
            Log.d(TAG_PLAY,"start");
          sendState("start");
          playerState = "start";
        }

        else if(!player.getPlayWhenReady() && playerState.equals("start")){
            Log.d(TAG_PLAY,"pause");
          sendState("pause");
          playerState = "pause";
        }

        else if(stallingEvent){
            Log.d(TAG_PLAY,"stalling");
          sendState("start");
          stallingEvent = false;
        }

        if(!GetSaveTimeActivated){
          Log.d(TAG_PLAY,"CountDown");
          if(CountDownActivated){
            cdTimer.cancel();
            cdTimer = null;
          }

          CountDownPlayer(player.getDuration(), player.getCurrentPosition());
          cdTimer.start();
        }

        else {
          ; // Pause
        }
        break;

      default:
        break;
    }

    updateButtonVisibilities();
    maybeReportPlayerState();

  }

  @Override
  public void onPositionDiscontinuity() {

    Log.d(TAG_PLAY,"onPositionDiscontinuity");

    if (needRetrySource) {
      // This will only occur if the user has performed a seek whilst in the error state. Update the
      // resume position so that if the user then retries, playback will resume from the position to
      // which they seeked.
      updateResumePosition();
    }
  }

  @Override
  public void onTimelineChanged(Timeline timeline, Object manifest) {
    // Do nothing.
  }

  @Override
  public void onPlayerError(ExoPlaybackException e) {

    Log.d(TAG_PLAY,"onPlayerError");

    String errorString = null;

    if (e.type == ExoPlaybackException.TYPE_RENDERER) {

      Exception cause = e.getRendererException();

      if (cause instanceof DecoderInitializationException) {

        // Special case for decoder initialization failures.
        DecoderInitializationException decoderInitializationException = (DecoderInitializationException) cause;

        if (decoderInitializationException.decoderName == null) {


          if (decoderInitializationException.getCause() instanceof DecoderQueryException) {

            errorString = getString(R.string.error_querying_decoders);
          }

          else if (decoderInitializationException.secureDecoderRequired) {

            errorString = getString(R.string.error_no_secure_decoder,
                decoderInitializationException.mimeType);
          }

          else {

            errorString = getString(R.string.error_no_decoder,
                decoderInitializationException.mimeType);
          }
        }

        else {

          errorString = getString(R.string.error_instantiating_decoder,
              decoderInitializationException.decoderName);
        }
      }
    }

    if (errorString != null) {
      showToast(errorString);
    }

    needRetrySource = true;

    if (isBehindLiveWindow(e)) {
      clearResumePosition();
      initializePlayer();
    }

    else {
      updateResumePosition();
      updateButtonVisibilities();
      showControls();
    }
  }

  @Override
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    Log.d(TAG_PLAY,"onTracksChanged");

    updateButtonVisibilities();
    MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();

    if (mappedTrackInfo != null) {

      if (mappedTrackInfo.getTrackTypeRendererSupport(C.TRACK_TYPE_VIDEO) == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
        showToast(R.string.error_unsupported_video);
      }

      if (mappedTrackInfo.getTrackTypeRendererSupport(C.TRACK_TYPE_AUDIO) == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
        showToast(R.string.error_unsupported_audio);
      }
    }
  }

  // User controls

  private void updateButtonVisibilities() {
    debugRootView.removeAllViews();

    retryButton.setVisibility(needRetrySource ? View.VISIBLE : View.GONE);
    proxyButton.setVisibility(getProxyState() ? View.VISIBLE : View.GONE);
    resolutionButton.setVisibility(View.VISIBLE);
    debugRootView.addView(retryButton);
    debugRootView.addView(proxyButton);
    debugRootView.addView(resolutionButton);

    if (player == null) {
      return;
    }

    MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
    if (mappedTrackInfo == null) {
      return;
    }

    for (int i = 0; i < mappedTrackInfo.length; i++) {
      TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(i);

      if (trackGroups.length != 0) {
        Button button = new Button(this);
        int label;
        switch (player.getRendererType(i)) {
          case C.TRACK_TYPE_AUDIO:
            label = R.string.audio;
            break;
          case C.TRACK_TYPE_VIDEO:
            label = R.string.video;
            break;
          case C.TRACK_TYPE_TEXT:
            label = R.string.text;
            break;
          default:
            continue;
        }

        button.setText(label);
        button.setTag(i);
        button.setOnClickListener(this);
        debugRootView.addView(button, debugRootView.getChildCount() - 1);
      }
    }

    if(ContextInformation.onServerError && !streamNowChanging){
      onStreamShouldBeChanged(currentVideoURL);
      ContextInformation.onServerError = false;
    }
  }

  private void showControls() {
    debugRootView.setVisibility(View.VISIBLE);
  }

  private void showToast(int messageId) {
    showToast(getString(messageId));
  }

  private void showToast(String message) {
    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
  }

  private static boolean isBehindLiveWindow(ExoPlaybackException e) {
    if (e.type != ExoPlaybackException.TYPE_SOURCE) {
      return false;
    }

    Throwable cause = e.getSourceException();
    while (cause != null) {

      if (cause instanceof BehindLiveWindowException) {
        return true;
      }
      cause = cause.getCause();
    }

    return false;
  }
  //////////////////////////////////////////////////////////////////////////////////////////////////



  //////////////////////////////////////// LifeMedia ///////////////////////////////////////////////

  private long getSaveTime(String Url) {
    Log.d(TAG_PLAY,"getSaveTime");
    long destroyTime = 0;
    SharedPreferences pref = getSharedPreferences("VideoTime", MODE_PRIVATE);
    destroyTime = pref.getLong(Url, 0);

    if (destroyTime != 0) {
    }

    return pref.getLong(Url, 0);
  }

  private void setSaveTime(String Url, long destroyTime) {
    Log.d(TAG_PLAY,"setSaveTime");
    SharedPreferences pref = getSharedPreferences("VideoTime", MODE_PRIVATE);
    SharedPreferences.Editor editor = pref.edit();
    editor.putLong(Url, destroyTime);
    editor.commit();
  }

  public String getServerURL(String key) {
    //Log.d(TAG_PLAY,"getServerURL");
    SharedPreferences sp = getSharedPreferences("ServerURL", MODE_PRIVATE);
    return sp.getString(key, "");
  }

  public void CountDownPlayer(long VideoDuration, long CurrentPosition) {
    Log.d(TAG_PLAY,"CountDownPlayer");
    CountDownActivated = true;
    cdTimer = new CountDownTimer(VideoDuration - CurrentPosition, 1000) {
      @Override
      public void onFinish() {
      }

      @Override
      public void onTick(long millisUntilFinished) {

        if (CountDownCanceled) {
          cancel();
        }

        else {
          try {
            sendCurrentPosition();
          }

          catch (Exception e) {
            e.printStackTrace();
          }
        }
      }
    };
  }

  public void sendCurrentPosition() {
    Log.d(TAG_PLAY,"sendCurrentPosition");

    String ProxyURL = getServerURL("ProxyURL");
    String ProxyPort = getServerURL("ProxyPort");
    ProxyURL = ProxyURL + ":" + ProxyPort + "/dash_lifemedia/lifemedia-monitoring/MonitorServlet?";

    String RequestMessage = "";
    Map<String, String> requestHeader = new HashMap<String, String>();

    long currentPosition = player.getCurrentPosition();
    long videoDuration = player.getDuration();
    int videoHeight = 0;
    int currentBitrate = 0;
    int RSSI = ContextInformation.RSSI;
    int batteryPercent = ContextInformation.batteryPercent;
    long estimatedBandwidth = BANDWIDTH_METER.getBitrateEstimate();
    long estimatedBandwidthJitter = BANDWIDTH_METER.getBitrateJitterEstimate();

    Log.d(TAG_PLAY,"Estimated Bandwidth : " + estimatedBandwidth);

    currentTimeInfo = currentPosition;

    if(player.getVideoFormat() == null){
      videoHeight = -1;
      currentBitrate = -1;
    }

    else{
      currentBitrate = player.getVideoFormat().bitrate;
      videoHeight = player.getVideoFormat().height;
    }

    Log.d(TAG_PLAY,"Bitrate : " + currentBitrate);

    double bufferedPostionPercent = ((double) player.getBufferedPercentage() / 100.0)
            * (double) player.getDuration();
    double positionMs = (bufferedPostionPercent - (double) player.getCurrentPosition());

    requestHeader.put("X-LifeMedia-AndroidID", Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID));
    requestHeader.put("User-Agent", Util.getUserAgent(getApplicationContext(), "ExoPlayerDemo"));
    requestHeader.put("X-LifeMedia-CurrentPosition", String.valueOf(currentPosition / 1000));
    requestHeader.put("X-LifeMedia-CurrentBitrate", String.valueOf(currentBitrate));
    requestHeader.put("X-LifeMedia-CurrentQuality", String.valueOf(videoHeight));
    requestHeader.put("X-LifeMedia-Duration", String.valueOf(videoDuration));
    requestHeader.put("X-LifeMedia-CurrentBuffer", String.valueOf(positionMs));
    requestHeader.put("X-LifeMedia-EstimatedBandwidth", String.valueOf(estimatedBandwidth * 1000));
    requestHeader.put("X-LifeMedia-ContentURL", currentVideoURL);
    requestHeader.put("X-LifeMedia-EstimatedBandwidthJitter", String.valueOf(estimatedBandwidthJitter));
    requestHeader.put("X-LifeMedia-RSSI", String.valueOf(RSSI));
    requestHeader.put("X-LifeMedia-BatteryPercent",String.valueOf(batteryPercent));

    RequestMessage = "currentInfo=OKOK";
    // Why this message exists?

    Log.d(TAG_PLAY,"Current Quality : " + String.valueOf(videoHeight));

    try {
      StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
      StrictMode.setThreadPolicy(policy);
      Util.executePost(ProxyURL, RequestMessage.getBytes(), requestHeader);
    }

    catch (IOException e) {
      e.printStackTrace();
    }

    catch (Exception e) {
      e.printStackTrace();
    }

    Log.d(TAG_PLAY,"Player State : " + playerState);

    if(playerState != "pause"){ // Notify Real-Time Update
      sendState("start");
    }

    RequestEvent();

  }

  public String getCurrentVideoURL(String Url) {
    // Log.d(TAG_PLAY,"getCurrentVideoURL");
    String returnUrl = Url;

    String ServerURL = getServerURL("ServerURL");
    String ServerPath = getServerURL("ServerPath");
    String ProxyURL = getServerURL("ProxyURL");
    String ProxyPort = getServerURL("ProxyPort");
    String ProxyPath = getServerURL("ProxyPath");

    if (!enableProxySetting) {

      if (Url.contains(ProxyURL) && Url.contains(ProxyPort) && Url.contains(ProxyPath) && Url.contains(".mpd")) {
        returnUrl = Url.replace(ProxyURL, ServerURL).replace(":" + ProxyPort, "").replace(ProxyPath, ServerPath)
                .replace(".mpd", ".mp4");
      }
    }

    else {

      if (Url.contains(ServerURL) && Url.contains(ServerPath) && Url.contains(".mp4")) {
        returnUrl = Url.replace(ServerURL + "/", ProxyURL + ":" + ProxyPort + "/")
                .replace(ServerPath, ProxyPath).replace(".mp4", ".mpd");
      }
    }

    return returnUrl;
  }

  public void reloadVideo(boolean proxyEnable) {
    Log.d(TAG_PLAY,"reloadVideo");
    Intent intent = getIntent();
    String videoUri = currentVideoURL;
    int videoType = 0;

    String ServerURL = getServerURL("ServerURL");
    String ServerPath = getServerURL("ServerPath");
    String ProxyURL = getServerURL("ProxyURL");
    String ProxyPort = getServerURL("ProxyPort");
    String ProxyPath = getServerURL("ProxyPath");

    if (!proxyEnable) {

      if (videoUri.contains(ProxyURL) && videoUri.contains(ProxyPort) && videoUri.contains(ProxyPath)
              && videoUri.contains(".mpd")) {
        videoUri = videoUri.replace(ProxyURL, ServerURL).replace(":" + ProxyPort, "")
                .replace(ProxyPath, ServerPath).replace(".mpd", ".mp4");
        videoType = C.TYPE_OTHER;
        streamNowChanging = true;
      }
    }

    else {

      if (videoUri.contains(ServerURL) && videoUri.contains(ServerPath) && videoUri.contains(".mp4")) {
        videoUri = videoUri.replace(ServerURL + "/", ProxyURL + ":" + ProxyPort + "/")
                .replace(ServerPath, ProxyPath).replace(".mp4", ".mpd");
        videoType = C.TYPE_DASH;
        streamNowChanging = false;
      }
    }

    currentVideoURL = videoUri;
    Sample sample = new Sample(videoUri, videoUri, videoType);
    Intent mpdIntent = new Intent(this, this.getClass()).setData(Uri.parse(sample.uri))
            .putExtra(PlayerActivity.CONTENT_ID_EXTRA, sample.contentId)
            .putExtra(PlayerActivity.CONTENT_TYPE_EXTRA, sample.type)
            .putExtra(PlayerActivity.PROVIDER_EXTRA, sample.provider);

    contentUri = mpdIntent.getData();
    contentType = mpdIntent.getIntExtra(CONTENT_TYPE_EXTRA, inferContentType(contentUri, intent.getStringExtra(CONTENT_EXT_EXTRA)));
    contentId = mpdIntent.getStringExtra(CONTENT_ID_EXTRA);
    provider = mpdIntent.getStringExtra(PROVIDER_EXTRA);

    streamChanged = true;
    playerPosition = player.getCurrentPosition();

    if(!enableProxySetting){ // releasePlayer() -> initializePlayer(), Switching delay is too long
      sendState(existingState);
    }

    else if(enableProxySetting){
      sendState(existingState);
    }

    initializePlayer();
  }

  @SuppressLint("SimpleDateFormat")
  public void sendState(String clientState) {

    Log.d(TAG_PLAY,"sendState");

    Log.d(TAG_PLAY,"Response Quality : " + Util.responseHeader);

    String ProxyURL = getServerURL("ProxyURL");
    String ProxyPort = getServerURL("ProxyPort");


    ProxyURL = ProxyURL + ":" + ProxyPort + "/dash_lifemedia/lifemedia-monitoring/MonitorServlet?";
    Map<String, String> requestHeader = new HashMap<String, String>();
    requestHeader.put("X-LifeMedia-AndroidID", Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID));
    requestHeader.put("User-Agent", Util.getUserAgent(getApplicationContext(), "ExoPlayerDemo"));
    int playType = 0;

    if(!enableProxySetting){
      playType = QoSNotEnable;
    }

    else{

      if(ContextInformation.contextEnable){
        playType = QoSandContextEnable;
      }

      else{
        playType = QoSEnable;
      }
    }

    Log.d(TAG_PLAY,"Client State : " + clientState);

    if (clientState.equals("stop")) {
      requestHeader.put("X-LifeMedia-ViewingDuration", String.valueOf(currentTimeInfo / 1000));
      requestHeader.put("X-LifeMedia-ViewingDeviceType", ContextInformation.deviceType);
      //long time = System.currentTimeMillis();
      Date today = new Date();

      SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd aa",Locale.US);
      SimpleDateFormat time = new SimpleDateFormat("hh:mm:ss",Locale.US);

      String dayString = day.format(today);
      String timeString = time.format(today);

      requestHeader.put("X-LifeMedia-ViewingTime", dayString + " " + timeString);

      //Log.d(TAG_PLAY,"Viewing Duration : " + String.valueOf(playerPosition));
    }

    try {
      StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
      StrictMode.setThreadPolicy(policy);
      String stateData = "state=" + clientState + "&playtype=" + playType;
      existingState = clientState;
      Util.executePost(ProxyURL, stateData.getBytes(), requestHeader);
    }

    catch (IOException e) {
      e.printStackTrace();
    }
  }

  private boolean getProxyState() {
    return true;
  }

  public void showProxyPopup(View v) {
    Log.d(TAG_PLAY,"showProxyPopup");
    PopupMenu popup = new PopupMenu(this, v);
    Menu menu = popup.getMenu();
    menu.add(Menu.NONE, 0, Menu.NONE, R.string.on);
    menu.add(Menu.NONE, 1, Menu.NONE, R.string.context_aware);
    final MenuItem enableProxyItem = menu.findItem(0);
    enableProxyItem.setCheckable(true);
    enableProxyItem.setChecked(enableProxySetting);
    final MenuItem contextAwareItem = menu.findItem(1);
    contextAwareItem.setVisible(enableProxySetting);
    contextAwareItem.setCheckable(enableProxySetting);
    contextAwareItem.setChecked(ContextInformation.contextEnable);

    final PopupMenu.OnMenuItemClickListener clickListener = new PopupMenu.OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {

        if (item == enableProxyItem) {
          enableProxySetting = !item.isChecked();

          if(!playerState.equals("end")){
            reloadVideo(enableProxySetting);
          }

          return true;
        }

        if (item == contextAwareItem) {
          ContextInformation.contextEnable = !item.isChecked();
          sendState(existingState);
          return true;
        }

        return false;
      }
    };

    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        return (clickListener != null && clickListener.onMenuItemClick(item));
      }
    });

    popup.show();
  }

  public void showVerboseLogPopup(View v) {

    PopupMenu popup = new PopupMenu(this, v);
    Menu menu = popup.getMenu();
    menu.add(Menu.NONE, 0, Menu.NONE, R.string.logging_normal);
    menu.add(Menu.NONE, 1, Menu.NONE, R.string.logging_verbose);
    menu.setGroupCheckable(Menu.NONE, true, true);
    menu.findItem((VerboseLogUtil.areAllTagsEnabled()) ? 1 : 0).setChecked(true);
    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {

        if (item.getItemId() == 0) {
          VerboseLogUtil.setEnableAllTags(false);
        }

        else {
          VerboseLogUtil.setEnableAllTags(true);
        }

        return true;
      }
    });

    popup.show();
  }

  private static int inferContentType(Uri uri, String fileExtension) {

    String lastPathSegment = !TextUtils.isEmpty(fileExtension) ? "." + fileExtension : uri.getLastPathSegment();
    return Util.inferContentType(lastPathSegment);
  }

  private void onShown() {
    Log.d(TAG_PLAY,"onShown");
    Intent intent = getIntent();
    contentUri = intent.getData();
    contentType = intent.getIntExtra(CONTENT_TYPE_EXTRA, inferContentType(contentUri, intent.getStringExtra(CONTENT_EXT_EXTRA)));
    contentId = intent.getStringExtra(CONTENT_ID_EXTRA);
    provider = intent.getStringExtra(PROVIDER_EXTRA);

    String stringContentUri = intent.getDataString();

    if (stringContentUri.contains(".mpd")) {
      enableProxySetting = true;
      proxyVideoURL = stringContentUri;
    }

    else if (stringContentUri.contains(".mp4")) {
      enableProxySetting = false;
      serverVideoURL = stringContentUri;
    }

    currentVideoURL = getCurrentVideoURL(stringContentUri);

    if (player == null) {
      if (!maybeRequestPermission()) {
        initializePlayer();
      }
    }

    else {
      // Nothing
    }
  }

  public void RequestEvent() {
    //Log.d(TAG_PLAY,"RequestEvent");
    // Display

    WindowManager windowManager = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
    Display display = windowManager.getDefaultDisplay();
    Point displaySize = new Point();

    if (Util.SDK_INT >= 23) {
     // Log.d(TAG_PLAY,"SDK_INT >= 23");
      getDisplaySizeV23(display, displaySize);
    }

    else if (Util.SDK_INT >= 17) {
    // Log.d(TAG_PLAY,"SDK_INT >= 17");
      getDisplaySizeV17(display, displaySize);
    }

    else if (Util.SDK_INT >= 16) {
     // Log.d(TAG_PLAY,"SDK_INT >= 16");
      getDisplaySizeV16(display, displaySize);
    }

    else {
      Log.d(TAG_PLAY,"SDK_INT >= 9");
      getDisplaySizeV9(display, displaySize);
    }

    // Internet Connection Type
    WifiManager wifiManager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
    WifiInfo wifiInfo = null;
    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
    boolean isConnected = activeNetwork.isConnectedOrConnecting();
    boolean isWiFi;
    String connectionType;
    int RSSI = 0;

    if(wifiManager != null){
      wifiInfo = wifiManager.getConnectionInfo();
      if(wifiInfo != null){
        RSSI = wifiInfo.getRssi();
      }
    }

    Intent temporalIntent = getIntent();

    int batteryStatus = temporalIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
    boolean isCharging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING || batteryStatus == BatteryManager.BATTERY_STATUS_FULL;
    int level = temporalIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    int scale = temporalIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    int batteryRatio = (level * 100) / scale;

    if(isCharging && batteryRatio != 0){
      ContextInformation.batteryPercent = batteryRatio;
    }

    else if(batteryRatio < 100 && batteryRatio != 0){
      ContextInformation.batteryPercent = batteryRatio;
    }

    if (isConnected) {
      isWiFi = activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
      ContextInformation.connectionType = isWiFi ? "WiFi" : "Mobile";
    }

    connectionType = ContextInformation.connectionType;

    if(connectionType != null){
      if(connectionType.equals("Mobile")){
        ContextInformation.RSSI = 0;
      }

      else if(connectionType.equals("WiFi") && RSSI != 0 && wifiInfo != null){
        ContextInformation.RSSI = RSSI;
      }
    }

    String deviceType = isTablet(getApplicationContext()) ? "Tab" : "Phone";

    double bufferedPostionPercent = ((double) player.getBufferedPercentage() / 100.0) * (double) player.getDuration();
    double positionMs = (bufferedPostionPercent - (double) player.getCurrentPosition());


    // Network Context Information
    ContextInformation.bandwidth = BANDWIDTH_METER.getBitrateEstimate();
    ContextInformation.bitrateJitter = BANDWIDTH_METER.getBitrateJitterEstimate();
    // Device Context Information
    ContextInformation.deviceModel = Build.MODEL;
    ContextInformation.width = displaySize.x;
    ContextInformation.height = displaySize.y;
    ContextInformation.deviceType = deviceType;
    ContextInformation.bufferMs = positionMs;
    ContextInformation.androidID = Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID);
  }

  @TargetApi(23)
  private static void getDisplaySizeV23(Display display, Point outSize) {
    Display.Mode mode = display.getMode();
    outSize.x = mode.getPhysicalWidth();
    outSize.y = mode.getPhysicalHeight();
  }

  @TargetApi(17)
  private static void getDisplaySizeV17(Display display, Point outSize) {
    display.getRealSize(outSize);
  }

  @TargetApi(16)
  private static void getDisplaySizeV16(Display display, Point outSize) {
    display.getSize(outSize);
  }

  @SuppressWarnings("deprecation")
  private static void getDisplaySizeV9(Display display, Point outSize) {
    outSize.x = display.getWidth();
    outSize.y = display.getHeight();
  }

  private static boolean isTablet(Context context) {
    return (context.getResources().getConfiguration().screenLayout
            & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
  }

  @TargetApi(23)
  private boolean maybeRequestPermission() {
    if (requiresPermission(contentUri)) {
      requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE }, 0);
      return true;
    }

    else {
      return false;
    }
  }

  @TargetApi(23)
  private boolean requiresPermission(Uri uri) {
    return Util.SDK_INT >= 23 && Util.isLocalFileUri(uri)
            && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
  }

  private void maybeReportPlayerState() {
    Log.d(TAG_PLAY,"maybeReportPlayerState");
    boolean playWhenReady = player.getPlayWhenReady();
    int playbackState = player.getPlaybackState();
    if (lastReportedPlayWhenReady != playWhenReady || lastReportedPlaybackState != playbackState) {
      for (ExoPlayer.EventListener listener : listeners) {
        listener.onPlayerStateChanged(playWhenReady, playbackState);
      }
      lastReportedPlayWhenReady = playWhenReady;
      lastReportedPlaybackState = playbackState;
    }
  }

  public void onStreamShouldBeChanged(String url) {
    Log.d(TAG_PLAY, "onStreamShouldBeChanged");
    String ProxyURL = getServerURL("ProxyURL");
    String ProxyPort = getServerURL("ProxyPort");
    String ProxyPath = getServerURL("ProxyPath");

    Toast.makeText(getApplicationContext(), "Stream Error is occurred : " + url, Toast.LENGTH_SHORT).show();

    if (url.contains(ProxyURL) && url.contains(ProxyPort) && url.contains(ProxyPath) && !streamNowChanging) {
      enableProxySetting = enableProxySetting ? false : true;
      streamNowChanging = streamNowChanging ? false : true;
      streamChanged = true;
      reloadVideo(enableProxySetting);
    }
  }

  private void retry(){
    Log.d(TAG_PLAY , "retry");
    toggleControlsVisibility();
  }

  private void toggleControlsVisibility(){
    Log.d(TAG_PLAY, "toggleControlsVisibility");
      debugRootView.setVisibility(View.GONE);
      proxyButton.setVisibility(View.GONE);
      resolutionButton.setVisibility(View.GONE);
  }
}
