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
import static org.mockito.Matchers.anyInt;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.chromium.net.CronetEngine;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlRequestException;
import org.chromium.net.UrlResponseInfo;
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
  private static final int TEST_BUFFER_SIZE = 16;
  private static final int TEST_CONNECTION_STATUS = 5;

  private DataSpec testDataSpec;
  private DataSpec testPostDataSpec;
  private Map<String, String> testResponseHeader;
  private UrlResponseInfo testUrlResponseInfo;

  /**
   * MockableCronetEngine is an abstract class for helping creating new Requests.
   */
  public abstract static class MockableCronetEngine extends CronetEngine {

    @Override
    public abstract UrlRequest createRequest(String url, UrlRequest.Callback callback,
        Executor executor, int priority,
        Collection<Object> connectionAnnotations,
        boolean disableCache,
        boolean disableConnectionMigration);
  }

  @Mock
  private UrlRequest mockUrlRequest;
  @Mock
  private Predicate<String> mockContentTypePredicate;
  @Mock
  private TransferListener mockTransferListener;
  @Mock
  private Clock mockClock;
  @Mock
  private Executor mockExecutor;
  @Mock
  private UrlRequestException mockUrlRequestException;
  @Mock
  private MockableCronetEngine mockCronetEngine;

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
    when(mockCronetEngine.createRequest(
        anyString(),
        any(UrlRequest.Callback.class),
        any(Executor.class),
        anyInt(),
        eq(Collections.emptyList()),
        any(Boolean.class),
        any(Boolean.class))).thenReturn(mockUrlRequest);
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
    return new UrlResponseInfo(
        Collections.singletonList(TEST_URL),
        statusCode,
        null, // httpStatusText
        responseHeaderList,
        false, // wasCached
        null, // negotiatedProtocol
        null); // proxyServer
  }

  @Test(expected = IllegalStateException.class)
  public void testOpeningTwiceThrows() throws HttpDataSourceException, IllegalStateException {
    mockResponesStartSuccess();

    assertConnectionState(CronetDataSource.IDLE_CONNECTION);
    dataSourceUnderTest.open(testDataSpec);
    assertConnectionState(CronetDataSource.OPEN_CONNECTION);
    dataSourceUnderTest.open(testDataSpec);
  }

  @Test
  public void testCallbackFromPreviousRequest() throws HttpDataSourceException {
    mockResponesStartSuccess();

    dataSourceUnderTest.open(testDataSpec);
    dataSourceUnderTest.close();
    // Prepare a mock UrlRequest to be used in the second open() call.
    final UrlRequest mockUrlRequest2 = mock(UrlRequest.class);
    when(mockCronetEngine.createRequest(
        anyString(),
        any(UrlRequest.Callback.class),
        any(Executor.class),
        anyInt(),
        eq(Collections.emptyList()),
        any(Boolean.class),
        any(Boolean.class))).thenReturn(mockUrlRequest2);
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        // Invoke the callback for the previous request.
        dataSourceUnderTest.onFailed(
            mockUrlRequest,
            testUrlResponseInfo,
            null);
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
    mockResponesStartSuccess();

    dataSourceUnderTest.open(testDataSpec);
    verify(mockCronetEngine).createRequest(
        eq(TEST_URL),
        any(UrlRequest.Callback.class),
        any(Executor.class),
        anyInt(),
        eq(Collections.emptyList()),
        any(Boolean.class),
        any(Boolean.class));
    verify(mockUrlRequest).start();
  }

  @Test
  public void testRequestHeadersSet() throws HttpDataSourceException {
    mockResponesStartSuccess();

    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 1000, 5000, null);
    testResponseHeader.put("Content-Length", Long.toString(5000L));

    dataSourceUnderTest.setRequestProperty("firstHeader", "firstValue");
    dataSourceUnderTest.setRequestProperty("secondHeader", "secondValue");

    dataSourceUnderTest.open(testDataSpec);
    // The header value to add is current position to current position + length - 1.
    verify(mockUrlRequest).addHeader("Range", "bytes=1000-5999");
    verify(mockUrlRequest).addHeader("firstHeader", "firstValue");
    verify(mockUrlRequest).addHeader("secondHeader", "secondValue");
    verify(mockUrlRequest).start();
  }

  @Test
  public void testRequestOpen() throws HttpDataSourceException {
    mockResponesStartSuccess();

    assertEquals(TEST_CONTENT_LENGTH, dataSourceUnderTest.open(testDataSpec));
    assertConnectionState(CronetDataSource.OPEN_CONNECTION);
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
      assertConnectionState(CronetDataSource.OPENING_CONNECTION);
      verify(mockTransferListener, never()).onTransferStart(dataSourceUnderTest, testDataSpec);
    }
  }

  @Test
  public void testRequestOpenFailDueToDnsFailure() {
    mockResponseStartFailure();
    when(mockUrlRequestException.getErrorCode()).thenReturn(
        UrlRequestException.ERROR_HOSTNAME_NOT_RESOLVED);

    try {
      dataSourceUnderTest.open(testDataSpec);
      fail("HttpDataSource.HttpDataSourceException expected");
    } catch (HttpDataSourceException e) {
      // Check for connection not automatically closed.
      assertTrue(e.getCause() instanceof UnknownHostException);
      verify(mockUrlRequest, never()).cancel();
      assertConnectionState(CronetDataSource.OPENING_CONNECTION);
      verify(mockTransferListener, never()).onTransferStart(dataSourceUnderTest, testDataSpec);
    }
  }

  @Test
  public void testRequestOpenValidatesStatusCode() {
    mockResponesStartSuccess();
    testUrlResponseInfo = createUrlResponseInfo(500); // statusCode

    try {
      dataSourceUnderTest.open(testDataSpec);
      fail("HttpDataSource.HttpDataSourceException expected");
    } catch (HttpDataSourceException e) {
      assertTrue(e instanceof HttpDataSource.InvalidResponseCodeException);
      // Check for connection not automatically closed.
      verify(mockUrlRequest, never()).cancel();
      assertConnectionState(CronetDataSource.OPENING_CONNECTION);
      verify(mockTransferListener, never()).onTransferStart(dataSourceUnderTest, testDataSpec);
    }
  }

  @Test
  public void testRequestOpenValidatesContentTypePredicate() {
    mockResponesStartSuccess();
    when(mockContentTypePredicate.evaluate(anyString())).thenReturn(false);

    try {
      dataSourceUnderTest.open(testDataSpec);
      fail("HttpDataSource.HttpDataSourceException expected");
    } catch (HttpDataSourceException e) {
      assertTrue(e instanceof HttpDataSource.InvalidContentTypeException);
      // Check for connection not automatically closed.
      verify(mockUrlRequest, never()).cancel();
      assertConnectionState(CronetDataSource.OPENING_CONNECTION);
      verify(mockContentTypePredicate).evaluate(TEST_CONTENT_TYPE);
    }
  }

  @Test
  public void testRequestOpenValidatesContentLength() {
    mockResponesStartSuccess();

    // Data spec's requested length, 5000. Test response's length, 16,000.
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 1000, 5000, null);

    try {
      dataSourceUnderTest.open(testDataSpec);
      fail("HttpDataSource.HttpDataSourceException expected");
    } catch (HttpDataSourceException e) {
      verify(mockUrlRequest).addHeader("Range", "bytes=1000-5999");
      // Check for connection not automatically closed.
      verify(mockUrlRequest, never()).cancel();
      assertConnectionState(CronetDataSource.OPENING_CONNECTION);
      verify(mockTransferListener, never()).onTransferStart(dataSourceUnderTest, testPostDataSpec);
    }
  }

  @Test
  public void testPostRequestOpen() throws HttpDataSourceException {
    mockResponesStartSuccess();

    dataSourceUnderTest.setRequestProperty("Content-Type", TEST_CONTENT_TYPE);
    assertEquals(TEST_CONTENT_LENGTH, dataSourceUnderTest.open(testPostDataSpec));
    assertConnectionState(CronetDataSource.OPEN_CONNECTION);
    verify(mockTransferListener).onTransferStart(dataSourceUnderTest, testPostDataSpec);
  }

  @Test
  public void testPostRequestOpenValidatesContentType() {
    mockResponesStartSuccess();

    try {
      dataSourceUnderTest.open(testPostDataSpec);
      fail("HttpDataSource.HttpDataSourceException expected");
    } catch (HttpDataSourceException e) {
      verify(mockUrlRequest, never()).start();
    }
  }

  @Test
  public void testPostRequestOpenRejects307Redirects() {
    mockResponesStartSuccess();
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
    mockResponesStartSuccess();
    mockReadSuccess();

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
    mockResponesStartSuccess();
    mockReadSuccess();

    byte[] returnedBuffer = new byte[8];

    // First request.
    testResponseHeader.put("Content-Length", Long.toString(1L));
    testUrlResponseInfo = createUrlResponseInfo(200); // statusCode
    dataSourceUnderTest.open(testDataSpec);
    dataSourceUnderTest.read(returnedBuffer, 0, 1);
    dataSourceUnderTest.close();

    // Second request. There's no Content-Length response header.
    testResponseHeader.remove("Content-Length");
    testUrlResponseInfo = createUrlResponseInfo(200); // statusCode
    dataSourceUnderTest.open(testDataSpec);
    returnedBuffer = new byte[16];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 10);
    assertEquals(10, bytesRead);

    mockResponseFinished();

    // Should read whats left in the buffer first.
    bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 10);
    assertEquals(6, bytesRead);
    bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 10);
    assertEquals(C.RESULT_END_OF_INPUT, bytesRead);
  }

  @Test
  public void testReadWithOffset() throws HttpDataSourceException {
    mockResponesStartSuccess();
    mockReadSuccess();

    dataSourceUnderTest.open(testDataSpec);

    byte[] returnedBuffer = new byte[16];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 8, 8);
    assertArrayEquals(prefixZeros(buildTestDataArray(0, 8), 16), returnedBuffer);
    assertEquals(8, bytesRead);
    verify(mockTransferListener).onBytesTransferred(dataSourceUnderTest, 8);
  }

  @Test
  public void testReadReturnsWhatItCan() throws HttpDataSourceException {
    mockResponesStartSuccess();
    mockReadSuccess();

    dataSourceUnderTest.open(testDataSpec);

    byte[] returnedBuffer = new byte[24];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 24);
    assertArrayEquals(suffixZeros(buildTestDataArray(0, 16), 24), returnedBuffer);
    assertEquals(16, bytesRead);
    verify(mockTransferListener).onBytesTransferred(dataSourceUnderTest, 16);
  }

  @Test
  public void testClosedMeansClosed() throws HttpDataSourceException {
    mockResponesStartSuccess();
    mockReadSuccess();

    int bytesRead = 0;
    dataSourceUnderTest.open(testDataSpec);

    byte[] returnedBuffer = new byte[8];
    bytesRead += dataSourceUnderTest.read(returnedBuffer, 0, 8);
    assertArrayEquals(buildTestDataArray(0, 8), returnedBuffer);
    assertEquals(8, bytesRead);

    dataSourceUnderTest.close();
    verify(mockTransferListener).onTransferEnd(dataSourceUnderTest);
    assertConnectionState(CronetDataSource.IDLE_CONNECTION);

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
    mockResponesStartSuccess();
    mockReadSuccess();

    // Ask for 16 bytes
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 10000, 16, null);
    // Let the response promise to give 16 bytes back.
    testResponseHeader.put("Content-Length", Long.toString(16L));

    dataSourceUnderTest.open(testDataSpec);

    byte[] returnedBuffer = new byte[8];
    int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 8);
    assertArrayEquals(buildTestDataArray(0, 8), returnedBuffer);
    assertEquals(8, bytesRead);

    // The current buffer is kept if not completely consumed by DataSource reader.
    returnedBuffer = new byte[8];
    bytesRead += dataSourceUnderTest.read(returnedBuffer, 0, 6);
    assertArrayEquals(suffixZeros(buildTestDataArray(8, 6), 8), returnedBuffer);
    assertEquals(14, bytesRead);

    // 2 bytes left at this point.
    returnedBuffer = new byte[8];
    bytesRead += dataSourceUnderTest.read(returnedBuffer, 0, 8);
    assertArrayEquals(suffixZeros(buildTestDataArray(14, 2), 8), returnedBuffer);
    assertEquals(16, bytesRead);

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
    assertConnectionState(CronetDataSource.OPEN_CONNECTION);
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
    assertEquals(CronetDataSource.OPENING_CONNECTION, dataSourceUnderTest.connectionState);
    // We should still be trying to open as we approach the timeout.
    when(mockClock.elapsedRealtime()).thenReturn((long) TEST_CONNECT_TIMEOUT_MS - 1);
    assertFalse(timedOutCondition.block(50));
    assertEquals(CronetDataSource.OPENING_CONNECTION, dataSourceUnderTest.connectionState);
    // Now we timeout.
    when(mockClock.elapsedRealtime()).thenReturn((long) TEST_CONNECT_TIMEOUT_MS);
    timedOutCondition.block();
    assertEquals(CronetDataSource.OPENING_CONNECTION, dataSourceUnderTest.connectionState);

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
    assertEquals(CronetDataSource.OPENING_CONNECTION, dataSourceUnderTest.connectionState);
    // We should still be trying to open as we approach the timeout.
    when(mockClock.elapsedRealtime()).thenReturn((long) TEST_CONNECT_TIMEOUT_MS - 1);
    assertFalse(openCondition.block(50));
    assertEquals(CronetDataSource.OPENING_CONNECTION, dataSourceUnderTest.connectionState);
    // The response arrives just in time.
    dataSourceUnderTest.onResponseStarted(mockUrlRequest, testUrlResponseInfo);
    openCondition.block();
    assertEquals(CronetDataSource.OPEN_CONNECTION, dataSourceUnderTest.connectionState);
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
    assertEquals(CronetDataSource.OPENING_CONNECTION, dataSourceUnderTest.connectionState);
    // We should still be trying to open as we approach the timeout.
    when(mockClock.elapsedRealtime()).thenReturn((long) TEST_CONNECT_TIMEOUT_MS - 1);
    assertFalse(timedOutCondition.block(50));
    assertEquals(CronetDataSource.OPENING_CONNECTION, dataSourceUnderTest.connectionState);
    // A redirect arrives just in time.
    dataSourceUnderTest.onRedirectReceived(mockUrlRequest, testUrlResponseInfo,
        "RandomRedirectedUrl1");

    long newTimeoutMs = 2 * TEST_CONNECT_TIMEOUT_MS - 1;
    when(mockClock.elapsedRealtime()).thenReturn(newTimeoutMs - 1);
    // Give the thread some time to run.
    assertFalse(timedOutCondition.block(newTimeoutMs));
    // We should still be trying to open as we approach the new timeout.
    assertFalse(timedOutCondition.block(50));
    assertEquals(CronetDataSource.OPENING_CONNECTION, dataSourceUnderTest.connectionState);
    // A redirect arrives just in time.
    dataSourceUnderTest.onRedirectReceived(mockUrlRequest, testUrlResponseInfo,
        "RandomRedirectedUrl2");

    newTimeoutMs = 3 * TEST_CONNECT_TIMEOUT_MS - 2;
    when(mockClock.elapsedRealtime()).thenReturn(newTimeoutMs - 1);
    // Give the thread some time to run.
    assertFalse(timedOutCondition.block(newTimeoutMs));
    // We should still be trying to open as we approach the new timeout.
    assertFalse(timedOutCondition.block(50));
    assertEquals(CronetDataSource.OPENING_CONNECTION, dataSourceUnderTest.connectionState);
    // Now we timeout.
    when(mockClock.elapsedRealtime()).thenReturn(newTimeoutMs);
    timedOutCondition.block();
    assertEquals(CronetDataSource.OPENING_CONNECTION, dataSourceUnderTest.connectionState);

    verify(mockTransferListener, never()).onTransferStart(dataSourceUnderTest, testDataSpec);
    assertEquals(1, openExceptions.get());
  }

  @Test
  public void testExceptionFromTransferListener() throws HttpDataSourceException {
    mockResponesStartSuccess();

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
    mockResponesStartSuccess();
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

  private void mockResponesStartSuccess() {
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
            mockUrlRequestException);
        return null;
      }
    }).when(mockUrlRequest).start();
  }

  private void mockReadSuccess() {
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        ByteBuffer inputBuffer = (ByteBuffer) invocation.getArguments()[0];
        inputBuffer.put(buildTestDataBuffer());
        dataSourceUnderTest.onReadCompleted(
            mockUrlRequest,
            testUrlResponseInfo,
            inputBuffer);
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
            null);
        return null;
      }
    }).when(mockUrlRequest).read(any(ByteBuffer.class));
  }

  private void mockResponseFinished() {
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        dataSourceUnderTest.onSucceeded(mockUrlRequest, testUrlResponseInfo);
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

  private static byte[] buildTestDataArray(int start, int length) {
    return Arrays.copyOfRange(buildTestDataBuffer().array(), start, start + length);
  }

  public static byte[] prefixZeros(byte[] data, int requiredLength) {
    byte[] prefixedData = new byte[requiredLength];
    System.arraycopy(data, 0, prefixedData, requiredLength - data.length, data.length);
    return prefixedData;
  }

  public static byte[] suffixZeros(byte[] data, int requiredLength) {
    return Arrays.copyOf(data, requiredLength);
  }

  private static ByteBuffer buildTestDataBuffer() {
    ByteBuffer testBuffer = ByteBuffer.allocate(TEST_BUFFER_SIZE);
    for (byte i = 1; i <= TEST_BUFFER_SIZE; i++) {
      testBuffer.put(i);
    }
    testBuffer.flip();
    return testBuffer;
  }

  private void assertConnectionState(int state) {
    assertEquals(state, dataSourceUnderTest.connectionState);
  }

}
