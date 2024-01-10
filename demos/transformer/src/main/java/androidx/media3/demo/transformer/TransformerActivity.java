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
package androidx.media3.demo.transformer;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.READ_MEDIA_VIDEO;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.SDK_INT;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_NOT_STARTED;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.media3.common.C;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.ChannelMixingAudioProcessor;
import androidx.media3.common.audio.ChannelMixingMatrix;
import androidx.media3.common.audio.SonicAudioProcessor;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSourceBitmapLoader;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.Contrast;
import androidx.media3.effect.DebugTraceUtil;
import androidx.media3.effect.DrawableOverlay;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.GlShaderProgram;
import androidx.media3.effect.HslAdjustment;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.OverlaySettings;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.RgbAdjustment;
import androidx.media3.effect.RgbFilter;
import androidx.media3.effect.RgbMatrix;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.effect.SingleColorLut;
import androidx.media3.effect.TextOverlay;
import androidx.media3.effect.TextureOverlay;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor;
import androidx.media3.exoplayer.util.DebugTextViewHelper;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.DefaultMuxer;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.InAppMuxer;
import androidx.media3.transformer.JsonUtil;
import androidx.media3.transformer.Muxer;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.json.JSONException;
import org.json.JSONObject;

/** An {@link Activity} that exports and plays media using {@link Transformer}. */
public final class TransformerActivity extends AppCompatActivity {
  private static final String TAG = "TransformerActivity";

  private @MonotonicNonNull Button displayInputButton;
  private @MonotonicNonNull MaterialCardView inputCardView;
  private @MonotonicNonNull TextView inputTextView;
  private @MonotonicNonNull ImageView inputImageView;
  private @MonotonicNonNull PlayerView inputPlayerView;
  private @MonotonicNonNull PlayerView outputPlayerView;
  private @MonotonicNonNull TextView outputVideoTextView;
  private @MonotonicNonNull TextView debugTextView;
  private @MonotonicNonNull TextView informationTextView;
  private @MonotonicNonNull ViewGroup progressViewGroup;
  private @MonotonicNonNull LinearProgressIndicator progressIndicator;
  private @MonotonicNonNull Button cancelButton;
  private @MonotonicNonNull Button resumeButton;
  private @MonotonicNonNull Stopwatch exportStopwatch;
  private @MonotonicNonNull AspectRatioFrameLayout debugFrame;

  @Nullable private DebugTextViewHelper debugTextViewHelper;
  @Nullable private ExoPlayer inputPlayer;
  @Nullable private ExoPlayer outputPlayer;
  @Nullable private Transformer transformer;
  @Nullable private File outputFile;
  @Nullable private File oldOutputFile;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.transformer_activity);

    inputCardView = findViewById(R.id.input_card_view);
    inputTextView = findViewById(R.id.input_text_view);
    inputImageView = findViewById(R.id.input_image_view);
    inputPlayerView = findViewById(R.id.input_player_view);
    outputPlayerView = findViewById(R.id.output_player_view);
    outputVideoTextView = findViewById(R.id.output_video_text_view);
    debugTextView = findViewById(R.id.debug_text_view);
    informationTextView = findViewById(R.id.information_text_view);
    progressViewGroup = findViewById(R.id.progress_view_group);
    progressIndicator = findViewById(R.id.progress_indicator);
    cancelButton = findViewById(R.id.cancel_button);
    cancelButton.setOnClickListener(this::cancelExport);
    resumeButton = findViewById(R.id.resume_button);
    resumeButton.setOnClickListener(view -> startExport());
    debugFrame = findViewById(R.id.debug_aspect_ratio_frame_layout);
    displayInputButton = findViewById(R.id.display_input_button);
    displayInputButton.setOnClickListener(this::toggleInputVideoDisplay);

    exportStopwatch =
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

    startExport();

    checkNotNull(inputPlayerView).onResume();
    checkNotNull(outputPlayerView).onResume();
  }

  @Override
  protected void onStop() {
    super.onStop();

    if (transformer != null) {
      transformer.cancel();
      transformer = null;
    }

    // The stop watch is reset after cancelling the export, in case cancelling causes the stop watch
    // to be stopped in a transformer callback.
    checkNotNull(exportStopwatch).reset();

    checkNotNull(inputPlayerView).onPause();
    checkNotNull(outputPlayerView).onPause();
    releasePlayer();

    checkNotNull(outputFile).delete();
    outputFile = null;
    if (oldOutputFile != null) {
      oldOutputFile.delete();
      oldOutputFile = null;
    }
  }

  private void startExport() {
    checkNotNull(progressIndicator);
    checkNotNull(informationTextView);
    checkNotNull(exportStopwatch);
    checkNotNull(inputCardView);
    checkNotNull(inputTextView);
    checkNotNull(inputImageView);
    checkNotNull(inputPlayerView);
    checkNotNull(outputPlayerView);
    checkNotNull(outputVideoTextView);
    checkNotNull(debugTextView);
    checkNotNull(progressViewGroup);
    checkNotNull(debugFrame);
    checkNotNull(displayInputButton);
    checkNotNull(cancelButton);
    checkNotNull(resumeButton);

    requestReadVideoPermission(/* activity= */ this);

    Intent intent = getIntent();
    Uri inputUri = checkNotNull(intent.getData());
    try {
      outputFile =
          createExternalCacheFile("transformer-output-" + Clock.DEFAULT.elapsedRealtime() + ".mp4");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    String outputFilePath = outputFile.getAbsolutePath();
    @Nullable Bundle bundle = intent.getExtras();
    MediaItem mediaItem = createMediaItem(bundle, inputUri);
    Transformer transformer = createTransformer(bundle, inputUri, outputFilePath);
    Composition composition = createComposition(mediaItem, bundle);
    exportStopwatch.reset();
    exportStopwatch.start();
    if (oldOutputFile == null) {
      transformer.start(composition, outputFilePath);
    } else {
      transformer.resume(composition, outputFilePath, oldOutputFile.getAbsolutePath());
    }
    this.transformer = transformer;
    displayInputButton.setVisibility(View.GONE);
    inputCardView.setVisibility(View.GONE);
    outputPlayerView.setVisibility(View.GONE);
    outputVideoTextView.setVisibility(View.GONE);
    debugTextView.setVisibility(View.GONE);
    informationTextView.setText(R.string.export_started);
    progressViewGroup.setVisibility(View.VISIBLE);
    cancelButton.setVisibility(View.VISIBLE);
    resumeButton.setVisibility(View.GONE);
    progressIndicator.setProgress(0);
    Handler mainHandler = new Handler(getMainLooper());
    ProgressHolder progressHolder = new ProgressHolder();
    mainHandler.post(
        new Runnable() {
          @Override
          public void run() {
            if (transformer != null
                && transformer.getProgress(progressHolder) != PROGRESS_STATE_NOT_STARTED) {
              progressIndicator.setProgress(progressHolder.progress);
              informationTextView.setText(
                  getString(R.string.export_timer, exportStopwatch.elapsed(TimeUnit.SECONDS)));
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
    "inputTextView",
    "inputImageView",
    "inputPlayerView",
    "outputPlayerView",
    "outputVideoTextView",
    "displayInputButton",
    "debugTextView",
    "informationTextView",
    "exportStopwatch",
    "progressViewGroup",
    "debugFrame",
  })
  private Transformer createTransformer(@Nullable Bundle bundle, Uri inputUri, String filePath) {
    Transformer.Builder transformerBuilder = new Transformer.Builder(/* context= */ this);
    if (bundle != null) {
      @Nullable String audioMimeType = bundle.getString(ConfigurationActivity.AUDIO_MIME_TYPE);
      if (audioMimeType != null) {
        transformerBuilder.setAudioMimeType(audioMimeType);
      }
      @Nullable String videoMimeType = bundle.getString(ConfigurationActivity.VIDEO_MIME_TYPE);
      if (videoMimeType != null) {
        transformerBuilder.setVideoMimeType(videoMimeType);
      }

      transformerBuilder.setEncoderFactory(
          new DefaultEncoderFactory.Builder(this.getApplicationContext())
              .setEnableFallback(bundle.getBoolean(ConfigurationActivity.ENABLE_FALLBACK))
              .build());

      long maxDelayBetweenSamplesMs = DefaultMuxer.Factory.DEFAULT_MAX_DELAY_BETWEEN_SAMPLES_MS;
      if (!bundle.getBoolean(ConfigurationActivity.ABORT_SLOW_EXPORT)) {
        maxDelayBetweenSamplesMs = C.TIME_UNSET;
      }

      Muxer.Factory muxerFactory = new DefaultMuxer.Factory(maxDelayBetweenSamplesMs);
      if (bundle.getBoolean(ConfigurationActivity.PRODUCE_FRAGMENTED_MP4)) {
        muxerFactory =
            new InAppMuxer.Factory.Builder()
                .setMaxDelayBetweenSamplesMs(maxDelayBetweenSamplesMs)
                .setFragmentedMp4Enabled(true)
                .build();
      }
      transformerBuilder.setMuxerFactory(muxerFactory);

      if (bundle.getBoolean(ConfigurationActivity.ENABLE_DEBUG_PREVIEW)) {
        transformerBuilder.setDebugViewProvider(new DemoDebugViewProvider());
      }
    }

    return transformerBuilder
        .addListener(
            new Transformer.Listener() {
              @Override
              public void onCompleted(Composition composition, ExportResult exportResult) {
                TransformerActivity.this.onCompleted(inputUri, filePath, exportResult);
              }

              @Override
              public void onError(
                  Composition composition,
                  ExportResult exportResult,
                  ExportException exportException) {
                TransformerActivity.this.onError(exportException);
              }
            })
        .build();
  }

  /** Creates a cache file, resetting it if it already exists. */
  private File createExternalCacheFile(String fileName) throws IOException {
    File file = new File(getExternalCacheDir(), fileName);
    if (file.exists() && !file.delete()) {
      throw new IllegalStateException("Could not delete the previous export output file");
    }
    if (!file.createNewFile()) {
      throw new IllegalStateException("Could not create the export output file");
    }
    return file;
  }

  @RequiresNonNull({
    "inputCardView",
    "outputPlayerView",
    "exportStopwatch",
    "progressViewGroup",
  })
  private Composition createComposition(MediaItem mediaItem, @Nullable Bundle bundle) {
    EditedMediaItem.Builder editedMediaItemBuilder = new EditedMediaItem.Builder(mediaItem);
    // For image inputs. Automatically ignored if input is audio/video.
    editedMediaItemBuilder.setDurationUs(5_000_000).setFrameRate(30);
    if (bundle != null) {
      ImmutableList<AudioProcessor> audioProcessors = createAudioProcessorsFromBundle(bundle);
      ImmutableList<Effect> videoEffects = createVideoEffectsFromBundle(bundle);
      editedMediaItemBuilder
          .setRemoveAudio(bundle.getBoolean(ConfigurationActivity.SHOULD_REMOVE_AUDIO))
          .setRemoveVideo(bundle.getBoolean(ConfigurationActivity.SHOULD_REMOVE_VIDEO))
          .setFlattenForSlowMotion(
              bundle.getBoolean(ConfigurationActivity.SHOULD_FLATTEN_FOR_SLOW_MOTION))
          .setEffects(new Effects(audioProcessors, videoEffects));
    }
    Composition.Builder compositionBuilder =
        new Composition.Builder(new EditedMediaItemSequence(editedMediaItemBuilder.build()));
    if (bundle != null) {
      compositionBuilder
          .setHdrMode(bundle.getInt(ConfigurationActivity.HDR_MODE))
          .experimentalSetForceAudioTrack(
              bundle.getBoolean(ConfigurationActivity.FORCE_AUDIO_TRACK));
    }
    return compositionBuilder.build();
  }

  private ImmutableList<AudioProcessor> createAudioProcessorsFromBundle(Bundle bundle) {
    @Nullable
    boolean[] selectedAudioEffects =
        bundle.getBooleanArray(ConfigurationActivity.AUDIO_EFFECTS_SELECTIONS);

    if (selectedAudioEffects == null) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<AudioProcessor> processors = new ImmutableList.Builder<>();

    if (selectedAudioEffects[ConfigurationActivity.HIGH_PITCHED_INDEX]
        || selectedAudioEffects[ConfigurationActivity.SAMPLE_RATE_INDEX]) {
      SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
      if (selectedAudioEffects[ConfigurationActivity.HIGH_PITCHED_INDEX]) {
        sonicAudioProcessor.setPitch(2f);
      }
      if (selectedAudioEffects[ConfigurationActivity.SAMPLE_RATE_INDEX]) {
        sonicAudioProcessor.setOutputSampleRateHz(48_000);
      }
      processors.add(sonicAudioProcessor);
    }

    if (selectedAudioEffects[ConfigurationActivity.SKIP_SILENCE_INDEX]) {
      SilenceSkippingAudioProcessor silenceSkippingAudioProcessor =
          new SilenceSkippingAudioProcessor();
      silenceSkippingAudioProcessor.setEnabled(true);
      processors.add(silenceSkippingAudioProcessor);
    }

    boolean mixToMono = selectedAudioEffects[ConfigurationActivity.CHANNEL_MIXING_INDEX];
    boolean scaleVolumeToHalf = selectedAudioEffects[ConfigurationActivity.VOLUME_SCALING_INDEX];
    if (mixToMono || scaleVolumeToHalf) {
      ChannelMixingAudioProcessor mixingAudioProcessor = new ChannelMixingAudioProcessor();
      for (int inputChannelCount = 1; inputChannelCount <= 6; inputChannelCount++) {
        ChannelMixingMatrix matrix;
        if (mixToMono) {
          float[] mixingCoefficients = new float[inputChannelCount];
          // Each channel is equally weighted in the mix to mono.
          Arrays.fill(mixingCoefficients, 1f / inputChannelCount);
          matrix =
              new ChannelMixingMatrix(
                  inputChannelCount, /* outputChannelCount= */ 1, mixingCoefficients);
        } else {
          // Identity matrix.
          matrix =
              ChannelMixingMatrix.create(
                  inputChannelCount, /* outputChannelCount= */ inputChannelCount);
        }

        // Apply the volume adjustment.
        mixingAudioProcessor.putChannelMixingMatrix(
            scaleVolumeToHalf ? matrix.scaleBy(0.5f) : matrix);
      }
      processors.add(mixingAudioProcessor);
    }

    return processors.build();
  }

  private ImmutableList<Effect> createVideoEffectsFromBundle(Bundle bundle) {
    boolean[] selectedEffects =
        bundle.getBooleanArray(ConfigurationActivity.VIDEO_EFFECTS_SELECTIONS);

    if (selectedEffects == null) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<Effect> effects = new ImmutableList.Builder<>();
    if (selectedEffects[ConfigurationActivity.DIZZY_CROP_INDEX]) {
      effects.add(MatrixTransformationFactory.createDizzyCropEffect());
    }
    if (selectedEffects[ConfigurationActivity.EDGE_DETECTOR_INDEX]) {
      try {
        Class<?> clazz = Class.forName("androidx.media3.demo.transformer.MediaPipeShaderProgram");
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
                    return (GlShaderProgram)
                        constructor.newInstance(
                            context,
                            useHdr,
                            /* graphName= */ "edge_detector_mediapipe_graph.binarypb",
                            /* isSingleFrameGraph= */ true,
                            /* inputStreamName= */ "input_video",
                            /* outputStreamName= */ "output_video");
                  } catch (Exception e) {
                    runOnUiThread(() -> showToast(R.string.no_media_pipe_error));
                    throw new RuntimeException("Failed to load MediaPipeShaderProgram", e);
                  }
                });
      } catch (Exception e) {
        showToast(R.string.no_media_pipe_error);
      }
    }
    if (selectedEffects[ConfigurationActivity.COLOR_FILTERS_INDEX]) {
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
    if (selectedEffects[ConfigurationActivity.MAP_WHITE_TO_GREEN_LUT_INDEX]) {
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
    if (selectedEffects[ConfigurationActivity.RGB_ADJUSTMENTS_INDEX]) {
      effects.add(
          new RgbAdjustment.Builder()
              .setRedScale(bundle.getFloat(ConfigurationActivity.RGB_ADJUSTMENT_RED_SCALE))
              .setGreenScale(bundle.getFloat(ConfigurationActivity.RGB_ADJUSTMENT_GREEN_SCALE))
              .setBlueScale(bundle.getFloat(ConfigurationActivity.RGB_ADJUSTMENT_BLUE_SCALE))
              .build());
    }
    if (selectedEffects[ConfigurationActivity.HSL_ADJUSTMENT_INDEX]) {
      effects.add(
          new HslAdjustment.Builder()
              .adjustHue(bundle.getFloat(ConfigurationActivity.HSL_ADJUSTMENTS_HUE))
              .adjustSaturation(bundle.getFloat(ConfigurationActivity.HSL_ADJUSTMENTS_SATURATION))
              .adjustLightness(bundle.getFloat(ConfigurationActivity.HSL_ADJUSTMENTS_LIGHTNESS))
              .build());
    }
    if (selectedEffects[ConfigurationActivity.CONTRAST_INDEX]) {
      effects.add(new Contrast(bundle.getFloat(ConfigurationActivity.CONTRAST_VALUE)));
    }
    if (selectedEffects[ConfigurationActivity.PERIODIC_VIGNETTE_INDEX]) {
      effects.add(
          (GlEffect)
              (Context context, boolean useHdr) ->
                  new PeriodicVignetteShaderProgram(
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
    if (selectedEffects[ConfigurationActivity.SPIN_3D_INDEX]) {
      effects.add(MatrixTransformationFactory.createSpin3dEffect());
    }
    if (selectedEffects[ConfigurationActivity.ZOOM_IN_INDEX]) {
      effects.add(MatrixTransformationFactory.createZoomInTransition());
    }

    @Nullable OverlayEffect overlayEffect = createOverlayEffectFromBundle(bundle, selectedEffects);
    if (overlayEffect != null) {
      effects.add(overlayEffect);
    }

    float scaleX = bundle.getFloat(ConfigurationActivity.SCALE_X, /* defaultValue= */ 1);
    float scaleY = bundle.getFloat(ConfigurationActivity.SCALE_Y, /* defaultValue= */ 1);
    float rotateDegrees =
        bundle.getFloat(ConfigurationActivity.ROTATE_DEGREES, /* defaultValue= */ 0);
    if (scaleX != 1f || scaleY != 1f || rotateDegrees != 0f) {
      effects.add(
          new ScaleAndRotateTransformation.Builder()
              .setScale(scaleX, scaleY)
              .setRotationDegrees(rotateDegrees)
              .build());
    }

    int resolutionHeight =
        bundle.getInt(ConfigurationActivity.RESOLUTION_HEIGHT, /* defaultValue= */ C.LENGTH_UNSET);
    if (resolutionHeight != C.LENGTH_UNSET) {
      effects.add(Presentation.createForHeight(resolutionHeight));
    }

    return effects.build();
  }

  @Nullable
  private OverlayEffect createOverlayEffectFromBundle(Bundle bundle, boolean[] selectedEffects) {
    ImmutableList.Builder<TextureOverlay> overlaysBuilder = new ImmutableList.Builder<>();
    if (selectedEffects[ConfigurationActivity.OVERLAY_LOGO_AND_TIMER_INDEX]) {
      OverlaySettings logoSettings =
          new OverlaySettings.Builder()
              // Place the logo in the bottom left corner of the screen with some padding from the
              // edges.
              .setOverlayFrameAnchor(/* x= */ 1f, /* y= */ 1f)
              .setBackgroundFrameAnchor(/* x= */ -0.95f, /* y= */ -0.95f)
              .build();
      Drawable logo;
      try {
        logo = getPackageManager().getApplicationIcon(getPackageName());
      } catch (PackageManager.NameNotFoundException e) {
        throw new IllegalStateException(e);
      }
      logo.setBounds(
          /* left= */ 0, /* top= */ 0, logo.getIntrinsicWidth(), logo.getIntrinsicHeight());
      TextureOverlay logoOverlay = DrawableOverlay.createStaticDrawableOverlay(logo, logoSettings);
      TextureOverlay timerOverlay = new TimerOverlay();
      overlaysBuilder.add(logoOverlay, timerOverlay);
    }
    if (selectedEffects[ConfigurationActivity.BITMAP_OVERLAY_INDEX]) {
      OverlaySettings overlaySettings =
          new OverlaySettings.Builder()
              .setAlphaScale(
                  bundle.getFloat(
                      ConfigurationActivity.BITMAP_OVERLAY_ALPHA, /* defaultValue= */ 1))
              .build();
      BitmapOverlay bitmapOverlay =
          BitmapOverlay.createStaticBitmapOverlay(
              getApplicationContext(),
              Uri.parse(checkNotNull(bundle.getString(ConfigurationActivity.BITMAP_OVERLAY_URI))),
              overlaySettings);
      overlaysBuilder.add(bitmapOverlay);
    }
    if (selectedEffects[ConfigurationActivity.TEXT_OVERLAY_INDEX]) {
      OverlaySettings overlaySettings =
          new OverlaySettings.Builder()
              .setAlphaScale(
                  bundle.getFloat(ConfigurationActivity.TEXT_OVERLAY_ALPHA, /* defaultValue= */ 1))
              .build();
      SpannableString overlayText =
          new SpannableString(
              checkNotNull(bundle.getString(ConfigurationActivity.TEXT_OVERLAY_TEXT)));
      overlayText.setSpan(
          new ForegroundColorSpan(bundle.getInt(ConfigurationActivity.TEXT_OVERLAY_TEXT_COLOR)),
          /* start= */ 0,
          overlayText.length(),
          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      TextOverlay textOverlay = TextOverlay.createStaticTextOverlay(overlayText, overlaySettings);
      overlaysBuilder.add(textOverlay);
    }

    ImmutableList<TextureOverlay> overlays = overlaysBuilder.build();
    return overlays.isEmpty() ? null : new OverlayEffect(overlays);
  }

  @RequiresNonNull({
    "informationTextView",
    "progressViewGroup",
    "debugFrame",
    "exportStopwatch",
  })
  private void onError(ExportException exportException) {
    exportStopwatch.stop();
    informationTextView.setText(R.string.export_error);
    progressViewGroup.setVisibility(View.GONE);
    debugFrame.removeAllViews();
    Toast.makeText(getApplicationContext(), "Export error: " + exportException, Toast.LENGTH_LONG)
        .show();
    Log.e(TAG, "Export error", exportException);
  }

  @RequiresNonNull({
    "inputCardView",
    "inputTextView",
    "inputImageView",
    "inputPlayerView",
    "outputPlayerView",
    "outputVideoTextView",
    "debugTextView",
    "displayInputButton",
    "informationTextView",
    "progressViewGroup",
    "debugFrame",
    "exportStopwatch",
  })
  private void onCompleted(Uri inputUri, String filePath, ExportResult exportResult) {
    exportStopwatch.stop();
    long elapsedTimeMs = exportStopwatch.elapsed(TimeUnit.MILLISECONDS);
    informationTextView.setText(
        getString(R.string.export_completed, elapsedTimeMs / 1000.f, filePath));
    progressViewGroup.setVisibility(View.GONE);
    debugFrame.removeAllViews();
    inputCardView.setVisibility(View.VISIBLE);
    outputPlayerView.setVisibility(View.VISIBLE);
    outputVideoTextView.setVisibility(View.VISIBLE);
    debugTextView.setVisibility(View.VISIBLE);
    displayInputButton.setVisibility(View.VISIBLE);
    Log.d(TAG, DebugTraceUtil.generateTraceSummary());
    File file = new File(getExternalFilesDir(null), "trace.tsv");
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      DebugTraceUtil.dumpTsv(writer);
      Log.d(TAG, file.getAbsolutePath());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    playMediaItems(MediaItem.fromUri(inputUri), MediaItem.fromUri("file://" + filePath));
    Log.d(TAG, "Output file path: file://" + filePath);
    try {
      JSONObject resultJson =
          JsonUtil.exportResultAsJsonObject(exportResult)
              .put("elapsedTimeMs", elapsedTimeMs)
              .put("device", JsonUtil.getDeviceDetailsAsJsonObject());
      for (String line : Util.split(resultJson.toString(2), "\n")) {
        Log.d(TAG, line);
      }
    } catch (JSONException e) {
      Log.d(TAG, "Unable to convert exportResult to JSON", e);
    }
  }

  @RequiresNonNull({
    "inputCardView",
    "inputTextView",
    "inputImageView",
    "inputPlayerView",
    "outputPlayerView",
    "debugTextView",
  })
  private void playMediaItems(MediaItem inputMediaItem, MediaItem outputMediaItem) {
    inputPlayerView.setPlayer(null);
    outputPlayerView.setPlayer(null);
    releasePlayer();

    Uri uri = checkNotNull(inputMediaItem.localConfiguration).uri;
    ExoPlayer outputPlayer = new ExoPlayer.Builder(/* context= */ this).build();
    outputPlayerView.setPlayer(outputPlayer);
    outputPlayerView.setControllerAutoShow(false);
    outputPlayer.setMediaItem(outputMediaItem);
    outputPlayer.prepare();
    this.outputPlayer = outputPlayer;

    // Only support showing jpg images.
    if (uri.toString().endsWith("jpg")) {
      inputPlayerView.setVisibility(View.GONE);
      inputImageView.setVisibility(View.VISIBLE);
      inputTextView.setText(getString(R.string.input_image));

      BitmapLoader bitmapLoader = new DataSourceBitmapLoader(getApplicationContext());
      ListenableFuture<Bitmap> future = bitmapLoader.loadBitmap(uri);
      try {
        Bitmap bitmap = future.get();
        inputImageView.setImageBitmap(bitmap);
      } catch (ExecutionException | InterruptedException e) {
        throw new IllegalArgumentException("Failed to load bitmap.", e);
      }
    } else {
      inputPlayerView.setVisibility(View.VISIBLE);
      inputImageView.setVisibility(View.GONE);
      inputTextView.setText(getString(R.string.input_video_no_sound));

      ExoPlayer inputPlayer = new ExoPlayer.Builder(/* context= */ this).build();
      inputPlayerView.setPlayer(inputPlayer);
      inputPlayerView.setControllerAutoShow(false);
      inputPlayerView.setOnClickListener(this::onClickingPlayerView);
      outputPlayerView.setOnClickListener(this::onClickingPlayerView);
      inputPlayer.setMediaItem(inputMediaItem);
      inputPlayer.prepare();
      this.inputPlayer = inputPlayer;
      inputPlayer.setVolume(0f);
      inputPlayer.play();
    }
    outputPlayer.play();

    debugTextViewHelper = new DebugTextViewHelper(outputPlayer, debugTextView);
    debugTextViewHelper.start();
  }

  private void onClickingPlayerView(View view) {
    if (view == inputPlayerView) {
      if (inputPlayer != null && inputTextView != null) {
        inputPlayer.setVolume(1f);
        inputTextView.setText(R.string.input_video_playing_sound);
      }
      checkNotNull(outputPlayer).setVolume(0f);
      checkNotNull(outputVideoTextView).setText(R.string.output_video_no_sound);
    } else {
      if (inputPlayer != null && inputTextView != null) {
        inputPlayer.setVolume(0f);
        inputTextView.setText(getString(R.string.input_video_no_sound));
      }
      checkNotNull(outputPlayer).setVolume(1f);
      checkNotNull(outputVideoTextView).setText(R.string.output_video_playing_sound);
    }
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

  private static void requestReadVideoPermission(AppCompatActivity activity) {
    String permission = SDK_INT >= 33 ? READ_MEDIA_VIDEO : READ_EXTERNAL_STORAGE;
    if (ActivityCompat.checkSelfPermission(activity, permission)
        != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(activity, new String[] {permission}, /* requestCode= */ 0);
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
      if (inputPlayer != null) {
        inputPlayer.pause();
      }
      inputCardView.setVisibility(View.GONE);
      displayInputButton.setText(getString(R.string.show_input_video));
    }
  }

  @RequiresNonNull({"transformer", "exportStopwatch", "cancelButton", "resumeButton"})
  private void cancelExport(View view) {
    transformer.cancel();
    transformer = null;
    exportStopwatch.stop();
    cancelButton.setVisibility(View.GONE);
    resumeButton.setVisibility(View.VISIBLE);
    if (oldOutputFile != null) {
      oldOutputFile.delete();
    }
    oldOutputFile = outputFile;
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
          "Transformer should not change the output size mid-export.");
      if (surfaceView != null) {
        return surfaceView;
      }

      this.width = width;
      this.height = height;

      // Update the UI on the main thread and wait for the output surface to be available.
      CountDownLatch surfaceCreatedCountDownLatch = new CountDownLatch(1);
      runOnUiThread(
          () -> {
            surfaceView = new SurfaceView(/* context= */ TransformerActivity.this);
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
      return surfaceView;
    }
  }
}
