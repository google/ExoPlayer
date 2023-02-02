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

package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.transformer.TransformationException.ERROR_CODE_IO_UNSPECIFIED;
import static androidx.media3.transformer.TransformationException.ERROR_CODE_UNSPECIFIED;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_AVAILABLE;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_NOT_STARTED;

import android.graphics.Bitmap;
import android.os.Looper;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.SimpleBitmapLoader;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

/** An {@link AssetLoader} implementation that loads images into {@link Bitmap} instances. */
@UnstableApi
public final class ImageAssetLoader implements AssetLoader {

  /** An {@link AssetLoader.Factory} for {@link ImageAssetLoader} instances. */
  public static final class Factory implements AssetLoader.Factory {

    @Override
    public AssetLoader createAssetLoader(
        EditedMediaItem editedMediaItem, Looper looper, Listener listener) {
      return new ImageAssetLoader(editedMediaItem, listener);
    }
  }

  public static final String MIME_TYPE_IMAGE_ALL = MimeTypes.BASE_TYPE_IMAGE + "/*";

  private final EditedMediaItem editedMediaItem;
  private final Listener listener;

  private @Transformer.ProgressState int progressState;
  private int progress;

  private ImageAssetLoader(EditedMediaItem editedMediaItem, Listener listener) {
    this.editedMediaItem = editedMediaItem;
    this.listener = listener;

    progressState = PROGRESS_STATE_NOT_STARTED;
  }

  @Override
  public void start() {
    progressState = PROGRESS_STATE_AVAILABLE;
    listener.onTrackCount(1);
    BitmapLoader bitmapLoader = new SimpleBitmapLoader();
    MediaItem.LocalConfiguration localConfiguration =
        checkNotNull(editedMediaItem.mediaItem.localConfiguration);
    ListenableFuture<Bitmap> future = bitmapLoader.loadBitmap(localConfiguration.uri);
    Futures.addCallback(
        future,
        new FutureCallback<Bitmap>() {
          @Override
          public void onSuccess(Bitmap bitmap) {
            progress = 50;
            try {
              Format format =
                  new Format.Builder()
                      .setHeight(bitmap.getHeight())
                      .setWidth(bitmap.getWidth())
                      .setSampleMimeType(MIME_TYPE_IMAGE_ALL)
                      .build();
              SampleConsumer sampleConsumer =
                  listener.onTrackAdded(
                      format,
                      SUPPORTED_OUTPUT_TYPE_DECODED,
                      /* streamStartPositionUs= */ 0,
                      /* streamOffsetUs= */ 0);
              checkState(editedMediaItem.durationUs != C.TIME_UNSET);
              checkState(editedMediaItem.frameRate != C.RATE_UNSET_INT);
              // TODO(b/262693274): consider using listener.onDurationUs() or the MediaItem change
              //    callback (when it's added) rather than setting duration here.
              sampleConsumer.queueInputBitmap(
                  bitmap, editedMediaItem.durationUs, editedMediaItem.frameRate);
              sampleConsumer.signalEndOfVideoInput();
            } catch (TransformationException e) {
              listener.onTransformationError(e);
            } catch (RuntimeException e) {
              listener.onTransformationError(
                  TransformationException.createForAssetLoader(e, ERROR_CODE_UNSPECIFIED));
            }
            progress = 100;
          }

          @Override
          public void onFailure(Throwable t) {
            listener.onTransformationError(
                TransformationException.createForAssetLoader(t, ERROR_CODE_IO_UNSPECIFIED));
          }
        },
        MoreExecutors.directExecutor());
  }

  @Override
  public @Transformer.ProgressState int getProgress(ProgressHolder progressHolder) {
    if (progressState == PROGRESS_STATE_AVAILABLE) {
      progressHolder.progress = progress;
    }
    return progressState;
  }

  @Override
  public ImmutableMap<Integer, String> getDecoderNames() {
    return ImmutableMap.of();
  }

  @Override
  public void release() {
    progressState = PROGRESS_STATE_NOT_STARTED;
  }
}
