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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.transformer.ExportException.ERROR_CODE_IO_UNSPECIFIED;
import static com.google.android.exoplayer2.transformer.ExportException.ERROR_CODE_UNSPECIFIED;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_AVAILABLE;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_NOT_STARTED;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceBitmapLoader;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.util.BitmapLoader;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * An {@link AssetLoader} implementation that loads images into {@link Bitmap} instances.
 *
 * <p>Supports the image formats listed <a
 * href="https://developer.android.com/guide/topics/media/media-formats#image-formats">here</a>
 * except from GIFs, which could exhibit unexpected behavior.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class ImageAssetLoader implements AssetLoader {

  /** An {@link AssetLoader.Factory} for {@link ImageAssetLoader} instances. */
  public static final class Factory implements AssetLoader.Factory {

    private final Context context;

    public Factory(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public AssetLoader createAssetLoader(
        EditedMediaItem editedMediaItem, Looper looper, Listener listener) {
      return new ImageAssetLoader(context, editedMediaItem, listener);
    }
  }

  public static final String MIME_TYPE_IMAGE_ALL = MimeTypes.BASE_TYPE_IMAGE + "/*";

  private static final int QUEUE_BITMAP_INTERVAL_MS = 10;

  private final EditedMediaItem editedMediaItem;
  private final DataSource.Factory dataSourceFactory;
  private final Listener listener;
  private final ScheduledExecutorService scheduledExecutorService;

  @Nullable private SampleConsumer sampleConsumer;
  private @Transformer.ProgressState int progressState;

  private volatile int progress;

  private ImageAssetLoader(Context context, EditedMediaItem editedMediaItem, Listener listener) {
    checkState(editedMediaItem.durationUs != C.TIME_UNSET);
    checkState(editedMediaItem.frameRate != C.RATE_UNSET_INT);
    this.editedMediaItem = editedMediaItem;
    dataSourceFactory = new DefaultDataSource.Factory(context);
    this.listener = listener;
    scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    progressState = PROGRESS_STATE_NOT_STARTED;
  }

  @Override
  // Ignore Future returned by scheduledExecutorService because failures are already handled in the
  // runnable.
  @SuppressWarnings("FutureReturnValueIgnored")
  public void start() {
    progressState = PROGRESS_STATE_AVAILABLE;
    listener.onDurationUs(editedMediaItem.durationUs);
    listener.onTrackCount(1);
    BitmapLoader bitmapLoader =
        new DataSourceBitmapLoader(
            MoreExecutors.listeningDecorator(scheduledExecutorService), dataSourceFactory);
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
                      .setColorInfo(ColorInfo.SRGB_BT709_FULL)
                      .build();
              listener.onTrackAdded(format, SUPPORTED_OUTPUT_TYPE_DECODED);
              scheduledExecutorService.submit(() -> queueBitmapInternal(bitmap, format));
            } catch (RuntimeException e) {
              listener.onError(ExportException.createForAssetLoader(e, ERROR_CODE_UNSPECIFIED));
            }
          }

          @Override
          public void onFailure(Throwable t) {
            listener.onError(ExportException.createForAssetLoader(t, ERROR_CODE_IO_UNSPECIFIED));
          }
        },
        scheduledExecutorService);
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
    scheduledExecutorService.shutdownNow();
  }

  // Ignore Future returned by scheduledExecutorService because failures are already handled in the
  // runnable.
  @SuppressWarnings("FutureReturnValueIgnored")
  private void queueBitmapInternal(Bitmap bitmap, Format format) {
    try {
      if (sampleConsumer == null) {
        sampleConsumer = listener.onOutputFormat(format);
      }
      // TODO(b/262693274): consider using listener.onDurationUs() or the MediaItem change
      //    callback rather than setting duration here.
      if (sampleConsumer == null
          || !sampleConsumer.queueInputBitmap(
              bitmap, editedMediaItem.durationUs, editedMediaItem.frameRate)) {
        scheduledExecutorService.schedule(
            () -> queueBitmapInternal(bitmap, format), QUEUE_BITMAP_INTERVAL_MS, MILLISECONDS);
        return;
      }
      sampleConsumer.signalEndOfVideoInput();
      progress = 100;
    } catch (ExportException e) {
      listener.onError(e);
    } catch (RuntimeException e) {
      listener.onError(ExportException.createForAssetLoader(e, ERROR_CODE_UNSPECIFIED));
    }
  }
}
