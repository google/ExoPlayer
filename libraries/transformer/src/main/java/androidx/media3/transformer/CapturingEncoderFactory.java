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
package androidx.media3.transformer;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;

/** A forwarding {@link Codec.EncoderFactory} that captures details about the codecs created. */
/* package */ final class CapturingEncoderFactory implements Codec.EncoderFactory {
  private final Codec.EncoderFactory encoderFactory;

  @Nullable private String audioEncoderName;
  @Nullable private String videoEncoderName;

  public CapturingEncoderFactory(Codec.EncoderFactory encoderFactory) {
    this.encoderFactory = encoderFactory;
  }

  @Override
  public Codec createForAudioEncoding(Format format) throws ExportException {
    Codec audioEncoder = encoderFactory.createForAudioEncoding(format);
    audioEncoderName = audioEncoder.getName();
    return audioEncoder;
  }

  @Override
  public Codec createForVideoEncoding(Format format) throws ExportException {
    Codec videoEncoder = encoderFactory.createForVideoEncoding(format);
    videoEncoderName = videoEncoder.getName();
    return videoEncoder;
  }

  @Override
  public boolean audioNeedsEncoding() {
    return encoderFactory.audioNeedsEncoding();
  }

  @Override
  public boolean videoNeedsEncoding() {
    return encoderFactory.videoNeedsEncoding();
  }

  /**
   * Returns the name of the last audio {@linkplain Codec encoder} created, or {@code null} if none
   * were created.
   */
  @Nullable
  public String getAudioEncoderName() {
    return audioEncoderName;
  }

  /**
   * Returns the name of the last video {@linkplain Codec encoder} created, or {@code null} if none
   * were created.
   */
  @Nullable
  public String getVideoEncoderName() {
    return videoEncoderName;
  }
}
