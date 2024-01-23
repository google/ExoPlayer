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
import static androidx.media3.transformer.Composition.HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR;
import static androidx.media3.transformer.Composition.HDR_MODE_KEEP_HDR;
import static androidx.media3.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC;
import static androidx.media3.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import androidx.media3.transformer.Composition;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * An {@link Activity} that sets the configuration to use for exporting and playing media, using
 * {@link TransformerActivity}.
 */
public final class ConfigurationActivity extends AppCompatActivity {
  public static final String SHOULD_REMOVE_AUDIO = "should_remove_audio";
  public static final String SHOULD_REMOVE_VIDEO = "should_remove_video";
  public static final String SHOULD_FLATTEN_FOR_SLOW_MOTION = "should_flatten_for_slow_motion";
  public static final String FORCE_AUDIO_TRACK = "force_audio_track";
  public static final String AUDIO_MIME_TYPE = "audio_mime_type";
  public static final String VIDEO_MIME_TYPE = "video_mime_type";
  public static final String RESOLUTION_HEIGHT = "resolution_height";
  public static final String SCALE_X = "scale_x";
  public static final String SCALE_Y = "scale_y";
  public static final String ROTATE_DEGREES = "rotate_degrees";
  public static final String TRIM_START_MS = "trim_start_ms";
  public static final String TRIM_END_MS = "trim_end_ms";
  public static final String ENABLE_FALLBACK = "enable_fallback";
  public static final String ENABLE_DEBUG_PREVIEW = "enable_debug_preview";
  public static final String ABORT_SLOW_EXPORT = "abort_slow_export";
  public static final String PRODUCE_FRAGMENTED_MP4 = "produce_fragmented_mp4";
  public static final String HDR_MODE = "hdr_mode";
  public static final String AUDIO_EFFECTS_SELECTIONS = "audio_effects_selections";
  public static final String VIDEO_EFFECTS_SELECTIONS = "video_effects_selections";
  public static final String PERIODIC_VIGNETTE_CENTER_X = "periodic_vignette_center_x";
  public static final String PERIODIC_VIGNETTE_CENTER_Y = "periodic_vignette_center_y";
  public static final String PERIODIC_VIGNETTE_INNER_RADIUS = "periodic_vignette_inner_radius";
  public static final String PERIODIC_VIGNETTE_OUTER_RADIUS = "periodic_vignette_outer_radius";
  public static final String COLOR_FILTER_SELECTION = "color_filter_selection";
  public static final String CONTRAST_VALUE = "contrast_value";
  public static final String RGB_ADJUSTMENT_RED_SCALE = "rgb_adjustment_red_scale";
  public static final String RGB_ADJUSTMENT_GREEN_SCALE = "rgb_adjustment_green_scale";
  public static final String RGB_ADJUSTMENT_BLUE_SCALE = "rgb_adjustment_blue_scale";
  public static final String HSL_ADJUSTMENTS_HUE = "hsl_adjustments_hue";
  public static final String HSL_ADJUSTMENTS_SATURATION = "hsl_adjustments_saturation";
  public static final String HSL_ADJUSTMENTS_LIGHTNESS = "hsl_adjustments_lightness";
  public static final String BITMAP_OVERLAY_URI = "bitmap_overlay_uri";
  public static final String BITMAP_OVERLAY_ALPHA = "bitmap_overlay_alpha";
  public static final String TEXT_OVERLAY_TEXT = "text_overlay_text";
  public static final String TEXT_OVERLAY_TEXT_COLOR = "text_overlay_text_color";
  public static final String TEXT_OVERLAY_ALPHA = "text_overlay_alpha";

  // Video effect selections.
  public static final int DIZZY_CROP_INDEX = 0;
  public static final int EDGE_DETECTOR_INDEX = 1;
  public static final int COLOR_FILTERS_INDEX = 2;
  public static final int MAP_WHITE_TO_GREEN_LUT_INDEX = 3;
  public static final int RGB_ADJUSTMENTS_INDEX = 4;
  public static final int HSL_ADJUSTMENT_INDEX = 5;
  public static final int CONTRAST_INDEX = 6;
  public static final int PERIODIC_VIGNETTE_INDEX = 7;
  public static final int SPIN_3D_INDEX = 8;
  public static final int ZOOM_IN_INDEX = 9;
  public static final int OVERLAY_LOGO_AND_TIMER_INDEX = 10;
  public static final int BITMAP_OVERLAY_INDEX = 11;
  public static final int TEXT_OVERLAY_INDEX = 12;

  // Audio effect selections.
  public static final int HIGH_PITCHED_INDEX = 0;
  public static final int SAMPLE_RATE_INDEX = 1;
  public static final int SKIP_SILENCE_INDEX = 2;
  public static final int CHANNEL_MIXING_INDEX = 3;
  public static final int VOLUME_SCALING_INDEX = 4;

  // Color filter options.
  public static final int COLOR_FILTER_GRAYSCALE = 0;
  public static final int COLOR_FILTER_INVERTED = 1;
  public static final int COLOR_FILTER_SEPIA = 2;

  public static final int FILE_PERMISSION_REQUEST_CODE = 1;
  private static final String[] PRESET_FILE_URIS = {
    "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4",
    "https://storage.googleapis.com/exoplayer-test-media-0/android-block-1080-hevc.mp4",
    "https://html5demos.com/assets/dizzy.mp4",
    "https://html5demos.com/assets/dizzy.webm",
    "https://storage.googleapis.com/exoplayer-test-media-1/mp4/portrait_4k60.mp4",
    "https://storage.googleapis.com/exoplayer-test-media-1/mp4/8k24fps_4s.mp4",
    "https://storage.googleapis.com/exoplayer-test-media-1/mp4/1920w_1080h_4s.mp4",
    "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4",
    "https://storage.googleapis.com/exoplayer-test-media-1/mp4/portrait_avc_aac.mp4",
    "https://storage.googleapis.com/exoplayer-test-media-1/mp4/portrait_rotated_avc_aac.mp4",
    "https://storage.googleapis.com/exoplayer-test-media-1/jpg/london.jpg",
    "https://storage.googleapis.com/exoplayer-test-media-1/jpg/tokyo.jpg",
    "https://storage.googleapis.com/exoplayer-test-media-1/mp4/slow-motion/slowMotion_stopwatch_240fps_long.mp4",
    "https://storage.googleapis.com/exoplayer-test-media-1/gen/screens/dash-vod-single-segment/manifest-baseline.mpd",
    "https://storage.googleapis.com/exoplayer-test-media-1/mp4/samsung-s21-hdr-hdr10.mp4",
    "https://storage.googleapis.com/exoplayer-test-media-1/mp4/Pixel7Pro_HLG_1080P.mp4",
    "https://storage.googleapis.com/exoplayer-test-media-1/mp4/sample_video_track_only.mp4",
  };
  private static final String[] PRESET_FILE_URI_DESCRIPTIONS = { // same order as PRESET_FILE_URIS
    "720p H264 video and AAC audio (B-frames)",
    "1080p H265 video and AAC audio (B-frames)",
    "360p H264 video and AAC audio",
    "360p VP8 video and Vorbis audio",
    "4K H264 video and AAC audio (portrait, no B-frames)",
    "8k H265 video and AAC audio",
    "Short 1080p H265 video and AAC audio",
    "Long 180p H264 video and AAC audio",
    "H264 video and AAC audio (portrait, H > W, 0°)",
    "H264 video and AAC audio (portrait, H < W, 90°)",
    "London JPG image (Plays for 5secs at 30fps)",
    "Tokyo JPG image (Portrait, Plays for 5secs at 30fps)",
    "SEF slow motion with 240 fps",
    "480p DASH (non-square pixels)",
    "HDR (HDR10) H265 limited range video (encoding may fail)",
    "HDR (HLG) H265 limited range video (encoding may fail)",
    "720p H264 video with no audio (B-frames)",
  };
  private static final String[] AUDIO_EFFECTS = {
    "High pitched",
    "Sample rate of 48000Hz",
    "Skip silence",
    "Mix channels into mono",
    "Scale volume to 50%"
  };
  private static final String[] VIDEO_EFFECTS = {
    "Dizzy crop",
    "Edge detector (Media Pipe)",
    "Color filters",
    "Map White to Green Color Lookup Table",
    "RGB Adjustments",
    "HSL Adjustments",
    "Contrast",
    "Periodic vignette",
    "3D spin",
    "Zoom in start",
    "Overlay logo & timer",
    "Custom Bitmap Overlay",
    "Custom Text Overlay",
  };
  private static final ImmutableMap<String, @Composition.HdrMode Integer> HDR_MODE_DESCRIPTIONS =
      new ImmutableMap.Builder<String, @Composition.HdrMode Integer>()
          .put("Keep HDR", HDR_MODE_KEEP_HDR)
          .put("MediaCodec tone-map HDR to SDR", HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC)
          .put("OpenGL tone-map HDR to SDR", HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
          .put("Force Interpret HDR as SDR", HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR)
          .build();
  private static final ImmutableMap<String, Integer> OVERLAY_COLORS =
      new ImmutableMap.Builder<String, Integer>()
          .put("BLACK", Color.BLACK)
          .put("BLUE", Color.BLUE)
          .put("CYAN", Color.CYAN)
          .put("DKGRAY", Color.DKGRAY)
          .put("GRAY", Color.GRAY)
          .put("GREEN", Color.GREEN)
          .put("LTGRAY", Color.LTGRAY)
          .put("MAGENTA", Color.MAGENTA)
          .put("RED", Color.RED)
          .put("WHITE", Color.WHITE)
          .put("YELLOW", Color.YELLOW)
          .build();
  private static final String SAME_AS_INPUT_OPTION = "same as input";
  private static final float HALF_DIAGONAL = 1f / (float) Math.sqrt(2);

  private @MonotonicNonNull Runnable onPermissionsGranted;
  private @MonotonicNonNull ActivityResultLauncher<Intent> videoLocalFilePickerLauncher;
  private @MonotonicNonNull ActivityResultLauncher<Intent> overlayLocalFilePickerLauncher;
  private @MonotonicNonNull Button selectPresetFileButton;
  private @MonotonicNonNull Button selectLocalFileButton;
  private @MonotonicNonNull TextView selectedFileTextView;
  private @MonotonicNonNull CheckBox removeAudioCheckbox;
  private @MonotonicNonNull CheckBox removeVideoCheckbox;
  private @MonotonicNonNull CheckBox flattenForSlowMotionCheckbox;
  private @MonotonicNonNull CheckBox forceAudioTrackCheckbox;
  private @MonotonicNonNull Spinner audioMimeSpinner;
  private @MonotonicNonNull Spinner videoMimeSpinner;
  private @MonotonicNonNull Spinner resolutionHeightSpinner;
  private @MonotonicNonNull Spinner scaleSpinner;
  private @MonotonicNonNull Spinner rotateSpinner;
  private @MonotonicNonNull CheckBox trimCheckBox;
  private @MonotonicNonNull CheckBox enableFallbackCheckBox;
  private @MonotonicNonNull CheckBox enableDebugPreviewCheckBox;
  private @MonotonicNonNull CheckBox abortSlowExportCheckBox;
  private @MonotonicNonNull CheckBox produceFragmentedMp4CheckBox;
  private @MonotonicNonNull Spinner hdrModeSpinner;
  private @MonotonicNonNull Button selectAudioEffectsButton;
  private @MonotonicNonNull Button selectVideoEffectsButton;
  private boolean @MonotonicNonNull [] audioEffectsSelections;
  private boolean @MonotonicNonNull [] videoEffectsSelections;
  private @Nullable Uri localFileUri;
  private int inputUriPosition;
  private long trimStartMs;
  private long trimEndMs;
  private int colorFilterSelection;
  private float rgbAdjustmentRedScale;
  private float rgbAdjustmentGreenScale;
  private float rgbAdjustmentBlueScale;
  private float contrastValue;
  private float hueAdjustment;
  private float saturationAdjustment;
  private float lightnessAdjustment;
  private float periodicVignetteCenterX;
  private float periodicVignetteCenterY;
  private float periodicVignetteInnerRadius;
  private float periodicVignetteOuterRadius;
  private @MonotonicNonNull String bitmapOverlayUri;
  private float bitmapOverlayAlpha;
  private @MonotonicNonNull String textOverlayText;
  private int textOverlayTextColor;
  private float textOverlayAlpha;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.configuration_activity);

    findViewById(R.id.export_button).setOnClickListener(this::startExport);

    videoLocalFilePickerLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            this::videoLocalFilePickerLauncherResult);
    overlayLocalFilePickerLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            this::overlayLocalFilePickerLauncherResult);

    selectPresetFileButton = findViewById(R.id.select_preset_file_button);
    selectPresetFileButton.setOnClickListener(this::selectPresetFile);

    selectLocalFileButton = findViewById(R.id.select_local_file_button);
    selectLocalFileButton.setOnClickListener(
        view ->
            selectLocalFile(
                checkNotNull(videoLocalFilePickerLauncher),
                /* mimeTypes= */ new String[] {"image/*", "video/*", "audio/*"}));

    selectedFileTextView = findViewById(R.id.selected_file_text_view);
    selectedFileTextView.setText(PRESET_FILE_URI_DESCRIPTIONS[inputUriPosition]);

    removeAudioCheckbox = findViewById(R.id.remove_audio_checkbox);
    removeAudioCheckbox.setOnClickListener(this::onRemoveAudio);

    removeVideoCheckbox = findViewById(R.id.remove_video_checkbox);
    removeVideoCheckbox.setOnClickListener(this::onRemoveVideo);

    flattenForSlowMotionCheckbox = findViewById(R.id.flatten_for_slow_motion_checkbox);

    forceAudioTrackCheckbox = findViewById(R.id.force_audio_track_checkbox);

    ArrayAdapter<String> audioMimeAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    audioMimeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    audioMimeSpinner = findViewById(R.id.audio_mime_spinner);
    audioMimeSpinner.setAdapter(audioMimeAdapter);
    audioMimeAdapter.addAll(
        SAME_AS_INPUT_OPTION, MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_AMR_NB, MimeTypes.AUDIO_AMR_WB);

    ArrayAdapter<String> videoMimeAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    videoMimeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    videoMimeSpinner = findViewById(R.id.video_mime_spinner);
    videoMimeSpinner.setAdapter(videoMimeAdapter);
    videoMimeAdapter.addAll(
        SAME_AS_INPUT_OPTION, MimeTypes.VIDEO_H263, MimeTypes.VIDEO_H264, MimeTypes.VIDEO_MP4V);
    if (SDK_INT >= 24) {
      videoMimeAdapter.add(MimeTypes.VIDEO_H265);
    }

    ArrayAdapter<String> resolutionHeightAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    resolutionHeightAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    resolutionHeightSpinner = findViewById(R.id.resolution_height_spinner);
    resolutionHeightSpinner.setAdapter(resolutionHeightAdapter);
    resolutionHeightAdapter.addAll(
        SAME_AS_INPUT_OPTION, "144", "240", "360", "480", "720", "1080", "1440", "2160");

    ArrayAdapter<String> scaleAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    scaleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    scaleSpinner = findViewById(R.id.scale_spinner);
    scaleSpinner.setAdapter(scaleAdapter);
    scaleAdapter.addAll(SAME_AS_INPUT_OPTION, "-1, -1", "-1, 1", "1, 1", ".5, 1", ".5, .5", "2, 2");

    ArrayAdapter<String> rotateAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    rotateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    rotateSpinner = findViewById(R.id.rotate_spinner);
    rotateSpinner.setAdapter(rotateAdapter);
    rotateAdapter.addAll(SAME_AS_INPUT_OPTION, "0", "10", "45", "60", "90", "180");

    trimCheckBox = findViewById(R.id.trim_checkbox);
    trimCheckBox.setOnCheckedChangeListener(this::selectTrimBounds);
    trimStartMs = C.TIME_UNSET;
    trimEndMs = C.TIME_UNSET;

    enableFallbackCheckBox = findViewById(R.id.enable_fallback_checkbox);
    enableDebugPreviewCheckBox = findViewById(R.id.enable_debug_preview_checkbox);

    abortSlowExportCheckBox = findViewById(R.id.abort_slow_export_checkbox);
    produceFragmentedMp4CheckBox = findViewById(R.id.produce_fragmented_mp4_checkbox);

    ArrayAdapter<String> hdrModeAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    hdrModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    hdrModeSpinner = findViewById(R.id.hdr_mode_spinner);
    hdrModeSpinner.setAdapter(hdrModeAdapter);
    hdrModeAdapter.addAll(HDR_MODE_DESCRIPTIONS.keySet());

    audioEffectsSelections = new boolean[AUDIO_EFFECTS.length];
    selectAudioEffectsButton = findViewById(R.id.select_audio_effects_button);
    selectAudioEffectsButton.setOnClickListener(this::selectAudioEffects);

    videoEffectsSelections = new boolean[VIDEO_EFFECTS.length];
    selectVideoEffectsButton = findViewById(R.id.select_video_effects_button);
    selectVideoEffectsButton.setOnClickListener(this::selectVideoEffects);
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    if (requestCode == FILE_PERMISSION_REQUEST_CODE
        && grantResults.length == 1
        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      checkNotNull(onPermissionsGranted).run();
    } else {
      Toast.makeText(
              getApplicationContext(), getString(R.string.permission_denied), Toast.LENGTH_LONG)
          .show();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    @Nullable Uri intentUri = getIntent().getData();
    if (intentUri != null) {
      checkNotNull(selectPresetFileButton).setEnabled(false);
      checkNotNull(selectLocalFileButton).setEnabled(false);
      checkNotNull(selectedFileTextView).setText(intentUri.toString());
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
  }

  @RequiresNonNull({
    "removeAudioCheckbox",
    "removeVideoCheckbox",
    "flattenForSlowMotionCheckbox",
    "forceAudioTrackCheckbox",
    "audioMimeSpinner",
    "videoMimeSpinner",
    "resolutionHeightSpinner",
    "scaleSpinner",
    "rotateSpinner",
    "trimCheckBox",
    "enableFallbackCheckBox",
    "enableDebugPreviewCheckBox",
    "abortSlowExportCheckBox",
    "produceFragmentedMp4CheckBox",
    "hdrModeSpinner",
    "audioEffectsSelections",
    "videoEffectsSelections"
  })
  private void startExport(View view) {
    Intent transformerIntent = new Intent(/* packageContext= */ this, TransformerActivity.class);
    Bundle bundle = new Bundle();
    bundle.putBoolean(SHOULD_REMOVE_AUDIO, removeAudioCheckbox.isChecked());
    bundle.putBoolean(SHOULD_REMOVE_VIDEO, removeVideoCheckbox.isChecked());
    bundle.putBoolean(SHOULD_FLATTEN_FOR_SLOW_MOTION, flattenForSlowMotionCheckbox.isChecked());
    bundle.putBoolean(FORCE_AUDIO_TRACK, forceAudioTrackCheckbox.isChecked());
    String selectedAudioMimeType = String.valueOf(audioMimeSpinner.getSelectedItem());
    if (!SAME_AS_INPUT_OPTION.equals(selectedAudioMimeType)) {
      bundle.putString(AUDIO_MIME_TYPE, selectedAudioMimeType);
    }
    String selectedVideoMimeType = String.valueOf(videoMimeSpinner.getSelectedItem());
    if (!SAME_AS_INPUT_OPTION.equals(selectedVideoMimeType)) {
      bundle.putString(VIDEO_MIME_TYPE, selectedVideoMimeType);
    }
    String selectedResolutionHeight = String.valueOf(resolutionHeightSpinner.getSelectedItem());
    if (!SAME_AS_INPUT_OPTION.equals(selectedResolutionHeight)) {
      bundle.putInt(RESOLUTION_HEIGHT, Integer.parseInt(selectedResolutionHeight));
    }
    String selectedScale = String.valueOf(scaleSpinner.getSelectedItem());
    if (!SAME_AS_INPUT_OPTION.equals(selectedScale)) {
      List<String> scaleXY = Arrays.asList(selectedScale.split(", "));
      checkState(scaleXY.size() == 2);
      bundle.putFloat(SCALE_X, Float.parseFloat(scaleXY.get(0)));
      bundle.putFloat(SCALE_Y, Float.parseFloat(scaleXY.get(1)));
    }
    String selectedRotate = String.valueOf(rotateSpinner.getSelectedItem());
    if (!SAME_AS_INPUT_OPTION.equals(selectedRotate)) {
      bundle.putFloat(ROTATE_DEGREES, Float.parseFloat(selectedRotate));
    }
    if (trimCheckBox.isChecked()) {
      bundle.putLong(TRIM_START_MS, trimStartMs);
      bundle.putLong(TRIM_END_MS, trimEndMs);
    }
    bundle.putBoolean(ENABLE_FALLBACK, enableFallbackCheckBox.isChecked());
    bundle.putBoolean(ENABLE_DEBUG_PREVIEW, enableDebugPreviewCheckBox.isChecked());
    bundle.putBoolean(ABORT_SLOW_EXPORT, abortSlowExportCheckBox.isChecked());
    bundle.putBoolean(PRODUCE_FRAGMENTED_MP4, produceFragmentedMp4CheckBox.isChecked());
    String selectedhdrMode = String.valueOf(hdrModeSpinner.getSelectedItem());
    bundle.putInt(HDR_MODE, checkNotNull(HDR_MODE_DESCRIPTIONS.get(selectedhdrMode)));
    bundle.putBooleanArray(AUDIO_EFFECTS_SELECTIONS, audioEffectsSelections);
    bundle.putBooleanArray(VIDEO_EFFECTS_SELECTIONS, videoEffectsSelections);
    bundle.putInt(COLOR_FILTER_SELECTION, colorFilterSelection);
    bundle.putFloat(CONTRAST_VALUE, contrastValue);
    bundle.putFloat(RGB_ADJUSTMENT_RED_SCALE, rgbAdjustmentRedScale);
    bundle.putFloat(RGB_ADJUSTMENT_GREEN_SCALE, rgbAdjustmentGreenScale);
    bundle.putFloat(RGB_ADJUSTMENT_BLUE_SCALE, rgbAdjustmentBlueScale);
    bundle.putFloat(HSL_ADJUSTMENTS_HUE, hueAdjustment);
    bundle.putFloat(HSL_ADJUSTMENTS_SATURATION, saturationAdjustment);
    bundle.putFloat(HSL_ADJUSTMENTS_LIGHTNESS, lightnessAdjustment);
    bundle.putFloat(PERIODIC_VIGNETTE_CENTER_X, periodicVignetteCenterX);
    bundle.putFloat(PERIODIC_VIGNETTE_CENTER_Y, periodicVignetteCenterY);
    bundle.putFloat(PERIODIC_VIGNETTE_INNER_RADIUS, periodicVignetteInnerRadius);
    bundle.putFloat(PERIODIC_VIGNETTE_OUTER_RADIUS, periodicVignetteOuterRadius);
    bundle.putString(BITMAP_OVERLAY_URI, bitmapOverlayUri);
    bundle.putFloat(BITMAP_OVERLAY_ALPHA, bitmapOverlayAlpha);
    bundle.putString(TEXT_OVERLAY_TEXT, textOverlayText);
    bundle.putInt(TEXT_OVERLAY_TEXT_COLOR, textOverlayTextColor);
    bundle.putFloat(TEXT_OVERLAY_ALPHA, textOverlayAlpha);
    transformerIntent.putExtras(bundle);

    @Nullable Uri intentUri;
    if (getIntent().getData() != null) {
      intentUri = getIntent().getData();
    } else if (localFileUri != null) {
      intentUri = localFileUri;
    } else {
      intentUri = Uri.parse(PRESET_FILE_URIS[inputUriPosition]);
    }
    transformerIntent.setData(intentUri);

    startActivity(transformerIntent);
  }

  private void selectPresetFile(View view) {
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.select_preset_file_title)
        .setSingleChoiceItems(
            PRESET_FILE_URI_DESCRIPTIONS, inputUriPosition, this::selectPresetFileInDialog)
        .setPositiveButton(android.R.string.ok, /* listener= */ null)
        .create()
        .show();
  }

  @RequiresNonNull("selectedFileTextView")
  private void selectPresetFileInDialog(DialogInterface dialog, int which) {
    inputUriPosition = which;
    localFileUri = null;
    selectedFileTextView.setText(PRESET_FILE_URI_DESCRIPTIONS[inputUriPosition]);
  }

  private void selectLocalFile(
      ActivityResultLauncher<Intent> localFilePickerLauncher, String[] mimeTypes) {
    String permission = SDK_INT >= 33 ? READ_MEDIA_VIDEO : READ_EXTERNAL_STORAGE;
    if (ActivityCompat.checkSelfPermission(/* context= */ this, permission)
        != PackageManager.PERMISSION_GRANTED) {
      onPermissionsGranted = () -> launchLocalFilePicker(localFilePickerLauncher, mimeTypes);
      ActivityCompat.requestPermissions(
          /* activity= */ this, new String[] {permission}, FILE_PERMISSION_REQUEST_CODE);
    } else {
      launchLocalFilePicker(localFilePickerLauncher, mimeTypes);
    }
  }

  private void launchLocalFilePicker(
      ActivityResultLauncher<Intent> localFilePickerLauncher, String[] mimeTypes) {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.setType("*/*");
    intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
    checkNotNull(localFilePickerLauncher).launch(intent);
  }

  @RequiresNonNull("selectedFileTextView")
  private void videoLocalFilePickerLauncherResult(ActivityResult result) {
    Intent data = result.getData();
    if (data != null) {
      localFileUri = checkNotNull(data.getData());
      selectedFileTextView.setText(localFileUri.toString());
    } else {
      Toast.makeText(
              getApplicationContext(),
              getString(R.string.local_file_picker_failed),
              Toast.LENGTH_SHORT)
          .show();
    }
  }

  private void overlayLocalFilePickerLauncherResult(ActivityResult result) {
    Intent data = result.getData();
    if (data != null) {
      bitmapOverlayUri = checkNotNull(data.getData()).toString();
    } else {
      Toast.makeText(
              getApplicationContext(),
              getString(R.string.local_file_picker_failed),
              Toast.LENGTH_SHORT)
          .show();
    }
  }

  private void selectAudioEffects(View view) {
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.select_audio_effects)
        .setMultiChoiceItems(
            AUDIO_EFFECTS, checkNotNull(audioEffectsSelections), this::selectAudioEffect)
        .setPositiveButton(android.R.string.ok, /* listener= */ null)
        .create()
        .show();
  }

  private void selectVideoEffects(View view) {
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.select_video_effects)
        .setMultiChoiceItems(
            VIDEO_EFFECTS, checkNotNull(videoEffectsSelections), this::selectVideoEffect)
        .setPositiveButton(android.R.string.ok, /* listener= */ null)
        .create()
        .show();
  }

  private void selectTrimBounds(View view, boolean isChecked) {
    if (!isChecked) {
      return;
    }
    View dialogView = getLayoutInflater().inflate(R.layout.trim_options, /* root= */ null);
    RangeSlider trimRangeSlider =
        checkNotNull(dialogView.findViewById(R.id.trim_bounds_range_slider));
    trimRangeSlider.setValues(0f, 1f); // seconds
    new AlertDialog.Builder(/* context= */ this)
        .setView(dialogView)
        .setPositiveButton(
            android.R.string.ok,
            (DialogInterface dialogInterface, int i) -> {
              List<Float> trimRange = trimRangeSlider.getValues();
              trimStartMs = Math.round(1000 * trimRange.get(0));
              trimEndMs = Math.round(1000 * trimRange.get(1));
            })
        .create()
        .show();
  }

  @RequiresNonNull("audioEffectsSelections")
  private void selectAudioEffect(DialogInterface dialog, int which, boolean isChecked) {
    audioEffectsSelections[which] = isChecked;
  }

  @RequiresNonNull("videoEffectsSelections")
  private void selectVideoEffect(DialogInterface dialog, int which, boolean isChecked) {
    videoEffectsSelections[which] = isChecked;
    if (!isChecked) {
      return;
    }

    switch (which) {
      case COLOR_FILTERS_INDEX:
        controlColorFiltersSettings();
        break;
      case RGB_ADJUSTMENTS_INDEX:
        controlRgbAdjustmentsScale();
        break;
      case CONTRAST_INDEX:
        controlContrastSettings();
        break;
      case HSL_ADJUSTMENT_INDEX:
        controlHslAdjustmentSettings();
        break;
      case PERIODIC_VIGNETTE_INDEX:
        controlPeriodicVignetteSettings();
        break;
      case BITMAP_OVERLAY_INDEX:
        controlBitmapOverlaySettings();
        break;
      case TEXT_OVERLAY_INDEX:
        controlTextOverlaySettings();
        break;
    }
  }

  private void controlColorFiltersSettings() {
    new AlertDialog.Builder(/* context= */ this)
        .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> dialogInterface.dismiss())
        .setSingleChoiceItems(
            this.getResources().getStringArray(R.array.color_filter_options),
            colorFilterSelection,
            (DialogInterface dialogInterface, int i) -> {
              checkState(
                  i == COLOR_FILTER_GRAYSCALE
                      || i == COLOR_FILTER_INVERTED
                      || i == COLOR_FILTER_SEPIA);
              colorFilterSelection = i;
              dialogInterface.dismiss();
            })
        .create()
        .show();
  }

  private void controlRgbAdjustmentsScale() {
    View dialogView =
        getLayoutInflater().inflate(R.layout.rgb_adjustment_options, /* root= */ null);
    Slider redScaleSlider = checkNotNull(dialogView.findViewById(R.id.rgb_adjustment_red_scale));
    Slider greenScaleSlider =
        checkNotNull(dialogView.findViewById(R.id.rgb_adjustment_green_scale));
    Slider blueScaleSlider = checkNotNull(dialogView.findViewById(R.id.rgb_adjustment_blue_scale));
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.rgb_adjustment_options)
        .setView(dialogView)
        .setPositiveButton(
            android.R.string.ok,
            (DialogInterface dialogInterface, int i) -> {
              rgbAdjustmentRedScale = redScaleSlider.getValue();
              rgbAdjustmentGreenScale = greenScaleSlider.getValue();
              rgbAdjustmentBlueScale = blueScaleSlider.getValue();
            })
        .create()
        .show();
  }

  private void controlContrastSettings() {
    View dialogView = getLayoutInflater().inflate(R.layout.contrast_options, /* root= */ null);
    Slider contrastSlider = checkNotNull(dialogView.findViewById(R.id.contrast_slider));
    new AlertDialog.Builder(/* context= */ this)
        .setView(dialogView)
        .setPositiveButton(
            android.R.string.ok,
            (DialogInterface dialogInterface, int i) -> contrastValue = contrastSlider.getValue())
        .create()
        .show();
  }

  private void controlHslAdjustmentSettings() {
    View dialogView =
        getLayoutInflater().inflate(R.layout.hsl_adjustment_options, /* root= */ null);
    Slider hueAdjustmentSlider = checkNotNull(dialogView.findViewById(R.id.hsl_adjustments_hue));
    Slider saturationAdjustmentSlider =
        checkNotNull(dialogView.findViewById(R.id.hsl_adjustments_saturation));
    Slider lightnessAdjustmentSlider =
        checkNotNull(dialogView.findViewById(R.id.hsl_adjustment_lightness));
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.hsl_adjustment_options)
        .setView(dialogView)
        .setPositiveButton(
            android.R.string.ok,
            (DialogInterface dialogInterface, int i) -> {
              hueAdjustment = hueAdjustmentSlider.getValue();
              saturationAdjustment = saturationAdjustmentSlider.getValue();
              lightnessAdjustment = lightnessAdjustmentSlider.getValue();
            })
        .create()
        .show();
  }

  private void controlPeriodicVignetteSettings() {
    View dialogView =
        getLayoutInflater().inflate(R.layout.periodic_vignette_options, /* root= */ null);
    Slider centerXSlider =
        checkNotNull(dialogView.findViewById(R.id.periodic_vignette_center_x_slider));
    Slider centerYSlider =
        checkNotNull(dialogView.findViewById(R.id.periodic_vignette_center_y_slider));
    RangeSlider radiusRangeSlider =
        checkNotNull(dialogView.findViewById(R.id.periodic_vignette_radius_range_slider));
    radiusRangeSlider.setValues(0f, HALF_DIAGONAL);
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.periodic_vignette_options)
        .setView(dialogView)
        .setPositiveButton(
            android.R.string.ok,
            (DialogInterface dialogInterface, int i) -> {
              periodicVignetteCenterX = centerXSlider.getValue();
              periodicVignetteCenterY = centerYSlider.getValue();
              List<Float> radiusRange = radiusRangeSlider.getValues();
              periodicVignetteInnerRadius = radiusRange.get(0);
              periodicVignetteOuterRadius = radiusRange.get(1);
            })
        .create()
        .show();
  }

  private void controlBitmapOverlaySettings() {
    View dialogView =
        getLayoutInflater().inflate(R.layout.bitmap_overlay_options, /* root= */ null);
    Button uriButton = checkNotNull(dialogView.findViewById(R.id.bitmap_overlay_uri));
    uriButton.setOnClickListener(
        (view ->
            selectLocalFile(
                checkNotNull(overlayLocalFilePickerLauncher),
                /* mimeTypes= */ new String[] {"image/*"})));
    Slider alphaSlider = checkNotNull(dialogView.findViewById(R.id.bitmap_overlay_alpha_slider));
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.bitmap_overlay_settings)
        .setView(dialogView)
        .setPositiveButton(
            android.R.string.ok,
            (DialogInterface dialogInterface, int i) -> {
              bitmapOverlayAlpha = alphaSlider.getValue();
            })
        .create()
        .show();
  }

  private void controlTextOverlaySettings() {
    View dialogView = getLayoutInflater().inflate(R.layout.text_overlay_options, /* root= */ null);
    EditText textEditText = checkNotNull(dialogView.findViewById(R.id.text_overlay_text));

    ArrayAdapter<String> textColorAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    textColorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    Spinner textColorSpinner = checkNotNull(dialogView.findViewById(R.id.text_overlay_text_color));
    textColorSpinner.setAdapter(textColorAdapter);
    textColorAdapter.addAll(OVERLAY_COLORS.keySet());

    Slider alphaSlider = checkNotNull(dialogView.findViewById(R.id.text_overlay_alpha_slider));
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.bitmap_overlay_settings)
        .setView(dialogView)
        .setPositiveButton(
            android.R.string.ok,
            (DialogInterface dialogInterface, int i) -> {
              textOverlayText = textEditText.getText().toString();
              String selectedTextColor = String.valueOf(textColorSpinner.getSelectedItem());
              textOverlayTextColor = checkNotNull(OVERLAY_COLORS.get(selectedTextColor));
              textOverlayAlpha = alphaSlider.getValue();
            })
        .create()
        .show();
  }

  @RequiresNonNull({
    "removeVideoCheckbox",
    "forceAudioTrackCheckbox",
    "audioMimeSpinner",
    "videoMimeSpinner",
    "resolutionHeightSpinner",
    "scaleSpinner",
    "rotateSpinner",
    "enableDebugPreviewCheckBox",
    "hdrModeSpinner",
    "selectAudioEffectsButton",
    "selectVideoEffectsButton"
  })
  private void onRemoveAudio(View view) {
    if (((CheckBox) view).isChecked()) {
      removeVideoCheckbox.setChecked(false);
      enableTrackSpecificOptions(/* isAudioEnabled= */ false, /* isVideoEnabled= */ true);
    } else {
      enableTrackSpecificOptions(/* isAudioEnabled= */ true, /* isVideoEnabled= */ true);
    }
  }

  @RequiresNonNull({
    "removeAudioCheckbox",
    "forceAudioTrackCheckbox",
    "audioMimeSpinner",
    "videoMimeSpinner",
    "resolutionHeightSpinner",
    "scaleSpinner",
    "rotateSpinner",
    "enableDebugPreviewCheckBox",
    "hdrModeSpinner",
    "selectAudioEffectsButton",
    "selectVideoEffectsButton"
  })
  private void onRemoveVideo(View view) {
    if (((CheckBox) view).isChecked()) {
      removeAudioCheckbox.setChecked(false);
      enableTrackSpecificOptions(/* isAudioEnabled= */ true, /* isVideoEnabled= */ false);
    } else {
      enableTrackSpecificOptions(/* isAudioEnabled= */ true, /* isVideoEnabled= */ true);
    }
  }

  @RequiresNonNull({
    "forceAudioTrackCheckbox",
    "audioMimeSpinner",
    "videoMimeSpinner",
    "resolutionHeightSpinner",
    "scaleSpinner",
    "rotateSpinner",
    "enableDebugPreviewCheckBox",
    "hdrModeSpinner",
    "selectAudioEffectsButton",
    "selectVideoEffectsButton"
  })
  private void enableTrackSpecificOptions(boolean isAudioEnabled, boolean isVideoEnabled) {
    forceAudioTrackCheckbox.setEnabled(isVideoEnabled);
    audioMimeSpinner.setEnabled(isAudioEnabled);
    videoMimeSpinner.setEnabled(isVideoEnabled);
    resolutionHeightSpinner.setEnabled(isVideoEnabled);
    scaleSpinner.setEnabled(isVideoEnabled);
    rotateSpinner.setEnabled(isVideoEnabled);
    enableDebugPreviewCheckBox.setEnabled(isVideoEnabled);
    hdrModeSpinner.setEnabled(isVideoEnabled);
    selectAudioEffectsButton.setEnabled(isAudioEnabled);
    selectVideoEffectsButton.setEnabled(isVideoEnabled);

    findViewById(R.id.audio_mime_text_view).setEnabled(isAudioEnabled);
    findViewById(R.id.video_mime_text_view).setEnabled(isVideoEnabled);
    findViewById(R.id.resolution_height_text_view).setEnabled(isVideoEnabled);
    findViewById(R.id.scale).setEnabled(isVideoEnabled);
    findViewById(R.id.rotate).setEnabled(isVideoEnabled);
    findViewById(R.id.hdr_mode).setEnabled(isVideoEnabled);
  }
}
