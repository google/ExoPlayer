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

package androidx.media3.datasource;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.fail;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DataSpec}. */
@RunWith(AndroidJUnit4.class)
public class DataSpecTest {

  @SuppressWarnings("deprecation")
  @Test
  public void createDataSpec_withDefaultValues() {
    Uri uri = Uri.parse("www.google.com");

    DataSpec dataSpec = new DataSpec(uri);
    assertDefaultDataSpec(dataSpec, uri);

    dataSpec = new DataSpec(uri, /* position= */ 0, C.LENGTH_UNSET, /* key= */ null);
    assertDefaultDataSpec(dataSpec, uri);
  }

  @Test
  public void createDataSpec_withBuilder_withDefaultValues() {
    Uri uri = Uri.parse("www.google.com");

    DataSpec dataSpec = new DataSpec.Builder().setUri(uri).build();
    assertDefaultDataSpec(dataSpec, uri);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void createDataSpec_deprecatedConstructor_setsSomeValues() {
    Uri uri = Uri.parse("www.google.com");

    DataSpec dataSpec = new DataSpec(uri, /* position= */ 150, /* length= */ 5, /* key= */ "key");

    assertThat(dataSpec.uri).isEqualTo(uri);
    assertThat(dataSpec.httpMethod).isEqualTo(DataSpec.HTTP_METHOD_GET);
    assertThat(dataSpec.httpBody).isNull();
    assertThat(dataSpec.httpRequestHeaders).isEmpty();
    assertThat(dataSpec.absoluteStreamPosition).isEqualTo(150);
    assertThat(dataSpec.position).isEqualTo(150);
    // uriPositionOffset = absoluteStreamPosition - position
    assertThat(dataSpec.uriPositionOffset).isEqualTo(0);
    assertThat(dataSpec.length).isEqualTo(5);
    assertThat(dataSpec.key).isEqualTo("key");
    assertThat(dataSpec.flags).isEqualTo(0);
    assertHttpRequestHeadersReadOnly(dataSpec);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void createDataSpec_withBuilder_setsValues() {
    Uri uri = Uri.parse("www.google.com");
    Map<String, String> httpRequestHeaders = createHttpRequestHeaders(3);
    byte[] httpBody = new byte[] {0, 1, 2, 3};
    Object customData = new Object();

    DataSpec dataSpec =
        new DataSpec.Builder()
            .setUri(uri)
            .setUriPositionOffset(50)
            .setHttpMethod(DataSpec.HTTP_METHOD_POST)
            .setHttpBody(httpBody)
            .setPosition(150)
            .setLength(5)
            .setKey("key")
            .setFlags(DataSpec.FLAG_ALLOW_GZIP)
            .setHttpRequestHeaders(httpRequestHeaders)
            .setCustomData(customData)
            .build();

    assertThat(dataSpec.uri).isEqualTo(uri);
    assertThat(dataSpec.uriPositionOffset).isEqualTo(50);
    assertThat(dataSpec.httpMethod).isEqualTo(DataSpec.HTTP_METHOD_POST);
    assertThat(dataSpec.httpBody).isEqualTo(httpBody);
    assertThat(dataSpec.httpRequestHeaders).isEqualTo(httpRequestHeaders);
    // absoluteStreamPosition = uriPositionOffset + position
    assertThat(dataSpec.absoluteStreamPosition).isEqualTo(200);
    assertThat(dataSpec.position).isEqualTo(150);
    assertThat(dataSpec.length).isEqualTo(5);
    assertThat(dataSpec.key).isEqualTo("key");
    assertThat(dataSpec.flags).isEqualTo(DataSpec.FLAG_ALLOW_GZIP);
    assertThat(dataSpec.customData).isEqualTo(customData);
    assertHttpRequestHeadersReadOnly(dataSpec);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void buildUponDataSpec_setsValues() {
    Uri uri = Uri.parse("www.google.com");
    Map<String, String> httpRequestHeaders = createHttpRequestHeaders(3);
    byte[] httpBody = new byte[] {0, 1, 2, 3};
    Object customData = new Object();

    DataSpec dataSpec =
        new DataSpec.Builder()
            .setUri(uri)
            .setUriPositionOffset(50)
            .setHttpMethod(DataSpec.HTTP_METHOD_POST)
            .setHttpBody(httpBody)
            .setPosition(150)
            .setLength(5)
            .setKey("key")
            .setFlags(DataSpec.FLAG_ALLOW_GZIP)
            .setHttpRequestHeaders(httpRequestHeaders)
            .setCustomData(customData)
            .build();

    // Build upon the DataSpec.
    dataSpec = dataSpec.buildUpon().build();

    assertThat(dataSpec.uri).isEqualTo(uri);
    assertThat(dataSpec.uriPositionOffset).isEqualTo(50);
    assertThat(dataSpec.httpMethod).isEqualTo(DataSpec.HTTP_METHOD_POST);
    assertThat(dataSpec.httpBody).isEqualTo(httpBody);
    assertThat(dataSpec.httpRequestHeaders).isEqualTo(httpRequestHeaders);
    // absoluteStreamPosition = uriPositionOffset + position
    assertThat(dataSpec.absoluteStreamPosition).isEqualTo(200);
    assertThat(dataSpec.position).isEqualTo(150);
    assertThat(dataSpec.length).isEqualTo(5);
    assertThat(dataSpec.key).isEqualTo("key");
    assertThat(dataSpec.flags).isEqualTo(DataSpec.FLAG_ALLOW_GZIP);
    assertThat(dataSpec.customData).isEqualTo(customData);
    assertHttpRequestHeadersReadOnly(dataSpec);
  }

  @Test
  public void withUri_copiesHttpRequestHeaders() {
    Map<String, String> httpRequestHeaders = createHttpRequestHeaders(5);
    DataSpec dataSpec = createDataSpecWithHttpRequestHeaders(httpRequestHeaders);

    DataSpec dataSpecCopy = dataSpec.withUri(Uri.parse("www.new-uri.com"));

    assertThat(dataSpecCopy.httpRequestHeaders).isEqualTo(httpRequestHeaders);
  }

  @Test
  public void subrange_copiesHttpRequestHeaders() {
    Map<String, String> httpRequestHeaders = createHttpRequestHeaders(5);
    DataSpec dataSpec = createDataSpecWithHttpRequestHeaders(httpRequestHeaders);

    DataSpec dataSpecCopy = dataSpec.subrange(2);

    assertThat(dataSpecCopy.httpRequestHeaders).isEqualTo(httpRequestHeaders);
  }

  @Test
  public void subrange_withOffsetAndLength_copiesHttpRequestHeaders() {
    Map<String, String> httpRequestHeaders = createHttpRequestHeaders(5);
    DataSpec dataSpec = createDataSpecWithHttpRequestHeaders(httpRequestHeaders);

    DataSpec dataSpecCopy = dataSpec.subrange(2, 2);

    assertThat(dataSpecCopy.httpRequestHeaders).isEqualTo(httpRequestHeaders);
  }

  @Test
  public void withRequestHeaders_setsCorrectHeaders() {
    Map<String, String> httpRequestHeaders = createHttpRequestHeaders(5);
    DataSpec dataSpec = createDataSpecWithHttpRequestHeaders(httpRequestHeaders);

    Map<String, String> newRequestHeaders = createHttpRequestHeaders(5, 10);
    DataSpec dataSpecCopy = dataSpec.withRequestHeaders(newRequestHeaders);

    assertThat(dataSpecCopy.httpRequestHeaders).isEqualTo(newRequestHeaders);
  }

  @Test
  public void withAdditionalHeaders_setsCorrectHeaders() {
    Map<String, String> httpRequestHeaders = createHttpRequestHeaders(5);
    DataSpec dataSpec = createDataSpecWithHttpRequestHeaders(httpRequestHeaders);
    Map<String, String> additionalHeaders = createHttpRequestHeaders(5, 10);
    // additionalHeaders may overwrite a header key
    String existingKey = httpRequestHeaders.keySet().iterator().next();
    additionalHeaders.put(existingKey, "overwritten");
    Map<String, String> expectedHeaders = new HashMap<>(httpRequestHeaders);
    expectedHeaders.putAll(additionalHeaders);

    DataSpec dataSpecCopy = dataSpec.withAdditionalHeaders(additionalHeaders);

    assertThat(dataSpecCopy.httpRequestHeaders).isEqualTo(expectedHeaders);
  }

  private static Map<String, String> createHttpRequestHeaders(int howMany) {
    return createHttpRequestHeaders(0, howMany);
  }

  private static Map<String, String> createHttpRequestHeaders(int from, int to) {
    assertThat(from).isLessThan(to);

    Map<String, String> httpRequestParameters = new HashMap<>();
    for (int i = from; i < to; i++) {
      httpRequestParameters.put("key-" + i, "value-" + i);
    }

    return httpRequestParameters;
  }

  private static DataSpec createDataSpecWithHttpRequestHeaders(
      Map<String, String> httpRequestHeaders) {
    return new DataSpec.Builder()
        .setUri("www.google.com")
        .setHttpRequestHeaders(httpRequestHeaders)
        .build();
  }

  @SuppressWarnings("deprecation")
  private static void assertDefaultDataSpec(DataSpec dataSpec, Uri uri) {
    assertThat(dataSpec.uri).isEqualTo(uri);
    assertThat(dataSpec.uriPositionOffset).isEqualTo(0);
    assertThat(dataSpec.httpMethod).isEqualTo(DataSpec.HTTP_METHOD_GET);
    assertThat(dataSpec.httpBody).isNull();
    assertThat(dataSpec.httpRequestHeaders).isEmpty();
    assertThat(dataSpec.absoluteStreamPosition).isEqualTo(0);
    assertThat(dataSpec.position).isEqualTo(0);
    assertThat(dataSpec.length).isEqualTo(C.LENGTH_UNSET);
    assertThat(dataSpec.key).isNull();
    assertThat(dataSpec.flags).isEqualTo(0);
    assertThat(dataSpec.customData).isNull();
    assertHttpRequestHeadersReadOnly(dataSpec);
  }

  private static void assertHttpRequestHeadersReadOnly(DataSpec dataSpec) {
    try {
      dataSpec.httpRequestHeaders.put("key", "value");
      fail();
    } catch (UnsupportedOperationException expected) {
      // Expected
    }
  }
}
