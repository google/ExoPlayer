/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.session;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;
import static org.robolectric.annotation.GraphicsMode.Mode.NATIVE;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import androidx.media3.datasource.DataSourceBitmapLoader;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.annotation.GraphicsMode;

/**
 * Tests for {@link CacheBitmapLoader}.
 *
 * <p>This test needs to run as an androidTest because robolectric's {@link BitmapFactory} is not
 * fully functional.
 */
@RunWith(AndroidJUnit4.class)
@GraphicsMode(value = NATIVE)
public class CacheBitmapLoaderTest {

  private static final String TEST_IMAGE_PATH = "media/jpeg/non-motion-photo-shortened.jpg";

  private static final String SECOND_TEST_IMAGE_PATH = "media/jpeg/ss-motion-photo-shortened.jpg";

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private Context context;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
  }

  @Test
  public void decodeBitmap_requestWithSameDataTwice_success() throws Exception {
    CacheBitmapLoader cacheBitmapLoader =
        new CacheBitmapLoader(new DataSourceBitmapLoader(context));
    byte[] imageData = TestUtil.getByteArray(context, TEST_IMAGE_PATH);
    Bitmap expectedBitmap =
        apply90DegreeExifRotation(
            BitmapFactory.decodeByteArray(imageData, /* offset= */ 0, imageData.length));

    // First request, no cached bitmap load request.
    ListenableFuture<Bitmap> future1 = cacheBitmapLoader.decodeBitmap(imageData);

    assertThat(future1.get(10, SECONDS).sameAs(expectedBitmap)).isTrue();

    // Second request, has cached bitmap load request.
    ListenableFuture<Bitmap> future2 = cacheBitmapLoader.decodeBitmap(imageData);

    assertThat(future1).isSameInstanceAs(future2);
  }

  @Test
  public void decodeBitmap_requestWithDifferentData_success() throws Exception {
    CacheBitmapLoader cacheBitmapLoader =
        new CacheBitmapLoader(new DataSourceBitmapLoader(context));
    byte[] imageData1 = TestUtil.getByteArray(context, TEST_IMAGE_PATH);
    byte[] imageData2 = TestUtil.getByteArray(context, SECOND_TEST_IMAGE_PATH);
    Bitmap expectedBitmap1 =
        apply90DegreeExifRotation(
            BitmapFactory.decodeByteArray(imageData1, /* offset= */ 0, imageData1.length));
    Bitmap expectedBitmap2 =
        apply90DegreeExifRotation(
            BitmapFactory.decodeByteArray(imageData2, /* offset= */ 0, imageData2.length));

    // First request.
    ListenableFuture<Bitmap> future1 = cacheBitmapLoader.decodeBitmap(imageData1);

    assertThat(future1.get(10, SECONDS).sameAs(expectedBitmap1)).isTrue();

    // Second request.
    ListenableFuture<Bitmap> future2 = cacheBitmapLoader.decodeBitmap(imageData2);

    assertThat(future2.get(10, SECONDS).sameAs(expectedBitmap2)).isTrue();
    assertThat(future1).isNotSameInstanceAs(future2);
  }

  @Test
  public void decodeBitmap_requestWithSameDataTwice_throwsException() {
    CacheBitmapLoader cacheBitmapLoader =
        new CacheBitmapLoader(new DataSourceBitmapLoader(context));

    // First request, no cached bitmap load request.
    ListenableFuture<Bitmap> future1 = cacheBitmapLoader.decodeBitmap(new byte[0]);

    // Second request, has cached bitmap load request.
    ListenableFuture<Bitmap> future2 = cacheBitmapLoader.decodeBitmap(new byte[0]);

    assertThat(future1).isSameInstanceAs(future2);
    ExecutionException executionException =
        assertThrows(ExecutionException.class, () -> future1.get(10, SECONDS));
    assertThat(executionException).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(executionException).hasMessageThat().contains("Could not decode image data");
  }

  @Test
  public void loadBitmap_httpUri_requestWithSameUriTwice_success() throws Exception {
    CacheBitmapLoader cacheBitmapLoader =
        new CacheBitmapLoader(new DataSourceBitmapLoader(context));
    byte[] imageData = TestUtil.getByteArray(context, TEST_IMAGE_PATH);
    Buffer responseBody = new Buffer().write(imageData);
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(responseBody));
    Uri uri = Uri.parse(mockWebServer.url("test_path").toString());
    Bitmap expectedBitmap =
        apply90DegreeExifRotation(
            BitmapFactory.decodeByteArray(imageData, /* offset= */ 0, imageData.length));

    // First request, no cached bitmap load request.
    Bitmap bitmap = cacheBitmapLoader.loadBitmap(uri).get(10, SECONDS);

    assertThat(bitmap.sameAs(expectedBitmap)).isTrue();

    // Second request, has cached bitmap load request.
    bitmap = cacheBitmapLoader.loadBitmap(uri).get(10, SECONDS);

    assertThat(bitmap.sameAs(expectedBitmap)).isTrue();
    assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
  }

  @Test
  public void loadBitmap_httpUri_requestWithDifferentUri_success() throws Exception {
    CacheBitmapLoader cacheBitmapLoader =
        new CacheBitmapLoader(new DataSourceBitmapLoader(context));
    byte[] imageData1 = TestUtil.getByteArray(context, TEST_IMAGE_PATH);
    byte[] imageData2 = TestUtil.getByteArray(context, SECOND_TEST_IMAGE_PATH);
    Buffer responseBody1 = new Buffer().write(imageData1);
    Buffer responseBody2 = new Buffer().write(imageData2);
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(responseBody1));
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(responseBody2));
    Uri uri1 = Uri.parse(mockWebServer.url("test_path_1").toString());
    Uri uri2 = Uri.parse(mockWebServer.url("test_path_2").toString());
    Bitmap expectedBitmap1 =
        apply90DegreeExifRotation(
            BitmapFactory.decodeByteArray(imageData1, /* offset= */ 0, imageData1.length));
    Bitmap expectedBitmap2 =
        apply90DegreeExifRotation(
            BitmapFactory.decodeByteArray(imageData2, /* offset= */ 0, imageData2.length));

    // First request.
    Bitmap bitmap = cacheBitmapLoader.loadBitmap(uri1).get(10, SECONDS);

    assertThat(bitmap.sameAs(expectedBitmap1)).isTrue();

    // Second request.
    bitmap = cacheBitmapLoader.loadBitmap(uri2).get(10, SECONDS);

    assertThat(bitmap.sameAs(expectedBitmap2)).isTrue();
    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
  }

  @Test
  public void loadBitmap_httpUri_requestWithSameUriTwice_throwsException() {
    CacheBitmapLoader cacheBitmapLoader =
        new CacheBitmapLoader(new DataSourceBitmapLoader(context));
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.enqueue(new MockResponse().setResponseCode(404));
    Uri uri = Uri.parse(mockWebServer.url("test_path").toString());

    // First request, no cached bitmap load request.
    ListenableFuture<Bitmap> future1 = cacheBitmapLoader.loadBitmap(uri);

    // Second request, has cached bitmap load request.
    ListenableFuture<Bitmap> future2 = cacheBitmapLoader.loadBitmap(uri);

    ExecutionException executionException1 =
        assertThrows(ExecutionException.class, () -> future1.get(10, SECONDS));
    ExecutionException executionException2 =
        assertThrows(ExecutionException.class, () -> future2.get(10, SECONDS));
    assertThat(executionException1)
        .hasCauseThat()
        .isInstanceOf(HttpDataSource.InvalidResponseCodeException.class);
    assertThat(executionException2)
        .hasCauseThat()
        .isInstanceOf(HttpDataSource.InvalidResponseCodeException.class);
    assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
  }

  private static Bitmap apply90DegreeExifRotation(Bitmap bitmap) {
    Matrix rotationMatrix = new Matrix();
    rotationMatrix.postRotate(/* degrees= */ 90);
    return Bitmap.createBitmap(
        bitmap,
        /* x= */ 0,
        /* y= */ 0,
        bitmap.getWidth(),
        bitmap.getHeight(),
        rotationMatrix,
        /* filter= */ false);
  }
}
