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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.net.Uri;
import android.os.ConditionVariable;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Predicate;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.chromium.net.CronetEngine;
import org.chromium.net.NetworkException;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;
import org.chromium.net.impl.UrlResponseInfoImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Tests for {@link CronetDataSource}.
 */
@RunWith(AndroidJUnit4.class)
public final class CronetDataSourceTest {

  private static final int TEST_CONNECT_TIMEOUT_MS = 100;
  private static final int TEST_READ_TIMEOUT_MS = 50;
  private static final String TEST_URL = "http://google.com";
  private static final String TEST_CONTENT_TYPE = "test/test";
  private static final byte[] TEST_POST_BODY = "test post body".getBytes();
  private static final long TEST_CONTENT_LENGTH = 16000L;
  private static final int TEST_CONNECTION_STATUS = 5;

  private DataSpec testDataSpec;
  private DataSpec testPostDataSpec;
  private Map<String, String> testResponseHeader;
  private UrlResponseInfo testUrlResponseInfo;

  @Mock private UrlRequest.Builder mockUrlRequestBuilder;
  @Mock
  private UrlRequest mockUrlRequest;
  @Mock
  private Predicate<String> mockContentTypePredicate;
  @Mock
  private TransferListener<CronetDataSource> mockTransferListener;
  @Mock
  private Clock mockClock;
  @Mock
  private Executor mockExecutor;
  @Mock
  private NetworkException mockNetworkException;
  @Mock private CronetEngine mockCronetEngine;

  private CronetDataSource dataSourceUnderTest;

  @Before
  public void setUp() throws Exception {
    System.setProperty("dexmaker.dexcache",
        InstrumentationRegistry.getTargetContext().getCacheDir().getPath());
    initMocks(this);
    dataSourceUnderTest = spy(
        new CronetDataSource(
            mockCronetEngine,
            mockExecutor,
            mockContentTypePredicate,
            mockTransferListener,
            TEST_CONNECT_TIMEOUT_MS,
            TEST_READ_TIMEOUT_MS,
            true, // resetTimeoutOnRedirects
            mockClock));
    when(mockContentTypePredicate.evaluate(anyString())).thenReturn(true);
    when(mockCronetEngine.newUrlRequestBuilder(
            anyString(), any(UrlRequest.Callback.class), any(Executor.class)))
        .thenReturn(mockUrlRequestBuilder);
    when(mockUrlRequestBuilder.build()).thenReturn(mockUrlRequest);
    mockStatusResponse();

    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 0, C.LENGTH_UNSET, null);
    testPostDataSpec = new DataSpec(
        Uri.parse(TEST_URL), TEST_POST_BODY, 0, 0, C.LENGTH_UNSET, null, 0);
    testResponseHeader = new HashMap<>();
    testResponseHeader.put("Content-Type", TEST_CONTENT_TYPE);
    // This value can be anything since the DataSpec is unset.
    testResponseHeader.put("Content-Length", Long.toString(TEST_CONTENT_LENGTH));
    testUrlResponseInfo = createUrlResponseInfo(200); // statusCode
  }

  private UrlResponseInfo createUrlResponseInfo(int statusCode) {
    ArrayList<Map.Entry<String, String>> responseHeaderList = new ArrayList<>();
    responseHeaderList.addAll(testResponseHeader.entrySet());
    return new UrlResponseInfoImpl(
        Collections.singletonList(TEST_URL),
        statusCode,
        null, // httpStatusText
        responseHeaderList,
        false, // wasCached
        null, // negotiatedProtocol
        null); // proxyServer
  }

  @Test(expected = IllegalStateException.class)
  public void testOpeningTwiceThrows() throws HttpDataSourceException {
    mockResponseStartSuccess();
    dataSourceUnderTest.open(testDataSpec);
    dataSourceUnderTest.open(testDataSpec);
  }

  @Test
  public void testCallbackFromPreviousRequest() throws HttpDataSourceException {
    mockResponseStartSuccess();

    dataSourceUnderTest.open(testDataSpec);
    dataSourceUnderTest.close();
    // Prepare a mock UrlRequest to be used in the second open() call.
    final UrlRequest mockUrlRequest2 = mock(UrlRequest.class);
    when(mockUrlRequestBuilder.build()).thenReturn(mockUrlRequest2);
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        // Invoke the callback for the previous request.
        dataSourceUnderTest.onFailed(
            mockUrlRequest,
            testUrlResponseInfo,
            mockNetworkException);
        dataSourceUnderTest.onResponseStarted(
            mockUrlRequest2,
            testUrlResponseInfo);
        return null;
      }
    }).when(mockUrlRequest2).start();
    dataSourceUnderTest.open(testDataSpec);
  }

  @Test
  public void testRequestStartCalled() throws HttpDataSourceException {
    mockResponseStartSuccess();

    dataSourceUnderTest.open(testDataSpec);
    verify(mockCronetEngine)
        .newUrlRequestBuilder(eq(TEST_URL), any(UrlRequest.Callback.class), any(Executor.class));
    verify(mockUrlRequest).start();
  }

  @Test
  public void testRequestHeadersSet() throws HttpDataSourceException {
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 1000, 5000, null);
    mockResponseStartSuccess();

    dataSourceUnderTest.setRequestProperty("firstHeader", "firstValue");
    dataSourceUnderTest.setRequestProperty("secondHeader", "secondValue");

    dataSourceUnderTest.open(testDataSpec);
    // The header value to add is current position to current position + length - 1.
    verify(mockUrlRequestBuilder).addHeader("Range", "bytes=1000-5999");
    verify(mockUrlRequestBuilder).addHeader("firstHeader", "firstValue");
    verify(mockUrlRequestBuilder).addHeader("secondHeader", "secondValue");
    verify(mockUrlRequest).start();
  }

  @Test
  public void testRequestOpen() throws HttpDataSourceException {
    mockResponseStartSuccess();
    assertEquals(TEST_CONTENT_LENGTH, dataSourceUnderTest.open(testDataSpec));
    verify(mockTransferListener).onTransferStart(dataSourceUnderTest, testDataSpec);
  }

  @Test
  public void testRequestOpenGzippedCompressedReturnsDataSpecLength()
      throws HttpDataSourceException {
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 0, 5000, null);
    testResponseHeader.put("Content-Encoding", "gzip");
    testResponseHeader.put("Content-Length", Long.toString(50L));
    mockResponseStartSuccess();

    assertEquals(5000 /* contentLength */, dataSourceUnderTest.open(testDataSpec));
    verify(mockTransferListener).onTransferStart(dataSourceUnderTest, testDataSpec);
  }

  @Test
  public void testRequestOpenFail() {
    mockResponseStartFailure();

    try {
      dataSourceUnderTest.open(testDataSpec);
      fail("HttpDataSource.HttpDataSourceException expected");
    } catch (HttpDataSourceException e) {
      // Check for connection not automatically closed.
      assertFalse(e.getCause() instanceof UnknownHostException);
      verify(mockUrlRequest, never()).cancel();
      verify(mockTransferListener, never()).onTransferStart(dataSourceUnderTest, testDataSpec);
    }
  }

  @Test
  public void testRequestOpenFailDueToDnsFailure() {
    mockResponseStartFailure();
    when(mockNetworkException.getErrorCode()).thenReturn(
        NetworkException.ERROR_HOSTNAME_NOT_RESOLVED);

    try {
      dataSourceUnderTest.open(testDataSpec);
      fail("HttpDataSource.HttpDataSourceException expected");
    } catch (HttpDataSourceException e) {
      // Check for connection not automatically closed.
      assertTrue(e.getCause() instanceof UnknownHostException);
      verify(mockUrlRequest, never()).cancel();
      verify(mockTransferListener, never()).onTransferStart(dataSourceUnderTest, testDataSpec);
    }
  }

  @Test
  public void testRequestOpenValidatesStatusCode() {
    mockResponseStartSuccess();
    testUrlResponseInfo = createUrlResponseInfo(500); // statusCode

    try {
      dataSourceUnderTest.open(testDataSpec);
      fail("HttpDataSource.HttpDataSourceException expected");
    } catch (HttpDataSourceException e) {
      assertTrue(e instanceof HttpDataSource.InvalidResponseCodeException);
      // Check for connection not automatically closed.
      verify(mockUrlRequest, never()).cancel();
      verify(mockTransferListener, never()).onTransferStart(dataSourceUnderTest, testDataSpec);
    }
  }

  @Test
  public void testRequestOpenValidatesContentTypePredicate() {
    mockResponseStartSuccess();
    when(mockContentTypePredicate.evaluate(anyString())).thenReturn(false);

    try {
      dataSourceUnderTest.open(testDataSpec);
      fail("HttpDataSource.HttpDataSourceException expected");
    } catch (HttpDataSourceException e) {
      assertTrue(e instanceof HttpDataSource.InvalidContentTypeException);
      // Check for connection not automatically closed.
      verify(mockUrlRequest, never()).cancel();
      verify(mockContentTypePredicate).evaluate(TEST_CONTENT_TYPE);
    }
  }

  @Test
  public void testPostRequestOpen() throws HttpDataSourceException {
    mockResponseStartSuccess();

    dataSourceUnderTest.setRequestProperty("Content-Type", TEST_CONTENT_TYPE);
    assertEquals(TEST_CONTENT_LENGTH, dataSourceUnderTest.open(testPostDataSpec));
    verify(mockTransferListener).onTransferStart(dataSourceUnderTest, testPostDataSpec);
  }

  @Test
  public void testPostRequestOpenValidatesContentType() {
    mockResponseStartSuccess();

    try {
      dataSourceUnderTest.open(testPostDataSpec);
      fail("HttpDataSource.HttpDataSourceException expected");
    } catch (HttpDataSourceException e) {
      verify(mockUrlRequest, never()).start();
    }
  }

  @Test
  public void testPostRequestOpenRejects307Redirects() {
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
  public void testRequestReadTwice() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadSuccess(0, 16);

    dataSourceUnderTest.open(testDataSpec);

    byte[] returnedBuffer = new byte[8];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 8);
    assertArrayEquals(buildTestDataArray(0, 8), returnedBuffer);
    assertEquals(8, bytesRead);

    returnedBuffer = new byte[8];
    bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 8);
    assertArrayEquals(buildTestDataArray(8, 8), returnedBuffer);
    assertEquals(8, bytesRead);

    // Should have only called read on cronet once.
    verify(mockUrlRequest, times(1)).read(any(ByteBuffer.class));
    verify(mockTransferListener, times(2)).onBytesTransferred(dataSourceUnderTest, 8);
  }

  @Test
  public void testSecondRequestNoContentLength() throws HttpDataSourceException {
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
    assertEquals(10, bytesRead);
    bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 10);
    assertEquals(6, bytesRead);
    bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 10);
    assertEquals(C.RESULT_END_OF_INPUT, bytesRead);
  }

  @Test
  public void testReadWithOffset() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadSuccess(0, 16);

    dataSourceUnderTest.open(testDataSpec);

    byte[] returnedBuffer = new byte[16];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 8, 8);
    assertEquals(8, bytesRead);
    assertArrayEquals(prefixZeros(buildTestDataArray(0, 8), 16), returnedBuffer);
    verify(mockTransferListener).onBytesTransferred(dataSourceUnderTest, 8);
  }

  @Test
  public void testRangeRequestWith206Response() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadSuccess(1000, 5000);
    testUrlResponseInfo = createUrlResponseInfo(206); // Server supports range requests.
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 1000, 5000, null);

    dataSourceUnderTest.open(testDataSpec);

    byte[] returnedBuffer = new byte[16];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 16);
    assertEquals(16, bytesRead);
    assertArrayEquals(buildTestDataArray(1000, 16), returnedBuffer);
    verify(mockTransferListener).onBytesTransferred(dataSourceUnderTest, 16);
  }

  @Test
  public void testRangeRequestWith200Response() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadSuccess(0, 7000);
    testUrlResponseInfo = createUrlResponseInfo(200); // Server does not support range requests.
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 1000, 5000, null);

    dataSourceUnderTest.open(testDataSpec);

    byte[] returnedBuffer = new byte[16];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 16);
    assertEquals(16, bytesRead);
    assertArrayEquals(buildTestDataArray(1000, 16), returnedBuffer);
    verify(mockTransferListener).onBytesTransferred(dataSourceUnderTest, 16);
  }

  @Test
  public void testReadWithUnsetLength() throws HttpDataSourceException {
    testResponseHeader.remove("Content-Length");
    mockResponseStartSuccess();
    mockReadSuccess(0, 16);

    dataSourceUnderTest.open(testDataSpec);

    byte[] returnedBuffer = new byte[16];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 8, 8);
    assertArrayEquals(prefixZeros(buildTestDataArray(0, 8), 16), returnedBuffer);
    assertEquals(8, bytesRead);
    verify(mockTransferListener).onBytesTransferred(dataSourceUnderTest, 8);
  }

  @Test
  public void testReadReturnsWhatItCan() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadSuccess(0, 16);

    dataSourceUnderTest.open(testDataSpec);

    byte[] returnedBuffer = new byte[24];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 24);
    assertArrayEquals(suffixZeros(buildTestDataArray(0, 16), 24), returnedBuffer);
    assertEquals(16, bytesRead);
    verify(mockTransferListener).onBytesTransferred(dataSourceUnderTest, 16);
  }

  @Test
  public void testClosedMeansClosed() throws HttpDataSourceException {
    mockResponseStartSuccess();
    mockReadSuccess(0, 16);

    int bytesRead = 0;
    dataSourceUnderTest.open(testDataSpec);

    byte[] returnedBuffer = new byte[8];
    bytesRead += dataSourceUnderTest.read(returnedBuffer, 0, 8);
    assertArrayEquals(buildTestDataArray(0, 8), returnedBuffer);
    assertEquals(8, bytesRead);

    dataSourceUnderTest.close();
    verify(mockTransferListener).onTransferEnd(dataSourceUnderTest);

    try {
      bytesRead += dataSourceUnderTest.read(returnedBuffer, 0, 8);
      fail();
    } catch (IllegalStateException e) {
      // Expected.
    }

    // 16 bytes were attempted but only 8 should have been successfully read.
    assertEquals(8, bytesRead);
  }

  @Test
  public void testOverread() throws HttpDataSourceException {
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 0, 16, null);
    testResponseHeader.put("Content-Length", Long.toString(16L));
    mockResponseStartSuccess();
    mockReadSuccess(0, 16);

    dataSourceUnderTest.open(testDataSpec);

    byte[] returnedBuffer = new byte[8];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 8);
    assertEquals(8, bytesRead);
    assertArrayEquals(buildTestDataArray(0, 8), returnedBuffer);

    // The current buffer is kept if not completely consumed by DataSource reader.
    returnedBuffer = new byte[8];
    bytesRead += dataSourceUnderTest.read(returnedBuffer, 0, 6);
    assertEquals(14, bytesRead);
    assertArrayEquals(suffixZeros(buildTestDataArray(8, 6), 8), returnedBuffer);

    // 2 bytes left at this point.
    returnedBuffer = new byte[8];
    bytesRead += dataSourceUnderTest.read(returnedBuffer, 0, 8);
    assertEquals(16, bytesRead);
    assertArrayEquals(suffixZeros(buildTestDataArray(14, 2), 8), returnedBuffer);

    // Should have only called read on cronet once.
    verify(mockUrlRequest, times(1)).read(any(ByteBuffer.class));
    verify(mockTransferListener, times(1)).onBytesTransferred(dataSourceUnderTest, 8);
    verify(mockTransferListener, times(1)).onBytesTransferred(dataSourceUnderTest, 6);
    verify(mockTransferListener, times(1)).onBytesTransferred(dataSourceUnderTest, 2);

    // Now we already returned the 16 bytes initially asked.
    // Try to read again even though all requested 16 bytes are already returned.
    // Return C.RESULT_END_OF_INPUT
    returnedBuffer = new byte[16];
    int bytesOverRead = dataSourceUnderTest.read(returnedBuffer, 0, 16);
    assertEquals(C.RESULT_END_OF_INPUT, bytesOverRead);
    assertArrayEquals(new byte[16], returnedBuffer);
    // C.RESULT_END_OF_INPUT should not be reported though the TransferListener.
    verify(mockTransferListener, never()).onBytesTransferred(dataSourceUnderTest,
        C.RESULT_END_OF_INPUT);
    // There should still be only one call to read on cronet.
    verify(mockUrlRequest, times(1)).read(any(ByteBuffer.class));
    // Check for connection not automatically closed.
    verify(mockUrlRequest, never()).cancel();
    assertEquals(16, bytesRead);
  }

  @Test
  public void testConnectTimeout() {
    when(mockClock.elapsedRealtime()).thenReturn(0L);
    final ConditionVariable startCondition = buildUrlRequestStartedCondition();
    final ConditionVariable timedOutCondition = new ConditionVariable();

    new Thread() {
      @Override
      public void run() {
        try {
          dataSourceUnderTest.open(testDataSpec);
          fail();
        } catch (HttpDataSourceException e) {
          // Expected.
          assertTrue(e instanceof CronetDataSource.OpenException);
          assertTrue(e.getCause() instanceof SocketTimeoutException);
          assertEquals(
              TEST_CONNECTION_STATUS,
              ((CronetDataSource.OpenException) e).cronetConnectionStatus);
          timedOutCondition.open();
        }
      }
    }.start();
    startCondition.block();

    // We should still be trying to open.
    assertFalse(timedOutCondition.block(50));
    // We should still be trying to open as we approach the timeout.
    when(mockClock.elapsedRealtime()).thenReturn((long) TEST_CONNECT_TIMEOUT_MS - 1);
    assertFalse(timedOutCondition.block(50));
    // Now we timeout.
    when(mockClock.elapsedRealtime()).thenReturn((long) TEST_CONNECT_TIMEOUT_MS);
    timedOutCondition.block();

    verify(mockTransferListener, never()).onTransferStart(dataSourceUnderTest, testDataSpec);
  }

  @Test
  public void testConnectResponseBeforeTimeout() {
    when(mockClock.elapsedRealtime()).thenReturn(0L);
    final ConditionVariable startCondition = buildUrlRequestStartedCondition();
    final ConditionVariable openCondition = new ConditionVariable();

    new Thread() {
      @Override
      public void run() {
        try {
          dataSourceUnderTest.open(testDataSpec);
          openCondition.open();
        } catch (HttpDataSourceException e) {
          fail();
        }
      }
    }.start();
    startCondition.block();

    // We should still be trying to open.
    assertFalse(openCondition.block(50));
    // We should still be trying to open as we approach the timeout.
    when(mockClock.elapsedRealtime()).thenReturn((long) TEST_CONNECT_TIMEOUT_MS - 1);
    assertFalse(openCondition.block(50));
    // The response arrives just in time.
    dataSourceUnderTest.onResponseStarted(mockUrlRequest, testUrlResponseInfo);
    openCondition.block();
  }

  @Test
  public void testRedirectIncreasesConnectionTimeout() throws InterruptedException {
    when(mockClock.elapsedRealtime()).thenReturn(0L);
    final ConditionVariable startCondition = buildUrlRequestStartedCondition();
    final ConditionVariable timedOutCondition = new ConditionVariable();
    final AtomicInteger openExceptions = new AtomicInteger(0);

    new Thread() {
      @Override
      public void run() {
        try {
          dataSourceUnderTest.open(testDataSpec);
          fail();
        } catch (HttpDataSourceException e) {
          // Expected.
          assertTrue(e instanceof CronetDataSource.OpenException);
          assertTrue(e.getCause() instanceof SocketTimeoutException);
          openExceptions.getAndIncrement();
          timedOutCondition.open();
        }
      }
    }.start();
    startCondition.block();

    // We should still be trying to open.
    assertFalse(timedOutCondition.block(50));
    // We should still be trying to open as we approach the timeout.
    when(mockClock.elapsedRealtime()).thenReturn((long) TEST_CONNECT_TIMEOUT_MS - 1);
    assertFalse(timedOutCondition.block(50));
    // A redirect arrives just in time.
    dataSourceUnderTest.onRedirectReceived(mockUrlRequest, testUrlResponseInfo,
        "RandomRedirectedUrl1");

    long newTimeoutMs = 2 * TEST_CONNECT_TIMEOUT_MS - 1;
    when(mockClock.elapsedRealtime()).thenReturn(newTimeoutMs - 1);
    // Give the thread some time to run.
    assertFalse(timedOutCondition.block(newTimeoutMs));
    // We should still be trying to open as we approach the new timeout.
    assertFalse(timedOutCondition.block(50));
    // A redirect arrives just in time.
    dataSourceUnderTest.onRedirectReceived(mockUrlRequest, testUrlResponseInfo,
        "RandomRedirectedUrl2");

    newTimeoutMs = 3 * TEST_CONNECT_TIMEOUT_MS - 2;
    when(mockClock.elapsedRealtime()).thenReturn(newTimeoutMs - 1);
    // Give the thread some time to run.
    assertFalse(timedOutCondition.block(newTimeoutMs));
    // We should still be trying to open as we approach the new timeout.
    assertFalse(timedOutCondition.block(50));
    // Now we timeout.
    when(mockClock.elapsedRealtime()).thenReturn(newTimeoutMs);
    timedOutCondition.block();

    verify(mockTransferListener, never()).onTransferStart(dataSourceUnderTest, testDataSpec);
    assertEquals(1, openExceptions.get());
  }

  @Test
  public void testExceptionFromTransferListener() throws HttpDataSourceException {
    mockResponseStartSuccess();

    // Make mockTransferListener throw an exception in CronetDataSource.close(). Ensure that
    // the subsequent open() call succeeds.
    doThrow(new NullPointerException()).when(mockTransferListener).onTransferEnd(
        dataSourceUnderTest);
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
  public void testReadFailure() throws HttpDataSourceException {
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

  // Helper methods.

  private void mockStatusResponse() {
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        UrlRequest.StatusListener statusListener =
            (UrlRequest.StatusListener) invocation.getArguments()[0];
        statusListener.onStatus(TEST_CONNECTION_STATUS);
        return null;
      }
    }).when(mockUrlRequest).getStatus(any(UrlRequest.StatusListener.class));
  }

  private void mockResponseStartSuccess() {
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        dataSourceUnderTest.onResponseStarted(
            mockUrlRequest,
            testUrlResponseInfo);
        return null;
      }
    }).when(mockUrlRequest).start();
  }

  private void mockResponseStartRedirect() {
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        dataSourceUnderTest.onRedirectReceived(
            mockUrlRequest,
            createUrlResponseInfo(307), // statusCode
            "http://redirect.location.com");
        return null;
      }
    }).when(mockUrlRequest).start();
  }

  private void mockResponseStartFailure() {
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        dataSourceUnderTest.onFailed(
            mockUrlRequest,
            createUrlResponseInfo(500), // statusCode
            mockNetworkException);
        return null;
      }
    }).when(mockUrlRequest).start();
  }

  private void mockReadSuccess(int position, int length) {
    final int[] positionAndRemaining = new int[] {position, length};
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        if (positionAndRemaining[1] == 0) {
          dataSourceUnderTest.onSucceeded(mockUrlRequest, testUrlResponseInfo);
        } else {
          ByteBuffer inputBuffer = (ByteBuffer) invocation.getArguments()[0];
          int readLength = Math.min(positionAndRemaining[1], inputBuffer.remaining());
          inputBuffer.put(buildTestDataBuffer(positionAndRemaining[0], readLength));
          positionAndRemaining[0] += readLength;
          positionAndRemaining[1] -= readLength;
          dataSourceUnderTest.onReadCompleted(
              mockUrlRequest,
              testUrlResponseInfo,
              inputBuffer);
        }
        return null;
      }
    }).when(mockUrlRequest).read(any(ByteBuffer.class));
  }

  private void mockReadFailure() {
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        dataSourceUnderTest.onFailed(
            mockUrlRequest,
            createUrlResponseInfo(500), // statusCode
            mockNetworkException);
        return null;
      }
    }).when(mockUrlRequest).read(any(ByteBuffer.class));
  }

  private ConditionVariable buildUrlRequestStartedCondition() {
    final ConditionVariable startedCondition = new ConditionVariable();
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        startedCondition.open();
        return null;
      }
    }).when(mockUrlRequest).start();
    return startedCondition;
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

}
