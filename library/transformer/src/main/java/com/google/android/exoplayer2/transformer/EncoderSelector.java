/*
 * Copyright 2022 The Android Open Source Project
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

package com.google.android.exoplayer2.transformer;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;

/** Selector of {@link MediaCodec} encoder instances. */
public interface EncoderSelector {

  /**
   * Default implementation of {@link EncoderSelector}, which returns the preferred encoders for the
   * given {@link MimeTypes MIME type}.
   */
  EncoderSelector DEFAULT = EncoderUtil::getSupportedEncoders;

  /**
   * Returns a list of encoders that can encode media in the specified {@code mimeType}, in priority
   * order.
   *
   * @param mimeType The {@linkplain MimeTypes MIME type} for which an encoder is required.
   * @return An immutable list of {@linkplain MediaCodecInfo encoders} that support the {@code
   *     mimeType}. The list may be empty.
   */
  ImmutableList<MediaCodecInfo> selectEncoderInfos(String mimeType);
}
