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
import com.google.android.exoplayer2.util.MimeTypes;
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
  public static final String TRANSLATE_X = "translate_x";
  public static final String TRANSLATE_Y = "translate_y";
  public static final String SCALE_X = "scale_x";
  public static final String SCALE_Y = "scale_y";
  public static final String ROTATE_DEGREES = "rotate_degrees";
  public static final String ENABLE_HDR_EDITING = "enable_hdr_editing";
  private static final String[] INPUT_URIS = {
    "https://html5demos.com/assets/dizzy.mp4",
    "https://storage.googleapis.com/exoplayer-test-media-0/android-block-1080-hevc.mp4",
    "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4",
    "https://html5demos.com/assets/dizzy.webm",
  };
  private static final String[] URI_DESCRIPTIONS = { // same order as INPUT_URIS
    "MP4 with H264 video and AAC audio",
    "MP4 with H265 video and AAC audio",
    "Long MP4 with H264 video and AAC audio",
    "WebM with VP8 video and Vorbis audio",
  };
  private static final String SAME_AS_INPUT_OPTION = "same as input";

  private @MonotonicNonNull Button chooseFileButton;
  private @MonotonicNonNull TextView chosenFileTextView;
  private @MonotonicNonNull CheckBox removeAudioCheckbox;
  private @MonotonicNonNull CheckBox removeVideoCheckbox;
  private @MonotonicNonNull CheckBox flattenForSlowMotionCheckbox;
  private @MonotonicNonNull Spinner audioMimeSpinner;
  private @MonotonicNonNull Spinner videoMimeSpinner;
  private @MonotonicNonNull Spinner resolutionHeightSpinner;
  private @MonotonicNonNull Spinner translateSpinner;
  private @MonotonicNonNull Spinner scaleSpinner;
  private @MonotonicNonNull Spinner rotateSpinner;
  private @MonotonicNonNull CheckBox enableHdrEditingCheckBox;
  private int inputUriPosition;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.configuration_activity);

    findViewById(R.id.transform_button).setOnClickListener(this::startTransformation);

    chooseFileButton = findViewById(R.id.choose_file_button);
    chooseFileButton.setOnClickListener(this::chooseFile);

    chosenFileTextView = findViewById(R.id.chosen_file_text_view);
    chosenFileTextView.setText(URI_DESCRIPTIONS[inputUriPosition]);

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
        SAME_AS_INPUT_OPTION,
        MimeTypes.VIDEO_H263,
        MimeTypes.VIDEO_H264,
        MimeTypes.VIDEO_H265,
        MimeTypes.VIDEO_MP4V);

    ArrayAdapter<String> resolutionHeightAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    resolutionHeightAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    resolutionHeightSpinner = findViewById(R.id.resolution_height_spinner);
    resolutionHeightSpinner.setAdapter(resolutionHeightAdapter);
    resolutionHeightAdapter.addAll(
        SAME_AS_INPUT_OPTION, "144", "240", "360", "480", "720", "1080", "1440", "2160");

    ArrayAdapter<String> translateAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    translateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    translateSpinner = findViewById(R.id.translate_spinner);
    translateSpinner.setAdapter(translateAdapter);
    translateAdapter.addAll(
        SAME_AS_INPUT_OPTION, "-.1, -.1", "0, 0", ".5, 0", "0, .5", "1, 1", "1.9, 0", "0, 1.9");

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
    rotateAdapter.addAll(SAME_AS_INPUT_OPTION, "0", "10", "45", "90", "180");

    enableHdrEditingCheckBox = findViewById(R.id.hdr_editing_checkbox);
  }

  @Override
  protected void onResume() {
    super.onResume();
    @Nullable Uri intentUri = getIntent().getData();
    if (intentUri != null) {
      checkNotNull(chooseFileButton).setEnabled(false);
      checkNotNull(chosenFileTextView).setText(intentUri.toString());
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
    "translateSpinner",
    "scaleSpinner",
    "rotateSpinner",
    "enableHdrEditingCheckBox"
  })
  private void startTransformation(View view) {
    Intent transformerIntent = new Intent(this, TransformerActivity.class);
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
    String selectedTranslate = String.valueOf(translateSpinner.getSelectedItem());
    if (!SAME_AS_INPUT_OPTION.equals(selectedTranslate)) {
      List<String> translateXY = Arrays.asList(selectedTranslate.split(", "));
      checkState(translateXY.size() == 2);
      bundle.putFloat(TRANSLATE_X, Float.parseFloat(translateXY.get(0)));
      bundle.putFloat(TRANSLATE_Y, Float.parseFloat(translateXY.get(1)));
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
    bundle.putBoolean(ENABLE_HDR_EDITING, enableHdrEditingCheckBox.isChecked());
    transformerIntent.putExtras(bundle);

    @Nullable Uri intentUri = getIntent().getData();
    transformerIntent.setData(
        intentUri != null ? intentUri : Uri.parse(INPUT_URIS[inputUriPosition]));

    startActivity(transformerIntent);
  }

  private void chooseFile(View view) {
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.choose_file_title)
        .setSingleChoiceItems(URI_DESCRIPTIONS, inputUriPosition, this::selectFileInDialog)
        .setPositiveButton(android.R.string.ok, /* listener= */ null)
        .create()
        .show();
  }

  @RequiresNonNull("chosenFileTextView")
  private void selectFileInDialog(DialogInterface dialog, int which) {
    inputUriPosition = which;
    chosenFileTextView.setText(URI_DESCRIPTIONS[inputUriPosition]);
  }

  @RequiresNonNull({
    "removeVideoCheckbox",
    "audioMimeSpinner",
    "videoMimeSpinner",
    "resolutionHeightSpinner",
    "translateSpinner",
    "scaleSpinner",
    "rotateSpinner",
    "enableHdrEditingCheckBox"
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
    "translateSpinner",
    "scaleSpinner",
    "rotateSpinner",
    "enableHdrEditingCheckBox"
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
    "translateSpinner",
    "scaleSpinner",
    "rotateSpinner",
    "enableHdrEditingCheckBox"
  })
  private void enableTrackSpecificOptions(boolean isAudioEnabled, boolean isVideoEnabled) {
    audioMimeSpinner.setEnabled(isAudioEnabled);
    videoMimeSpinner.setEnabled(isVideoEnabled);
    resolutionHeightSpinner.setEnabled(isVideoEnabled);
    translateSpinner.setEnabled(isVideoEnabled);
    scaleSpinner.setEnabled(isVideoEnabled);
    rotateSpinner.setEnabled(isVideoEnabled);
    enableHdrEditingCheckBox.setEnabled(isVideoEnabled);

    findViewById(R.id.audio_mime_text_view).setEnabled(isAudioEnabled);
    findViewById(R.id.video_mime_text_view).setEnabled(isVideoEnabled);
    findViewById(R.id.resolution_height_text_view).setEnabled(isVideoEnabled);
    findViewById(R.id.translate).setEnabled(isVideoEnabled);
    findViewById(R.id.scale).setEnabled(isVideoEnabled);
    findViewById(R.id.rotate).setEnabled(isVideoEnabled);
    findViewById(R.id.hdr_editing).setEnabled(isVideoEnabled);
  }
}
