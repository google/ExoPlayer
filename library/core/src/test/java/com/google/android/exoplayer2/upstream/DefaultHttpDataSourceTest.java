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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/** Unit tests for {@link DefaultHttpDataSource}. */
@RunWith(AndroidJUnit4.class)
public class DefaultHttpDataSourceTest {

  @Test
  public void open_withSpecifiedRequestParameters_usesCorrectParameters() throws IOException {

    /*
     * This test will set HTTP default request parameters (1) in the DefaultHttpDataSource, (2) via
     * DefaultHttpDataSource.setRequestProperty() and (3) in the DataSpec instance according to the
     * table below. Values wrapped in '*' are the ones that should be set in the connection request.
     *
     * +-----------------------+---+-----+-----+-----+-----+-----+
     * |                       |            Header Key           |
     * +-----------------------+---+-----+-----+-----+-----+-----+
     * |       Location        | 0 |  1  |  2  |  3  |  4  |  5  |
     * +-----------------------+---+-----+-----+-----+-----+-----+
     * | Default               |*Y*|  Y  |  Y  |     |     |     |
     * | DefaultHttpDataSource |   | *Y* |  Y  |  Y  | *Y* |     |
     * | DataSpec              |   |     | *Y* | *Y* |     | *Y* |
     * +-----------------------+---+-----+-----+-----+-----+-----+
     */

    String defaultParameter = "Default";
    String dataSourceInstanceParameter = "DefaultHttpDataSource";
    String dataSpecParameter = "Dataspec";

    HttpDataSource.RequestProperties defaultParameters = new HttpDataSource.RequestProperties();
    defaultParameters.set("0", defaultParameter);
    defaultParameters.set("1", defaultParameter);
    defaultParameters.set("2", defaultParameter);

    DefaultHttpDataSource defaultHttpDataSource =
        Mockito.spy(
            new DefaultHttpDataSource(
                /* userAgent= */ "testAgent",
                /* connectTimeoutMillis= */ 1000,
                /* readTimeoutMillis= */ 1000,
                /* allowCrossProtocolRedirects= */ false,
                defaultParameters));

    Map<String, String> sentRequestProperties = new HashMap<>();
    HttpURLConnection mockHttpUrlConnection = makeMockHttpUrlConnection(sentRequestProperties);
    Mockito.doReturn(mockHttpUrlConnection)
        .when(defaultHttpDataSource)
        .openConnection(ArgumentMatchers.any());

    defaultHttpDataSource.setRequestProperty("1", dataSourceInstanceParameter);
    defaultHttpDataSource.setRequestProperty("2", dataSourceInstanceParameter);
    defaultHttpDataSource.setRequestProperty("3", dataSourceInstanceParameter);
    defaultHttpDataSource.setRequestProperty("4", dataSourceInstanceParameter);

    Map<String, String> dataSpecRequestProperties = new HashMap<>();
    dataSpecRequestProperties.put("2", dataSpecParameter);
    dataSpecRequestProperties.put("3", dataSpecParameter);
    dataSpecRequestProperties.put("5", dataSpecParameter);

    DataSpec dataSpec =
        new DataSpec.Builder()
            .setUri("http://www.google.com")
            .setHttpBody(new byte[] {0, 0, 0, 0})
            .setLength(1)
            .setKey("key")
            .setHttpRequestHeaders(dataSpecRequestProperties)
            .build();

    defaultHttpDataSource.open(dataSpec);

    assertThat(sentRequestProperties.get("0")).isEqualTo(defaultParameter);
    assertThat(sentRequestProperties.get("1")).isEqualTo(dataSourceInstanceParameter);
    assertThat(sentRequestProperties.get("2")).isEqualTo(dataSpecParameter);
    assertThat(sentRequestProperties.get("3")).isEqualTo(dataSpecParameter);
    assertThat(sentRequestProperties.get("4")).isEqualTo(dataSourceInstanceParameter);
    assertThat(sentRequestProperties.get("5")).isEqualTo(dataSpecParameter);
  }

  /**
   * Creates a mock {@link HttpURLConnection} that stores all request parameters inside {@code
   * requestProperties}.
   */
  private static HttpURLConnection makeMockHttpUrlConnection(Map<String, String> requestProperties)
      throws IOException {
    HttpURLConnection mockHttpUrlConnection = Mockito.mock(HttpURLConnection.class);
    Mockito.when(mockHttpUrlConnection.usingProxy()).thenReturn(false);

    Mockito.when(mockHttpUrlConnection.getInputStream())
        .thenReturn(new ByteArrayInputStream(new byte[128]));

    Mockito.when(mockHttpUrlConnection.getOutputStream()).thenReturn(new ByteArrayOutputStream());

    Mockito.when(mockHttpUrlConnection.getResponseCode()).thenReturn(200);
    Mockito.when(mockHttpUrlConnection.getResponseMessage()).thenReturn("OK");

    Mockito.doAnswer(
            (invocation) -> {
              String key = invocation.getArgument(0);
              String value = invocation.getArgument(1);
              requestProperties.put(key, value);
              return null;
            })
        .when(mockHttpUrlConnection)
        .setRequestProperty(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());

    return mockHttpUrlConnection;
  }
}
