/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.google.android.exoplayer2.upstream;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.util.HashMap;
import java.util.Map;
import okhttp3.Headers;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DefaultHttpDataSource}. */
@RunWith(AndroidJUnit4.class)
public class DefaultHttpDataSourceTest {

  /**
   * This test will set HTTP default request parameters (1) in the DefaultHttpDataSource, (2) via
   * DefaultHttpDataSource.setRequestProperty() and (3) in the DataSpec instance according to the
   * table below. Values wrapped in '*' are the ones that should be set in the connection request.
   *
   * <pre>{@code
   * +---------------+-----+-----+-----+-----+-----+-----+-----+
   * |               |               Header Key                |
   * +---------------+-----+-----+-----+-----+-----+-----+-----+
   * |   Location    |  0  |  1  |  2  |  3  |  4  |  5  |  6  |
   * +---------------+-----+-----+-----+-----+-----+-----+-----+
   * | Constructor   | *Y* |  Y  |  Y  |     |  Y  |     |     |
   * | Setter        |     | *Y* |  Y  |  Y  |     | *Y* |     |
   * | DataSpec      |     |     | *Y* | *Y* | *Y* |     | *Y* |
   * +---------------+-----+-----+-----+-----+-----+-----+-----+
   * }</pre>
   */
  @Test
  public void open_withSpecifiedRequestParameters_usesCorrectParameters() throws Exception {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.enqueue(new MockResponse());

    String propertyFromFactory = "fromFactory";
    Map<String, String> defaultRequestProperties = new HashMap<>();
    defaultRequestProperties.put("0", propertyFromFactory);
    defaultRequestProperties.put("1", propertyFromFactory);
    defaultRequestProperties.put("2", propertyFromFactory);
    defaultRequestProperties.put("4", propertyFromFactory);
    DefaultHttpDataSource dataSource =
        new DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(1000)
            .setReadTimeoutMs(1000)
            .setDefaultRequestProperties(defaultRequestProperties)
            .createDataSource();

    String propertyFromSetter = "fromSetter";
    dataSource.setRequestProperty("1", propertyFromSetter);
    dataSource.setRequestProperty("2", propertyFromSetter);
    dataSource.setRequestProperty("3", propertyFromSetter);
    dataSource.setRequestProperty("5", propertyFromSetter);

    String propertyFromDataSpec = "fromDataSpec";
    Map<String, String> dataSpecRequestProperties = new HashMap<>();
    dataSpecRequestProperties.put("2", propertyFromDataSpec);
    dataSpecRequestProperties.put("3", propertyFromDataSpec);
    dataSpecRequestProperties.put("4", propertyFromDataSpec);
    dataSpecRequestProperties.put("6", propertyFromDataSpec);
    DataSpec dataSpec =
        new DataSpec.Builder()
            .setUri(mockWebServer.url("/test-path").toString())
            .setHttpRequestHeaders(dataSpecRequestProperties)
            .build();

    dataSource.open(dataSpec);

    Headers headers = mockWebServer.takeRequest(10, SECONDS).getHeaders();
    assertThat(headers.get("0")).isEqualTo(propertyFromFactory);
    assertThat(headers.get("1")).isEqualTo(propertyFromSetter);
    assertThat(headers.get("2")).isEqualTo(propertyFromDataSpec);
    assertThat(headers.get("3")).isEqualTo(propertyFromDataSpec);
    assertThat(headers.get("4")).isEqualTo(propertyFromDataSpec);
    assertThat(headers.get("5")).isEqualTo(propertyFromSetter);
    assertThat(headers.get("6")).isEqualTo(propertyFromDataSpec);
  }

  @Test
  public void open_invalidResponseCode() {
    DefaultHttpDataSource defaultHttpDataSource =
        new DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(1000)
            .setReadTimeoutMs(1000)
            .createDataSource();

    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(404)
            .setBody(new Buffer().write(TestUtil.createByteArray(1, 2, 3))));

    DataSpec dataSpec =
        new DataSpec.Builder().setUri(mockWebServer.url("/test-path").toString()).build();

    HttpDataSource.InvalidResponseCodeException exception =
        assertThrows(
            HttpDataSource.InvalidResponseCodeException.class,
            () -> defaultHttpDataSource.open(dataSpec));

    assertThat(exception.responseCode).isEqualTo(404);
    assertThat(exception.responseBody).isEqualTo(TestUtil.createByteArray(1, 2, 3));
  }

  @Test
  public void factory_setRequestPropertyAfterCreation_setsCorrectHeaders() throws Exception {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.enqueue(new MockResponse());
    DataSpec dataSpec =
        new DataSpec.Builder().setUri(mockWebServer.url("/test-path").toString()).build();
    DefaultHttpDataSource.Factory factory = new DefaultHttpDataSource.Factory();
    HttpDataSource dataSource = factory.createDataSource();

    Map<String, String> defaultRequestProperties = new HashMap<>();
    defaultRequestProperties.put("0", "afterCreation");
    factory.setDefaultRequestProperties(defaultRequestProperties);
    dataSource.open(dataSpec);

    Headers headers = mockWebServer.takeRequest(10, SECONDS).getHeaders();
    assertThat(headers.get("0")).isEqualTo("afterCreation");
  }
}
