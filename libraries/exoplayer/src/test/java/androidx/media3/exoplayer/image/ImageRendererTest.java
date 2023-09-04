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
package androidx.media3.exoplayer.image;

import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.oneByteSample;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.TimedValueQueue;
import androidx.media3.exoplayer.RendererConfiguration;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.test.utils.FakeSampleStream;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link ImageRenderer}. */
@RunWith(AndroidJUnit4.class)
public class ImageRendererTest {

  private static final Format FORMAT =
      new Format.Builder()
          .setContainerMimeType(MimeTypes.IMAGE_PNG)
          .setTileCountVertical(1)
          .setTileCountHorizontal(1)
          .build();

  private final TimedValueQueue<Bitmap> renderedBitmaps = new TimedValueQueue<>();
  private final Bitmap fakeDecodedBitmap =
      Bitmap.createBitmap(/* width= */ 1, /* height= */ 1, Bitmap.Config.ARGB_8888);

  private ImageRenderer renderer;

  @Before
  public void setUp() throws Exception {
    ImageDecoder.Factory fakeDecoderFactory =
        new DefaultImageDecoder.Factory((data, length) -> fakeDecodedBitmap);
    ImageOutput capturingImageOutput = renderedBitmaps::add;
    renderer = new ImageRenderer(fakeDecoderFactory, capturingImageOutput);
    renderer.init(/* index= */ 0, PlayerId.UNSET, Clock.DEFAULT);
  }

  @After
  public void tearDown() throws Exception {
    renderedBitmaps.clear();
    renderer.disable();
    renderer.release();
  }

  @Test
  public void renderOneStream_rendersToImageOutput() throws Exception {
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            FORMAT,
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(/* startPositionUs= */ 0);
    // TODO(b/289989736): When the mediaPeriodId is signalled to the renders set durationUs here and
    //  assert on it.
    renderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {FORMAT},
        fakeSampleStream,
        /* positionUs= */ 0,
        /* joining= */ false,
        /* mayRenderStartOfStream= */ true,
        /* startPositionUs= */ 0,
        /* offsetUs= */ 0,
        new MediaSource.MediaPeriodId(new Object()));
    renderer.setCurrentStreamFinal();

    while (!renderer.isReady()) {
      renderer.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    }

    assertThat(renderedBitmaps.size()).isEqualTo(1);
    assertThat(renderedBitmaps.poll(0)).isSameInstanceAs(fakeDecodedBitmap);

    renderer.render(
        /* positionUs= */ C.MICROS_PER_SECOND, /* elapsedRealtimeUs= */ C.MICROS_PER_SECOND);
    assertThat(renderer.isEnded()).isTrue();
  }
}
