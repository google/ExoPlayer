/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.transformerdemo;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.effect.Contrast;
import com.google.android.exoplayer2.effect.GlEffect;
import com.google.android.exoplayer2.effect.GlTextureProcessor;
import com.google.android.exoplayer2.effect.HslAdjustment;
import com.google.android.exoplayer2.effect.RgbAdjustment;
import com.google.android.exoplayer2.effect.RgbFilter;
import com.google.android.exoplayer2.effect.RgbMatrix;
import com.google.android.exoplayer2.effect.SingleColorLut;
import com.google.android.exoplayer2.transformer.DefaultEncoderFactory;
import com.google.android.exoplayer2.transformer.ProgressHolder;
import com.google.android.exoplayer2.transformer.TransformationException;
import com.google.android.exoplayer2.transformer.TransformationRequest;
import com.google.android.exoplayer2.transformer.TransformationResult;
import com.google.android.exoplayer2.transformer.Transformer;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.util.DebugTextViewHelper;
import com.google.android.exoplayer2.util.DebugViewProvider;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** An {@link Activity} that transforms and plays media using {@link Transformer}. */
public final class TransformerActivity extends AppCompatActivity {
  private static final String TAG = "TransformerActivity";

  private @MonotonicNonNull Button displayInputButton;
  private @MonotonicNonNull MaterialCardView inputCardView;
  private @MonotonicNonNull StyledPlayerView inputPlayerView;
  private @MonotonicNonNull StyledPlayerView outputPlayerView;
  private @MonotonicNonNull TextView debugTextView;
  private @MonotonicNonNull TextView informationTextView;
  private @MonotonicNonNull ViewGroup progressViewGroup;
  private @MonotonicNonNull LinearProgressIndicator progressIndicator;
  private @MonotonicNonNull Stopwatch transformationStopwatch;
  private @MonotonicNonNull AspectRatioFrameLayout debugFrame;

  @Nullable private DebugTextViewHelper debugTextViewHelper;
  @Nullable private ExoPlayer inputPlayer;
  @Nullable private ExoPlayer outputPlayer;
  @Nullable private Transformer transformer;
  @Nullable private File externalCacheFile;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.transformer_activity);

    inputCardView = findViewById(R.id.input_card_view);
    inputPlayerView = findViewById(R.id.input_player_view);
    outputPlayerView = findViewById(R.id.output_player_view);
    debugTextView = findViewById(R.id.debug_text_view);
    informationTextView = findViewById(R.id.information_text_view);
    progressViewGroup = findViewById(R.id.progress_view_group);
    progressIndicator = findViewById(R.id.progress_indicator);
    debugFrame = findViewById(R.id.debug_aspect_ratio_frame_layout);
    displayInputButton = findViewById(R.id.display_input_button);
    displayInputButton.setOnClickListener(this::toggleInputVideoDisplay);

    transformationStopwatch =
        Stopwatch.createUnstarted(
            new Ticker() {
              @Override
              public long read() {
                return android.os.SystemClock.elapsedRealtimeNanos();
              }
            });
  }

  @Override
  protected void onStart() {
    super.onStart();

    checkNotNull(progressIndicator);
    checkNotNull(informationTextView);
    checkNotNull(transformationStopwatch);
    checkNotNull(inputCardView);
    checkNotNull(inputPlayerView);
    checkNotNull(outputPlayerView);
    checkNotNull(debugTextView);
    checkNotNull(progressViewGroup);
    checkNotNull(debugFrame);
    checkNotNull(displayInputButton);
    startTransformation();

    inputPlayerView.onResume();
    outputPlayerView.onResume();
  }

  @Override
  protected void onStop() {
    super.onStop();

    checkNotNull(transformer).cancel();
    transformer = null;

    // The stop watch is reset after cancelling the transformation, in case cancelling causes the
    // stop watch to be stopped in a transformer callback.
    checkNotNull(transformationStopwatch).reset();

    checkNotNull(inputPlayerView).onPause();
    checkNotNull(outputPlayerView).onPause();
    releasePlayer();

    checkNotNull(externalCacheFile).delete();
    externalCacheFile = null;
  }

  @RequiresNonNull({
    "inputCardView",
    "inputPlayerView",
    "outputPlayerView",
    "displayInputButton",
    "debugTextView",
    "informationTextView",
    "progressIndicator",
    "transformationStopwatch",
    "progressViewGroup",
    "debugFrame",
  })
  private void startTransformation() {
    requestTransformerPermission();

    Intent intent = getIntent();
    Uri uri = checkNotNull(intent.getData());
    try {
      externalCacheFile = createExternalCacheFile("transformer-output.mp4");
      String filePath = externalCacheFile.getAbsolutePath();
      @Nullable Bundle bundle = intent.getExtras();
      MediaItem mediaItem = createMediaItem(bundle, uri);
      Transformer transformer = createTransformer(bundle, filePath);
      transformationStopwatch.start();
      transformer.startTransformation(mediaItem, filePath);
      this.transformer = transformer;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    informationTextView.setText(R.string.transformation_started);
    inputCardView.setVisibility(View.GONE);
    outputPlayerView.setVisibility(View.GONE);
    Handler mainHandler = new Handler(getMainLooper());
    ProgressHolder progressHolder = new ProgressHolder();
    mainHandler.post(
        new Runnable() {
          @Override
          public void run() {
            if (transformer != null
                && transformer.getProgress(progressHolder)
                    != Transformer.PROGRESS_STATE_NO_TRANSFORMATION) {
              progressIndicator.setProgress(progressHolder.progress);
              informationTextView.setText(
                  getString(
                      R.string.transformation_timer,
                      transformationStopwatch.elapsed(TimeUnit.SECONDS)));
              mainHandler.postDelayed(/* r= */ this, /* delayMillis= */ 500);
            }
          }
        });
  }

  private MediaItem createMediaItem(@Nullable Bundle bundle, Uri uri) {
    MediaItem.Builder mediaItemBuilder = new MediaItem.Builder().setUri(uri);
    if (bundle != null) {
      long trimStartMs =
          bundle.getLong(ConfigurationActivity.TRIM_START_MS, /* defaultValue= */ C.TIME_UNSET);
      long trimEndMs =
          bundle.getLong(ConfigurationActivity.TRIM_END_MS, /* defaultValue= */ C.TIME_UNSET);
      if (trimStartMs != C.TIME_UNSET && trimEndMs != C.TIME_UNSET) {
        mediaItemBuilder.setClippingConfiguration(
            new MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(trimStartMs)
                .setEndPositionMs(trimEndMs)
                .build());
      }
    }
    return mediaItemBuilder.build();
  }

  @RequiresNonNull({
    "inputCardView",
    "inputPlayerView",
    "outputPlayerView",
    "displayInputButton",
    "debugTextView",
    "informationTextView",
    "transformationStopwatch",
    "progressViewGroup",
    "debugFrame",
  })
  private Transformer createTransformer(@Nullable Bundle bundle, String filePath) {
    Transformer.Builder transformerBuilder = new Transformer.Builder(/* context= */ this);
    if (bundle != null) {
      TransformationRequest.Builder requestBuilder = new TransformationRequest.Builder();
      requestBuilder.setFlattenForSlowMotion(
          bundle.getBoolean(ConfigurationActivity.SHOULD_FLATTEN_FOR_SLOW_MOTION));
      @Nullable String audioMimeType = bundle.getString(ConfigurationActivity.AUDIO_MIME_TYPE);
      if (audioMimeType != null) {
        requestBuilder.setAudioMimeType(audioMimeType);
      }
      @Nullable String videoMimeType = bundle.getString(ConfigurationActivity.VIDEO_MIME_TYPE);
      if (videoMimeType != null) {
        requestBuilder.setVideoMimeType(videoMimeType);
      }
      int resolutionHeight =
          bundle.getInt(
              ConfigurationActivity.RESOLUTION_HEIGHT, /* defaultValue= */ C.LENGTH_UNSET);
      if (resolutionHeight != C.LENGTH_UNSET) {
        requestBuilder.setResolution(resolutionHeight);
      }

      float scaleX = bundle.getFloat(ConfigurationActivity.SCALE_X, /* defaultValue= */ 1);
      float scaleY = bundle.getFloat(ConfigurationActivity.SCALE_Y, /* defaultValue= */ 1);
      requestBuilder.setScale(scaleX, scaleY);

      float rotateDegrees =
          bundle.getFloat(ConfigurationActivity.ROTATE_DEGREES, /* defaultValue= */ 0);
      requestBuilder.setRotationDegrees(rotateDegrees);

      requestBuilder.setEnableRequestSdrToneMapping(
          bundle.getBoolean(ConfigurationActivity.ENABLE_REQUEST_SDR_TONE_MAPPING));
      requestBuilder.experimental_setForceInterpretHdrVideoAsSdr(
          bundle.getBoolean(ConfigurationActivity.FORCE_INTERPRET_HDR_VIDEO_AS_SDR));
      requestBuilder.experimental_setEnableHdrEditing(
          bundle.getBoolean(ConfigurationActivity.ENABLE_HDR_EDITING));
      transformerBuilder
          .setTransformationRequest(requestBuilder.build())
          .setRemoveAudio(bundle.getBoolean(ConfigurationActivity.SHOULD_REMOVE_AUDIO))
          .setRemoveVideo(bundle.getBoolean(ConfigurationActivity.SHOULD_REMOVE_VIDEO))
          .setEncoderFactory(
              new DefaultEncoderFactory.Builder(this.getApplicationContext())
                  .setEnableFallback(bundle.getBoolean(ConfigurationActivity.ENABLE_FALLBACK))
                  .build());

      transformerBuilder.setVideoEffects(createVideoEffectsListFromBundle(bundle));

      if (bundle.getBoolean(ConfigurationActivity.ENABLE_DEBUG_PREVIEW)) {
        transformerBuilder.setDebugViewProvider(new DemoDebugViewProvider());
      }
    }
    return transformerBuilder
        .addListener(
            new Transformer.Listener() {
              @Override
              public void onTransformationCompleted(
                  MediaItem mediaItem, TransformationResult transformationResult) {
                TransformerActivity.this.onTransformationCompleted(filePath, mediaItem);
              }

              @Override
              public void onTransformationError(
                  MediaItem mediaItem, TransformationException exception) {
                TransformerActivity.this.onTransformationError(exception);
              }
            })
        .build();
  }

  /** Creates a cache file, resetting it if it already exists. */
  private File createExternalCacheFile(String fileName) throws IOException {
    File file = new File(getExternalCacheDir(), fileName);
    if (file.exists() && !file.delete()) {
      throw new IllegalStateException("Could not delete the previous transformer output file");
    }
    if (!file.createNewFile()) {
      throw new IllegalStateException("Could not create the transformer output file");
    }
    return file;
  }

  private ImmutableList<Effect> createVideoEffectsListFromBundle(Bundle bundle) {
    @Nullable
    boolean[] selectedEffects =
        bundle.getBooleanArray(ConfigurationActivity.DEMO_EFFECTS_SELECTIONS);
    if (selectedEffects == null) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<Effect> effects = new ImmutableList.Builder<>();
    if (selectedEffects[0]) {
      effects.add(MatrixTransformationFactory.createDizzyCropEffect());
    }
    if (selectedEffects[1]) {
      try {
        Class<?> clazz =
            Class.forName("com.google.android.exoplayer2.transformerdemo.MediaPipeProcessor");
        Constructor<?> constructor =
            clazz.getConstructor(
                Context.class,
                boolean.class,
                String.class,
                boolean.class,
                String.class,
                String.class);
        effects.add(
            (GlEffect)
                (Context context, boolean useHdr) -> {
                  try {
                    return (GlTextureProcessor)
                        constructor.newInstance(
                            context,
                            useHdr,
                            /* graphName= */ "edge_detector_mediapipe_graph.binarypb",
                            /* isSingleFrameGraph= */ true,
                            /* inputStreamName= */ "input_video",
                            /* outputStreamName= */ "output_video");
                  } catch (Exception e) {
                    runOnUiThread(() -> showToast(R.string.no_media_pipe_error));
                    throw new RuntimeException("Failed to load MediaPipe processor", e);
                  }
                });
      } catch (Exception e) {
        showToast(R.string.no_media_pipe_error);
      }
    }
    if (selectedEffects[2]) {
      switch (bundle.getInt(ConfigurationActivity.COLOR_FILTER_SELECTION)) {
        case ConfigurationActivity.COLOR_FILTER_GRAYSCALE:
          effects.add(RgbFilter.createGrayscaleFilter());
          break;
        case ConfigurationActivity.COLOR_FILTER_INVERTED:
          effects.add(RgbFilter.createInvertedFilter());
          break;
        case ConfigurationActivity.COLOR_FILTER_SEPIA:
          // W3C Sepia RGBA matrix with sRGB as a target color space:
          // https://www.w3.org/TR/filter-effects-1/#sepiaEquivalent
          // The matrix is defined for the sRGB color space and the Transformer library
          // uses a linear RGB color space internally. Meaning this is only for demonstration
          // purposes and it does not display a correct sepia frame.
          float[] sepiaMatrix = {
            0.393f, 0.349f, 0.272f, 0, 0.769f, 0.686f, 0.534f, 0, 0.189f, 0.168f, 0.131f, 0, 0, 0,
            0, 1
          };
          effects.add((RgbMatrix) (presentationTimeUs, useHdr) -> sepiaMatrix);
          break;
        default:
          throw new IllegalStateException(
              "Unexpected color filter "
                  + bundle.getInt(ConfigurationActivity.COLOR_FILTER_SELECTION));
      }
    }
    if (selectedEffects[3]) {
      int length = 3;
      int[][][] mapWhiteToGreenLut = new int[length][length][length];
      int scale = 255 / (length - 1);
      for (int r = 0; r < length; r++) {
        for (int g = 0; g < length; g++) {
          for (int b = 0; b < length; b++) {
            mapWhiteToGreenLut[r][g][b] =
                Color.rgb(/* red= */ r * scale, /* green= */ g * scale, /* blue= */ b * scale);
          }
        }
      }
      mapWhiteToGreenLut[length - 1][length - 1][length - 1] = Color.GREEN;
      effects.add(SingleColorLut.createFromCube(mapWhiteToGreenLut));
    }
    if (selectedEffects[4]) {
      effects.add(
          new RgbAdjustment.Builder()
              .setRedScale(bundle.getFloat(ConfigurationActivity.RGB_ADJUSTMENT_RED_SCALE))
              .setGreenScale(bundle.getFloat(ConfigurationActivity.RGB_ADJUSTMENT_GREEN_SCALE))
              .setBlueScale(bundle.getFloat(ConfigurationActivity.RGB_ADJUSTMENT_BLUE_SCALE))
              .build());
    }
    if (selectedEffects[5]) {
      effects.add(
          new HslAdjustment.Builder()
              .adjustHue(bundle.getFloat(ConfigurationActivity.HSL_ADJUSTMENTS_HUE))
              .adjustSaturation(bundle.getFloat(ConfigurationActivity.HSL_ADJUSTMENTS_SATURATION))
              .adjustLightness(bundle.getFloat(ConfigurationActivity.HSL_ADJUSTMENTS_LIGHTNESS))
              .build());
    }
    if (selectedEffects[6]) {
      effects.add(new Contrast(bundle.getFloat(ConfigurationActivity.CONTRAST_VALUE)));
    }
    if (selectedEffects[7]) {
      effects.add(
          (GlEffect)
              (Context context, boolean useHdr) ->
                  new PeriodicVignetteProcessor(
                      context,
                      useHdr,
                      bundle.getFloat(ConfigurationActivity.PERIODIC_VIGNETTE_CENTER_X),
                      bundle.getFloat(ConfigurationActivity.PERIODIC_VIGNETTE_CENTER_Y),
                      /* minInnerRadius= */ bundle.getFloat(
                          ConfigurationActivity.PERIODIC_VIGNETTE_INNER_RADIUS),
                      /* maxInnerRadius= */ bundle.getFloat(
                          ConfigurationActivity.PERIODIC_VIGNETTE_OUTER_RADIUS),
                      bundle.getFloat(ConfigurationActivity.PERIODIC_VIGNETTE_OUTER_RADIUS)));
    }
    if (selectedEffects[8]) {
      effects.add(MatrixTransformationFactory.createSpin3dEffect());
    }
    if (selectedEffects[9]) {
      effects.add((GlEffect) BitmapOverlayProcessor::new);
    }
    if (selectedEffects[10]) {
      effects.add(MatrixTransformationFactory.createZoomInTransition());
    }
    return effects.build();
  }

  @RequiresNonNull({
    "informationTextView",
    "progressViewGroup",
    "debugFrame",
    "transformationStopwatch",
  })
  private void onTransformationError(TransformationException exception) {
    transformationStopwatch.stop();
    informationTextView.setText(R.string.transformation_error);
    progressViewGroup.setVisibility(View.GONE);
    debugFrame.removeAllViews();
    Toast.makeText(getApplicationContext(), "Transformation error: " + exception, Toast.LENGTH_LONG)
        .show();
    Log.e(TAG, "Transformation error", exception);
  }

  @RequiresNonNull({
    "inputCardView",
    "inputPlayerView",
    "outputPlayerView",
    "displayInputButton",
    "debugTextView",
    "informationTextView",
    "progressViewGroup",
    "debugFrame",
    "transformationStopwatch",
  })
  private void onTransformationCompleted(String filePath, MediaItem inputMediaItem) {
    transformationStopwatch.stop();
    informationTextView.setText(
        getString(
            R.string.transformation_completed, transformationStopwatch.elapsed(TimeUnit.SECONDS)));
    progressViewGroup.setVisibility(View.GONE);
    debugFrame.removeAllViews();
    inputCardView.setVisibility(View.VISIBLE);
    outputPlayerView.setVisibility(View.VISIBLE);
    displayInputButton.setVisibility(View.VISIBLE);
    playMediaItems(inputMediaItem, MediaItem.fromUri("file://" + filePath));
    Log.d(TAG, "Output file path: file://" + filePath);
  }

  @RequiresNonNull({
    "inputCardView",
    "inputPlayerView",
    "outputPlayerView",
    "debugTextView",
  })
  private void playMediaItems(MediaItem inputMediaItem, MediaItem outputMediaItem) {
    inputPlayerView.setPlayer(null);
    outputPlayerView.setPlayer(null);
    releasePlayer();

    ExoPlayer inputPlayer = new ExoPlayer.Builder(/* context= */ this).build();
    inputPlayerView.setPlayer(inputPlayer);
    inputPlayerView.setControllerAutoShow(false);
    inputPlayer.setMediaItem(inputMediaItem);
    inputPlayer.prepare();
    this.inputPlayer = inputPlayer;
    inputPlayer.setVolume(0f);

    ExoPlayer outputPlayer = new ExoPlayer.Builder(/* context= */ this).build();
    outputPlayerView.setPlayer(outputPlayer);
    outputPlayerView.setControllerAutoShow(false);
    outputPlayer.setMediaItem(outputMediaItem);
    outputPlayer.prepare();
    this.outputPlayer = outputPlayer;

    inputPlayer.play();
    outputPlayer.play();

    debugTextViewHelper = new DebugTextViewHelper(outputPlayer, debugTextView);
    debugTextViewHelper.start();
  }

  private void releasePlayer() {
    if (debugTextViewHelper != null) {
      debugTextViewHelper.stop();
      debugTextViewHelper = null;
    }
    if (inputPlayer != null) {
      inputPlayer.release();
      inputPlayer = null;
    }
    if (outputPlayer != null) {
      outputPlayer.release();
      outputPlayer = null;
    }
  }

  private void requestTransformerPermission() {
    if (Util.SDK_INT < 23) {
      return;
    }
    if (checkSelfPermission(READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {READ_EXTERNAL_STORAGE}, /* requestCode= */ 0);
    }
  }

  private void showToast(@StringRes int messageResource) {
    Toast.makeText(getApplicationContext(), getString(messageResource), Toast.LENGTH_LONG).show();
  }

  @RequiresNonNull({
    "inputCardView",
    "displayInputButton",
  })
  private void toggleInputVideoDisplay(View view) {
    if (inputCardView.getVisibility() == View.GONE) {
      inputCardView.setVisibility(View.VISIBLE);
      displayInputButton.setText(getString(R.string.hide_input_video));
    } else if (inputCardView.getVisibility() == View.VISIBLE) {
      checkNotNull(inputPlayer).pause();
      inputCardView.setVisibility(View.GONE);
      displayInputButton.setText(getString(R.string.show_input_video));
    }
  }

  private final class DemoDebugViewProvider implements DebugViewProvider {

    private @MonotonicNonNull SurfaceView surfaceView;
    private int width;
    private int height;

    public DemoDebugViewProvider() {
      width = C.LENGTH_UNSET;
      height = C.LENGTH_UNSET;
    }

    @Nullable
    @Override
    public SurfaceView getDebugPreviewSurfaceView(int width, int height) {
      checkState(
          surfaceView == null || (this.width == width && this.height == height),
          "Transformer should not change the output size mid-transformation.");
      if (surfaceView != null) {
        return surfaceView;
      }

      this.width = width;
      this.height = height;

      // Update the UI on the main thread and wait for the output surface to be available.
      CountDownLatch surfaceCreatedCountDownLatch = new CountDownLatch(1);
      SurfaceView surfaceView = new SurfaceView(/* context= */ TransformerActivity.this);
      runOnUiThread(
          () -> {
            AspectRatioFrameLayout debugFrame = checkNotNull(TransformerActivity.this.debugFrame);
            debugFrame.addView(surfaceView);
            debugFrame.setAspectRatio((float) width / height);
            surfaceView
                .getHolder()
                .addCallback(
                    new SurfaceHolder.Callback() {
                      @Override
                      public void surfaceCreated(SurfaceHolder surfaceHolder) {
                        surfaceCreatedCountDownLatch.countDown();
                      }

                      @Override
                      public void surfaceChanged(
                          SurfaceHolder surfaceHolder, int format, int width, int height) {
                        // Do nothing.
                      }

                      @Override
                      public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                        // Do nothing.
                      }
                    });
          });
      try {
        surfaceCreatedCountDownLatch.await();
      } catch (InterruptedException e) {
        Log.w(TAG, "Interrupted waiting for debug surface.");
        Thread.currentThread().interrupt();
        return null;
      }
      this.surfaceView = surfaceView;
      return surfaceView;
    }
  }
}
