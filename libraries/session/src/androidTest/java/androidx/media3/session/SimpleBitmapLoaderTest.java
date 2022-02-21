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
package androidx.media3.session;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;

/**
 * Tests for {@link SimpleBitmapLoader}.
 *
 * <p>This test needs to run as an androidTest because robolectric's BitmapFactory is not fully
 * functional.
 */
@RunWith(AndroidJUnit4.class)
public class SimpleBitmapLoaderTest {

  private static final String TEST_IMAGE_PATH = "media/jpeg/non-motion-photo-shortened.jpg";

  @Test
  public void loadData() throws Exception {
    SimpleBitmapLoader bitmapLoader =
        new SimpleBitmapLoader(MoreExecutors.newDirectExecutorService());
    byte[] imageData =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TEST_IMAGE_PATH);

    Bitmap bitmap = bitmapLoader.decodeBitmap(imageData).get();

    assertThat(
            bitmap.sameAs(
                BitmapFactory.decodeByteArray(imageData, /* offset= */ 0, imageData.length)))
        .isTrue();
  }

  @Test
  public void loadData_withInvalidData_throwsException() {
    SimpleBitmapLoader bitmapLoader =
        new SimpleBitmapLoader(MoreExecutors.newDirectExecutorService());

    ListenableFuture<Bitmap> future = bitmapLoader.decodeBitmap(new byte[0]);

    assertException(
        future::get, IllegalArgumentException.class, /* messagePart= */ "Could not decode bitmap");
  }

  @Test
  public void loadUri_loadsImage() throws Exception {
    SimpleBitmapLoader bitmapLoader =
        new SimpleBitmapLoader(MoreExecutors.newDirectExecutorService());
    MockWebServer mockWebServer = new MockWebServer();
    byte[] imageData =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TEST_IMAGE_PATH);
    Buffer responseBody = new Buffer().write(imageData);
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(responseBody));

    Bitmap bitmap =
        bitmapLoader.loadBitmap(Uri.parse(mockWebServer.url("test_path").toString())).get();

    assertThat(
            bitmap.sameAs(
                BitmapFactory.decodeByteArray(imageData, /* offset= */ 0, imageData.length)))
        .isTrue();
  }

  @Test
  public void loadUri_serverError_throwsException() {
    SimpleBitmapLoader bitmapLoader =
        new SimpleBitmapLoader(MoreExecutors.newDirectExecutorService());
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.enqueue(new MockResponse().setResponseCode(404));

    ListenableFuture<Bitmap> future =
        bitmapLoader.loadBitmap(Uri.parse(mockWebServer.url("test_path").toString()));

    assertException(future::get, IOException.class, /* messagePart= */ "Invalid response status");
  }

  @Test
  public void loadUri_nonHttpUri_throwsException() {
    SimpleBitmapLoader bitmapLoader =
        new SimpleBitmapLoader(MoreExecutors.newDirectExecutorService());

    assertException(
        () -> bitmapLoader.loadBitmap(Uri.parse("/local/path")).get(),
        MalformedURLException.class,
        /* messagePart= */ "no protocol");
    assertException(
        () -> bitmapLoader.loadBitmap(Uri.parse("file://local/path")).get(),
        UnsupportedOperationException.class,
        /* messagePart= */ "Unsupported scheme");
    assertException(
        () -> bitmapLoader.loadBitmap(Uri.parse("asset://asset/path")).get(),
        MalformedURLException.class,
        /* messagePart= */ "unknown protocol");
    assertException(
        () -> bitmapLoader.loadBitmap(Uri.parse("raw://raw/path")).get(),
        MalformedURLException.class,
        /* messagePart= */ "unknown protocol");
    assertException(
        () -> bitmapLoader.loadBitmap(Uri.parse("data://data")).get(),
        MalformedURLException.class,
        /* messagePart= */ "unknown protocol");
  }

  private static void assertException(
      ThrowingRunnable runnable, Class<? extends Exception> clazz, String messagePart) {
    ExecutionException executionException = assertThrows(ExecutionException.class, runnable);
    assertThat(executionException).hasCauseThat().isInstanceOf(clazz);
    assertThat(executionException).hasMessageThat().contains(messagePart);
  }
}
