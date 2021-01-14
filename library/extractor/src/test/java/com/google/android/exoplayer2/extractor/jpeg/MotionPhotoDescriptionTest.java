/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.jpeg;

import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.metadata.mp4.MotionPhotoMetadata;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link MotionPhotoDescription}. */
@RunWith(AndroidJUnit4.class)
public final class MotionPhotoDescriptionTest {

  private static final long TEST_PRESENTATION_TIMESTAMP_US = 5L;
  private static final long TEST_MOTION_PHOTO_LENGTH_BYTES = 20;
  private static final long TEST_MOTION_PHOTO_VIDEO_LENGTH_BYTES = 7;
  private static final long TEST_MOTION_PHOTO_PHOTO_PADDING_BYTES = 1;

  @Test
  public void getMotionPhotoMetadata_withPrimaryAndSecondaryMediaItems() {
    MotionPhotoDescription motionPhotoDescription =
        new MotionPhotoDescription(
            TEST_PRESENTATION_TIMESTAMP_US,
            ImmutableList.of(
                new MotionPhotoDescription.ContainerItem(
                    MimeTypes.IMAGE_JPEG,
                    "Primary",
                    /* length= */ 0,
                    TEST_MOTION_PHOTO_PHOTO_PADDING_BYTES),
                new MotionPhotoDescription.ContainerItem(
                    MimeTypes.VIDEO_MP4,
                    "MotionPhoto",
                    TEST_MOTION_PHOTO_VIDEO_LENGTH_BYTES,
                    /* padding= */ 0)));

    @Nullable
    MotionPhotoMetadata metadata =
        motionPhotoDescription.getMotionPhotoMetadata(TEST_MOTION_PHOTO_LENGTH_BYTES);

    assertThat(metadata.photoStartPosition).isEqualTo(0);
    assertThat(metadata.photoSize)
        .isEqualTo(
            TEST_MOTION_PHOTO_LENGTH_BYTES
                - TEST_MOTION_PHOTO_VIDEO_LENGTH_BYTES
                - TEST_MOTION_PHOTO_PHOTO_PADDING_BYTES);
    assertThat(metadata.photoPresentationTimestampUs).isEqualTo(TEST_PRESENTATION_TIMESTAMP_US);
    assertThat(metadata.videoStartPosition)
        .isEqualTo(TEST_MOTION_PHOTO_LENGTH_BYTES - TEST_MOTION_PHOTO_VIDEO_LENGTH_BYTES);
    assertThat(metadata.videoSize).isEqualTo(TEST_MOTION_PHOTO_VIDEO_LENGTH_BYTES);
  }

  @Test
  public void
      getMotionPhotoMetadata_withPrimaryAndMultipleSecondaryMediaItems_returnsSecondMediaItemAsVideo() {
    MotionPhotoDescription motionPhotoDescription =
        new MotionPhotoDescription(
            TEST_PRESENTATION_TIMESTAMP_US,
            ImmutableList.of(
                new MotionPhotoDescription.ContainerItem(
                    MimeTypes.IMAGE_JPEG,
                    "Primary",
                    /* length= */ 0,
                    TEST_MOTION_PHOTO_PHOTO_PADDING_BYTES),
                new MotionPhotoDescription.ContainerItem(
                    MimeTypes.VIDEO_MP4,
                    "MotionPhoto",
                    TEST_MOTION_PHOTO_VIDEO_LENGTH_BYTES,
                    /* padding= */ 0),
                new MotionPhotoDescription.ContainerItem(
                    MimeTypes.VIDEO_MP4,
                    "MotionPhoto",
                    TEST_MOTION_PHOTO_VIDEO_LENGTH_BYTES,
                    /* padding= */ 0)));

    @Nullable
    MotionPhotoMetadata metadata =
        motionPhotoDescription.getMotionPhotoMetadata(TEST_MOTION_PHOTO_LENGTH_BYTES);

    assertThat(metadata.photoStartPosition).isEqualTo(0);
    assertThat(metadata.photoSize)
        .isEqualTo(
            TEST_MOTION_PHOTO_LENGTH_BYTES
                - TEST_MOTION_PHOTO_VIDEO_LENGTH_BYTES * 2
                - TEST_MOTION_PHOTO_PHOTO_PADDING_BYTES);
    assertThat(metadata.photoPresentationTimestampUs).isEqualTo(TEST_PRESENTATION_TIMESTAMP_US);
    assertThat(metadata.videoStartPosition)
        .isEqualTo(TEST_MOTION_PHOTO_LENGTH_BYTES - TEST_MOTION_PHOTO_VIDEO_LENGTH_BYTES * 2);
    assertThat(metadata.videoSize).isEqualTo(TEST_MOTION_PHOTO_VIDEO_LENGTH_BYTES);
  }

  @Test
  public void
      getMotionPhotoMetadata_withPrimaryAndSecondaryItemSharingData_returnsPrimaryItemAsPhotoAndVideo() {
    // Theoretical example of an HEIF file that has both an image and a video represented in the
    // same file, which looks like an MP4.
    MotionPhotoDescription motionPhotoDescription =
        new MotionPhotoDescription(
            TEST_PRESENTATION_TIMESTAMP_US,
            ImmutableList.of(
                new MotionPhotoDescription.ContainerItem(
                    MimeTypes.VIDEO_MP4,
                    "Primary",
                    /* length= */ 0,
                    TEST_MOTION_PHOTO_PHOTO_PADDING_BYTES),
                new MotionPhotoDescription.ContainerItem(
                    MimeTypes.VIDEO_MP4, "MotionPhoto", /* length= */ 0, /* padding= */ 0)));

    @Nullable
    MotionPhotoMetadata metadata =
        motionPhotoDescription.getMotionPhotoMetadata(TEST_MOTION_PHOTO_LENGTH_BYTES);

    assertThat(metadata.photoStartPosition).isEqualTo(0);
    assertThat(metadata.photoSize)
        .isEqualTo(TEST_MOTION_PHOTO_LENGTH_BYTES - TEST_MOTION_PHOTO_PHOTO_PADDING_BYTES);
    assertThat(metadata.photoPresentationTimestampUs).isEqualTo(TEST_PRESENTATION_TIMESTAMP_US);
    assertThat(metadata.videoStartPosition).isEqualTo(0);
    assertThat(metadata.videoSize)
        .isEqualTo(TEST_MOTION_PHOTO_LENGTH_BYTES - TEST_MOTION_PHOTO_PHOTO_PADDING_BYTES);
  }

  @Test
  public void getMotionPhotoMetadata_withOnlyPrimaryItem_returnsNull() {
    MotionPhotoDescription motionPhotoDescription =
        new MotionPhotoDescription(
            TEST_PRESENTATION_TIMESTAMP_US,
            ImmutableList.of(
                new MotionPhotoDescription.ContainerItem(
                    MimeTypes.VIDEO_MP4,
                    "Primary",
                    /* length= */ 0,
                    TEST_MOTION_PHOTO_PHOTO_PADDING_BYTES)));

    @Nullable
    MotionPhotoMetadata metadata =
        motionPhotoDescription.getMotionPhotoMetadata(TEST_MOTION_PHOTO_LENGTH_BYTES);

    assertThat(metadata).isNull();
  }
}
