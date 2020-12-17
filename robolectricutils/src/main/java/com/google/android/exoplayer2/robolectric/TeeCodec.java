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

import android.media.MediaCodec;
import com.google.android.exoplayer2.testutil.CapturingRenderersFactory;
import com.google.android.exoplayer2.testutil.Dumper;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.robolectric.shadows.ShadowMediaCodec;

/**
 * @deprecated Use {@link CapturingRenderersFactory} to access {@link MediaCodec} interactions
 *     instead.
 */
@Deprecated
public final class TeeCodec implements ShadowMediaCodec.CodecConfig.Codec, Dumper.Dumpable {

  private final String mimeType;
  private final List<byte[]> receivedBuffers;

  public TeeCodec(String mimeType) {
    this.mimeType = mimeType;
    this.receivedBuffers = Collections.synchronizedList(new ArrayList<>());
  }

  @Override
  public void process(ByteBuffer in, ByteBuffer out) {
    byte[] bytes = new byte[in.remaining()];
    in.get(bytes);
    receivedBuffers.add(bytes);

    if (!MimeTypes.isAudio(mimeType)) {
      // Don't output audio bytes, because ShadowAudioTrack doesn't advance the playback position so
      // playback never completes.
      // TODO: Update ShadowAudioTrack to advance the playback position in a realistic way.
      out.put(bytes);
    }
  }

  @Override
  public void dump(Dumper dumper) {
    if (receivedBuffers.isEmpty()) {
      return;
    }
    dumper.startBlock("MediaCodec (" + mimeType + ")");
    dumper.add("buffers.length", receivedBuffers.size());
    for (int i = 0; i < receivedBuffers.size(); i++) {
      dumper.add("buffers[" + i + "]", receivedBuffers.get(i));
    }

    dumper.endBlock();
  }

  /**
   * Return the buffers received by this codec.
   *
   * <p>The list is sorted in the order the buffers were passed to {@link #process(ByteBuffer,
   * ByteBuffer)}.
   */
  public ImmutableList<byte[]> getReceivedBuffers() {
    return ImmutableList.copyOf(receivedBuffers);
  }
}
