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
package com.google.android.exoplayer2.e2etest.util;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.testutil.Dumper;
import com.google.android.exoplayer2.util.Assertions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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

  private final ShadowMediaCodecConfig codecConfig;

  // TODO: Add support for subtitles too
  private final List<Metadata> metadatas;

  private PlaybackOutput(SimpleExoPlayer player, ShadowMediaCodecConfig codecConfig) {
    this.codecConfig = codecConfig;

    metadatas = Collections.synchronizedList(new ArrayList<>());
    // TODO: Consider passing playback position into MetadataOutput and TextOutput. Calling
    // player.getCurrentPosition() inside onMetadata/Cues will likely be non-deterministic
    // because renderer-thread != playback-thread.
    player.addMetadataOutput(metadatas::add);
  }

  /**
   * Create an instance that captures the metadata and text output from {@code player} and the audio
   * and video output via the {@link TeeCodec TeeCodecs} exposed by {@code mediaCodecConfig}.
   *
   * <p>Must be called <b>before</b> playback to ensure metadata and text output is captured
   * correctly.
   *
   * @param player The {@link SimpleExoPlayer} to capture metadata and text output from.
   * @param mediaCodecConfig The {@link ShadowMediaCodecConfig} to capture audio and video output
   *     from.
   * @return A new instance that can be used to dump the playback output.
   */
  public static PlaybackOutput register(
      SimpleExoPlayer player, ShadowMediaCodecConfig mediaCodecConfig) {
    return new PlaybackOutput(player, mediaCodecConfig);
  }

  @Override
  public void dump(Dumper dumper) {
    ImmutableMap<String, TeeCodec> codecs = codecConfig.getCodecs();
    ImmutableList<String> mimeTypes = ImmutableList.sortedCopyOf(codecs.keySet());
    for (String mimeType : mimeTypes) {
      dumper.add(Assertions.checkNotNull(codecs.get(mimeType)));
    }

    dumpMetadata(dumper);
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
        dumper.add("entry[" + j + "]", metadata.get(j).getClass().getSimpleName());
      }
      dumper.endBlock();
    }
    dumper.endBlock();
  }
}
