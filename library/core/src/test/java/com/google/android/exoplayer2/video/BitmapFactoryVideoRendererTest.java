package com.google.android.exoplayer2.video;

import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.oneByteSample;
import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.sample;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import androidx.arch.core.util.Function;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.testutil.FakeSampleStream;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowBitmapFactory;
import org.robolectric.shadows.ShadowLooper;

@RunWith(AndroidJUnit4.class)
@Config(shadows = {ShadowSurfaceExtended.class})
public class BitmapFactoryVideoRendererTest {
  private final static Format FORMAT_MJPEG = new Format.Builder().
      setSampleMimeType(MimeTypes.VIDEO_MJPEG).
      setWidth(320).setHeight(240).
      setFrameRate(15f).build();

  FakeEventListener fakeEventListener = new FakeEventListener();
  BitmapFactoryVideoRenderer bitmapFactoryVideoRenderer;

  @Before
  public void before() {
    fakeEventListener = new FakeEventListener();
    final Handler handler = new Handler(Looper.getMainLooper());
    bitmapFactoryVideoRenderer = new BitmapFactoryVideoRenderer(handler, fakeEventListener);
  }

  @After
  public void after() {
    //Kill the Thread
    bitmapFactoryVideoRenderer.onDisabled();
  }

  @Test
  public void getName() {
    Assert.assertEquals(BitmapFactoryVideoRenderer.TAG, bitmapFactoryVideoRenderer.getName());
  }

  @Test
  public void onEnabled_givenMayRenderStartOfStream() throws PlaybackException {
    bitmapFactoryVideoRenderer.onEnabled(false, true);
    ShadowLooper.idleMainLooper();
    Assert.assertNotNull(bitmapFactoryVideoRenderer.getDecoderCounters());
    Assert.assertEquals(Thread.State.RUNNABLE, bitmapFactoryVideoRenderer.getThread().getState());
    Assert.assertTrue(fakeEventListener.isVideoEnabled());
  }

  @Test
  public void onStarted_givenThreadNotStarted() throws PlaybackException {
    bitmapFactoryVideoRenderer.onStarted();
    ShadowLooper.idleMainLooper();
    Assert.assertEquals(Thread.State.RUNNABLE, bitmapFactoryVideoRenderer.getThread().getState());
  }

  @Test
  public void onDisabled_givenOnEnabled() throws PlaybackException, InterruptedException {
    onEnabled_givenMayRenderStartOfStream();
    bitmapFactoryVideoRenderer.onDisabled();
    ShadowLooper.idleMainLooper();
    Assert.assertFalse(fakeEventListener.isVideoEnabled());
    //Ensure Thread is shutdown
    bitmapFactoryVideoRenderer.getThread().join(500L);
    Assert.assertTrue(bitmapFactoryVideoRenderer.isEnded());
  }

  private FakeSampleStream getSampleStream() throws IOException {
    final Context context = ApplicationProvider.getApplicationContext();
    final byte[] bytes = TestUtil.getByteArray(context, "media/jpeg/image-320-240.jpg");
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ FORMAT_MJPEG,
            ImmutableList.of(
                sample(0L, C.BUFFER_FLAG_KEY_FRAME, bytes),
                END_OF_STREAM_ITEM));
    return fakeSampleStream;
  }

  private Surface setSurface() throws ExoPlaybackException {
    final Surface surface = ShadowSurfaceExtended.newInstance();
    final ShadowSurfaceExtended shadowSurfaceExtended = Shadow.extract(surface);
    shadowSurfaceExtended.setSize(1080, 1920);
    bitmapFactoryVideoRenderer.handleMessage(Renderer.MSG_SET_VIDEO_OUTPUT, surface);
    return surface;
  }

  @Test
  public void handleMessage_givenSurface() throws ExoPlaybackException {
    final Surface surface = setSurface();
    Assert.assertSame(surface, bitmapFactoryVideoRenderer.getSurface());
    bitmapFactoryVideoRenderer.handleMessage(Renderer.MSG_SET_VIDEO_OUTPUT, null);
    Assert.assertNull(bitmapFactoryVideoRenderer.getSurface());
  }

  @Test
  public void isReady_givenSurface() throws ExoPlaybackException {
    Assert.assertFalse(bitmapFactoryVideoRenderer.isReady());
    setSurface();
    Assert.assertTrue(bitmapFactoryVideoRenderer.isReady());
  }

  @Test
  public void render_givenJpegAndSurface() throws IOException, ExoPlaybackException {
    final Surface surface = setSurface();
    final ShadowSurfaceExtended shadowSurfaceExtended = Shadow.extract(surface);

    FakeSampleStream fakeSampleStream = getSampleStream();
    fakeSampleStream.writeData(0L);
    bitmapFactoryVideoRenderer.enable(RendererConfiguration.DEFAULT, new Format[]{FORMAT_MJPEG},
        fakeSampleStream, 0L, false, true, 0L, 0L);
    bitmapFactoryVideoRenderer.render(0L, 0L);
    // This test actually decodes the JPEG (very cool!),
    // May need to bump up timers for slow machines
    Assert.assertTrue(shadowSurfaceExtended.waitForPost(500L));
  }

  @Test
  public void supportsFormat_givenMjpegFormat() throws ExoPlaybackException{
    Assert.assertEquals(C.FORMAT_HANDLED,
        bitmapFactoryVideoRenderer.supportsFormat(FORMAT_MJPEG) & C.FORMAT_HANDLED);
  }

  @Test
  public void supportsFormat_givenMp4vFormat() throws ExoPlaybackException{
    final Format format = new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_MP4V).build();
    Assert.assertEquals(0,
        bitmapFactoryVideoRenderer.supportsFormat(format) & C.FORMAT_HANDLED);
  }

  @Test
  public void renderBitmap_given4by3BitmapAnd16by9Canvas() {
    final Bitmap bitmap = Bitmap.createBitmap(FORMAT_MJPEG.width, FORMAT_MJPEG.height, Bitmap.Config.ARGB_8888);
    final Bitmap canvasBitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888);
    final Canvas canvas = new Canvas(canvasBitmap);
    bitmapFactoryVideoRenderer.renderBitmap(bitmap, canvas);
    ShadowLooper.idleMainLooper();

    final Rect rect = bitmapFactoryVideoRenderer.getRect();
    Assert.assertEquals(canvas.getWidth(), rect.width());
    Assert.assertEquals(canvas.getHeight(), rect.height());
    final VideoSize videoSize = fakeEventListener.videoSize;
    Assert.assertEquals(bitmap.getWidth(), videoSize.width);

    bitmapFactoryVideoRenderer.renderBitmap(bitmap, canvas);
    ShadowLooper.idleMainLooper();
    Assert.assertSame(videoSize, fakeEventListener.videoSize);
  }

  @Test
  public void RenderRunnable_run_givenLateFrame() throws IOException, ExoPlaybackException {
    final Function<String, Boolean> sleep = why -> {throw new RuntimeException(why);};

    FakeSampleStream fakeSampleStream = getSampleStream();
    fakeSampleStream.writeData(0L);
    //Don't enable so the Thread is not running
    bitmapFactoryVideoRenderer.replaceStream(new Format[]{FORMAT_MJPEG}, fakeSampleStream, 0L, 0L);
    BitmapFactoryVideoRenderer.RenderRunnable renderRunnable =
        bitmapFactoryVideoRenderer.getRenderRunnable();
    renderRunnable.sleepFunction = sleep;
    bitmapFactoryVideoRenderer.render(1_000_000L, 0L);
    try {
      renderRunnable.run();
    } catch (RuntimeException e) {
      Assert.assertEquals(BitmapFactoryVideoRenderer.STREAM_EMPTY, e.getMessage());
    }
    ShadowLooper.idleMainLooper();
    Assert.assertEquals(1, fakeEventListener.getDroppedFrames());
  }

  @Test
  public void RenderRunnable_run_givenBadJpeg() throws IOException, ExoPlaybackException {
    final Function<String, Boolean> sleep = why -> {throw new RuntimeException(why);};
    FakeSampleStream fakeSampleStream =
        new FakeSampleStream(
            new DefaultAllocator(/* trimOnReset= */ true, /* individualAllocationSize= */ 1024),
            /* mediaSourceEventDispatcher= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher(),
            /* initialFormat= */ FORMAT_MJPEG,
            ImmutableList.of(
                oneByteSample(0L, C.BUFFER_FLAG_KEY_FRAME),
                END_OF_STREAM_ITEM));
    fakeSampleStream.writeData(0L);

    //Don't enable so the Thread is not running
    bitmapFactoryVideoRenderer.replaceStream(new Format[]{FORMAT_MJPEG}, fakeSampleStream, 0L, 0L);
    BitmapFactoryVideoRenderer.RenderRunnable renderRunnable =
        bitmapFactoryVideoRenderer.getRenderRunnable();
    renderRunnable.sleepFunction = sleep;
    bitmapFactoryVideoRenderer.render(0L, 0L);
    // There is a bug in Robolectric where it doesn't handle null images,
    // so we won't get our Exception
    ShadowBitmapFactory.setAllowInvalidImageData(false);
    try {
      renderRunnable.run();
    } catch (RuntimeException e) {
      Assert.assertEquals(BitmapFactoryVideoRenderer.STREAM_EMPTY, e.getMessage());
    }
    ShadowLooper.idleMainLooper();
    Assert.assertTrue(fakeEventListener.getVideoCodecError() instanceof NullPointerException);

  }
}
