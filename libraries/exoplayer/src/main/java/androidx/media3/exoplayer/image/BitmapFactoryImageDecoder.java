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

import static androidx.annotation.VisibleForTesting.PRIVATE;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.isBitmapFactorySupportedMimeType;
import static androidx.media3.decoder.DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.exifinterface.media.ExifInterface;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoder;
import androidx.media3.exoplayer.RendererCapabilities;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * An image decoder that uses {@link BitmapFactory} to decode images.
 *
 * <p>Only supports decoding one input buffer into one output buffer (i.e. one {@link Bitmap}
 * alongside one timestamp)).
 */
@UnstableApi
public final class BitmapFactoryImageDecoder
    extends SimpleDecoder<DecoderInputBuffer, ImageOutputBuffer, ImageDecoderException>
    implements ImageDecoder {

  /** A functional interface for turning byte arrays into bitmaps. */
  @VisibleForTesting(otherwise = PRIVATE)
  public interface BitmapDecoder {

    /**
     * Decodes data into a {@link Bitmap}.
     *
     * @param data An array holding the data to be decoded, starting at position 0.
     * @param length The length of the input to be decoded.
     * @return The decoded {@link Bitmap}.
     * @throws ImageDecoderException If a decoding error occurs.
     */
    Bitmap decode(byte[] data, int length) throws ImageDecoderException;
  }

  /** A factory for {@link BitmapFactoryImageDecoder} instances. */
  public static final class Factory implements ImageDecoder.Factory {

    private final BitmapDecoder bitmapDecoder;

    /**
     * Creates an instance using a {@link BitmapFactory} implementation of {@link BitmapDecoder}.
     */
    public Factory() {
      this.bitmapDecoder = BitmapFactoryImageDecoder::decode;
    }

    /**
     * Creates an instance.
     *
     * @param bitmapDecoder The {@link BitmapDecoder} used to turn a byte arrays into a bitmap.
     */
    public Factory(BitmapDecoder bitmapDecoder) {
      this.bitmapDecoder = bitmapDecoder;
    }

    @Override
    public @RendererCapabilities.Capabilities int supportsFormat(Format format) {
      if (format.sampleMimeType == null || !MimeTypes.isImage(format.sampleMimeType)) {
        return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
      }
      return isBitmapFactorySupportedMimeType(format.sampleMimeType)
          ? RendererCapabilities.create(C.FORMAT_HANDLED)
          : RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE);
    }

    @Override
    public BitmapFactoryImageDecoder createImageDecoder() {
      return new BitmapFactoryImageDecoder(bitmapDecoder);
    }
  }

  private final BitmapDecoder bitmapDecoder;

  private BitmapFactoryImageDecoder(BitmapDecoder bitmapDecoder) {
    super(new DecoderInputBuffer[1], new ImageOutputBuffer[1]);
    this.bitmapDecoder = bitmapDecoder;
  }

  @Override
  public String getName() {
    return "BitmapFactoryImageDecoder";
  }

  @Override
  protected DecoderInputBuffer createInputBuffer() {
    return new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_NORMAL);
  }

  @Override
  protected ImageOutputBuffer createOutputBuffer() {
    return new ImageOutputBuffer() {
      @Override
      public void release() {
        BitmapFactoryImageDecoder.this.releaseOutputBuffer(this);
      }
    };
  }

  @Override
  protected ImageDecoderException createUnexpectedDecodeException(Throwable error) {
    return new ImageDecoderException("Unexpected decode error", error);
  }

  @Nullable
  @Override
  protected ImageDecoderException decode(
      DecoderInputBuffer inputBuffer, ImageOutputBuffer outputBuffer, boolean reset) {
    try {
      ByteBuffer inputData = checkNotNull(inputBuffer.data);
      checkState(inputData.hasArray());
      checkArgument(inputData.arrayOffset() == 0);
      outputBuffer.bitmap = bitmapDecoder.decode(inputData.array(), inputData.remaining());
      outputBuffer.timeUs = inputBuffer.timeUs;
      return null;
    } catch (ImageDecoderException e) {
      return e;
    }
  }

  /**
   * Decodes data into a {@link Bitmap}.
   *
   * @param data An array holding the data to be decoded, starting at position 0.
   * @param length The length of the input to be decoded.
   * @return The decoded {@link Bitmap}.
   * @throws ImageDecoderException If a decoding error occurs.
   */
  private static Bitmap decode(byte[] data, int length) throws ImageDecoderException {
    @Nullable Bitmap bitmap = BitmapFactory.decodeByteArray(data, /* offset= */ 0, length);
    if (bitmap == null) {
      throw new ImageDecoderException(
          "Could not decode image data with BitmapFactory. (data.length = "
              + data.length
              + ", input length = "
              + length
              + ")");
    }
    // BitmapFactory doesn't read the exif header, so we use the ExifInterface to this do ensure the
    // bitmap is correctly orientated.
    ExifInterface exifInterface;
    try (InputStream inputStream = new ByteArrayInputStream(data, /* offset= */ 0, length)) {
      exifInterface = new ExifInterface(inputStream);
    } catch (IOException e) {
      throw new ImageDecoderException(e);
    }
    int rotationDegrees = exifInterface.getRotationDegrees();
    if (rotationDegrees != 0) {
      Matrix matrix = new Matrix();
      matrix.postRotate(rotationDegrees);
      bitmap =
          Bitmap.createBitmap(
              bitmap,
              /* x= */ 0,
              /* y= */ 0,
              bitmap.getWidth(),
              bitmap.getHeight(),
              matrix,
              /* filter= */ false);
    }
    return bitmap;
  }
}
