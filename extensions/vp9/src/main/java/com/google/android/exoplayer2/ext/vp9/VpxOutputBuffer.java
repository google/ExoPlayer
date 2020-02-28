/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.vp9;

import com.google.android.exoplayer2.video.VideoDecoderOutputBuffer;

// TODO(b/139174707): Delete this class once binaries in WVVp9OpusPlaybackTest are updated to depend
// on VideoDecoderOutputBuffer. Also mark VideoDecoderOutputBuffer as final.
/**
 * Video output buffer, populated by {@link VpxDecoder}.
 *
 * @deprecated Use {@link VideoDecoderOutputBuffer} instead.
 */
@Deprecated
public final class VpxOutputBuffer extends VideoDecoderOutputBuffer {

  /**
   * Creates VpxOutputBuffer.
   *
   * @param owner Buffer owner.
   */
  public VpxOutputBuffer(VideoDecoderOutputBuffer.Owner owner) {
    super(owner);
  }
}
