/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.content.Context;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Utility methods used in transmux-transcode exports. */
/* package */ final class TransmuxTranscodeHelper {

  /** Provides metadata required to resume an export. */
  public static final class ResumeMetadata {
    /** The last sync sample timestamp of the previous output file. */
    public final long lastSyncSampleTimestampUs;

    /**
     * A {@link List} containing the index of the first {@link EditedMediaItem} to process and its
     * {@linkplain androidx.media3.common.MediaItem.ClippingConfiguration#startPositionMs additional
     * offset} for each {@link EditedMediaItemSequence} in a {@link Composition}.
     */
    public final ImmutableList<Pair<Integer, Long>> firstMediaItemIndexAndOffsetInfo;

    /** The video {@link Format} or {@code null} if there is no video track. */
    @Nullable public final Format videoFormat;

    public ResumeMetadata(
        long lastSyncSampleTimestampUs,
        ImmutableList<Pair<Integer, Long>> firstMediaItemIndexAndOffsetInfo,
        @Nullable Format videoFormat) {
      this.lastSyncSampleTimestampUs = lastSyncSampleTimestampUs;
      this.firstMediaItemIndexAndOffsetInfo = firstMediaItemIndexAndOffsetInfo;
      this.videoFormat = videoFormat;
    }
  }

  public static ListenableFuture<Mp4Info> getMp4Info(
      Context context, String filePath, long timeUs) {
    SettableFuture<Mp4Info> mp4InfoSettableFuture = SettableFuture.create();
    new Thread("TransmuxTranscodeHelper:Mp4Info") {
      @Override
      public void run() {
        try {
          mp4InfoSettableFuture.set(Mp4Info.create(context, filePath, timeUs));
        } catch (Exception ex) {
          mp4InfoSettableFuture.setException(ex);
        }
      }
    }.start();
    return mp4InfoSettableFuture;
  }

  public static Composition buildUponCompositionForTrimOptimization(
      Composition oldComposition,
      long startTimeUs,
      long endTimeUs,
      long mediaDurationUs,
      boolean startsAtKeyFrame,
      boolean clearVideoEffects) {
    EditedMediaItem firstEditedMediaItem = oldComposition.sequences.get(0).editedMediaItems.get(0);

    MediaItem.ClippingConfiguration clippingConfiguration =
        new MediaItem.ClippingConfiguration.Builder()
            .setStartPositionUs(startTimeUs)
            .setEndPositionUs(endTimeUs)
            .setStartsAtKeyFrame(startsAtKeyFrame)
            .build();

    MediaItem mediaItem =
        firstEditedMediaItem
            .mediaItem
            .buildUpon()
            .setClippingConfiguration(clippingConfiguration)
            .build();
    Effects effects =
        clearVideoEffects
            ? new Effects(
                firstEditedMediaItem.effects.audioProcessors,
                /* videoEffects= */ ImmutableList.of())
            : firstEditedMediaItem.effects;
    EditedMediaItem editedMediaItem =
        firstEditedMediaItem
            .buildUpon()
            .setMediaItem(mediaItem)
            .setDurationUs(mediaDurationUs)
            .setEffects(effects)
            .build();

    return oldComposition
        .buildUpon()
        .setSequences(ImmutableList.of(new EditedMediaItemSequence(editedMediaItem)))
        .build();
  }

  private TransmuxTranscodeHelper() {}

  /**
   * Returns a video only {@link Composition} from the given {@code filePath} and {@code
   * clippingEndPositionUs}.
   */
  public static Composition createVideoOnlyComposition(
      String filePath, long clippingEndPositionUs) {
    MediaItem.ClippingConfiguration clippingConfiguration =
        new MediaItem.ClippingConfiguration.Builder()
            .setEndPositionMs(Util.usToMs(clippingEndPositionUs))
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(
                new MediaItem.Builder()
                    .setUri(filePath)
                    .setClippingConfiguration(clippingConfiguration)
                    .build())
            .setRemoveAudio(true)
            .build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem));
    return new Composition.Builder(ImmutableList.of(sequence)).build();
  }

  /**
   * Returns a {@link Composition} for transcoding audio and transmuxing video.
   *
   * @param composition The {@link Composition} to transcode audio from.
   * @param videoFilePath The video only file path to transmux video.
   * @return The {@link Composition}.
   */
  public static Composition createAudioTranscodeAndVideoTransmuxComposition(
      Composition composition, String videoFilePath) {
    Composition audioOnlyComposition =
        TransmuxTranscodeHelper.buildUponComposition(
            checkNotNull(composition),
            /* removeAudio= */ false,
            /* removeVideo= */ true,
            /* resumeMetadata= */ null);

    Composition.Builder compositionBuilder = audioOnlyComposition.buildUpon();
    List<EditedMediaItemSequence> sequences = new ArrayList<>(audioOnlyComposition.sequences);

    // Video stream sequence.
    EditedMediaItem videoOnlyEditedMediaItem =
        new EditedMediaItem.Builder(new MediaItem.Builder().setUri(videoFilePath).build()).build();
    EditedMediaItemSequence videoOnlySequence =
        new EditedMediaItemSequence(ImmutableList.of(videoOnlyEditedMediaItem));

    sequences.add(videoOnlySequence);
    compositionBuilder.setSequences(sequences);
    compositionBuilder.setTransmuxVideo(true);
    return compositionBuilder.build();
  }

  /**
   * Builds a new {@link Composition} from a given {@link Composition}.
   *
   * <p>The new {@link Composition} will be built based on {@link
   * ResumeMetadata#firstMediaItemIndexAndOffsetInfo}.
   */
  public static Composition buildUponComposition(
      Composition composition,
      boolean removeAudio,
      boolean removeVideo,
      @Nullable ResumeMetadata resumeMetadata) {
    Composition.Builder compositionBuilder = composition.buildUpon();
    ImmutableList<EditedMediaItemSequence> editedMediaItemSequenceList = composition.sequences;
    List<EditedMediaItemSequence> newEditedMediaItemSequenceList = new ArrayList<>();
    @Nullable
    List<Pair<Integer, Long>> firstMediaItemIndexAndOffsetInfo =
        resumeMetadata != null ? resumeMetadata.firstMediaItemIndexAndOffsetInfo : null;

    for (int sequenceIndex = 0;
        sequenceIndex < editedMediaItemSequenceList.size();
        sequenceIndex++) {
      EditedMediaItemSequence currentEditedMediaItemSequence =
          editedMediaItemSequenceList.get(sequenceIndex);
      ImmutableList<EditedMediaItem> editedMediaItemList =
          currentEditedMediaItemSequence.editedMediaItems;
      List<EditedMediaItem> newEditedMediaItemList = new ArrayList<>();

      int firstMediaItemIndex = 0;
      long firstMediaItemOffsetUs = 0L;

      if (firstMediaItemIndexAndOffsetInfo != null) {
        firstMediaItemIndex = firstMediaItemIndexAndOffsetInfo.get(sequenceIndex).first;
        firstMediaItemOffsetUs = firstMediaItemIndexAndOffsetInfo.get(sequenceIndex).second;
      }

      for (int mediaItemIndex = firstMediaItemIndex;
          mediaItemIndex < editedMediaItemList.size();
          mediaItemIndex++) {
        EditedMediaItem currentEditedMediaItem = editedMediaItemList.get(mediaItemIndex);
        EditedMediaItem.Builder newEditedMediaItemBuilder = currentEditedMediaItem.buildUpon();

        if (mediaItemIndex == firstMediaItemIndex) {
          MediaItem.ClippingConfiguration clippingConfiguration =
              currentEditedMediaItem
                  .mediaItem
                  .clippingConfiguration
                  .buildUpon()
                  .setStartPositionMs(
                      currentEditedMediaItem.mediaItem.clippingConfiguration.startPositionMs
                          + Util.usToMs(firstMediaItemOffsetUs))
                  .build();
          newEditedMediaItemBuilder.setMediaItem(
              currentEditedMediaItem
                  .mediaItem
                  .buildUpon()
                  .setClippingConfiguration(clippingConfiguration)
                  .build());
        }

        if (removeAudio) {
          newEditedMediaItemBuilder.setRemoveAudio(true);
        }
        if (removeVideo) {
          newEditedMediaItemBuilder.setRemoveVideo(true);
        }

        newEditedMediaItemList.add(newEditedMediaItemBuilder.build());
      }

      newEditedMediaItemSequenceList.add(
          new EditedMediaItemSequence(
              newEditedMediaItemList, currentEditedMediaItemSequence.isLooping));
    }
    compositionBuilder.setSequences(newEditedMediaItemSequenceList);
    return compositionBuilder.build();
  }

  /**
   * Returns a {@link ListenableFuture} that provides {@link ResumeMetadata} for given input.
   *
   * @param context The {@link Context}.
   * @param filePath The old file path to resume the export from.
   * @param composition The {@link Composition} to export.
   * @return A {@link ListenableFuture} that provides {@link ResumeMetadata}.
   */
  public static ListenableFuture<ResumeMetadata> getResumeMetadataAsync(
      Context context, String filePath, Composition composition) {
    SettableFuture<ResumeMetadata> resumeMetadataSettableFuture = SettableFuture.create();
    new Thread("TransmuxTranscodeHelper:ResumeMetadata") {
      @Override
      public void run() {
        try {
          if (resumeMetadataSettableFuture.isCancelled()) {
            return;
          }
          Mp4Info mp4Info = Mp4Info.create(context, filePath);
          long lastSyncSampleTimestampUs = mp4Info.lastSyncSampleTimestampUs;

          ImmutableList.Builder<Pair<Integer, Long>> firstMediaItemIndexAndOffsetInfoBuilder =
              new ImmutableList.Builder<>();
          if (lastSyncSampleTimestampUs != C.TIME_UNSET) {
            for (int compositionSequenceIndex = 0;
                compositionSequenceIndex < composition.sequences.size();
                compositionSequenceIndex++) {
              ImmutableList<EditedMediaItem> editedMediaItemList =
                  composition.sequences.get(compositionSequenceIndex).editedMediaItems;
              long remainingDurationUsToSkip = lastSyncSampleTimestampUs;
              int editedMediaItemIndex = 0;
              long mediaItemOffset = 0L;
              while (editedMediaItemIndex < editedMediaItemList.size()
                  && remainingDurationUsToSkip > 0) {
                long mediaItemDuration =
                    getMediaItemDurationUs(
                        context, editedMediaItemList.get(editedMediaItemIndex).mediaItem);
                if (mediaItemDuration > remainingDurationUsToSkip) {
                  mediaItemOffset = remainingDurationUsToSkip;
                  break;
                }

                remainingDurationUsToSkip -= mediaItemDuration;
                editedMediaItemIndex++;
              }
              firstMediaItemIndexAndOffsetInfoBuilder.add(
                  new Pair<>(editedMediaItemIndex, mediaItemOffset));
            }
          }
          resumeMetadataSettableFuture.set(
              new ResumeMetadata(
                  lastSyncSampleTimestampUs,
                  firstMediaItemIndexAndOffsetInfoBuilder.build(),
                  mp4Info.videoFormat));
        } catch (Exception ex) {
          resumeMetadataSettableFuture.setException(ex);
        }
      }
    }.start();

    return resumeMetadataSettableFuture;
  }

  /** Copies {@link File} content from source to destination asynchronously. */
  public static ListenableFuture<Void> copyFileAsync(File source, File destination) {
    SettableFuture<Void> copyFileSettableFuture = SettableFuture.create();
    new Thread("TransmuxTranscodeHelper:CopyFile") {
      @Override
      public void run() {
        if (copyFileSettableFuture.isCancelled()) {
          return;
        }
        InputStream input = null;
        OutputStream output = null;
        try {
          input = new FileInputStream(source);
          output = new FileOutputStream(destination);
          ByteStreams.copy(input, output);
          copyFileSettableFuture.set(null);
        } catch (Exception ex) {
          copyFileSettableFuture.setException(ex);
        } finally {
          try {
            if (input != null) {
              input.close();
            }
            if (output != null) {
              output.close();
            }
          } catch (IOException exception) {
            // If the file copy was successful then this exception can be ignored and if there
            // was some other error during copy operation then that exception has already been
            // propagated in the catch block.
          }
        }
      }
    }.start();
    return copyFileSettableFuture;
  }

  private static long getMediaItemDurationUs(Context context, MediaItem mediaItem)
      throws IOException {
    String filePath = checkNotNull(mediaItem.localConfiguration).uri.toString();
    long startUs = Util.msToUs(mediaItem.clippingConfiguration.startPositionMs);
    long endUs;
    if (mediaItem.clippingConfiguration.endPositionMs != C.TIME_END_OF_SOURCE) {
      endUs = Util.msToUs(mediaItem.clippingConfiguration.endPositionMs);
    } else {
      endUs = Mp4Info.create(context, filePath).durationUs;
    }

    return endUs - startUs;
  }
}
