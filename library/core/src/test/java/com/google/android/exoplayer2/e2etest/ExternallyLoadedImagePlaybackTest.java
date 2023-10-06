/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.google.android.exoplayer2.e2etest;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.annotation.GraphicsMode.Mode.NATIVE;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.ext.image.BitmapFactoryImageDecoder;
import com.google.android.exoplayer2.ext.image.ImageDecoder;
import com.google.android.exoplayer2.ext.image.ImageDecoderException;
import com.google.android.exoplayer2.robolectric.PlaybackOutput;
import com.google.android.exoplayer2.robolectric.TestPlayerRunHelper;
import com.google.android.exoplayer2.testutil.CapturingRenderersFactory;
import com.google.android.exoplayer2.testutil.DumpFileAsserts;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.upstream.AssetDataSource;
import com.google.android.exoplayer2.upstream.DataSourceUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.base.Charsets;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.GraphicsMode;

/** End-to-end tests using image content loaded from an injected image management framework. */
@RunWith(AndroidJUnit4.class)
@GraphicsMode(value = NATIVE)
public final class ExternallyLoadedImagePlaybackTest {

  private static final String INPUT_FILE = "png/non-motion-photo-shortened.png";

  @Test
  public void test() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory renderersFactory =
        new CapturingRenderersFactory(applicationContext, /* addImageRenderer= */ true)
            .setImageDecoderFactory(new CustomImageDecoderFactory());
    Clock clock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, renderersFactory).setClock(clock).build();
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, renderersFactory);
    long durationMs = 5 * C.MILLIS_PER_SECOND;
    player.setMediaItem(
        new MediaItem.Builder()
            .setUri("asset:///media/" + INPUT_FILE)
            .setImageDurationMs(durationMs)
            .setMimeType(MimeTypes.APPLICATION_EXTERNALLY_LOADED_IMAGE)
            .build());
    player.prepare();

    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
    long playerStartedMs = clock.elapsedRealtime();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    long playbackDurationMs = clock.elapsedRealtime() - playerStartedMs;
    player.release();

    assertThat(playbackDurationMs).isAtLeast(durationMs);
    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/" + INPUT_FILE + ".dump");
  }

  private static final class CustomImageDecoderFactory implements ImageDecoder.Factory {

    @Override
    public @RendererCapabilities.Capabilities int supportsFormat(Format format) {
      return format.sampleMimeType.equals(MimeTypes.APPLICATION_EXTERNALLY_LOADED_IMAGE)
          ? RendererCapabilities.create(C.FORMAT_HANDLED)
          : RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
    }

    @Override
    public ImageDecoder createImageDecoder() {
      return new BitmapFactoryImageDecoder.Factory(ExternallyLoadedImagePlaybackTest::decode)
          .createImageDecoder();
    }
  }

  private static Bitmap decode(byte[] data, int length) throws ImageDecoderException {
    String uriString = new String(data, Charsets.UTF_8);
    AssetDataSource assetDataSource =
        new AssetDataSource(ApplicationProvider.getApplicationContext());
    DataSpec dataSpec = new DataSpec(Uri.parse(uriString));
    @Nullable Bitmap bitmap;

    try {
      assetDataSource.open(dataSpec);
      byte[] imageData = DataSourceUtil.readToEnd(assetDataSource);
      bitmap = BitmapFactory.decodeByteArray(imageData, /* offset= */ 0, imageData.length);
    } catch (IOException e) {
      throw new ImageDecoderException(e);
    }
    if (bitmap == null) {
      throw new ImageDecoderException(
          "Could not decode image data with BitmapFactory. uriString decoded from data = "
              + uriString);
    }
    return bitmap;
  }
}
