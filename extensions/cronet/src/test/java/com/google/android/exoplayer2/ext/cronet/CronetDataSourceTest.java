/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.cronet;

import static com.google.common.truth.Truth.assertThat;
import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;
import android.os.ConditionVariable;
import android.os.SystemClock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Headers;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.chromium.net.CronetEngine;
import org.chromium.net.NetworkException;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.shadows.ShadowLooper;

/** Tests for {@link CronetDataSource}. */
@RunWith(AndroidJUnit4.class)
public final class CronetDataSourceTest {

  private static final int TEST_CONNECT_TIMEOUT_MS = 100;
  private static final int TEST_READ_TIMEOUT_MS = 100;
  private static final String TEST_URL = "http://google.com";
  private static final String TEST_CONTENT_TYPE = "test/test";
  private static final byte[] TEST_POST_BODY = Util.getUtf8Bytes("test post body");
  private static final long TEST_CONTENT_LENGTH = 16000L;
  private static final int TEST_CONNECTION_STATUS = 5;
  private static final int TEST_INVALID_CONNECTION_STATUS = -1;

  private DataSpec testDataSpec;
  private DataSpec testPostDataSpec;
  private DataSpec testHeadDataSpec;
  private Map<String, String> testResponseHeader;
  private UrlResponseInfo testUrlResponseInfo;

  @Mock private UrlRequest.Builder mockUrlRequestBuilder;
  @Mock private UrlRequest mockUrlRequest;
  @Mock private TransferListener mockTransferListener;
  @Mock private NetworkException mockNetworkException;
  @Mock private CronetEngine mockCronetEngine;

  private ExecutorService executorService;
  private CronetDataSource dataSourceUnderTest;
  private boolean redirectCalled;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    Map<String, String> defaultRequestProperties = new HashMap<>();
    defaultRequestProperties.put("defaultHeader1", "defaultValue1");
    defaultRequestProperties.put("defaultHeader2", "defaultValue2");

    executorService = Executors.newSingleThreadExecutor();
    dataSourceUnderTest =
        (CronetDataSource)
            new CronetDataSource.Factory(new CronetEngineWrapper(mockCronetEngine), executorService)
                .setConnectionTimeoutMs(TEST_CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(TEST_READ_TIMEOUT_MS)
                .setResetTimeoutOnRedirects(true)
                .setDefaultRequestProperties(defaultRequestProperties)
                .createDataSource();
    dataSourceUnderTest.addTransferListener(mockTransferListener);
    when(mockCronetEngine.newUrlRequestBuilder(
            anyString(), any(UrlRequest.Callback.class), any(Executor.class)))
        .thenReturn(mockUrlRequestBuilder);
    when(mockUrlRequestBuilder.allowDirectExecutor()).thenReturn(mockUrlRequestBuilder);
    when(mockUrlRequestBuilder.build()).thenReturn(mockUrlRequest);
    mockStatusResponse();

    testDataSpec = new DataSpec(Uri.parse(TEST_URL));
    testPostDataSpec =
        new DataSpec.Builder()
            .setUri(TEST_URL)
            .setHttpMethod(DataSpec.HTTP_METHOD_POST)
            .setHttpBody(TEST_POST_BODY)
            .build();
    testHeadDataSpec =
        new DataSpec.Builder().setUri(TEST_URL).setHttpMethod(DataSpec.HTTP_METHOD_HEAD).build();
    testResponseHeader = new HashMap<>();
    testResponseHeader.put("Content-Type", TEST_CONTENT_TYPE);
    // This value can be anything since the DataSpec is unset.
    testResponseHeader.put("Content-Length", Long.toString(TEST_CONTENT_LENGTH));
    testUrlResponseInfo = createUrlResponseInfo(200); // statusCode
  }

  @After
  public void tearDown() {
    executorService.shutdown();
  }

  private UrlResponseInfo createUrlResponseInfo(int statusCode) {
    return createUrlResponseInfoWithUrl(TEST_URL, statusCode);
  }

  private UrlResponseInfo createUrlResponseInfoWithUrl(String url, int statusCode) {
    ArrayList<Map.Entry<String, String>> responseHeaderList = new ArrayList<>();
    Map<String, List<String>> responseHeaderMap = new HashMap<>();
    for (Map.Entry<String, String> entry : testResponseHeader.entrySet()) {
      responseHeaderList.add(entry);
      responseHeaderMap.put(entry.getKey(), Collections.singletonList(entry.getValue()));
    }
    return new UrlResponseInfo() {
      @Override
      public String getUrl() {
        return url;
      }

      @Override
      public List<String> getUrlChain() {
        return Collections.singletonList(url);
      }

      @Override
      public int getHttpStatusCode() {
        return statusCode;
      }

      @Override
      public String getHttpStatusText() {
        return null;
      }

      @Override
      public List<Map.Entry<String, String>> getAllHeadersAsList() {
        return responseHeaderList;
      }

      @Override
      public Map<String, List<String>> getAllHeaders() {
        return responseHeaderMap;
      }

      @Override
      public boolean wasCached() {
        return false;
      }

      @Override
      public String getNegotiatedProtocol() {
        return null;
      }

      @Override
      public String getProxyServer() {
        return null;
      }

      @Override
      public long getReceivedByteCount() {
        return 0;
      }
    };
  }

  @Test
  public void openingTwiceThrows() throws HttpDataSourceException {
    mockResponseStartSuccess();
    dataSourceUnderTest.open(testDataSpec);
    try {
      dataSourceUnderTest.open(testDataSpec);
      fail("Expected IllegalStateException.");
    } catch (IllegalStateException e) {
      // Expected.
    }
  }

  @Test
  public void callbackFromPreviousRequest() throws HttpDataSourceException {
    mockResponseStartSuccess();

    dataSourceUnderTest.open(testDataSpec);
    dataSourceUnderTest.close();
    // Prepare a mock UrlRequest to be used in the second open() call.
    final UrlRequest mockUrlRequest2 = mock(UrlRequest.class);
    when(mockUrlRequestBuilder.build()).thenReturn(mockUrlRequest2);
    doAnswer(
            invocation -> {
              // Invoke the callback for the previous request.
              dataSourceUnderTest.urlRequestCallback.onFailed(
                  mockUrlRequest, testUrlResponseInfo, mockNetworkException);
              dataSourceUnderTest.urlRequestCallback.onResponseStarted(
                  mockUrlRequest2, testUrlResponseInfo);
              return null;
            })
        .when(mockUrlRequest2)
        .start();
    dataSourceUnderTest.open(testDataSpec);
  }

  @Test
  public void requestStartCalled() throws HttpDataSourceException {
    mockResponseStartSuccess();

    dataSourceUnderTest.open(testDataSpec);
    verify(mockCronetEngine)
        .newUrlRequestBuilder(eq(TEST_URL), any(UrlRequest.Callback.class), any(Executor.class));
    verify(mockUrlRequest).start();
  }

  @Test
  public void requestSetsRangeHeader() throws HttpDataSourceException {
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 1000, 5000);
    mockResponseStartSuccess();
    mockReadSuccess(0, 1000);

    dataSourceUnderTest.open(testDataSpec);
    // The header value to add is current position to current position + length - 1.
    verify(mockUrlRequestBuilder).addHeader("Range", "bytes=1000-5999");
  }

  @Test
  public void requestHeadersSet() throws HttpDataSourceException {
    Map<String, String> headersSet = new HashMap<>();
    doAnswer(
            (invocation) -> {
              String key = invocation.getArgument(0);
              String value = invocation.getArgument(1);
              headersSet.put(key, value);
              return null;
            })
        .when(mockUrlRequestBuilder)
        .addHeader(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());

    dataSourceUnderTest.setRequestProperty("defaultHeader2", "dataSourceOverridesDefault");
    dataSourceUnderTest.setRequestProperty("dataSourceHeader1", "dataSourceValue1");
    dataSourceUnderTest.setRequestProperty("dataSourceHeader2", "dataSourceValue2");

    Map<String, String> dataSpecRequestProperties = new HashMap<>();
    dataSpecRequestProperties.put("defaultHeader3", "dataSpecOverridesAll");
    dataSpecRequestProperties.put("dataSourceHeader2", "dataSpecOverridesDataSource");
    dataSpecRequestProperties.put("dataSpecHeader1", "dataSpecValue1");

    testDataSpec =
        new DataSpec.Builder()
            .setUri(TEST_URL)
            .setHttpRequestHeaders(dataSpecRequestProperties)
            .build();
    mockResponseStartSuccess();

    dataSourceUnderTest.open(testDataSpec);

    assertThat(headersSet.get("defaultHeader1")).isEqualTo("defaultValue1");
    assertThat(headersSet.get("defaultHeader2")).isEqualTo("dataSourceOverridesDefault");
    assertThat(headersSet.get("defaultHeader3")).isEqualTo("dataSpecOverridesAll");
    assertThat(headersSet.get("dataSourceHeader1")).isEqualTo("dataSourceValue1");
    assertThat(headersSet.get("dataSourceHeader2")).isEqualTo("dataSpecOverridesDataSource");
    assertThat(headersSet.get("dataSpecHeader1")).isEqualTo("dataSpecValue1");

    verify(mockUrlRequest).start();
  }

  @Test
  public void requestOpen() throws HttpDataSourceException {
    mockResponseStartSuccess();
    assertThat(dataSourceUnderTest.open(testDataSpec)).isEqualTo(TEST_CONTENT_LENGTH);
    verify(mockTransferListener)
        .onTransferStart(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true);
  }

  @Test
  public void requestOpenGzippedCompressedReturnsDataSpecLength() throws HttpDataSourceException {
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 0, 5000);
    testResponseHeader.put("Content-Encoding", "gzip");
    testResponseHeader.put("Content-Length", Long.toString(50L));
    mockResponseStartSuccess();

    assertThat(dataSourceUnderTest.open(testDataSpec)).isEqualTo(5000 /* contentLength */);
    verify(mockTransferListener)
        .onTransferStart(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true);
  }

  @Test
  public void requestOpenFail() {
    mockResponseStartFailure();

    try {
      dataSourceUnderTest.open(testDataSpec);
      fail("HttpDataSource.HttpDataSourceException expected");
    } catch (HttpDataSourceException e) {
      // Check for connection not automatically closed.
      assertThat(e).hasCauseThat().isNotInstanceOf(UnknownHostException.class);
      verify(mockUrlRequest, never()).cancel();
      verify(mockTransferListener, never())
          .onTransferStart(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true);
    }
  }

  @Test
  public void open_ifBodyIsSetWithoutContentTypeHeader_fails() {
    testDataSpec =
        new DataSpec.Builder()
            .setUri(TEST_URL)
            .setHttpMethod(DataSpec.HTTP_METHOD_POST)
            .setHttpBody(new byte[1024])
            .setPosition(200)
            .setLength(1024)
            .setKey("key")
            .build();

    try {
      dataSourceUnderTest.open(testDataSpec);
      fail();
    } catch (IOException expected) {
      // Expected
    }
  }

  @Test
  public void requestOpenFailDueToDnsFailure() {
    mockResponseStartFailure();
    when(mockNetworkException.getErrorCode())
        .thenReturn(NetworkException.ERROR_HOSTNAME_NOT_RESOLVED);

    try {
      dataSourceUnderTest.open(testDataSpec);
      fail("HttpDataSource.HttpDataSourceException expected");
    } catch (HttpDataSourceException e) {
      // Check for connection not automatically closed.
      assertThat(e).hasCauseThat().isInstanceOf(UnknownHostException.class);
      verify(mockUrlRequest, never()).cancel();
      verify(mockTransferListener, never())
          .onTransferStart(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true);
    }
  }

  @Test
  public void requestOpen_withNon2xxResponseCode_throwsInvalidResponseCodeExceptionWithBody()
      throws Exception {
    mockResponseStartSuccess();
    // Use a size larger than CronetDataSource.READ_BUFFER_SIZE_BYTES
    int responseLength = 40 * 1024;
    mockReadSuccess(/* position= */ 0, /* length= */ responseLength);
    testUrlResponseInfo = createUrlResponseInfo(/* statusCode= */ 500);

    try {
      dataSourceUnderTest.open(testDataSpec);
      fail("InvalidResponseCodeException expected");
    } catch (InvalidResponseCodeException e) {
      assertThat(e.responseBody).isEqualTo(buildTestDataArray(0, responseLength));
      // Check for connection not automatically closed.
      verify(mockUrlRequest, never()).cancel();
      verify(mockTransferListener, never())
          .onTransferStart(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true);
    }
  }

  @Test
  public void
      requestOpen_withNon2xxResponseCode_andRequestBodyReadFailure_throwsInvalidResponseCodeExceptionWithoutBody()
          throws Exception {
    mockResponseStartSuccess();
    mockReadFailure();
    testUrlResponseInfo = createUrlResponseInfo(/* statusCode= */ 500);

    try {
      dataSourceUnderTest.open(testDataSpec);
      fail("InvalidResponseCodeException expected");
    } catch (InvalidResponseCodeException e) {
      assertThat(e.responseBody).isEmpty();
      // Check for connection not automatically closed.
      verify(mockUrlRequest, never()).cancel();
      verify(mockTransferListener, never())
          .onTransferStart(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true);
    }
  }

  @Test
  public void requestOpenValidatesContentTypePredicate() {
    mockResponseStartSuccess();

    ArrayList<String> testedContentTypes = new ArrayList<>();
    dataSourceUnderTest.setContentTypePredicate(
        (String input) -> {
          testedContentTypes.add(input);
          return false;
        });

    try {
      dataSourceUnderTest.open(testDataSpec);
      fail("HttpDataSource.HttpDataSourceException expected");
    } catch (HttpDataSourceException e) {
      assertThat(e).isInstanceOf(HttpDataSource.InvalidContentTypeException.class);
      // Check for connection not automatically closed.
      verify(mockUrlRequest, never()).cancel();
      assertThat(testedContentTypes).hasSize(1);
      assertThat(testedContentTypes.get(0)).isEqualTo(TEST_CONTENT_TYPE);
    }
  }

  @Test
  public void postRequestOpen() throws HttpDataSourceException {
    mockResponseStartSuccess();

    dataSourceUnderTest.setRequestProperty("Content-Type", TEST_CONTENT_TYPE);
    assertThat(dataSourceUnderTest.open(testPostDataSpec)).isEqualTo(TEST_CONTENT_LENGTH);
    verify(mockTransferListener)
        .onTransferStart(dataSourceUnderTest, testPostDataSpec, /* isNetwork= */ true);
  }

  @Test
  public void postRequestOpenValidatesContentType() {
    mockResponseStartSuccess();

    try {
      dataSourceUnderTest.open(testPostDataSpec);
      fail("HttpDataSource.HttpDataSourceException expected");
    } catch (HttpDataSourceException e) {
      verify(mockUrlRequest, never()).start();
    }
  }

  @Test
  public void postRequestOpenRejects307Redirects() {
    mockResponseStartSuccess();
    mockResponseStartRedirect();

    try {
      dataSourceUnderTest.setRequestProperty("Content-Type", TEST_CONTENT_TYPE);
      dataSourceUnderTest.open(testPostDataSpec);
      fail("HttpDataSource.HttpDataSourceException expected");
    } catch (HttpDataSourceException e) {
      verify(mockUrlRequest, never()).followRedirect();
    }
  }

  @Test
  public void headRequestOpen() throws HttpDataSourceException {
    mockResponseStartSuccess();
    dataSourceUnderTest.open(testHeadDataSpec);
    verify(mockTransferListener)
        .onTransferStart(dataSourceUnderTest, testHeadDataSpec, /* isNetwork= */ true);
    dataSourceUnderTest.close();
  }

  @Test
  public void requestReadTwice() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadSuccess(0, 16);

    dataSourceUnderTest.open(testDataSpec);

    byte[] returnedBuffer = new byte[8];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 8);
    assertThat(returnedBuffer).isEqualTo(buildTestDataArray(0, 8));
    assertThat(bytesRead).isEqualTo(8);

    returnedBuffer = new byte[8];
    bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 8);
    assertThat(returnedBuffer).isEqualTo(buildTestDataArray(8, 8));
    assertThat(bytesRead).isEqualTo(8);

    // Should have only called read on cronet once.
    verify(mockUrlRequest, times(1)).read(any(ByteBuffer.class));
    verify(mockTransferListener, times(2))
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 8);
  }

  @Test
  public void secondRequestNoContentLength() throws HttpDataSourceException {
    mockResponseStartSuccess();
    testResponseHeader.put("Content-Length", Long.toString(1L));
    mockReadSuccess(0, 16);

    // First request.
    dataSourceUnderTest.open(testDataSpec);
    byte[] returnedBuffer = new byte[8];
    dataSourceUnderTest.read(returnedBuffer, 0, 1);
    dataSourceUnderTest.close();

    testResponseHeader.remove("Content-Length");
    mockReadSuccess(0, 16);

    // Second request.
    dataSourceUnderTest.open(testDataSpec);
    returnedBuffer = new byte[16];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 10);
    assertThat(bytesRead).isEqualTo(10);
    bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 10);
    assertThat(bytesRead).isEqualTo(6);
    bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 10);
    assertThat(bytesRead).isEqualTo(C.RESULT_END_OF_INPUT);
  }

  @Test
  public void readWithOffset() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadSuccess(0, 16);

    dataSourceUnderTest.open(testDataSpec);

    byte[] returnedBuffer = new byte[16];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 8, 8);
    assertThat(bytesRead).isEqualTo(8);
    assertThat(returnedBuffer).isEqualTo(prefixZeros(buildTestDataArray(0, 8), 16));
    verify(mockTransferListener)
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 8);
  }

  @Test
  public void rangeRequestWith206Response() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadSuccess(1000, 5000);
    testUrlResponseInfo = createUrlResponseInfo(206); // Server supports range requests.
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 1000, 5000);

    long length = dataSourceUnderTest.open(testDataSpec);
    assertThat(length).isEqualTo(5000);

    byte[] returnedBuffer = new byte[16];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 16);
    assertThat(bytesRead).isEqualTo(16);
    assertThat(returnedBuffer).isEqualTo(buildTestDataArray(1000, 16));
    verify(mockTransferListener)
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 16);
  }

  @Test
  public void rangeRequestWith200Response() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadSuccess(0, 7000);
    testUrlResponseInfo = createUrlResponseInfo(200); // Server does not support range requests.
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 1000, 5000);

    long length = dataSourceUnderTest.open(testDataSpec);
    assertThat(length).isEqualTo(5000);

    byte[] returnedBuffer = new byte[16];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 16);
    assertThat(bytesRead).isEqualTo(16);
    assertThat(returnedBuffer).isEqualTo(buildTestDataArray(1000, 16));
    verify(mockTransferListener)
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 16);
  }

  @Test
  public void unboundedRangeRequestWith200Response() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadSuccess(0, (int) TEST_CONTENT_LENGTH);
    testUrlResponseInfo = createUrlResponseInfo(200); // Server does not support range requests.
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 1000, C.LENGTH_UNSET);

    long length = dataSourceUnderTest.open(testDataSpec);
    assertThat(length).isEqualTo(TEST_CONTENT_LENGTH - 1000);

    byte[] returnedBuffer = new byte[16];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 16);
    assertThat(bytesRead).isEqualTo(16);
    assertThat(returnedBuffer).isEqualTo(buildTestDataArray(1000, 16));
    verify(mockTransferListener)
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 16);
  }

  @Test
  public void readWithUnsetLength() throws HttpDataSourceException {
    testResponseHeader.remove("Content-Length");
    mockResponseStartSuccess();
    mockReadSuccess(0, 16);

    dataSourceUnderTest.open(testDataSpec);

    byte[] returnedBuffer = new byte[16];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 8, 8);
    assertThat(returnedBuffer).isEqualTo(prefixZeros(buildTestDataArray(0, 8), 16));
    assertThat(bytesRead).isEqualTo(8);
    verify(mockTransferListener)
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 8);
  }

  @Test
  public void readReturnsWhatItCan() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadSuccess(0, 16);

    dataSourceUnderTest.open(testDataSpec);

    byte[] returnedBuffer = new byte[24];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 24);
    assertThat(returnedBuffer).isEqualTo(suffixZeros(buildTestDataArray(0, 16), 24));
    assertThat(bytesRead).isEqualTo(16);
    verify(mockTransferListener)
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 16);
  }

  @Test
  public void closedMeansClosed() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadSuccess(0, 16);

    int bytesRead = 0;
    dataSourceUnderTest.open(testDataSpec);

    byte[] returnedBuffer = new byte[8];
    bytesRead += dataSourceUnderTest.read(returnedBuffer, 0, 8);
    assertThat(returnedBuffer).isEqualTo(buildTestDataArray(0, 8));
    assertThat(bytesRead).isEqualTo(8);

    dataSourceUnderTest.close();
    verify(mockTransferListener)
        .onTransferEnd(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true);

    try {
      bytesRead += dataSourceUnderTest.read(returnedBuffer, 0, 8);
      fail();
    } catch (IllegalStateException e) {
      // Expected.
    }

    // 16 bytes were attempted but only 8 should have been successfully read.
    assertThat(bytesRead).isEqualTo(8);
  }

  @Test
  public void overread() throws HttpDataSourceException {
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 0, 16);
    testResponseHeader.put("Content-Length", Long.toString(16L));
    mockResponseStartSuccess();
    mockReadSuccess(0, 16);

    dataSourceUnderTest.open(testDataSpec);

    byte[] returnedBuffer = new byte[8];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 8);
    assertThat(bytesRead).isEqualTo(8);
    assertThat(returnedBuffer).isEqualTo(buildTestDataArray(0, 8));

    // The current buffer is kept if not completely consumed by DataSource reader.
    returnedBuffer = new byte[8];
    bytesRead += dataSourceUnderTest.read(returnedBuffer, 0, 6);
    assertThat(bytesRead).isEqualTo(14);
    assertThat(returnedBuffer).isEqualTo(suffixZeros(buildTestDataArray(8, 6), 8));

    // 2 bytes left at this point.
    returnedBuffer = new byte[8];
    bytesRead += dataSourceUnderTest.read(returnedBuffer, 0, 8);
    assertThat(bytesRead).isEqualTo(16);
    assertThat(returnedBuffer).isEqualTo(suffixZeros(buildTestDataArray(14, 2), 8));

    // Should have only called read on cronet once.
    verify(mockUrlRequest, times(1)).read(any(ByteBuffer.class));
    verify(mockTransferListener, times(1))
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 8);
    verify(mockTransferListener, times(1))
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 6);
    verify(mockTransferListener, times(1))
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 2);

    // Now we already returned the 16 bytes initially asked.
    // Try to read again even though all requested 16 bytes are already returned.
    // Return C.RESULT_END_OF_INPUT
    returnedBuffer = new byte[16];
    int bytesOverRead = dataSourceUnderTest.read(returnedBuffer, 0, 16);
    assertThat(bytesOverRead).isEqualTo(C.RESULT_END_OF_INPUT);
    assertThat(returnedBuffer).isEqualTo(new byte[16]);
    // C.RESULT_END_OF_INPUT should not be reported though the TransferListener.
    verify(mockTransferListener, never())
        .onBytesTransferred(
            dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, C.RESULT_END_OF_INPUT);
    // There should still be only one call to read on cronet.
    verify(mockUrlRequest, times(1)).read(any(ByteBuffer.class));
    // Check for connection not automatically closed.
    verify(mockUrlRequest, never()).cancel();
    assertThat(bytesRead).isEqualTo(16);
  }

  @Test
  public void requestReadByteBufferTwice() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadSuccess(0, 16);

    dataSourceUnderTest.open(testDataSpec);

    ByteBuffer returnedBuffer = ByteBuffer.allocateDirect(8);
    int bytesRead = dataSourceUnderTest.read(returnedBuffer);
    assertThat(bytesRead).isEqualTo(8);
    returnedBuffer.flip();
    assertThat(copyByteBufferToArray(returnedBuffer)).isEqualTo(buildTestDataArray(0, 8));

    // Use a wrapped ByteBuffer instead of direct for coverage.
    returnedBuffer.rewind();
    bytesRead = dataSourceUnderTest.read(returnedBuffer);
    returnedBuffer.flip();
    assertThat(copyByteBufferToArray(returnedBuffer)).isEqualTo(buildTestDataArray(8, 8));
    assertThat(bytesRead).isEqualTo(8);

    // Separate cronet calls for each read.
    verify(mockUrlRequest, times(2)).read(any(ByteBuffer.class));
    verify(mockTransferListener, times(2))
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 8);
  }

  @Test
  public void requestIntermixRead() throws HttpDataSourceException {
    mockResponseStartSuccess();
    // Chunking reads into parts 6, 7, 8, 9.
    mockReadSuccess(0, 30);

    dataSourceUnderTest.open(testDataSpec);

    ByteBuffer returnedBuffer = ByteBuffer.allocateDirect(6);
    int bytesRead = dataSourceUnderTest.read(returnedBuffer);
    returnedBuffer.flip();
    assertThat(copyByteBufferToArray(returnedBuffer)).isEqualTo(buildTestDataArray(0, 6));
    assertThat(bytesRead).isEqualTo(6);

    byte[] returnedBytes = new byte[7];
    bytesRead += dataSourceUnderTest.read(returnedBytes, 0, 7);
    assertThat(returnedBytes).isEqualTo(buildTestDataArray(6, 7));
    assertThat(bytesRead).isEqualTo(6 + 7);

    returnedBuffer = ByteBuffer.allocateDirect(8);
    bytesRead += dataSourceUnderTest.read(returnedBuffer);
    returnedBuffer.flip();
    assertThat(copyByteBufferToArray(returnedBuffer)).isEqualTo(buildTestDataArray(13, 8));
    assertThat(bytesRead).isEqualTo(6 + 7 + 8);

    returnedBytes = new byte[9];
    bytesRead += dataSourceUnderTest.read(returnedBytes, 0, 9);
    assertThat(returnedBytes).isEqualTo(buildTestDataArray(21, 9));
    assertThat(bytesRead).isEqualTo(6 + 7 + 8 + 9);

    // First ByteBuffer call. The first byte[] call populates enough bytes for the rest.
    verify(mockUrlRequest, times(2)).read(any(ByteBuffer.class));
    verify(mockTransferListener, times(1))
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 6);
    verify(mockTransferListener, times(1))
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 7);
    verify(mockTransferListener, times(1))
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 8);
    verify(mockTransferListener, times(1))
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 9);
  }

  @Test
  public void secondRequestNoContentLengthReadByteBuffer() throws HttpDataSourceException {
    mockResponseStartSuccess();
    testResponseHeader.put("Content-Length", Long.toString(1L));
    mockReadSuccess(0, 16);

    // First request.
    dataSourceUnderTest.open(testDataSpec);
    ByteBuffer returnedBuffer = ByteBuffer.allocateDirect(8);
    dataSourceUnderTest.read(returnedBuffer);
    dataSourceUnderTest.close();

    testResponseHeader.remove("Content-Length");
    mockReadSuccess(0, 16);

    // Second request.
    dataSourceUnderTest.open(testDataSpec);
    returnedBuffer = ByteBuffer.allocateDirect(16);
    returnedBuffer.limit(10);
    int bytesRead = dataSourceUnderTest.read(returnedBuffer);
    assertThat(bytesRead).isEqualTo(10);
    returnedBuffer.limit(returnedBuffer.capacity());
    bytesRead = dataSourceUnderTest.read(returnedBuffer);
    assertThat(bytesRead).isEqualTo(6);
    returnedBuffer.rewind();
    bytesRead = dataSourceUnderTest.read(returnedBuffer);
    assertThat(bytesRead).isEqualTo(C.RESULT_END_OF_INPUT);
  }

  @Test
  public void rangeRequestWith206ResponseReadByteBuffer() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadSuccess(1000, 5000);
    testUrlResponseInfo = createUrlResponseInfo(206); // Server supports range requests.
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 1000, 5000);

    long length = dataSourceUnderTest.open(testDataSpec);
    assertThat(length).isEqualTo(5000);

    ByteBuffer returnedBuffer = ByteBuffer.allocateDirect(16);
    int bytesRead = dataSourceUnderTest.read(returnedBuffer);
    assertThat(bytesRead).isEqualTo(16);
    returnedBuffer.flip();
    assertThat(copyByteBufferToArray(returnedBuffer)).isEqualTo(buildTestDataArray(1000, 16));
    verify(mockTransferListener)
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 16);
  }

  @Test
  public void rangeRequestWith200ResponseReadByteBuffer() throws HttpDataSourceException {
    // Tests for skipping bytes.
    mockResponseStartSuccess();
    mockReadSuccess(0, 7000);
    testUrlResponseInfo = createUrlResponseInfo(200); // Server does not support range requests.
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 1000, 5000);

    long length = dataSourceUnderTest.open(testDataSpec);
    assertThat(length).isEqualTo(5000);

    ByteBuffer returnedBuffer = ByteBuffer.allocateDirect(16);
    int bytesRead = dataSourceUnderTest.read(returnedBuffer);
    assertThat(bytesRead).isEqualTo(16);
    returnedBuffer.flip();
    assertThat(copyByteBufferToArray(returnedBuffer)).isEqualTo(buildTestDataArray(1000, 16));
    verify(mockTransferListener)
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 16);
  }

  @Test
  public void readByteBufferWithUnsetLength() throws HttpDataSourceException {
    testResponseHeader.remove("Content-Length");
    mockResponseStartSuccess();
    mockReadSuccess(0, 16);

    dataSourceUnderTest.open(testDataSpec);

    ByteBuffer returnedBuffer = ByteBuffer.allocateDirect(16);
    returnedBuffer.limit(8);
    int bytesRead = dataSourceUnderTest.read(returnedBuffer);
    returnedBuffer.flip();
    assertThat(copyByteBufferToArray(returnedBuffer)).isEqualTo(buildTestDataArray(0, 8));
    assertThat(bytesRead).isEqualTo(8);
    verify(mockTransferListener)
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 8);
  }

  @Test
  public void readByteBufferReturnsWhatItCan() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadSuccess(0, 16);

    dataSourceUnderTest.open(testDataSpec);

    ByteBuffer returnedBuffer = ByteBuffer.allocateDirect(24);
    int bytesRead = dataSourceUnderTest.read(returnedBuffer);
    returnedBuffer.flip();
    assertThat(copyByteBufferToArray(returnedBuffer)).isEqualTo(buildTestDataArray(0, 16));
    assertThat(bytesRead).isEqualTo(16);
    verify(mockTransferListener)
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 16);
  }

  @Test
  public void overreadByteBuffer() throws HttpDataSourceException {
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 0, 16);
    testResponseHeader.put("Content-Length", Long.toString(16L));
    mockResponseStartSuccess();
    mockReadSuccess(0, 16);

    dataSourceUnderTest.open(testDataSpec);

    ByteBuffer returnedBuffer = ByteBuffer.allocateDirect(8);
    int bytesRead = dataSourceUnderTest.read(returnedBuffer);
    assertThat(bytesRead).isEqualTo(8);
    returnedBuffer.flip();
    assertThat(copyByteBufferToArray(returnedBuffer)).isEqualTo(buildTestDataArray(0, 8));

    // The current buffer is kept if not completely consumed by DataSource reader.
    returnedBuffer = ByteBuffer.allocateDirect(6);
    bytesRead += dataSourceUnderTest.read(returnedBuffer);
    assertThat(bytesRead).isEqualTo(14);
    returnedBuffer.flip();
    assertThat(copyByteBufferToArray(returnedBuffer)).isEqualTo(buildTestDataArray(8, 6));

    // 2 bytes left at this point.
    returnedBuffer = ByteBuffer.allocateDirect(8);
    bytesRead += dataSourceUnderTest.read(returnedBuffer);
    assertThat(bytesRead).isEqualTo(16);
    returnedBuffer.flip();
    assertThat(copyByteBufferToArray(returnedBuffer)).isEqualTo(buildTestDataArray(14, 2));

    // Called on each.
    verify(mockUrlRequest, times(3)).read(any(ByteBuffer.class));
    verify(mockTransferListener, times(1))
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 8);
    verify(mockTransferListener, times(1))
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 6);
    verify(mockTransferListener, times(1))
        .onBytesTransferred(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, 2);

    // Now we already returned the 16 bytes initially asked.
    // Try to read again even though all requested 16 bytes are already returned.
    // Return C.RESULT_END_OF_INPUT
    returnedBuffer = ByteBuffer.allocateDirect(16);
    int bytesOverRead = dataSourceUnderTest.read(returnedBuffer);
    assertThat(bytesOverRead).isEqualTo(C.RESULT_END_OF_INPUT);
    assertThat(returnedBuffer.position()).isEqualTo(0);
    // C.RESULT_END_OF_INPUT should not be reported though the TransferListener.
    verify(mockTransferListener, never())
        .onBytesTransferred(
            dataSourceUnderTest, testDataSpec, /* isNetwork= */ true, C.RESULT_END_OF_INPUT);
    // Number of calls to cronet should not have increased.
    verify(mockUrlRequest, times(3)).read(any(ByteBuffer.class));
    // Check for connection not automatically closed.
    verify(mockUrlRequest, never()).cancel();
    assertThat(bytesRead).isEqualTo(16);
  }

  @Test
  public void closedMeansClosedReadByteBuffer() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadSuccess(0, 16);

    int bytesRead = 0;
    dataSourceUnderTest.open(testDataSpec);

    ByteBuffer returnedBuffer = ByteBuffer.allocateDirect(16);
    returnedBuffer.limit(8);
    bytesRead += dataSourceUnderTest.read(returnedBuffer);
    returnedBuffer.flip();
    assertThat(copyByteBufferToArray(returnedBuffer)).isEqualTo(buildTestDataArray(0, 8));
    assertThat(bytesRead).isEqualTo(8);

    dataSourceUnderTest.close();
    verify(mockTransferListener)
        .onTransferEnd(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true);

    try {
      bytesRead += dataSourceUnderTest.read(returnedBuffer);
      fail();
    } catch (IllegalStateException e) {
      // Expected.
    }

    // 16 bytes were attempted but only 8 should have been successfully read.
    assertThat(bytesRead).isEqualTo(8);
  }

  @Test
  public void connectTimeout() throws InterruptedException {
    long startTimeMs = SystemClock.elapsedRealtime();
    final ConditionVariable startCondition = buildUrlRequestStartedCondition();
    final CountDownLatch timedOutLatch = new CountDownLatch(1);

    new Thread() {
      @Override
      public void run() {
        try {
          dataSourceUnderTest.open(testDataSpec);
          fail();
        } catch (HttpDataSourceException e) {
          // Expected.
          assertThat(e).isInstanceOf(CronetDataSource.OpenException.class);
          assertThat(e).hasCauseThat().isInstanceOf(SocketTimeoutException.class);
          assertThat(((CronetDataSource.OpenException) e).cronetConnectionStatus)
              .isEqualTo(TEST_CONNECTION_STATUS);
          timedOutLatch.countDown();
        }
      }
    }.start();
    startCondition.block();

    // We should still be trying to open.
    assertNotCountedDown(timedOutLatch);
    // We should still be trying to open as we approach the timeout.
    setSystemClockInMsAndTriggerPendingMessages(
        /* nowMs= */ startTimeMs + TEST_CONNECT_TIMEOUT_MS - 1);
    assertNotCountedDown(timedOutLatch);
    // Now we timeout.
    setSystemClockInMsAndTriggerPendingMessages(
        /* nowMs= */ startTimeMs + TEST_CONNECT_TIMEOUT_MS + 10);
    timedOutLatch.await();

    verify(mockTransferListener, never())
        .onTransferStart(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true);
  }

  @Test
  public void connectInterrupted() throws InterruptedException {
    long startTimeMs = SystemClock.elapsedRealtime();
    final ConditionVariable startCondition = buildUrlRequestStartedCondition();
    final CountDownLatch timedOutLatch = new CountDownLatch(1);

    Thread thread =
        new Thread() {
          @Override
          public void run() {
            try {
              dataSourceUnderTest.open(testDataSpec);
              fail();
            } catch (HttpDataSourceException e) {
              // Expected.
              assertThat(e).isInstanceOf(CronetDataSource.OpenException.class);
              assertThat(e).hasCauseThat().isInstanceOf(InterruptedIOException.class);
              assertThat(((CronetDataSource.OpenException) e).cronetConnectionStatus)
                  .isEqualTo(TEST_INVALID_CONNECTION_STATUS);
              timedOutLatch.countDown();
            }
          }
        };
    thread.start();
    startCondition.block();

    // We should still be trying to open.
    assertNotCountedDown(timedOutLatch);
    // We should still be trying to open as we approach the timeout.
    setSystemClockInMsAndTriggerPendingMessages(
        /* nowMs= */ startTimeMs + TEST_CONNECT_TIMEOUT_MS - 1);
    assertNotCountedDown(timedOutLatch);
    // Now we interrupt.
    thread.interrupt();
    timedOutLatch.await();

    verify(mockTransferListener, never())
        .onTransferStart(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true);
  }

  @Test
  public void connectResponseBeforeTimeout() throws Exception {
    long startTimeMs = SystemClock.elapsedRealtime();
    final ConditionVariable startCondition = buildUrlRequestStartedCondition();
    final CountDownLatch openLatch = new CountDownLatch(1);

    AtomicReference<Exception> exceptionOnTestThread = new AtomicReference<>();
    new Thread() {
      @Override
      public void run() {
        try {
          dataSourceUnderTest.open(testDataSpec);
        } catch (HttpDataSourceException e) {
          exceptionOnTestThread.set(e);
        } finally {
          openLatch.countDown();
        }
      }
    }.start();
    startCondition.block();

    // We should still be trying to open.
    assertNotCountedDown(openLatch);
    // We should still be trying to open as we approach the timeout.
    setSystemClockInMsAndTriggerPendingMessages(
        /* nowMs= */ startTimeMs + TEST_CONNECT_TIMEOUT_MS - 1);
    assertNotCountedDown(openLatch);
    // The response arrives just in time.
    dataSourceUnderTest.urlRequestCallback.onResponseStarted(mockUrlRequest, testUrlResponseInfo);
    openLatch.await();
    assertThat(exceptionOnTestThread.get()).isNull();
  }

  @Test
  public void redirectIncreasesConnectionTimeout() throws Exception {
    long startTimeMs = SystemClock.elapsedRealtime();
    final ConditionVariable startCondition = buildUrlRequestStartedCondition();
    final CountDownLatch timedOutLatch = new CountDownLatch(1);
    final AtomicInteger openExceptions = new AtomicInteger(0);

    new Thread() {
      @Override
      public void run() {
        try {
          dataSourceUnderTest.open(testDataSpec);
          fail();
        } catch (HttpDataSourceException e) {
          // Expected.
          assertThat(e).isInstanceOf(CronetDataSource.OpenException.class);
          assertThat(e).hasCauseThat().isInstanceOf(SocketTimeoutException.class);
          openExceptions.getAndIncrement();
          timedOutLatch.countDown();
        }
      }
    }.start();
    startCondition.block();

    // We should still be trying to open.
    assertNotCountedDown(timedOutLatch);
    // We should still be trying to open as we approach the timeout.
    setSystemClockInMsAndTriggerPendingMessages(
        /* nowMs= */ startTimeMs + TEST_CONNECT_TIMEOUT_MS - 1);
    assertNotCountedDown(timedOutLatch);
    // A redirect arrives just in time.
    dataSourceUnderTest.urlRequestCallback.onRedirectReceived(
        mockUrlRequest, testUrlResponseInfo, "RandomRedirectedUrl1");

    long newTimeoutMs = 2 * TEST_CONNECT_TIMEOUT_MS - 1;
    setSystemClockInMsAndTriggerPendingMessages(/* nowMs= */ startTimeMs + newTimeoutMs - 1);
    // We should still be trying to open as we approach the new timeout.
    assertNotCountedDown(timedOutLatch);
    // A redirect arrives just in time.
    dataSourceUnderTest.urlRequestCallback.onRedirectReceived(
        mockUrlRequest, testUrlResponseInfo, "RandomRedirectedUrl2");

    newTimeoutMs = 3 * TEST_CONNECT_TIMEOUT_MS - 2;
    setSystemClockInMsAndTriggerPendingMessages(/* nowMs= */ startTimeMs + newTimeoutMs - 1);
    // We should still be trying to open as we approach the new timeout.
    assertNotCountedDown(timedOutLatch);
    // Now we timeout.
    setSystemClockInMsAndTriggerPendingMessages(/* nowMs= */ startTimeMs + newTimeoutMs + 10);
    timedOutLatch.await();

    verify(mockTransferListener, never())
        .onTransferStart(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true);
    assertThat(openExceptions.get()).isEqualTo(1);
  }

  @Test
  public void redirectParseAndAttachCookie_dataSourceDoesNotHandleSetCookie_followsRedirect()
      throws HttpDataSourceException {
    mockSingleRedirectSuccess();
    mockFollowRedirectSuccess();

    testResponseHeader.put("Set-Cookie", "testcookie=testcookie; Path=/video");

    dataSourceUnderTest.open(testDataSpec);
    verify(mockUrlRequestBuilder, never()).addHeader(eq("Cookie"), any(String.class));
    verify(mockUrlRequest).followRedirect();
  }

  @Test
  public void
      testRedirectParseAndAttachCookie_dataSourceHandlesSetCookie_andPreservesOriginalRequestHeaders()
          throws HttpDataSourceException {
    dataSourceUnderTest =
        (CronetDataSource)
            new CronetDataSource.Factory(new CronetEngineWrapper(mockCronetEngine), executorService)
                .setConnectionTimeoutMs(TEST_CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(TEST_READ_TIMEOUT_MS)
                .setResetTimeoutOnRedirects(true)
                .setHandleSetCookieRequests(true)
                .createDataSource();
    dataSourceUnderTest.addTransferListener(mockTransferListener);
    dataSourceUnderTest.setRequestProperty("Content-Type", TEST_CONTENT_TYPE);

    mockSingleRedirectSuccess();

    testResponseHeader.put("Set-Cookie", "testcookie=testcookie; Path=/video");

    dataSourceUnderTest.open(testDataSpec);
    verify(mockUrlRequestBuilder).addHeader(eq("Cookie"), any(String.class));
    verify(mockUrlRequestBuilder, never()).addHeader(eq("Range"), any(String.class));
    verify(mockUrlRequestBuilder, times(2)).addHeader("Content-Type", TEST_CONTENT_TYPE);
    verify(mockUrlRequest, never()).followRedirect();
    verify(mockUrlRequest, times(2)).start();
  }

  @Test
  public void
      testRedirectParseAndAttachCookie_dataSourceHandlesSetCookie_andPreservesOriginalRequestHeadersIncludingByteRangeHeader()
          throws HttpDataSourceException {
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 1000, 5000);
    dataSourceUnderTest =
        (CronetDataSource)
            new CronetDataSource.Factory(new CronetEngineWrapper(mockCronetEngine), executorService)
                .setConnectionTimeoutMs(TEST_CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(TEST_READ_TIMEOUT_MS)
                .setResetTimeoutOnRedirects(true)
                .setHandleSetCookieRequests(true)
                .createDataSource();
    dataSourceUnderTest.addTransferListener(mockTransferListener);
    dataSourceUnderTest.setRequestProperty("Content-Type", TEST_CONTENT_TYPE);

    mockSingleRedirectSuccess();
    mockReadSuccess(0, 1000);

    testResponseHeader.put("Set-Cookie", "testcookie=testcookie; Path=/video");

    dataSourceUnderTest.open(testDataSpec);
    verify(mockUrlRequestBuilder).addHeader(eq("Cookie"), any(String.class));
    verify(mockUrlRequestBuilder, times(2)).addHeader("Range", "bytes=1000-5999");
    verify(mockUrlRequestBuilder, times(2)).addHeader("Content-Type", TEST_CONTENT_TYPE);
    verify(mockUrlRequest, never()).followRedirect();
    verify(mockUrlRequest, times(2)).start();
  }

  @Test
  public void redirectNoSetCookieFollowsRedirect() throws HttpDataSourceException {
    mockSingleRedirectSuccess();
    mockFollowRedirectSuccess();

    dataSourceUnderTest.open(testDataSpec);
    verify(mockUrlRequestBuilder, never()).addHeader(eq("Cookie"), any(String.class));
    verify(mockUrlRequest).followRedirect();
  }

  @Test
  public void redirectNoSetCookieFollowsRedirect_dataSourceHandlesSetCookie()
      throws HttpDataSourceException {
    dataSourceUnderTest =
        (CronetDataSource)
            new CronetDataSource.Factory(new CronetEngineWrapper(mockCronetEngine), executorService)
                .setConnectionTimeoutMs(TEST_CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(TEST_READ_TIMEOUT_MS)
                .setResetTimeoutOnRedirects(true)
                .setHandleSetCookieRequests(true)
                .createDataSource();
    dataSourceUnderTest.addTransferListener(mockTransferListener);
    mockSingleRedirectSuccess();
    mockFollowRedirectSuccess();

    dataSourceUnderTest.open(testDataSpec);
    verify(mockUrlRequestBuilder, never()).addHeader(eq("Cookie"), any(String.class));
    verify(mockUrlRequest).followRedirect();
  }

  @Test
  public void exceptionFromTransferListener() throws HttpDataSourceException {
    mockResponseStartSuccess();

    // Make mockTransferListener throw an exception in CronetDataSource.close(). Ensure that
    // the subsequent open() call succeeds.
    doThrow(new NullPointerException())
        .when(mockTransferListener)
        .onTransferEnd(dataSourceUnderTest, testDataSpec, /* isNetwork= */ true);
    dataSourceUnderTest.open(testDataSpec);
    try {
      dataSourceUnderTest.close();
      fail("NullPointerException expected");
    } catch (NullPointerException e) {
      // Expected.
    }
    // Open should return successfully.
    dataSourceUnderTest.open(testDataSpec);
  }

  @Test
  public void readFailure() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadFailure();

    dataSourceUnderTest.open(testDataSpec);
    byte[] returnedBuffer = new byte[8];
    try {
      dataSourceUnderTest.read(returnedBuffer, 0, 8);
      fail("dataSourceUnderTest.read() returned, but IOException expected");
    } catch (IOException e) {
      // Expected.
    }
  }

  @Test
  public void readByteBufferFailure() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadFailure();

    dataSourceUnderTest.open(testDataSpec);
    ByteBuffer returnedBuffer = ByteBuffer.allocateDirect(8);
    try {
      dataSourceUnderTest.read(returnedBuffer);
      fail("dataSourceUnderTest.read() returned, but IOException expected");
    } catch (IOException e) {
      // Expected.
    }
  }

  @Test
  public void readNonDirectedByteBufferFailure() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadFailure();

    dataSourceUnderTest.open(testDataSpec);
    byte[] returnedBuffer = new byte[8];
    try {
      dataSourceUnderTest.read(ByteBuffer.wrap(returnedBuffer));
      fail("dataSourceUnderTest.read() returned, but IllegalArgumentException expected");
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  @Test
  public void readInterrupted() throws HttpDataSourceException, InterruptedException {
    mockResponseStartSuccess();
    dataSourceUnderTest.open(testDataSpec);

    final ConditionVariable startCondition = buildReadStartedCondition();
    final CountDownLatch timedOutLatch = new CountDownLatch(1);
    byte[] returnedBuffer = new byte[8];
    Thread thread =
        new Thread() {
          @Override
          public void run() {
            try {
              dataSourceUnderTest.read(returnedBuffer, 0, 8);
              fail();
            } catch (HttpDataSourceException e) {
              // Expected.
              assertThat(e).hasCauseThat().isInstanceOf(InterruptedIOException.class);
              timedOutLatch.countDown();
            }
          }
        };
    thread.start();
    startCondition.block();

    assertNotCountedDown(timedOutLatch);
    // Now we interrupt.
    thread.interrupt();
    timedOutLatch.await();
  }

  @Test
  public void readByteBufferInterrupted() throws HttpDataSourceException, InterruptedException {
    mockResponseStartSuccess();
    dataSourceUnderTest.open(testDataSpec);

    final ConditionVariable startCondition = buildReadStartedCondition();
    final CountDownLatch timedOutLatch = new CountDownLatch(1);
    ByteBuffer returnedBuffer = ByteBuffer.allocateDirect(8);
    Thread thread =
        new Thread() {
          @Override
          public void run() {
            try {
              dataSourceUnderTest.read(returnedBuffer);
              fail();
            } catch (HttpDataSourceException e) {
              // Expected.
              assertThat(e).hasCauseThat().isInstanceOf(InterruptedIOException.class);
              timedOutLatch.countDown();
            }
          }
        };
    thread.start();
    startCondition.block();

    assertNotCountedDown(timedOutLatch);
    // Now we interrupt.
    thread.interrupt();
    timedOutLatch.await();
  }

  @Test
  public void allowDirectExecutor() throws HttpDataSourceException {
    testDataSpec = new DataSpec(Uri.parse(TEST_URL));
    mockResponseStartSuccess();

    dataSourceUnderTest.open(testDataSpec);
    verify(mockUrlRequestBuilder).allowDirectExecutor();
  }

  @Test
  public void factorySetFallbackHttpDataSourceFactory_cronetNotAvailable_usesFallbackFactory()
      throws HttpDataSourceException, InterruptedException {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.enqueue(new MockResponse());
    CronetEngineWrapper cronetEngineWrapper = new CronetEngineWrapper((CronetEngine) null);
    DefaultHttpDataSource.Factory fallbackFactory =
        new DefaultHttpDataSource.Factory().setUserAgent("customFallbackFactoryUserAgent");
    HttpDataSource dataSourceUnderTest =
        new CronetDataSource.Factory(cronetEngineWrapper, executorService)
            .setFallbackFactory(fallbackFactory)
            .createDataSource();

    dataSourceUnderTest.open(
        new DataSpec.Builder().setUri(mockWebServer.url("/test-path").toString()).build());

    Headers headers = mockWebServer.takeRequest(10, SECONDS).getHeaders();
    assertThat(headers.get("user-agent")).isEqualTo("customFallbackFactoryUserAgent");
  }

  @Test
  public void
      factory_noFallbackFactoryCronetNotAvailable_delegateTransferListenerToInternalFallbackFactory()
          throws HttpDataSourceException, InterruptedException {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.enqueue(new MockResponse());
    CronetEngineWrapper cronetEngineWrapper = new CronetEngineWrapper((CronetEngine) null);
    HttpDataSource dataSourceUnderTest =
        new CronetDataSource.Factory(cronetEngineWrapper, executorService)
            .setTransferListener(mockTransferListener)
            .createDataSource();
    DataSpec dataSpec =
        new DataSpec.Builder().setUri(mockWebServer.url("/test-path").toString()).build();

    dataSourceUnderTest.open(dataSpec);

    verify(mockTransferListener)
        .onTransferInitializing(eq(dataSourceUnderTest), eq(dataSpec), /* isNetwork= */ eq(true));
    verify(mockTransferListener)
        .onTransferStart(eq(dataSourceUnderTest), eq(dataSpec), /* isNetwork= */ eq(true));
  }

  @Test
  public void
      factory_noFallbackFactoryCronetNotAvailable_delegateDefaultRequestPropertiesToInternalFallbackFactory()
          throws HttpDataSourceException, InterruptedException {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.enqueue(new MockResponse());
    CronetEngineWrapper cronetEngineWrapper =
        new CronetEngineWrapper(ApplicationProvider.getApplicationContext());
    Map<String, String> defaultRequestProperties = new HashMap<>();
    defaultRequestProperties.put("0", "defaultRequestProperty0");
    HttpDataSource dataSourceUnderTest =
        new CronetDataSource.Factory(cronetEngineWrapper, executorService)
            .setDefaultRequestProperties(defaultRequestProperties)
            .createDataSource();

    dataSourceUnderTest.open(
        new DataSpec.Builder().setUri(mockWebServer.url("/test-path").toString()).build());

    Headers headers = mockWebServer.takeRequest(10, SECONDS).getHeaders();
    assertThat(headers.get("0")).isEqualTo("defaultRequestProperty0");
    assertThat(dataSourceUnderTest).isInstanceOf(DefaultHttpDataSource.class);
  }

  @Test
  public void
      factory_noFallbackFactoryCronetNotAvailable_delegateDefaultRequestPropertiesToInternalFallbackFactoryAfterCreation()
          throws HttpDataSourceException, InterruptedException {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.enqueue(new MockResponse());
    CronetEngineWrapper cronetEngineWrapper = new CronetEngineWrapper((CronetEngine) null);
    Map<String, String> defaultRequestProperties = new HashMap<>();
    defaultRequestProperties.put("0", "defaultRequestProperty0");
    CronetDataSource.Factory factory =
        new CronetDataSource.Factory(cronetEngineWrapper, executorService);
    HttpDataSource dataSourceUnderTest =
        factory.setDefaultRequestProperties(defaultRequestProperties).createDataSource();
    defaultRequestProperties.clear();
    defaultRequestProperties.put("1", "defaultRequestPropertyAfterCreation");
    factory.setDefaultRequestProperties(defaultRequestProperties);

    dataSourceUnderTest.open(
        new DataSpec.Builder().setUri(mockWebServer.url("/test-path").toString()).build());

    Headers headers = mockWebServer.takeRequest(10, SECONDS).getHeaders();
    assertThat(headers.get("0")).isNull();
    assertThat(headers.get("1")).isEqualTo("defaultRequestPropertyAfterCreation");
    assertThat(dataSourceUnderTest).isInstanceOf(DefaultHttpDataSource.class);
  }

  // Helper methods.

  private void mockStatusResponse() {
    doAnswer(
            invocation -> {
              UrlRequest.StatusListener statusListener =
                  (UrlRequest.StatusListener) invocation.getArguments()[0];
              statusListener.onStatus(TEST_CONNECTION_STATUS);
              return null;
            })
        .when(mockUrlRequest)
        .getStatus(any(UrlRequest.StatusListener.class));
  }

  private void mockResponseStartSuccess() {
    doAnswer(
            invocation -> {
              dataSourceUnderTest.urlRequestCallback.onResponseStarted(
                  mockUrlRequest, testUrlResponseInfo);
              return null;
            })
        .when(mockUrlRequest)
        .start();
  }

  private void mockResponseStartRedirect() {
    doAnswer(
            invocation -> {
              dataSourceUnderTest.urlRequestCallback.onRedirectReceived(
                  mockUrlRequest,
                  createUrlResponseInfo(307), // statusCode
                  "http://redirect.location.com");
              return null;
            })
        .when(mockUrlRequest)
        .start();
  }

  private void mockSingleRedirectSuccess() {
    doAnswer(
            invocation -> {
              if (!redirectCalled) {
                redirectCalled = true;
                dataSourceUnderTest.urlRequestCallback.onRedirectReceived(
                    mockUrlRequest,
                    createUrlResponseInfoWithUrl("http://example.com/video", 300),
                    "http://example.com/video/redirect");
              } else {
                dataSourceUnderTest.urlRequestCallback.onResponseStarted(
                    mockUrlRequest, testUrlResponseInfo);
              }
              return null;
            })
        .when(mockUrlRequest)
        .start();
  }

  private void mockFollowRedirectSuccess() {
    doAnswer(
            invocation -> {
              dataSourceUnderTest.urlRequestCallback.onResponseStarted(
                  mockUrlRequest, testUrlResponseInfo);
              return null;
            })
        .when(mockUrlRequest)
        .followRedirect();
  }

  private void mockResponseStartFailure() {
    doAnswer(
            invocation -> {
              dataSourceUnderTest.urlRequestCallback.onFailed(
                  mockUrlRequest,
                  createUrlResponseInfo(500), // statusCode
                  mockNetworkException);
              return null;
            })
        .when(mockUrlRequest)
        .start();
  }

  private void mockReadSuccess(int position, int length) {
    final int[] positionAndRemaining = new int[] {position, length};
    doAnswer(
            invocation -> {
              if (positionAndRemaining[1] == 0) {
                dataSourceUnderTest.urlRequestCallback.onSucceeded(
                    mockUrlRequest, testUrlResponseInfo);
              } else {
                ByteBuffer inputBuffer = (ByteBuffer) invocation.getArguments()[0];
                int readLength = min(positionAndRemaining[1], inputBuffer.remaining());
                inputBuffer.put(buildTestDataBuffer(positionAndRemaining[0], readLength));
                positionAndRemaining[0] += readLength;
                positionAndRemaining[1] -= readLength;
                dataSourceUnderTest.urlRequestCallback.onReadCompleted(
                    mockUrlRequest, testUrlResponseInfo, inputBuffer);
              }
              return null;
            })
        .when(mockUrlRequest)
        .read(any(ByteBuffer.class));
  }

  private void mockReadFailure() {
    doAnswer(
            invocation -> {
              dataSourceUnderTest.urlRequestCallback.onFailed(
                  mockUrlRequest,
                  createUrlResponseInfo(500), // statusCode
                  mockNetworkException);
              return null;
            })
        .when(mockUrlRequest)
        .read(any(ByteBuffer.class));
  }

  private ConditionVariable buildReadStartedCondition() {
    final ConditionVariable startedCondition = new ConditionVariable();
    doAnswer(
            invocation -> {
              startedCondition.open();
              return null;
            })
        .when(mockUrlRequest)
        .read(any(ByteBuffer.class));
    return startedCondition;
  }

  private ConditionVariable buildUrlRequestStartedCondition() {
    final ConditionVariable startedCondition = new ConditionVariable();
    doAnswer(
            invocation -> {
              startedCondition.open();
              return null;
            })
        .when(mockUrlRequest)
        .start();
    return startedCondition;
  }

  private void assertNotCountedDown(CountDownLatch countDownLatch) throws InterruptedException {
    // We are asserting that another thread does not count down the latch. We therefore sleep some
    // time to give the other thread the chance to fail this test.
    Thread.sleep(50);
    assertThat(countDownLatch.getCount()).isGreaterThan(0L);
  }

  private static byte[] buildTestDataArray(int position, int length) {
    return buildTestDataBuffer(position, length).array();
  }

  public static byte[] prefixZeros(byte[] data, int requiredLength) {
    byte[] prefixedData = new byte[requiredLength];
    System.arraycopy(data, 0, prefixedData, requiredLength - data.length, data.length);
    return prefixedData;
  }

  public static byte[] suffixZeros(byte[] data, int requiredLength) {
    return Arrays.copyOf(data, requiredLength);
  }

  private static ByteBuffer buildTestDataBuffer(int position, int length) {
    ByteBuffer testBuffer = ByteBuffer.allocate(length);
    for (int i = 0; i < length; i++) {
      testBuffer.put((byte) (position + i));
    }
    testBuffer.flip();
    return testBuffer;
  }

  // Returns a copy of what is remaining in the src buffer from the current position to capacity.
  private static byte[] copyByteBufferToArray(ByteBuffer src) {
    if (src == null) {
      return null;
    }
    byte[] copy = new byte[src.remaining()];
    int index = 0;
    while (src.hasRemaining()) {
      copy[index++] = src.get();
    }
    return copy;
  }

  private static void setSystemClockInMsAndTriggerPendingMessages(long nowMs) {
    SystemClock.setCurrentTimeMillis(nowMs);
    ShadowLooper.idleMainLooper();
  }
}
