/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.robolectric;

import android.graphics.Bitmap;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.dvbsi.AppInfoTable;
import com.google.android.exoplayer2.metadata.emsg.EventMessage;
import com.google.android.exoplayer2.metadata.flac.PictureFrame;
import com.google.android.exoplayer2.metadata.flac.VorbisComment;
import com.google.android.exoplayer2.metadata.icy.IcyHeaders;
import com.google.android.exoplayer2.metadata.icy.IcyInfo;
import com.google.android.exoplayer2.metadata.id3.Id3Frame;
import com.google.android.exoplayer2.metadata.mp4.MdtaMetadataEntry;
import com.google.android.exoplayer2.metadata.mp4.MotionPhotoMetadata;
import com.google.android.exoplayer2.metadata.mp4.SlowMotionData;
import com.google.android.exoplayer2.metadata.mp4.SmtaMetadataEntry;
import com.google.android.exoplayer2.metadata.scte35.SpliceCommand;
import com.google.android.exoplayer2.testutil.CapturingRenderersFactory;
import com.google.android.exoplayer2.testutil.Dumper;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Class to capture output from a playback test.
 *
 * <p>Implements {@link Dumper.Dumpable} so the output can be easily dumped to a string for
 * comparison against previous test runs.
 */
public final class PlaybackOutput implements Dumper.Dumpable {

  private final CapturingRenderersFactory capturingRenderersFactory;

  private final List<Metadata> metadatas;
  private final List<List<Cue>> subtitles;

  private PlaybackOutput(
      SimpleExoPlayer player, CapturingRenderersFactory capturingRenderersFactory) {
    this.capturingRenderersFactory = capturingRenderersFactory;

    metadatas = Collections.synchronizedList(new ArrayList<>());
    subtitles = Collections.synchronizedList(new ArrayList<>());
    // TODO: Consider passing playback position into MetadataOutput and TextOutput. Calling
    // player.getCurrentPosition() inside onMetadata/Cues will likely be non-deterministic
    // because renderer-thread != playback-thread.
    player.addMetadataOutput(metadatas::add);
    // TODO(internal b/174661563): Output subtitle data when it's not flaky.
  }

  /**
   * Create an instance that captures the metadata and text output from {@code player} and the audio
   * and video output via {@code capturingRenderersFactory}.
   *
   * <p>Must be called <b>before</b> playback to ensure metadata and text output is captured
   * correctly.
   *
   * @param player The {@link SimpleExoPlayer} to capture metadata and text output from.
   * @param capturingRenderersFactory The {@link CapturingRenderersFactory} to capture audio and
   *     video output from.
   * @return A new instance that can be used to dump the playback output.
   */
  public static PlaybackOutput register(
      SimpleExoPlayer player, CapturingRenderersFactory capturingRenderersFactory) {
    return new PlaybackOutput(player, capturingRenderersFactory);
  }

  @Override
  public void dump(Dumper dumper) {
    capturingRenderersFactory.dump(dumper);

    dumpMetadata(dumper);
    dumpSubtitles(dumper);
  }

  private void dumpMetadata(Dumper dumper) {
    if (metadatas.isEmpty()) {
      return;
    }
    dumper.startBlock("MetadataOutput");
    for (int i = 0; i < metadatas.size(); i++) {
      dumper.startBlock("Metadata[" + i + "]");
      Metadata metadata = metadatas.get(i);
      for (int j = 0; j < metadata.length(); j++) {
        dumper.add("entry[" + j + "]", getEntryAsString(metadata.get(j)));
      }
      dumper.endBlock();
    }
    dumper.endBlock();
  }

  /**
   * Returns {@code entry.toString()} if we know the implementation overrides it, otherwise returns
   * the simple class name.
   */
  private static String getEntryAsString(Metadata.Entry entry) {
    if (entry instanceof EventMessage
        || entry instanceof PictureFrame
        || entry instanceof VorbisComment
        || entry instanceof Id3Frame
        || entry instanceof MdtaMetadataEntry
        || entry instanceof MotionPhotoMetadata
        || entry instanceof SlowMotionData
        || entry instanceof SmtaMetadataEntry
        || entry instanceof AppInfoTable
        || entry instanceof IcyHeaders
        || entry instanceof IcyInfo
        || entry instanceof SpliceCommand
        || "com.google.android.exoplayer2.hls.HlsTrackMetadataEntry"
            .equals(entry.getClass().getCanonicalName())) {
      return entry.toString();
    } else {
      return entry.getClass().getSimpleName();
    }
  }

  private void dumpSubtitles(Dumper dumper) {
    if (subtitles.isEmpty()) {
      return;
    }
    dumper.startBlock("TextOutput");
    for (int i = 0; i < subtitles.size(); i++) {
      dumper.startBlock("Subtitle[" + i + "]");
      List<Cue> subtitle = subtitles.get(i);
      if (subtitle.isEmpty()) {
        dumper.add("Cues", ImmutableList.of());
      }
      for (int j = 0; j < subtitle.size(); j++) {
        dumper.startBlock("Cue[" + j + "]");
        Cue cue = subtitle.get(j);
        dumpIfNotEqual(dumper, "text", cue.text, null);
        dumpIfNotEqual(dumper, "textAlignment", cue.textAlignment, null);
        dumpBitmap(dumper, cue.bitmap);
        dumpIfNotEqual(dumper, "line", cue.line, Cue.DIMEN_UNSET);
        dumpIfNotEqual(dumper, "lineType", cue.lineType, Cue.TYPE_UNSET);
        dumpIfNotEqual(dumper, "lineAnchor", cue.lineAnchor, Cue.TYPE_UNSET);
        dumpIfNotEqual(dumper, "position", cue.position, Cue.DIMEN_UNSET);
        dumpIfNotEqual(dumper, "positionAnchor", cue.positionAnchor, Cue.TYPE_UNSET);
        dumpIfNotEqual(dumper, "size", cue.size, Cue.DIMEN_UNSET);
        dumpIfNotEqual(dumper, "bitmapHeight", cue.bitmapHeight, Cue.DIMEN_UNSET);
        if (cue.windowColorSet) {
          dumper.add("cue.windowColor", cue.windowColor);
        }
        dumpIfNotEqual(dumper, "textSizeType", cue.textSizeType, Cue.TYPE_UNSET);
        dumpIfNotEqual(dumper, "textSize", cue.textSize, Cue.DIMEN_UNSET);
        dumpIfNotEqual(dumper, "verticalType", cue.verticalType, Cue.TYPE_UNSET);
        dumper.endBlock();
      }
      dumper.endBlock();
    }
    dumper.endBlock();
  }

  private static void dumpIfNotEqual(
      Dumper dumper, String field, @Nullable Object actual, @Nullable Object comparison) {
    if (!Util.areEqual(actual, comparison)) {
      dumper.add(field, actual);
    }
  }

  private static void dumpBitmap(Dumper dumper, @Nullable Bitmap bitmap) {
    if (bitmap == null) {
      return;
    }
    byte[] bytes = new byte[bitmap.getByteCount()];
    bitmap.copyPixelsToBuffer(ByteBuffer.wrap(bytes));
    dumper.add("bitmap", bytes);
  }
}
