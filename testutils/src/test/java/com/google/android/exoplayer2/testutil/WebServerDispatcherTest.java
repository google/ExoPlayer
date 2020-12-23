/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link WebServerDispatcher}. */
@RunWith(AndroidJUnit4.class)
public class WebServerDispatcherTest {

  private static int seed;
  // Note: Leading slash is deliberately skipped to test that Resource#setPath() will add it if
  // it's missing.
  private static final String RANGE_SUPPORTED_PATH = "range/requests-supported";
  private static final byte[] RANGE_SUPPORTED_DATA =
      TestUtil.buildTestData(/* length= */ 20, seed++);
  private static final String RANGE_SUPPORTED_LENGTH_UNKNOWN_PATH =
      "range/requests-supported-length-unknown";
  private static final byte[] RANGE_SUPPORTED_LENGTH_UNKNOWN_DATA =
      TestUtil.buildTestData(/* length= */ 20, seed++);
  private static final String RANGE_UNSUPPORTED_PATH = "/range/requests/not-supported";
  private static final byte[] RANGE_UNSUPPORTED_DATA =
      TestUtil.buildTestData(/* length= */ 20, seed++);
  private static final String RANGE_UNSUPPORTED_LENGTH_UNKNOWN_PATH =
      "/range/requests/not-supported-length-unknown";
  private static final byte[] RANGE_UNSUPPORTED_LENGTH_UNKNOWN_DATA =
      TestUtil.buildTestData(/* length= */ 20, seed++);

  private MockWebServer mockWebServer;

  @Before
  public void setupServer() {
    mockWebServer = new MockWebServer();
    mockWebServer.setDispatcher(
        WebServerDispatcher.forResources(
            ImmutableList.of(
                new WebServerDispatcher.Resource.Builder()
                    .setPath(RANGE_SUPPORTED_PATH)
                    .setData(RANGE_SUPPORTED_DATA)
                    .supportsRangeRequests(true)
                    .build(),
                new WebServerDispatcher.Resource.Builder()
                    .setPath(RANGE_SUPPORTED_LENGTH_UNKNOWN_PATH)
                    .setData(RANGE_SUPPORTED_LENGTH_UNKNOWN_DATA)
                    .supportsRangeRequests(true)
                    .resolvesToUnknownLength(true)
                    .build(),
                new WebServerDispatcher.Resource.Builder()
                    .setPath(RANGE_UNSUPPORTED_PATH)
                    .setData(RANGE_UNSUPPORTED_DATA)
                    .supportsRangeRequests(false)
                    .build(),
                new WebServerDispatcher.Resource.Builder()
                    .setPath(RANGE_UNSUPPORTED_LENGTH_UNKNOWN_PATH)
                    .setData(RANGE_UNSUPPORTED_LENGTH_UNKNOWN_DATA)
                    .supportsRangeRequests(false)
                    .resolvesToUnknownLength(true)
                    .build())));
  }

  @Test
  public void rangeRequestsSupported_handlesConventionalRequest() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request = new Request.Builder().url(mockWebServer.url(RANGE_SUPPORTED_PATH)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(200);
      assertThat(response.header("Accept-Ranges")).isEqualTo("bytes");
      assertThat(response.header("Content-Length")).isEqualTo("20");
      assertThat(response.header("Content-Range")).isNull();
      assertThat(response.body().bytes()).isEqualTo(RANGE_SUPPORTED_DATA);
    }
  }

  @Test
  public void rangeRequestsSupported_boundedRange() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder()
            .url(mockWebServer.url(RANGE_SUPPORTED_PATH))
            .addHeader("Range", "bytes=5-10")
            .build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(206);
      assertThat(response.header("Accept-Ranges")).isEqualTo("bytes");
      assertThat(response.header("Content-Length")).isEqualTo("6");
      assertThat(response.header("Content-Range")).isEqualTo("bytes 5-10/20");
      assertThat(response.body().bytes())
          .isEqualTo(Arrays.copyOfRange(RANGE_SUPPORTED_DATA, 5, 11));
    }
  }

  @Test
  public void rangeRequestsSupported_startOnly() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder()
            .url(mockWebServer.url(RANGE_SUPPORTED_PATH))
            .addHeader("Range", "bytes=5-")
            .build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(206);
      assertThat(response.header("Accept-Ranges")).isEqualTo("bytes");
      assertThat(response.header("Content-Length")).isEqualTo("15");
      assertThat(response.header("Content-Range")).isEqualTo("bytes 5-19/20");
      assertThat(response.body().bytes())
          .isEqualTo(Arrays.copyOfRange(RANGE_SUPPORTED_DATA, 5, 20));
    }
  }

  @Test
  public void rangeRequestsSupported_suffixBytes() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder()
            .url(mockWebServer.url(RANGE_SUPPORTED_PATH))
            .addHeader("Range", "bytes=-5")
            .build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(206);
      assertThat(response.header("Accept-Ranges")).isEqualTo("bytes");
      assertThat(response.header("Content-Length")).isEqualTo("5");
      assertThat(response.header("Content-Range")).isEqualTo("bytes 15-19/20");
      assertThat(response.body().bytes())
          .isEqualTo(Arrays.copyOfRange(RANGE_SUPPORTED_DATA, 15, 20));
    }
  }

  @Test
  public void rangeRequestsSupported_truncatesBoundedRangeToLength() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder()
            .url(mockWebServer.url(RANGE_SUPPORTED_PATH))
            .addHeader("Range", "bytes=5-25")
            .build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(206);
      assertThat(response.header("Accept-Ranges")).isEqualTo("bytes");
      assertThat(response.header("Content-Length")).isEqualTo("15");
      assertThat(response.header("Content-Range")).isEqualTo("bytes 5-19/20");
      assertThat(response.body().bytes())
          .isEqualTo(Arrays.copyOfRange(RANGE_SUPPORTED_DATA, 5, 20));
    }
  }

  @Test
  public void rangeRequestsSupported_truncatesSuffixToLength() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder()
            .url(mockWebServer.url(RANGE_SUPPORTED_PATH))
            .addHeader("Range", "bytes=-25")
            .build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(206);
      assertThat(response.header("Accept-Ranges")).isEqualTo("bytes");
      assertThat(response.header("Content-Length")).isEqualTo("20");
      assertThat(response.header("Content-Range")).isEqualTo("bytes 0-19/20");
      assertThat(response.body().bytes()).isEqualTo(RANGE_SUPPORTED_DATA);
    }
  }

  @Test
  public void rangeRequestsSupported_rejectsStartAtResourceEnd() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder()
            .url(mockWebServer.url(RANGE_SUPPORTED_PATH))
            .addHeader("Range", "bytes=20-25")
            .build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(416);
      assertThat(response.header("Accept-Ranges")).isEqualTo("bytes");
      assertThat(response.header("Content-Range")).isEqualTo("bytes */20");
    }
  }

  @Test
  public void rangeRequestsSupported_rejectsStartAfterResourceEnd() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder()
            .url(mockWebServer.url(RANGE_SUPPORTED_PATH))
            .addHeader("Range", "bytes=25-30")
            .build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(416);
      assertThat(response.header("Accept-Ranges")).isEqualTo("bytes");
      assertThat(response.header("Content-Range")).isEqualTo("bytes */20");
    }
  }

  @Test
  public void rangeRequestsSupported_lengthUnknown_handlesConventionalRequest() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder().url(mockWebServer.url(RANGE_SUPPORTED_LENGTH_UNKNOWN_PATH)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(200);
      assertThat(response.header("Accept-Ranges")).isEqualTo("bytes");
      assertThat(response.header("Content-Length")).isEmpty();
      assertThat(response.header("Content-Range")).isNull();
      assertThat(response.body().contentLength()).isEqualTo(-1);

      // Calling ResponseBody#bytes() times out because Content-Length isn't set, so instead we
      // read exactly the number of bytes we expect.
      byte[] actualBytes = new byte[20];
      response.body().byteStream().read(actualBytes);
      assertThat(actualBytes).isEqualTo(RANGE_SUPPORTED_LENGTH_UNKNOWN_DATA);
    }
  }

  @Test
  public void rangeRequestsSupported_lengthUnknown_boundedRange() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder()
            .url(mockWebServer.url(RANGE_SUPPORTED_LENGTH_UNKNOWN_PATH))
            .addHeader("Range", "bytes=5-10")
            .build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(206);
      assertThat(response.header("Accept-Ranges")).isEqualTo("bytes");
      assertThat(response.header("Content-Length")).isEqualTo("6");
      assertThat(response.header("Content-Range")).isEqualTo("bytes 5-10/*");
      assertThat(response.body().bytes())
          .isEqualTo(Arrays.copyOfRange(RANGE_SUPPORTED_LENGTH_UNKNOWN_DATA, 5, 11));
    }
  }

  @Test
  public void rangeRequestsSupported_lengthUnknown_startOnly() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder()
            .url(mockWebServer.url(RANGE_SUPPORTED_LENGTH_UNKNOWN_PATH))
            .addHeader("Range", "bytes=5-")
            .build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(206);
      assertThat(response.header("Accept-Ranges")).isEqualTo("bytes");
      assertThat(response.header("Content-Length")).isEmpty();
      assertThat(response.header("Content-Range")).isEqualTo("bytes 5-19/*");
      assertThat(response.body().contentLength()).isEqualTo(-1);

      // Calling ResponseBody#bytes() times out because Content-Length isn't set, so instead we
      // read exactly the number of bytes we expect.
      byte[] actualBytes = new byte[15];
      response.body().byteStream().read(actualBytes);
      assertThat(actualBytes)
          .isEqualTo(Arrays.copyOfRange(RANGE_SUPPORTED_LENGTH_UNKNOWN_DATA, 5, 20));
    }
  }

  @Test
  public void rangeRequestsSupported_lengthUnknown_truncatesBoundedRangeToLength()
      throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder()
            .url(mockWebServer.url(RANGE_SUPPORTED_LENGTH_UNKNOWN_PATH))
            .addHeader("Range", "bytes=5-25")
            .build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(206);
      assertThat(response.header("Accept-Ranges")).isEqualTo("bytes");
      assertThat(response.header("Content-Length")).isEqualTo("15");
      assertThat(response.header("Content-Range")).isEqualTo("bytes 5-19/*");
      assertThat(response.body().bytes())
          .isEqualTo(Arrays.copyOfRange(RANGE_SUPPORTED_LENGTH_UNKNOWN_DATA, 5, 20));
    }
  }

  @Test
  public void rangeRequestsSupported_lengthUnknown_rejectsSuffixRange() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder()
            .url(mockWebServer.url(RANGE_SUPPORTED_LENGTH_UNKNOWN_PATH))
            .addHeader("Range", "bytes=-5")
            .build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(416);
    }
  }

  @Test
  public void rangeRequestsSupported_rejectsRangeWithEndBeforeStart() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder()
            .url(mockWebServer.url(RANGE_SUPPORTED_PATH))
            .addHeader("Range", "bytes=15-10")
            .build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(416);
      assertThat(response.header("Accept-Ranges")).isEqualTo("bytes");
      assertThat(response.header("Content-Range")).isEqualTo("bytes */20");
    }
  }

  @Test
  public void rangeRequestsSupported_rejectsNonByteRange() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder()
            .url(mockWebServer.url(RANGE_SUPPORTED_PATH))
            .addHeader("Range", "seconds=5-10")
            .build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(416);
    }
  }

  @Test
  public void rangeRequestsSupported_rejectsMultipartRange() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder()
            .url(mockWebServer.url(RANGE_SUPPORTED_PATH))
            .addHeader("Range", "bytes=2-6,10-12")
            .build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(416);
    }
  }

  @Test
  public void rangeRequestsSupported_rejectsMalformedRange() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder()
            .url(mockWebServer.url(RANGE_SUPPORTED_PATH))
            .addHeader("Range", "bytes=foo")
            .build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(416);
    }
  }

  @Test
  public void rangeRequestsUnsupported_conventionalRequestWorksAsExpected() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request = new Request.Builder().url(mockWebServer.url(RANGE_UNSUPPORTED_PATH)).build();

    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(200);
      assertThat(response.header("Accept-Ranges")).isNull();
      assertThat(response.header("Content-Length")).isEqualTo("20");
      assertThat(response.header("Content-Range")).isNull();
      assertThat(response.body().bytes()).isEqualTo(RANGE_UNSUPPORTED_DATA);
    }
  }

  @Test
  public void rangeRequestsUnsupported_rangeRequestHeadersIgnored() throws Exception {
    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder()
            .url(mockWebServer.url(RANGE_UNSUPPORTED_PATH))
            .addHeader("Range", "bytes=5-10")
            .build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(200);
      assertThat(response.header("Accept-Ranges")).isNull();
      assertThat(response.header("Content-Length")).isEqualTo("20");
      assertThat(response.header("Content-Range")).isNull();
      assertThat(response.body().bytes()).isEqualTo(RANGE_UNSUPPORTED_DATA);
    }
  }
}
