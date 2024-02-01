/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.exoplayer2.testutil;

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MetadataRetriever;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.video.ColorInfo;
import java.util.concurrent.ExecutionException;

/** Utilities for accessing details of media files. */
public final class FileUtil {

  /**
   * Asserts that the file has a certain color transfer.
   *
   * @param context The current context.
   * @param filePath The path of the input file.
   * @param expectedColorTransfer The expected {@link C.ColorTransfer} for the input file.
   */
  public static void assertFileHasColorTransfer(
      Context context, @Nullable String filePath, @C.ColorTransfer int expectedColorTransfer) {
    Format videoTrackFormat = retrieveTrackFormat(context, filePath, C.TRACK_TYPE_VIDEO);
    @Nullable ColorInfo colorInfo = videoTrackFormat.colorInfo;
    @C.ColorTransfer
    int actualColorTransfer =
        colorInfo == null || colorInfo.colorTransfer == Format.NO_VALUE
            ? C.COLOR_TRANSFER_SDR
            : colorInfo.colorTransfer;
    assertThat(actualColorTransfer).isEqualTo(expectedColorTransfer);
  }

  /** Returns {@linkplain Format track format} from the media file. */
  public static Format retrieveTrackFormat(
      Context context, @Nullable String filePath, @C.TrackType int trackType) {
    TrackGroupArray trackGroupArray;
    try {
      trackGroupArray =
          MetadataRetriever.retrieveMetadata(context, MediaItem.fromUri("file://" + filePath))
              .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (ExecutionException e) {
      throw new IllegalStateException(e);
    }

    for (int i = 0; i < trackGroupArray.length; i++) {
      TrackGroup trackGroup = trackGroupArray.get(i);
      if (trackGroup.type == trackType) {
        checkState(trackGroup.length == 1);
        return trackGroup.getFormat(0);
      }
    }
    throw new IllegalStateException("Couldn't find track");
  }

  private FileUtil() {}
}
