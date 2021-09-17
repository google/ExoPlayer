/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.trackselection;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.MimeTypes;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link TrackSelectionParameters}. */
@RunWith(AndroidJUnit4.class)
public class TrackSelectionParametersTest {

  @Test
  public void defaultValue_withoutChange_isAsExpected() {
    TrackSelectionParameters parameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT;

    // Video
    assertThat(parameters.maxVideoWidth).isEqualTo(Integer.MAX_VALUE);
    assertThat(parameters.maxVideoHeight).isEqualTo(Integer.MAX_VALUE);
    assertThat(parameters.maxVideoFrameRate).isEqualTo(Integer.MAX_VALUE);
    assertThat(parameters.maxVideoBitrate).isEqualTo(Integer.MAX_VALUE);
    assertThat(parameters.minVideoWidth).isEqualTo(0);
    assertThat(parameters.minVideoHeight).isEqualTo(0);
    assertThat(parameters.minVideoFrameRate).isEqualTo(0);
    assertThat(parameters.minVideoBitrate).isEqualTo(0);
    assertThat(parameters.viewportWidth).isEqualTo(Integer.MAX_VALUE);
    assertThat(parameters.viewportHeight).isEqualTo(Integer.MAX_VALUE);
    assertThat(parameters.viewportOrientationMayChange).isTrue();
    assertThat(parameters.preferredVideoMimeTypes).isEmpty();
    // Audio
    assertThat(parameters.preferredAudioLanguages).isEmpty();
    assertThat(parameters.preferredAudioRoleFlags).isEqualTo(0);
    assertThat(parameters.maxAudioChannelCount).isEqualTo(Integer.MAX_VALUE);
    assertThat(parameters.maxAudioBitrate).isEqualTo(Integer.MAX_VALUE);
    // Text
    assertThat(parameters.preferredAudioMimeTypes).isEmpty();
    assertThat(parameters.preferredTextLanguages).isEmpty();
    assertThat(parameters.preferredTextRoleFlags).isEqualTo(0);
    assertThat(parameters.selectUndeterminedTextLanguage).isFalse();
    // General
    assertThat(parameters.forceLowestBitrate).isFalse();
    assertThat(parameters.forceHighestSupportedBitrate).isFalse();
  }

  @Test
  public void parametersSet_fromDefault_isAsExpected() {
    TrackSelectionParameters parameters =
        TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
            .buildUpon()
            // Video
            .setMaxVideoSize(/* maxVideoWidth= */ 0, /* maxVideoHeight= */ 1)
            .setMaxVideoFrameRate(2)
            .setMaxVideoBitrate(3)
            .setMinVideoSize(/* minVideoWidth= */ 4, /* minVideoHeight= */ 5)
            .setMinVideoFrameRate(6)
            .setMinVideoBitrate(7)
            .setViewportSize(
                /* viewportWidth= */ 8,
                /* viewportHeight= */ 9,
                /* viewportOrientationMayChange= */ true)
            .setPreferredVideoMimeTypes(MimeTypes.VIDEO_AV1, MimeTypes.VIDEO_H264)
            // Audio
            .setPreferredAudioLanguages("zh", "jp")
            .setPreferredAudioRoleFlags(C.ROLE_FLAG_COMMENTARY)
            .setMaxAudioChannelCount(10)
            .setMaxAudioBitrate(11)
            .setPreferredAudioMimeTypes(MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3)
            // Text
            .setPreferredTextLanguages("de", "en")
            .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION)
            .setSelectUndeterminedTextLanguage(true)
            // General
            .setForceLowestBitrate(false)
            .setForceHighestSupportedBitrate(true)
            .build();

    // Video
    assertThat(parameters.maxVideoWidth).isEqualTo(0);
    assertThat(parameters.maxVideoHeight).isEqualTo(1);
    assertThat(parameters.maxVideoFrameRate).isEqualTo(2);
    assertThat(parameters.maxVideoBitrate).isEqualTo(3);
    assertThat(parameters.minVideoWidth).isEqualTo(4);
    assertThat(parameters.minVideoHeight).isEqualTo(5);
    assertThat(parameters.minVideoFrameRate).isEqualTo(6);
    assertThat(parameters.minVideoBitrate).isEqualTo(7);
    assertThat(parameters.viewportWidth).isEqualTo(8);
    assertThat(parameters.viewportHeight).isEqualTo(9);
    assertThat(parameters.viewportOrientationMayChange).isTrue();
    assertThat(parameters.preferredVideoMimeTypes)
        .containsExactly(MimeTypes.VIDEO_AV1, MimeTypes.VIDEO_H264)
        .inOrder();
    // Audio
    assertThat(parameters.preferredAudioLanguages).containsExactly("zh", "jp").inOrder();
    assertThat(parameters.preferredAudioRoleFlags).isEqualTo(C.ROLE_FLAG_COMMENTARY);
    assertThat(parameters.maxAudioChannelCount).isEqualTo(10);
    assertThat(parameters.maxAudioBitrate).isEqualTo(11);
    assertThat(parameters.preferredAudioMimeTypes)
        .containsExactly(MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3)
        .inOrder();
    // Text
    assertThat(parameters.preferredTextLanguages).containsExactly("de", "en").inOrder();
    assertThat(parameters.preferredTextRoleFlags).isEqualTo(C.ROLE_FLAG_CAPTION);
    assertThat(parameters.selectUndeterminedTextLanguage).isTrue();
    // General
    assertThat(parameters.forceLowestBitrate).isFalse();
    assertThat(parameters.forceHighestSupportedBitrate).isTrue();
  }

  @Test
  public void setMaxVideoSizeSd_defaultBuilder_parametersVideoSizeAreSd() {
    TrackSelectionParameters parameters =
        new TrackSelectionParameters.Builder(getApplicationContext()).setMaxVideoSizeSd().build();

    assertThat(parameters.maxVideoWidth).isEqualTo(1279);
    assertThat(parameters.maxVideoHeight).isEqualTo(719);
  }

  @Test
  public void clearVideoSizeConstraints_withSdConstrains_clearConstrains() {
    TrackSelectionParameters parameters =
        new TrackSelectionParameters.Builder(getApplicationContext())
            .setMaxVideoSizeSd()
            .clearVideoSizeConstraints()
            .build();

    assertThat(parameters.maxVideoWidth).isEqualTo(Integer.MAX_VALUE);
    assertThat(parameters.maxVideoHeight).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  public void clearViewPortConstraints_withConstrains_clearConstrains() {
    TrackSelectionParameters parameters =
        new TrackSelectionParameters.Builder(getApplicationContext())
            .setViewportSize(
                /*viewportWidth=*/ 1,
                /*viewportHeight=*/ 2,
                /*viewportOrientationMayChange=*/ false)
            .clearViewportSizeConstraints()
            .build();

    assertThat(parameters.viewportWidth).isEqualTo(Integer.MAX_VALUE);
    assertThat(parameters.viewportHeight).isEqualTo(Integer.MAX_VALUE);
    assertThat(parameters.viewportOrientationMayChange).isTrue();
  }
}
