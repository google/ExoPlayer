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

package com.google.android.exoplayer2.ext.okhttp;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import java.util.HashMap;
import java.util.Map;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/** Unit tests for {@link OkHttpDataSource}. */
@RunWith(AndroidJUnit4.class)
public class OkHttpDataSourceTest {

  @Test
  public void open_setsCorrectHeaders() throws HttpDataSource.HttpDataSourceException {
    /*
     * This test will set HTTP default request parameters (1) in the OkHttpDataSource, (2) via
     * OkHttpDataSource.setRequestProperty() and (3) in the DataSpec instance according to the table
     * below. Values wrapped in '*' are the ones that should be set in the connection request.
     *
     * +-----------------------+---+-----+-----+-----+-----+-----+
     * |                       |            Header Key           |
     * +-----------------------+---+-----+-----+-----+-----+-----+
     * |       Location        | 0 |  1  |  2  |  3  |  4  |  5  |
     * +-----------------------+---+-----+-----+-----+-----+-----+
     * | Default               |*Y*|  Y  |  Y  |     |     |     |
     * | OkHttpDataSource      |   | *Y* |  Y  |  Y  | *Y* |     |
     * | DataSpec              |   |     | *Y* | *Y* |     | *Y* |
     * +-----------------------+---+-----+-----+-----+-----+-----+
     */

    String defaultValue = "Default";
    String okHttpDataSourceValue = "OkHttpDataSource";
    String dataSpecValue = "DataSpec";

    // 1. Default properties on OkHttpDataSource
    HttpDataSource.RequestProperties defaultRequestProperties =
        new HttpDataSource.RequestProperties();
    defaultRequestProperties.set("0", defaultValue);
    defaultRequestProperties.set("1", defaultValue);
    defaultRequestProperties.set("2", defaultValue);

    Call.Factory mockCallFactory = Mockito.mock(Call.Factory.class);
    OkHttpDataSource okHttpDataSource =
        new OkHttpDataSource(
            mockCallFactory, "testAgent", /* cacheControl= */ null, defaultRequestProperties);

    // 2. Additional properties set with setRequestProperty().
    okHttpDataSource.setRequestProperty("1", okHttpDataSourceValue);
    okHttpDataSource.setRequestProperty("2", okHttpDataSourceValue);
    okHttpDataSource.setRequestProperty("3", okHttpDataSourceValue);
    okHttpDataSource.setRequestProperty("4", okHttpDataSourceValue);

    // 3. DataSpec properties
    Map<String, String> dataSpecRequestProperties = new HashMap<>();
    dataSpecRequestProperties.put("2", dataSpecValue);
    dataSpecRequestProperties.put("3", dataSpecValue);
    dataSpecRequestProperties.put("5", dataSpecValue);

    DataSpec dataSpec =
        new DataSpec.Builder()
            .setUri("http://www.google.com")
            .setPosition(1000)
            .setLength(5000)
            .setHttpRequestHeaders(dataSpecRequestProperties)
            .build();

    Mockito.doAnswer(
            invocation -> {
              Request request = invocation.getArgument(0);
              assertThat(request.header("0")).isEqualTo(defaultValue);
              assertThat(request.header("1")).isEqualTo(okHttpDataSourceValue);
              assertThat(request.header("2")).isEqualTo(dataSpecValue);
              assertThat(request.header("3")).isEqualTo(dataSpecValue);
              assertThat(request.header("4")).isEqualTo(okHttpDataSourceValue);
              assertThat(request.header("5")).isEqualTo(dataSpecValue);

              // return a Call whose .execute() will return a mock Response
              Call returnValue = Mockito.mock(Call.class);
              Mockito.doReturn(
                      new Response.Builder()
                          .request(request)
                          .protocol(Protocol.HTTP_1_1)
                          .code(200)
                          .message("OK")
                          .body(ResponseBody.create(MediaType.parse("text/plain"), ""))
                          .build())
                  .when(returnValue)
                  .execute();
              return returnValue;
            })
        .when(mockCallFactory)
        .newCall(ArgumentMatchers.any());
    okHttpDataSource.open(dataSpec);
  }
}
