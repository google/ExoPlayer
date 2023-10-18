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
import static org.robolectric.annotation.GraphicsMode.Mode.NATIVE;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.media3.common.MediaMetadata;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.annotation.GraphicsMode;

/** Tests for {@link SimpleBitmapLoader}. */
@SuppressWarnings("deprecation") // Testing deprecated class
@RunWith(AndroidJUnit4.class)
@GraphicsMode(value = NATIVE)
public class SimpleBitmapLoaderTest {

  private static final String TEST_IMAGE_PATH = "media/jpeg/non-motion-photo-shortened.jpg";

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

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
        future::get,
        IllegalArgumentException.class,
        /* messagePart= */ "Could not decode image data");
  }

  @Test
  public void load_httpUri_loadsImage() throws Exception {
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
  public void load_httpUriAndServerError_throwsException() {
    SimpleBitmapLoader bitmapLoader =
        new SimpleBitmapLoader(MoreExecutors.newDirectExecutorService());
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.enqueue(new MockResponse().setResponseCode(404));

    ListenableFuture<Bitmap> future =
        bitmapLoader.loadBitmap(Uri.parse(mockWebServer.url("test_path").toString()));

    assertException(future::get, IOException.class, /* messagePart= */ "Invalid response status");
  }

  @Test
  public void load_fileUri_loadsImage() throws Exception {
    byte[] imageData =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TEST_IMAGE_PATH);
    File file = tempFolder.newFile();
    Files.write(Paths.get(file.getAbsolutePath()), imageData);
    Uri uri = Uri.fromFile(file);
    SimpleBitmapLoader bitmapLoader =
        new SimpleBitmapLoader(MoreExecutors.newDirectExecutorService());

    Bitmap bitmap = bitmapLoader.loadBitmap(uri).get();

    assertThat(
            bitmap.sameAs(
                BitmapFactory.decodeByteArray(imageData, /* offset= */ 0, imageData.length)))
        .isTrue();
  }

  @Test
  public void fileUriWithFileNotExisting() throws Exception {
    SimpleBitmapLoader bitmapLoader =
        new SimpleBitmapLoader(MoreExecutors.newDirectExecutorService());

    assertException(
        () -> bitmapLoader.loadBitmap(Uri.parse("file:///not_valid/path/image.bmp")).get(),
        IllegalArgumentException.class,
        /* messagePart= */ "Could not read image from file");
  }

  @Test
  public void load_unhandledUriScheme_throwsException() {
    SimpleBitmapLoader bitmapLoader =
        new SimpleBitmapLoader(MoreExecutors.newDirectExecutorService());

    assertException(
        () -> bitmapLoader.loadBitmap(Uri.parse("/local/path")).get(),
        MalformedURLException.class,
        /* messagePart= */ "no protocol");
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

  @Test
  public void loadBitmapFromMetadata_decodeFromArtworkData() throws Exception {
    byte[] imageData =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TEST_IMAGE_PATH);
    MockWebServer mockWebServer = new MockWebServer();
    Uri uri = Uri.parse(mockWebServer.url("test_path").toString());
    // Set both artworkData and artworkUri
    MediaMetadata metadata =
        new MediaMetadata.Builder()
            .setArtworkData(imageData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .setArtworkUri(uri)
            .build();
    SimpleBitmapLoader bitmapLoader =
        new SimpleBitmapLoader(MoreExecutors.newDirectExecutorService());

    Bitmap bitmap = bitmapLoader.loadBitmapFromMetadata(metadata).get();

    assertThat(
            bitmap.sameAs(
                BitmapFactory.decodeByteArray(imageData, /* offset= */ 0, imageData.length)))
        .isTrue();
    assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
  }

  @Test
  public void loadBitmapFromMetadata_loadFromArtworkUri() throws Exception {
    byte[] imageData =
        TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TEST_IMAGE_PATH);
    MockWebServer mockWebServer = new MockWebServer();
    Buffer responseBody = new Buffer().write(imageData);
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(responseBody));
    Uri uri = Uri.parse(mockWebServer.url("test_path").toString());
    // Just set artworkUri
    MediaMetadata metadata = new MediaMetadata.Builder().setArtworkUri(uri).build();
    SimpleBitmapLoader bitmapLoader =
        new SimpleBitmapLoader(MoreExecutors.newDirectExecutorService());

    Bitmap bitmap = bitmapLoader.loadBitmapFromMetadata(metadata).get();

    assertThat(
            bitmap.sameAs(
                BitmapFactory.decodeByteArray(imageData, /* offset= */ 0, imageData.length)))
        .isTrue();
    assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
  }

  @Test
  public void loadBitmapFromMetadata_returnNull() throws Exception {
    // Neither artworkData nor artworkUri is set
    MediaMetadata metadata = new MediaMetadata.Builder().build();
    SimpleBitmapLoader bitmapLoader =
        new SimpleBitmapLoader(MoreExecutors.newDirectExecutorService());

    ListenableFuture<Bitmap> bitmapFuture = bitmapLoader.loadBitmapFromMetadata(metadata);

    assertThat(bitmapFuture).isNull();
  }

  private static void assertException(
      ThrowingRunnable runnable, Class<? extends Exception> clazz, String messagePart) {
    ExecutionException executionException = assertThrows(ExecutionException.class, runnable);
    assertThat(executionException).hasCauseThat().isInstanceOf(clazz);
    assertThat(executionException).hasMessageThat().contains(messagePart);
  }
}
