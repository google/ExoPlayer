/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.media3.exoplayer.image;

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link BitmapFactoryImageDecoder.Factory}. */
@RunWith(AndroidJUnit4.class)
public class BitmapFactoryImageDecoderFactoryTest {

  private final BitmapFactoryImageDecoder.Factory imageDecoderFactory =
      new BitmapFactoryImageDecoder.Factory();

  @Test
  public void supportsFormat_validFormat_returnsFormatSupported() throws Exception {
    Format.Builder format = new Format.Builder().setSampleMimeType(MimeTypes.IMAGE_JPEG);

    assertThat(imageDecoderFactory.supportsFormat(format.build()))
        .isEqualTo(RendererCapabilities.create(C.FORMAT_HANDLED));
  }

  @Test
  public void supportsFormat_noContainerMimeType_returnsUnsupportedType() throws Exception {
    Format.Builder format = new Format.Builder();

    assertThat(imageDecoderFactory.supportsFormat(format.build()))
        .isEqualTo(RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE));
  }

  @Test
  public void supportsFormat_nonImageMimeType_returnsUnsupportedType() throws Exception {
    Format.Builder format = new Format.Builder();

    format.setSampleMimeType(MimeTypes.VIDEO_AV1);

    assertThat(imageDecoderFactory.supportsFormat(format.build()))
        .isEqualTo(RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE));
  }

  @Test
  public void supportsFormat_unsupportedImageMimeType_returnsUnsupportedSubType() throws Exception {
    Format.Builder format = new Format.Builder();

    format.setSampleMimeType("image/custom");

    assertThat(imageDecoderFactory.supportsFormat(format.build()))
        .isEqualTo(RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE));
  }
}
