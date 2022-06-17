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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * An {@link Activity} that sets the configuration to use for transforming and playing media, using
 * {@link TransformerActivity}.
 */
public final class ConfigurationActivity extends AppCompatActivity {
  public static final String SHOULD_REMOVE_AUDIO = "should_remove_audio";
  public static final String SHOULD_REMOVE_VIDEO = "should_remove_video";
  public static final String SHOULD_FLATTEN_FOR_SLOW_MOTION = "should_flatten_for_slow_motion";
  public static final String AUDIO_MIME_TYPE = "audio_mime_type";
  public static final String VIDEO_MIME_TYPE = "video_mime_type";
  public static final String RESOLUTION_HEIGHT = "resolution_height";
  public static final String SCALE_X = "scale_x";
  public static final String SCALE_Y = "scale_y";
  public static final String ROTATE_DEGREES = "rotate_degrees";
  public static final String TRIM_START_MS = "trim_start_ms";
  public static final String TRIM_END_MS = "trim_end_ms";
  public static final String ENABLE_FALLBACK = "enable_fallback";
  public static final String ENABLE_REQUEST_SDR_TONE_MAPPING = "enable_request_sdr_tone_mapping";
  public static final String ENABLE_HDR_EDITING = "enable_hdr_editing";
  public static final String DEMO_EFFECTS_SELECTIONS = "demo_effects_selections";
  public static final String PERIODIC_VIGNETTE_CENTER_X = "periodic_vignette_center_x";
  public static final String PERIODIC_VIGNETTE_CENTER_Y = "periodic_vignette_center_y";
  public static final String PERIODIC_VIGNETTE_INNER_RADIUS = "periodic_vignette_inner_radius";
  public static final String PERIODIC_VIGNETTE_OUTER_RADIUS = "periodic_vignette_outer_radius";
  private static final String[] INPUT_URIS = {
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
    "https://storage.googleapis.com/exoplayer-test-media-1/mp4/slow-motion/slowMotion_stopwatch_240fps_long.mp4",
    "https://storage.googleapis.com/exoplayer-test-media-1/gen/screens/dash-vod-single-segment/manifest-baseline.mpd",
    "https://storage.googleapis.com/exoplayer-test-media-1/mp4/samsung-hdr-hdr10.mp4",
  };
  private static final String[] URI_DESCRIPTIONS = { // same order as INPUT_URIS
    "720p H264 video and AAC audio",
    "1080p H265 video and AAC audio",
    "360p H264 video and AAC audio",
    "360p VP8 video and Vorbis audio",
    "4K H264 video and AAC audio (portrait, no B-frames)",
    "8k H265 video and AAC audio",
    "Short 1080p H265 video and AAC audio",
    "Long 180p H264 video and AAC audio",
    "H264 video and AAC audio (portrait, H > W, 0\u00B0)",
    "H264 video and AAC audio (portrait, H < W, 90\u00B0)",
    "SEF slow motion with 240 fps",
    "480p DASH (non-square pixels)",
    "HDR (HDR10) H265 video (encoding may fail)",
  };
  private static final String[] DEMO_EFFECTS = {
    "Dizzy crop",
    "Edge detector (Media Pipe)",
    "Periodic vignette",
    "3D spin",
    "Overlay logo & timer",
    "Zoom in start",
  };
  private static final int PERIODIC_VIGNETTE_INDEX = 2;
  private static final String SAME_AS_INPUT_OPTION = "same as input";
  private static final float HALF_DIAGONAL = 1f / (float) Math.sqrt(2);

  private @MonotonicNonNull Button selectFileButton;
  private @MonotonicNonNull TextView selectedFileTextView;
  private @MonotonicNonNull CheckBox removeAudioCheckbox;
  private @MonotonicNonNull CheckBox removeVideoCheckbox;
  private @MonotonicNonNull CheckBox flattenForSlowMotionCheckbox;
  private @MonotonicNonNull Spinner audioMimeSpinner;
  private @MonotonicNonNull Spinner videoMimeSpinner;
  private @MonotonicNonNull Spinner resolutionHeightSpinner;
  private @MonotonicNonNull Spinner scaleSpinner;
  private @MonotonicNonNull Spinner rotateSpinner;
  private @MonotonicNonNull CheckBox trimCheckBox;
  private @MonotonicNonNull CheckBox enableFallbackCheckBox;
  private @MonotonicNonNull CheckBox enableRequestSdrToneMappingCheckBox;
  private @MonotonicNonNull CheckBox enableHdrEditingCheckBox;
  private @MonotonicNonNull Button selectDemoEffectsButton;
  private boolean @MonotonicNonNull [] demoEffectsSelections;
  private int inputUriPosition;
  private long trimStartMs;
  private long trimEndMs;
  private float periodicVignetteCenterX;
  private float periodicVignetteCenterY;
  private float periodicVignetteInnerRadius;
  private float periodicVignetteOuterRadius;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.configuration_activity);

    findViewById(R.id.transform_button).setOnClickListener(this::startTransformation);

    selectFileButton = findViewById(R.id.select_file_button);
    selectFileButton.setOnClickListener(this::selectFile);

    selectedFileTextView = findViewById(R.id.selected_file_text_view);
    selectedFileTextView.setText(URI_DESCRIPTIONS[inputUriPosition]);

    removeAudioCheckbox = findViewById(R.id.remove_audio_checkbox);
    removeAudioCheckbox.setOnClickListener(this::onRemoveAudio);

    removeVideoCheckbox = findViewById(R.id.remove_video_checkbox);
    removeVideoCheckbox.setOnClickListener(this::onRemoveVideo);

    flattenForSlowMotionCheckbox = findViewById(R.id.flatten_for_slow_motion_checkbox);

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
    if (Util.SDK_INT >= 24) {
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
    enableRequestSdrToneMappingCheckBox = findViewById(R.id.request_sdr_tone_mapping_checkbox);
    enableRequestSdrToneMappingCheckBox.setEnabled(isRequestSdrToneMappingSupported());
    findViewById(R.id.request_sdr_tone_mapping).setEnabled(isRequestSdrToneMappingSupported());
    enableHdrEditingCheckBox = findViewById(R.id.hdr_editing_checkbox);

    demoEffectsSelections = new boolean[DEMO_EFFECTS.length];
    selectDemoEffectsButton = findViewById(R.id.select_demo_effects_button);
    selectDemoEffectsButton.setOnClickListener(this::selectDemoEffects);
  }

  @Override
  protected void onResume() {
    super.onResume();
    @Nullable Uri intentUri = getIntent().getData();
    if (intentUri != null) {
      checkNotNull(selectFileButton).setEnabled(false);
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
    "audioMimeSpinner",
    "videoMimeSpinner",
    "resolutionHeightSpinner",
    "scaleSpinner",
    "rotateSpinner",
    "trimCheckBox",
    "enableFallbackCheckBox",
    "enableRequestSdrToneMappingCheckBox",
    "enableHdrEditingCheckBox",
    "demoEffectsSelections"
  })
  private void startTransformation(View view) {
    Intent transformerIntent = new Intent(/* packageContext= */ this, TransformerActivity.class);
    Bundle bundle = new Bundle();
    bundle.putBoolean(SHOULD_REMOVE_AUDIO, removeAudioCheckbox.isChecked());
    bundle.putBoolean(SHOULD_REMOVE_VIDEO, removeVideoCheckbox.isChecked());
    bundle.putBoolean(SHOULD_FLATTEN_FOR_SLOW_MOTION, flattenForSlowMotionCheckbox.isChecked());
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
    bundle.putBoolean(
        ENABLE_REQUEST_SDR_TONE_MAPPING, enableRequestSdrToneMappingCheckBox.isChecked());
    bundle.putBoolean(ENABLE_HDR_EDITING, enableHdrEditingCheckBox.isChecked());
    bundle.putBooleanArray(DEMO_EFFECTS_SELECTIONS, demoEffectsSelections);
    bundle.putFloat(PERIODIC_VIGNETTE_CENTER_X, periodicVignetteCenterX);
    bundle.putFloat(PERIODIC_VIGNETTE_CENTER_Y, periodicVignetteCenterY);
    bundle.putFloat(PERIODIC_VIGNETTE_INNER_RADIUS, periodicVignetteInnerRadius);
    bundle.putFloat(PERIODIC_VIGNETTE_OUTER_RADIUS, periodicVignetteOuterRadius);
    transformerIntent.putExtras(bundle);

    @Nullable Uri intentUri = getIntent().getData();
    transformerIntent.setData(
        intentUri != null ? intentUri : Uri.parse(INPUT_URIS[inputUriPosition]));

    startActivity(transformerIntent);
  }

  private void selectFile(View view) {
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.select_file_title)
        .setSingleChoiceItems(URI_DESCRIPTIONS, inputUriPosition, this::selectFileInDialog)
        .setPositiveButton(android.R.string.ok, /* listener= */ null)
        .create()
        .show();
  }

  private void selectDemoEffects(View view) {
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.select_demo_effects)
        .setMultiChoiceItems(
            DEMO_EFFECTS, checkNotNull(demoEffectsSelections), this::selectDemoEffect)
        .setPositiveButton(android.R.string.ok, /* listener= */ null)
        .create()
        .show();
  }

  private void selectTrimBounds(View view, boolean isChecked) {
    if (!isChecked) {
      return;
    }
    View dialogView = getLayoutInflater().inflate(R.layout.trim_options, /* root= */ null);
    RangeSlider radiusRangeSlider =
        checkNotNull(dialogView.findViewById(R.id.trim_bounds_range_slider));
    radiusRangeSlider.setValues(0f, 60f); // seconds
    new AlertDialog.Builder(/* context= */ this)
        .setView(dialogView)
        .setPositiveButton(
            android.R.string.ok,
            (DialogInterface dialogInterface, int i) -> {
              List<Float> radiusRange = radiusRangeSlider.getValues();
              trimStartMs = 1000 * radiusRange.get(0).longValue();
              trimEndMs = 1000 * radiusRange.get(1).longValue();
            })
        .create()
        .show();
  }

  @RequiresNonNull("selectedFileTextView")
  private void selectFileInDialog(DialogInterface dialog, int which) {
    inputUriPosition = which;
    selectedFileTextView.setText(URI_DESCRIPTIONS[inputUriPosition]);
  }

  @RequiresNonNull("demoEffectsSelections")
  private void selectDemoEffect(DialogInterface dialog, int which, boolean isChecked) {
    demoEffectsSelections[which] = isChecked;
    if (!isChecked || which != PERIODIC_VIGNETTE_INDEX) {
      return;
    }

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

  @RequiresNonNull({
    "removeVideoCheckbox",
    "audioMimeSpinner",
    "videoMimeSpinner",
    "resolutionHeightSpinner",
    "scaleSpinner",
    "rotateSpinner",
    "enableRequestSdrToneMappingCheckBox",
    "enableHdrEditingCheckBox",
    "selectDemoEffectsButton"
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
    "audioMimeSpinner",
    "videoMimeSpinner",
    "resolutionHeightSpinner",
    "scaleSpinner",
    "rotateSpinner",
    "enableRequestSdrToneMappingCheckBox",
    "enableHdrEditingCheckBox",
    "selectDemoEffectsButton"
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
    "audioMimeSpinner",
    "videoMimeSpinner",
    "resolutionHeightSpinner",
    "scaleSpinner",
    "rotateSpinner",
    "enableRequestSdrToneMappingCheckBox",
    "enableHdrEditingCheckBox",
    "selectDemoEffectsButton"
  })
  private void enableTrackSpecificOptions(boolean isAudioEnabled, boolean isVideoEnabled) {
    audioMimeSpinner.setEnabled(isAudioEnabled);
    videoMimeSpinner.setEnabled(isVideoEnabled);
    resolutionHeightSpinner.setEnabled(isVideoEnabled);
    scaleSpinner.setEnabled(isVideoEnabled);
    rotateSpinner.setEnabled(isVideoEnabled);
    enableRequestSdrToneMappingCheckBox.setEnabled(
        isRequestSdrToneMappingSupported() && isVideoEnabled);
    enableHdrEditingCheckBox.setEnabled(isVideoEnabled);
    selectDemoEffectsButton.setEnabled(isVideoEnabled);

    findViewById(R.id.audio_mime_text_view).setEnabled(isAudioEnabled);
    findViewById(R.id.video_mime_text_view).setEnabled(isVideoEnabled);
    findViewById(R.id.resolution_height_text_view).setEnabled(isVideoEnabled);
    findViewById(R.id.scale).setEnabled(isVideoEnabled);
    findViewById(R.id.rotate).setEnabled(isVideoEnabled);
    findViewById(R.id.request_sdr_tone_mapping)
        .setEnabled(isRequestSdrToneMappingSupported() && isVideoEnabled);
    findViewById(R.id.hdr_editing).setEnabled(isVideoEnabled);
  }

  private static boolean isRequestSdrToneMappingSupported() {
    return Util.SDK_INT >= 31;
  }
}
