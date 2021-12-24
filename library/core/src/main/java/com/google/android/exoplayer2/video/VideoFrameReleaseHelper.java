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
package com.google.android.exoplayer2.video;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.Choreographer;
import android.view.Choreographer.FrameCallback;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Helps a video {@link Renderer} release frames to a {@link Surface}. The helper:
 *
 * <ul>
 *   <li>Adjusts frame release timestamps to achieve a smoother visual result. The release
 *       timestamps are smoothed, and aligned with the default display's vsync signal.
 *   <li>Adjusts the {@link Surface} frame rate to inform the underlying platform of a fixed frame
 *       rate, when there is one.
 * </ul>
 */
public final class VideoFrameReleaseHelper {

  private static final String TAG = "VideoFrameReleaseHelper";

  /**
   * The minimum sum of frame durations used to calculate the current fixed frame rate estimate, for
   * the estimate to be treated as a high confidence estimate.
   */
  private static final long MINIMUM_MATCHING_FRAME_DURATION_FOR_HIGH_CONFIDENCE_NS = 5_000_000_000L;

  /**
   * The minimum change in media frame rate that will trigger a change in surface frame rate, given
   * a high confidence estimate.
   */
  private static final float MINIMUM_MEDIA_FRAME_RATE_CHANGE_FOR_UPDATE_HIGH_CONFIDENCE = 0.02f;

  /**
   * The minimum change in media frame rate that will trigger a change in surface frame rate, given
   * a low confidence estimate.
   */
  private static final float MINIMUM_MEDIA_FRAME_RATE_CHANGE_FOR_UPDATE_LOW_CONFIDENCE = 1f;

  /**
   * The minimum number of frames without a frame rate estimate, for the surface frame rate to be
   * cleared.
   */
  private static final int MINIMUM_FRAMES_WITHOUT_SYNC_TO_CLEAR_SURFACE_FRAME_RATE =
      2 * FixedFrameRateEstimator.CONSECUTIVE_MATCHING_FRAME_DURATIONS_FOR_SYNC;

  /** The period between sampling display VSYNC timestamps, in milliseconds. */
  private static final long VSYNC_SAMPLE_UPDATE_PERIOD_MS = 500;
  /**
   * The maximum adjustment that can be made to a frame release timestamp, in nanoseconds, excluding
   * the part of the adjustment that aligns frame release timestamps with the display VSYNC.
   */
  private static final long MAX_ALLOWED_ADJUSTMENT_NS = 20_000_000;
  /**
   * If a frame is targeted to a display VSYNC with timestamp {@code vsyncTime}, the adjusted frame
   * release timestamp will be calculated as {@code releaseTime = vsyncTime - ((vsyncDuration *
   * VSYNC_OFFSET_PERCENTAGE) / 100)}.
   */
  private static final long VSYNC_OFFSET_PERCENTAGE = 80;

  private final FixedFrameRateEstimator frameRateEstimator;
  @Nullable private final DisplayHelper displayHelper;
  @Nullable private final VSyncSampler vsyncSampler;

  private boolean started;
  @Nullable private Surface surface;

  /** The media frame rate specified in the {@link Format}. */
  private float formatFrameRate;
  /**
   * The media frame rate used to calculate the playback frame rate of the {@link Surface}. This may
   * be different to {@link #formatFrameRate} if {@link #formatFrameRate} is unspecified or
   * inaccurate.
   */
  private float surfaceMediaFrameRate;
  /** The playback frame rate set on the {@link Surface}. */
  private float surfacePlaybackFrameRate;

  private float playbackSpeed;
  @C.VideoChangeFrameRateStrategy private int changeFrameRateStrategy;

  private long vsyncDurationNs;
  private long vsyncOffsetNs;

  private long frameIndex;
  private long pendingLastAdjustedFrameIndex;
  private long pendingLastAdjustedReleaseTimeNs;
  private long lastAdjustedFrameIndex;
  private long lastAdjustedReleaseTimeNs;

  /**
   * Constructs an instance.
   *
   * @param context A context from which information about the default display can be retrieved.
   */
  public VideoFrameReleaseHelper(@Nullable Context context) {
    frameRateEstimator = new FixedFrameRateEstimator();
    displayHelper = maybeBuildDisplayHelper(context);
    vsyncSampler = displayHelper != null ? VSyncSampler.getInstance() : null;
    vsyncDurationNs = C.TIME_UNSET;
    vsyncOffsetNs = C.TIME_UNSET;
    formatFrameRate = Format.NO_VALUE;
    playbackSpeed = 1f;
    changeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS;
  }

  /**
   * Change the {@link C.VideoChangeFrameRateStrategy} used when calling {@link
   * Surface#setFrameRate}.
   */
  public void setChangeFrameRateStrategy(
      @C.VideoChangeFrameRateStrategy int changeFrameRateStrategy) {
    if (this.changeFrameRateStrategy == changeFrameRateStrategy) {
      return;
    }
    this.changeFrameRateStrategy = changeFrameRateStrategy;
    updateSurfacePlaybackFrameRate(/* forceUpdate= */ true);
  }

  /** Called when the renderer is enabled. */
  public void onEnabled() {
    if (displayHelper != null) {
      checkNotNull(vsyncSampler).addObserver();
      displayHelper.register(this::updateDefaultDisplayRefreshRateParams);
    }
  }

  /** Called when the renderer is started. */
  public void onStarted() {
    started = true;
    resetAdjustment();
    updateSurfacePlaybackFrameRate(/* forceUpdate= */ false);
  }

  /**
   * Called when the renderer changes which {@link Surface} it's rendering to renders to.
   *
   * @param surface The new {@link Surface}, or {@code null} if the renderer does not have one.
   */
  public void onSurfaceChanged(@Nullable Surface surface) {
    if (surface instanceof DummySurface) {
      // We don't care about dummy surfaces for release timing, since they're not visible.
      surface = null;
    }
    if (this.surface == surface) {
      return;
    }
    clearSurfaceFrameRate();
    this.surface = surface;
    updateSurfacePlaybackFrameRate(/* forceUpdate= */ true);
  }

  /** Called when the renderer's position is reset. */
  public void onPositionReset() {
    resetAdjustment();
  }

  /**
   * Called when the renderer's playback speed changes.
   *
   * @param playbackSpeed The factor by which playback is sped up.
   */
  public void onPlaybackSpeed(float playbackSpeed) {
    this.playbackSpeed = playbackSpeed;
    resetAdjustment();
    updateSurfacePlaybackFrameRate(/* forceUpdate= */ false);
  }

  /**
   * Called when the renderer's output format changes.
   *
   * @param formatFrameRate The format's frame rate, or {@link Format#NO_VALUE} if unknown.
   */
  public void onFormatChanged(float formatFrameRate) {
    this.formatFrameRate = formatFrameRate;
    frameRateEstimator.reset();
    updateSurfaceMediaFrameRate();
  }

  /**
   * Called by the renderer for each frame, prior to it being skipped, dropped or rendered.
   *
   * @param framePresentationTimeUs The frame presentation timestamp, in microseconds.
   */
  public void onNextFrame(long framePresentationTimeUs) {
    if (pendingLastAdjustedFrameIndex != C.INDEX_UNSET) {
      lastAdjustedFrameIndex = pendingLastAdjustedFrameIndex;
      lastAdjustedReleaseTimeNs = pendingLastAdjustedReleaseTimeNs;
    }
    frameIndex++;
    frameRateEstimator.onNextFrame(framePresentationTimeUs * 1000);
    updateSurfaceMediaFrameRate();
  }

  /** Called when the renderer is stopped. */
  public void onStopped() {
    started = false;
    clearSurfaceFrameRate();
  }

  /** Called when the renderer is disabled. */
  public void onDisabled() {
    if (displayHelper != null) {
      displayHelper.unregister();
      checkNotNull(vsyncSampler).removeObserver();
    }
  }

  // Frame release time adjustment.

  /**
   * Adjusts the release timestamp for the next frame. This is the frame whose presentation
   * timestamp was most recently passed to {@link #onNextFrame}.
   *
   * <p>This method may be called any number of times for each frame, including zero times (for
   * skipped frames, or when rendering the first frame prior to playback starting), or more than
   * once (if the caller wishes to give the helper the opportunity to refine a release time closer
   * to when the frame needs to be released).
   *
   * @param releaseTimeNs The frame's unadjusted release time, in nanoseconds and in the same time
   *     base as {@link System#nanoTime()}.
   * @return The adjusted frame release timestamp, in nanoseconds and in the same time base as
   *     {@link System#nanoTime()}.
   */
  /* framePresentationTimeUs:buffer队列中当前视频帧的时间戳,
     unadjustedReleaseTimeNs:待校准的送显预计时间（纳秒） */
  // 校准送显时间的原理分三步：
  //1.校准理论送显时间；
  //2.根据刷新率计算出距离理论送显时间最近的垂直同步信号时间点，作为最终的送显时间；
  //3.提前0.8个垂直同步信号来送显；
  public long adjustReleaseTime(long releaseTimeNs) {
    // Until we know better, the adjustment will be a no-op.
    /* 校准后的下次视频送显时间 */
    long adjustedReleaseTimeNs = releaseTimeNs;

    /* 代码一开始进来不会走这里 */
    if (lastAdjustedFrameIndex != C.INDEX_UNSET && frameRateEstimator.isSynced()) {
      long frameDurationNs = frameRateEstimator.getFrameDurationNs();
      /* 3.当前帧送显时间 = 上一帧送显的时间（已确定） + 平均每帧的持续时间 */
      // 然后根据平均帧间隔,加前一帧的pts,计算出一个ns级别的视频帧pts时间,否则从码流中读出来的pts往往只有ms精度.
      long candidateAdjustedReleaseTimeNs =
          lastAdjustedReleaseTimeNs
              + (long) ((frameDurationNs * (frameIndex - lastAdjustedFrameIndex)) / playbackSpeed); //首先计算平均帧间隔
      //推理出了一个当前帧的送显时间之后，还需要去做一次偏差波动的检测，看送检校准是否有意义
      if (adjustmentAllowed(releaseTimeNs, candidateAdjustedReleaseTimeNs)) {
        adjustedReleaseTimeNs = candidateAdjustedReleaseTimeNs;
      } else {
        resetAdjustment();
      }
    }
    pendingLastAdjustedFrameIndex = frameIndex;
    /* 更新本次送显的视频帧时间戳（已校准） */
    pendingLastAdjustedReleaseTimeNs = adjustedReleaseTimeNs;

    //计算出来的送显时间终究是一个理想值，我们需要找一个最近的物理设备刷新时间点去渲染视频
    if (vsyncSampler == null || vsyncDurationNs == C.TIME_UNSET) {
      //vsyncSampler会返回每个vsync信号的时间,正常情况下不会走到这个if逻辑里面
      return adjustedReleaseTimeNs;
    }
    // 寻找距离当前送显时间点最近(可能是在送显时间点之前,也可能是在送显时间点之后)的vsync时间点,我们的目标是在这个vsync时间点让视频帧显示出去,
    long sampledVsyncTimeNs = vsyncSampler.sampledVsyncTimeNs;
    if (sampledVsyncTimeNs == C.TIME_UNSET) {
      return adjustedReleaseTimeNs;
    }

    // Find the timestamp of the closest vsync. This is the vsync that we're targeting.
    /* 根据视频刷新率寻找最近的送显时间点 */
    //送显点物理设备在显示的时候，并不是等你有数据送过去了才会给你显示，它是自动刷新的
    //也就是我们经常听到的刷新率，比如显示器60Hz，144Hz
    // vsyncDuration_60Hz = 1000 / 60 = 16.7ms
    // vsyncDuration_144Hz = 1000 / 144 = 6.94ms

    // 上面计算出的是我们的目标vsync显示时间,但是要提前送,给后面的流程以时间,所以再减去vsyncOffsetNs时间,这个时间是写死的,
    // 定义为0.8*vsyncDuration,减完之后的这个值就是真正给mediacodec.releaseOutputBuffer方法的时间戳
    long snappedTimeNs = closestVsync(adjustedReleaseTimeNs, sampledVsyncTimeNs, vsyncDurationNs);
    // Apply an offset so that we release before the target vsync, but after the previous one.
    /* 提前送显：MediaCodec给的建议是最好提前两个vsync，但实际上exoplayer仅仅提前了0.8个vsync，原因不明 */
    return snappedTimeNs - vsyncOffsetNs;
  }

  private void resetAdjustment() {
    frameIndex = 0;
    lastAdjustedFrameIndex = C.INDEX_UNSET;
    pendingLastAdjustedFrameIndex = C.INDEX_UNSET;
  }

  //判断视频帧的pts距离他的送显时间是否有过大的偏移量
  private static boolean adjustmentAllowed(
      long unadjustedReleaseTimeNs, long adjustedReleaseTimeNs) {
    // 关于何为sync状态: 如果视频帧的pts和他的送显时间之间差了20ms以上,就认为偏移过大,也就认为失去sync了.
    // 在理想的情况下,一个视频帧的pts应该和它的送显时间一一对应,pts本身是个常量,送显时间的计算过程中存在着一个不确定变量,
    // 就是 elapsedSinceStartOfLoopUs, 这玩意的理想值永远是0,但实际上并不是,所以会在pts和对应的送显时间之间引入一些偏差,
    // 如果这个偏差大于20ms,就认为失去sync了,否则认为还没有失去sync.从一些简单的实验测试结果看,很少会有失去sync的情况出现
    // 以6帧作为间隔来计算平均帧间隔的问题是收敛会比较慢,可能算了半天都还有误差.当然好处是能够尽早开始计算,比较适合于码流本身帧间隔就不均匀的情况
    return Math.abs(unadjustedReleaseTimeNs - adjustedReleaseTimeNs) <= MAX_ALLOWED_ADJUSTMENT_NS;
  }

  // Surface frame rate adjustment.

  /**
   * Updates the media frame rate that's used to calculate the playback frame rate of the current
   * {@link #surface}. If the frame rate is updated then {@link #updateSurfacePlaybackFrameRate} is
   * called to update the surface.
   */
  private void updateSurfaceMediaFrameRate() {
    if (Util.SDK_INT < 30 || surface == null) {
      return;
    }

    float candidateFrameRate =
        frameRateEstimator.isSynced() ? frameRateEstimator.getFrameRate() : formatFrameRate;
    if (candidateFrameRate == surfaceMediaFrameRate) {
      return;
    }

    // The candidate is different to the current surface media frame rate. Decide whether to update
    // the surface media frame rate.
    boolean shouldUpdate;
    if (candidateFrameRate != Format.NO_VALUE && surfaceMediaFrameRate != Format.NO_VALUE) {
      boolean candidateIsHighConfidence =
          frameRateEstimator.isSynced()
              && frameRateEstimator.getMatchingFrameDurationSumNs()
                  >= MINIMUM_MATCHING_FRAME_DURATION_FOR_HIGH_CONFIDENCE_NS;
      float minimumChangeForUpdate =
          candidateIsHighConfidence
              ? MINIMUM_MEDIA_FRAME_RATE_CHANGE_FOR_UPDATE_HIGH_CONFIDENCE
              : MINIMUM_MEDIA_FRAME_RATE_CHANGE_FOR_UPDATE_LOW_CONFIDENCE;
      shouldUpdate = Math.abs(candidateFrameRate - surfaceMediaFrameRate) >= minimumChangeForUpdate;
    } else if (candidateFrameRate != Format.NO_VALUE) {
      shouldUpdate = true;
    } else {
      shouldUpdate =
          frameRateEstimator.getFramesWithoutSyncCount()
              >= MINIMUM_FRAMES_WITHOUT_SYNC_TO_CLEAR_SURFACE_FRAME_RATE;
    }

    if (shouldUpdate) {
      surfaceMediaFrameRate = candidateFrameRate;
      updateSurfacePlaybackFrameRate(/* forceUpdate= */ false);
    }
  }

  /**
   * Updates the playback frame rate of the current {@link #surface} based on the playback speed,
   * frame rate of the content, and whether the renderer is started.
   *
   * <p>Does nothing if {@link #changeFrameRateStrategy} is {@link
   * C#VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF}.
   *
   * @param forceUpdate Whether to call {@link Surface#setFrameRate} even if the frame rate is
   *     unchanged.
   */
  private void updateSurfacePlaybackFrameRate(boolean forceUpdate) {
    if (Util.SDK_INT < 30
        || surface == null
        || changeFrameRateStrategy == C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF) {
      return;
    }

    float surfacePlaybackFrameRate = 0;
    if (started && surfaceMediaFrameRate != Format.NO_VALUE) {
      surfacePlaybackFrameRate = surfaceMediaFrameRate * playbackSpeed;
    }
    // We always set the frame-rate if we have a new surface, since we have no way of knowing what
    // it might have been set to previously.
    if (!forceUpdate && this.surfacePlaybackFrameRate == surfacePlaybackFrameRate) {
      return;
    }
    this.surfacePlaybackFrameRate = surfacePlaybackFrameRate;
    Api30.setSurfaceFrameRate(surface, surfacePlaybackFrameRate);
  }

  /**
   * Clears the frame-rate of the current {@link #surface}.
   *
   * <p>Does nothing if {@link #changeFrameRateStrategy} is {@link
   * C#VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF}.
   */
  private void clearSurfaceFrameRate() {
    if (Util.SDK_INT < 30
        || surface == null
        || changeFrameRateStrategy == C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        || surfacePlaybackFrameRate == 0) {
      return;
    }
    surfacePlaybackFrameRate = 0;
    Api30.setSurfaceFrameRate(surface, /* frameRate= */ 0);
  }

  // Display refresh rate and vsync logic.

  private void updateDefaultDisplayRefreshRateParams(@Nullable Display defaultDisplay) {
    if (defaultDisplay != null) {
      double defaultDisplayRefreshRate = defaultDisplay.getRefreshRate();
      /* 垂直同步时间间隔 = 1秒钟 / 刷新率（60.0）纳秒*/
      vsyncDurationNs = (long) (C.NANOS_PER_SECOND / defaultDisplayRefreshRate);
      /* 提前送显的时间:VSYNC_OFFSET_PERCENTAGE = 80 */
      //要提前送显，这是因为为了保证送显的准时和高质量，
      //Google建议提前送显，送显的函数是MediaCodec.releaseOutputBuffer()
      //Google的建议是提前两个刷新点(即两个垂直同步信号)就调用这个接口去送显。
      //显然exoplayer并没有这么做，而是自己设定了一个固定值，0.8个vsync
      vsyncOffsetNs = (vsyncDurationNs * VSYNC_OFFSET_PERCENTAGE) / 100;
    } else {
      Log.w(TAG, "Unable to query display refresh rate");
      vsyncDurationNs = C.TIME_UNSET;
      vsyncOffsetNs = C.TIME_UNSET;
    }
  }

  /**
   * 根据视频刷新率寻找最近的送显时间点
   *
   * 寻找距离当前送显时间最近的vsync时间点
   *
   * @param releaseTime       最终计算出来的理论送显时间点（校准过后的理论送显时间）
   * @param sampledVsyncTime  记录物理设备渲染本帧的开始时间
   * @param vsyncDuration     垂直同步信号的时间间隔
   */
  private static long closestVsync(long releaseTime, long sampledVsyncTime, long vsyncDuration) {
    /* 1.计算需要刷新几次 */
    long vsyncCount = (releaseTime - sampledVsyncTime) / vsyncDuration;
    /* 2.计算出一个真实刷新时间 */
    long snappedTimeNs = sampledVsyncTime + (vsyncDuration * vsyncCount);
    long snappedBeforeNs;
    long snappedAfterNs;
    /* 3.计算出两种情况下距离送显时间最近的前后两个刷新点 */
    if (releaseTime <= snappedTimeNs) {
      // snappedTimeNs-vsyncDuration   ----    releaseTime ----- snappedTimeNs
      snappedBeforeNs = snappedTimeNs - vsyncDuration;
      snappedAfterNs = snappedTimeNs;
    } else {
      // snappedTimeNs   ----    releaseTime ----- snappedTimeNs+vsyncDuration
      snappedBeforeNs = snappedTimeNs;
      snappedAfterNs = snappedTimeNs + vsyncDuration;
    }
    /* 4.计算送显时间与前后两个刷新点的时间之差 */
    long snappedAfterDiff = snappedAfterNs - releaseTime;
    long snappedBeforeDiff = releaseTime - snappedBeforeNs;
    /* 5.哪个刷新点近就选哪个作为最终的送显时间 */
    return snappedAfterDiff < snappedBeforeDiff ? snappedAfterNs : snappedBeforeNs;
  }

  @Nullable
  private static DisplayHelper maybeBuildDisplayHelper(@Nullable Context context) {
    @Nullable DisplayHelper displayHelper = null;
    if (context != null) {
      context = context.getApplicationContext();
      if (Util.SDK_INT >= 17) {
        displayHelper = DisplayHelperV17.maybeBuildNewInstance(context);
      }
      if (displayHelper == null) {
        displayHelper = DisplayHelperV16.maybeBuildNewInstance(context);
      }
    }
    return displayHelper;
  }

  // Nested classes.

  @RequiresApi(30)
  private static final class Api30 {
    @DoNotInline
    public static void setSurfaceFrameRate(Surface surface, float frameRate) {
      int compatibility =
          frameRate == 0
              ? Surface.FRAME_RATE_COMPATIBILITY_DEFAULT
              : Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE;
      try {
        surface.setFrameRate(frameRate, compatibility);
      } catch (IllegalStateException e) {
        Log.e(TAG, "Failed to call Surface.setFrameRate", e);
      }
    }
  }

  /** Helper for listening to changes to the default display. */
  private interface DisplayHelper {

    /** Listener for changes to the default display. */
    interface Listener {

      /**
       * Called when the default display changes.
       *
       * @param defaultDisplay The default display, or {@code null} if a corresponding {@link
       *     Display} object could not be obtained.
       */
      void onDefaultDisplayChanged(@Nullable Display defaultDisplay);
    }

    /**
     * Enables the helper, invoking {@link Listener#onDefaultDisplayChanged(Display)} to pass the
     * initial default display.
     */
    void register(Listener listener);

    /** Disables the helper. */
    void unregister();
  }

  private static final class DisplayHelperV16 implements DisplayHelper {

    @Nullable
    public static DisplayHelper maybeBuildNewInstance(Context context) {
      WindowManager windowManager =
          (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
      return windowManager != null ? new DisplayHelperV16(windowManager) : null;
    }

    private final WindowManager windowManager;

    private DisplayHelperV16(WindowManager windowManager) {
      this.windowManager = windowManager;
    }

    @Override
    public void register(Listener listener) {
      listener.onDefaultDisplayChanged(windowManager.getDefaultDisplay());
    }

    @Override
    public void unregister() {
      // Do nothing.
    }
  }

  @RequiresApi(17)
  private static final class DisplayHelperV17
      implements DisplayHelper, DisplayManager.DisplayListener {

    @Nullable
    public static DisplayHelper maybeBuildNewInstance(Context context) {
      DisplayManager displayManager =
          (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
      return displayManager != null ? new DisplayHelperV17(displayManager) : null;
    }

    private final DisplayManager displayManager;
    @Nullable private Listener listener;

    private DisplayHelperV17(DisplayManager displayManager) {
      this.displayManager = displayManager;
    }

    @Override
    public void register(Listener listener) {
      this.listener = listener;
      displayManager.registerDisplayListener(this, Util.createHandlerForCurrentLooper());
      listener.onDefaultDisplayChanged(getDefaultDisplay());
    }

    @Override
    public void unregister() {
      displayManager.unregisterDisplayListener(this);
      listener = null;
    }

    @Override
    public void onDisplayChanged(int displayId) {
      if (listener != null && displayId == Display.DEFAULT_DISPLAY) {
        listener.onDefaultDisplayChanged(getDefaultDisplay());
      }
    }

    @Override
    public void onDisplayAdded(int displayId) {
      // Do nothing.
    }

    @Override
    public void onDisplayRemoved(int displayId) {
      // Do nothing.
    }

    private Display getDefaultDisplay() {
      return displayManager.getDisplay(Display.DEFAULT_DISPLAY);
    }
  }

  /**
   * Samples display vsync timestamps. A single instance using a single {@link Choreographer} is
   * shared by all {@link VideoFrameReleaseHelper} instances. This is done to avoid a resource leak
   * in the platform on API levels prior to 23. See [Internal: b/12455729].
   */
  private static final class VSyncSampler implements FrameCallback, Handler.Callback {

    public volatile long sampledVsyncTimeNs;

    private static final int CREATE_CHOREOGRAPHER = 0;
    private static final int MSG_ADD_OBSERVER = 1;
    private static final int MSG_REMOVE_OBSERVER = 2;

    private static final VSyncSampler INSTANCE = new VSyncSampler();

    private final Handler handler;
    private final HandlerThread choreographerOwnerThread;
    private @MonotonicNonNull Choreographer choreographer;
    private int observerCount;

    public static VSyncSampler getInstance() {
      return INSTANCE;
    }

    private VSyncSampler() {
      sampledVsyncTimeNs = C.TIME_UNSET;
      choreographerOwnerThread = new HandlerThread("ExoPlayer:FrameReleaseChoreographer");
      choreographerOwnerThread.start();
      handler = Util.createHandler(choreographerOwnerThread.getLooper(), /* callback= */ this);
      handler.sendEmptyMessage(CREATE_CHOREOGRAPHER);
    }

    /**
     * Notifies the sampler that a {@link VideoFrameReleaseHelper} is observing {@link
     * #sampledVsyncTimeNs}, and hence that the value should be periodically updated.
     */
    public void addObserver() {
      handler.sendEmptyMessage(MSG_ADD_OBSERVER);
    }

    /**
     * Notifies the sampler that a {@link VideoFrameReleaseHelper} is no longer observing {@link
     * #sampledVsyncTimeNs}.
     */
    public void removeObserver() {
      handler.sendEmptyMessage(MSG_REMOVE_OBSERVER);
    }

    //再去追doFrame，exoplayer的代码中是找不到的，因为这是个复写的方法，其来自接口FrameCallback
    // 就是在view开始绘制新的一帧视频时会记录该时间，然后回调给程序，那么，对应代码中的入参，
    // 我们也就清楚了，sampledVsyncTimeNs就是记录的物理设备在绘制上一帧时的开始时间
    @Override
    public void doFrame(long vsyncTimeNs) {
      /* 记录物理设备渲染本帧的开始时间 */
      sampledVsyncTimeNs = vsyncTimeNs;
      checkNotNull(choreographer).postFrameCallbackDelayed(this, VSYNC_SAMPLE_UPDATE_PERIOD_MS);
    }

    @Override
    public boolean handleMessage(Message message) {
      switch (message.what) {
        case CREATE_CHOREOGRAPHER:
          createChoreographerInstanceInternal();
          return true;
        case MSG_ADD_OBSERVER:
          addObserverInternal();
          return true;
        case MSG_REMOVE_OBSERVER:
          removeObserverInternal();
          return true;
        default:
          return false;
      }
    }

    private void createChoreographerInstanceInternal() {
      choreographer = Choreographer.getInstance();
    }

    private void addObserverInternal() {
      observerCount++;
      if (observerCount == 1) {
        checkNotNull(choreographer).postFrameCallback(this);
      }
    }

    private void removeObserverInternal() {
      observerCount--;
      if (observerCount == 0) {
        checkNotNull(choreographer).removeFrameCallback(this);
        sampledVsyncTimeNs = C.TIME_UNSET;
      }
    }
  }
}
