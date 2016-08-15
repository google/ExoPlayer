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

package com.google.android.exoplayer.ext.cronet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.HttpDataSource;
import com.google.android.exoplayer.upstream.HttpDataSource.HttpDataSourceException;
import com.google.android.exoplayer.upstream.TransferListener;
import com.google.android.exoplayer.util.Clock;
import com.google.android.exoplayer.util.Predicate;

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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
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
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * FakeConditionVariable is a subclass of ConditionVariable that works in a RobolectricTest.
 * <p/>
 * ConditionVariable.block(long timeout) never times out in a RobolectricTest. FakeConditionVariable
 * doesn't have this problem.
 */
class FakeConditionVariable extends ConditionVariable {
  private volatile boolean closed = true;

  @Override
  public boolean block(long timeout) {
    // This fake implmentation doesn't respect the timeout value, nor does it return early upon
    // open(). This is fine for the purpose of testing DirectCronetDataSource +
    // ExtendableTimeoutConditionVariable.
    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
      // ignore
    }
    return !closed;
  }

  @Override
  public void block() {
    while (!block(10)) {
    }
  }

  @Override
  public void close() {
    closed = true;
  }

  @Override
  public void open() {
    closed = false;
  }
}

/**
 * Tests for {@link CronetDataSource}.
 *
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class CronetDataSourceTest {

  private static final int TEST_CONNECT_TIMEOUT_MS = 100;
  private static final int TEST_READ_TIMEOUT_MS = 50;
  private static final String TEST_URL = "http://google.com";
  private static final String TEST_CONTENT_TYPE = "test/test";
  private static final byte[] TEST_POST_BODY = "test post body".getBytes();
  private static final long TEST_CONTENT_LENGTH = 16000L;
  private static final int TEST_BUFFER_SIZE = 16;
  private static final int TEST_CONNECTION_STATUS = 5;

  private static DataSpec testDataSpec;
  private static DataSpec testPostDataSpec;
  private static ByteBuffer testBuffer;
  private static Map<String, String> testResponseHeader;
  private static UrlResponseInfo testUrlResponseInfo;

  @Mock
  private MockableCronetEngine mockCronetEngine;
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
  private CronetDataSource dataSourceUnderTest;

  private abstract class MockableCronetEngine extends CronetEngine {
    @Override
    public abstract UrlRequest createRequest(String url, UrlRequest.Callback callback,
                                             Executor executor, int priority,
                                             Collection<Object> connectionAnnotations,
                                             boolean disableCache,
                                             boolean disableConnectionMigration);
  }

  ;

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() {
    initMocks(this);
    dataSourceUnderTest = spy(
        new CronetDataSource(
            mockCronetEngine,
            mockExecutor,
            mockClock,
            mockContentTypePredicate,
            mockTransferListener,
            TEST_CONNECT_TIMEOUT_MS,
            TEST_READ_TIMEOUT_MS,
            true));  // resetTimeoutOnRedirects
    when(mockContentTypePredicate.evaluate(anyString())).thenReturn(true);
    when(mockCronetEngine.createRequest(
        anyString(),
        any(UrlRequest.Callback.class),
        any(Executor.class),
        anyInt(),
        eq(Collections.emptyList()),
        any(Boolean.class),
        any(Boolean.class))).thenReturn(mockUrlRequest);
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        UrlRequest.StatusListener statusListener =
            (UrlRequest.StatusListener) invocation.getArguments()[0];
        statusListener.onStatus(TEST_CONNECTION_STATUS);
        return null;
      }
    }).when(mockUrlRequest).getStatus(any(UrlRequest.StatusListener.class));
    doAnswer(
        new Answer<Object>() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            dataSourceUnderTest.onResponseStarted(
                mockUrlRequest,
                testUrlResponseInfo);
            return null;
          }
        }).when(mockUrlRequest).start();

    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 0, C.LENGTH_UNBOUNDED, null);
    testPostDataSpec = new DataSpec(
        Uri.parse(TEST_URL), TEST_POST_BODY, 0, 0, C.LENGTH_UNBOUNDED, null, 0);
    testResponseHeader = new HashMap<>();
    testResponseHeader.put("Content-Type", TEST_CONTENT_TYPE);
    // This value can be anything since the DataSpec is unbounded.
    testResponseHeader.put("Content-Length", Long.toString(TEST_CONTENT_LENGTH));
    testUrlResponseInfo = createUrlResponseInfo(200); // statusCode

    testBuffer = ByteBuffer.allocate(TEST_BUFFER_SIZE);
    for (byte i = 1; i <= TEST_BUFFER_SIZE; i++) {
      testBuffer.put(i);
    }
    testBuffer.flip();
  }

  private UrlResponseInfo createUrlResponseInfo(int statusCode) {
    ArrayList<Map.Entry<String, String>> responseHeaderList = new ArrayList<>();
    responseHeaderList.addAll(testResponseHeader.entrySet());
    return new UrlResponseInfo(
        Arrays.asList(TEST_URL),
        statusCode,
        null, // httpStatusText
        responseHeaderList,
        false, // wasCached
        null, // negotiatedProtocol
        null); // proxyServer
  }

  @Test(expected = IllegalStateException.class)
  public void testOpeningTwiceThrows() throws HttpDataSourceException, IllegalStateException {
    assertConnectionState(CronetDataSource.ConnectionState.NEW);
    dataSourceUnderTest.open(testDataSpec);
    assertConnectionState(CronetDataSource.ConnectionState.OPEN);
    dataSourceUnderTest.open(testDataSpec);
  }

  @Test
  public void testCallbackFromPreviousRequest() throws HttpDataSourceException {
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
    doAnswer(
        new Answer<Object>() {
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
    try {
      dataSourceUnderTest.open(testDataSpec);
    } catch (HttpDataSourceException e) {
      fail("onFailed callback from the previous request should not cause an "
          + "HttpDataSource.HttpDataSourceException.");
    }
  }

  @Test
  public void testRequestStartCalled() {
    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
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
            } catch (Exception e) {
              fail(e.getMessage());
            }
          }
        });
  }

  @Test
  public void testRequestHeadersSet() {
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 1000, 5000, null);
    testResponseHeader.put("Content-Length", Long.toString(5000L));

    dataSourceUnderTest.setRequestProperty("firstHeader", "firstValue");
    dataSourceUnderTest.setRequestProperty("secondHeader", "secondValue");

    new BlockingBackgroundExecutor().execute(new Runnable() {
      @Override
      public void run() {
        try {
          dataSourceUnderTest.open(testDataSpec);
          // The header value to add is current position to current position + length - 1.
          verify(mockUrlRequest).addHeader("Range", "bytes=1000-5999");
          verify(mockUrlRequest).addHeader("firstHeader", "firstValue");
          verify(mockUrlRequest).addHeader("secondHeader", "secondValue");
          verify(mockUrlRequest).start();
        } catch (Exception e) {
          fail(e.getMessage());
        }
      }
    });
  }

  @Test
  public void testRequestOpen() {
    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              assertEquals(TEST_CONTENT_LENGTH, dataSourceUnderTest.open(testDataSpec));
              assertConnectionState(CronetDataSource.ConnectionState.OPEN);
              verify(mockTransferListener).onTransferStart();
            } catch (Exception e) {
              fail(e.getMessage());
            }
          }
        });
  }

  @Test
  public void testRequestOpenFail() {
    doAnswer(
        new Answer<Object>() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            dataSourceUnderTest.onFailed(
                mockUrlRequest,
                createUrlResponseInfo(500), // statusCode
                mockUrlRequestException);
            return null;
          }
        }).when(mockUrlRequest).start();

    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              dataSourceUnderTest.open(testDataSpec);
              fail("HttpDataSource.HttpDataSourceException expected");
            } catch (HttpDataSourceException e) {
              // Check for connection not automatically closed.
              assertFalse(e.getCause() instanceof UnknownHostException);
              verify(mockUrlRequest, never()).cancel();
              assertConnectionState(CronetDataSource.ConnectionState.OPENING);
              verify(mockTransferListener, never()).onTransferStart();
            }
          }
        });
  }

  @Test
  public void testRequestOpenFailDueToDnsFailure() {
    when(mockUrlRequestException.getErrorCode()).thenReturn(
        UrlRequestException.ERROR_HOSTNAME_NOT_RESOLVED);
    doAnswer(
        new Answer<Object>() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            dataSourceUnderTest.onFailed(
                mockUrlRequest,
                createUrlResponseInfo(500), // statusCode
                mockUrlRequestException);
            return null;
          }
        }).when(mockUrlRequest).start();

    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              dataSourceUnderTest.open(testDataSpec);
              fail("HttpDataSource.HttpDataSourceException expected");
            } catch (HttpDataSourceException e) {
              // Check for connection not automatically closed.
              assertTrue(e.getCause() instanceof UnknownHostException);
              verify(mockUrlRequest, never()).cancel();
              assertConnectionState(CronetDataSource.ConnectionState.OPENING);
              verify(mockTransferListener, never()).onTransferStart();
            }
          }
        });
  }

  @Test
  public void testRequestOpenValidatesStatusCode() {
    testUrlResponseInfo = createUrlResponseInfo(500); // statusCode

    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              dataSourceUnderTest.open(testDataSpec);
              fail("HttpDataSource.HttpDataSourceException expected");
            } catch (HttpDataSourceException e) {
              assertTrue(e instanceof HttpDataSource.InvalidResponseCodeException);
              // Check for connection not automatically closed.
              verify(mockUrlRequest, never()).cancel();
              assertConnectionState(CronetDataSource.ConnectionState.OPENING);
              verify(mockTransferListener, never()).onTransferStart();
            }
          }
        });
  }

  @Test
  public void testRequestOpenValidatesContentTypePredicate() {
    when(mockContentTypePredicate.evaluate(anyString())).thenReturn(false);

    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              dataSourceUnderTest.open(testDataSpec);
              fail("HttpDataSource.HttpDataSourceException expected");
            } catch (HttpDataSourceException e) {
              assertTrue(e instanceof HttpDataSource.InvalidContentTypeException);
              // Check for connection not automatically closed.
              verify(mockUrlRequest, never()).cancel();
              assertConnectionState(CronetDataSource.ConnectionState.OPENING);
              verify(mockContentTypePredicate).evaluate(TEST_CONTENT_TYPE);
            }
          }
        });
  }

  @Test
  public void testRequestOpenValidatesContentLength() {
    // Data spec's requested length, 5000. Test response's length, 16,000.
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 1000, 5000, null);

    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              dataSourceUnderTest.open(testDataSpec);
              fail("HttpDataSource.HttpDataSourceException expected");
            } catch (HttpDataSourceException e) {
              verify(mockUrlRequest).addHeader("Range", "bytes=1000-5999");
              // Check for connection not automatically closed.
              verify(mockUrlRequest, never()).cancel();
              assertConnectionState(CronetDataSource.ConnectionState.OPENING);
              verify(mockTransferListener, never()).onTransferStart();
            }
          }
        });
  }

  @Test
  public void testPostRequestOpen() {
    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              dataSourceUnderTest.setRequestProperty("Content-Type", TEST_CONTENT_TYPE);
              assertEquals(TEST_CONTENT_LENGTH, dataSourceUnderTest.open(testPostDataSpec));
              assertConnectionState(CronetDataSource.ConnectionState.OPEN);
              verify(mockTransferListener).onTransferStart();
            } catch (Exception e) {
              fail(e.getMessage());
            }
          }
        });
  }

  @Test
  public void testPostRequestOpenValidatesContentType() {
    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              dataSourceUnderTest.open(testPostDataSpec);
              fail("HttpDataSource.HttpDataSourceException expected");
            } catch (HttpDataSource.HttpDataSourceException e) {
              verify(mockUrlRequest, never()).start();
            }
          }
        });
  }

  @Test
  public void testPostRequestOpenRejects307Redirects() {
    testUrlResponseInfo = createUrlResponseInfo(307); // statusCode
    doAnswer(
        new Answer<Object>() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            dataSourceUnderTest.onRedirectReceived(
                mockUrlRequest,
                testUrlResponseInfo,
                "http://redirect.location.com");
            return null;
          }
        }).when(mockUrlRequest).start();

    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              dataSourceUnderTest.setRequestProperty("Content-Type", TEST_CONTENT_TYPE);
              dataSourceUnderTest.open(testPostDataSpec);
              fail("HttpDataSource.HttpDataSourceException expected");
            } catch (HttpDataSource.HttpDataSourceException e) {
              verify(mockUrlRequest, never()).followRedirect();
            }
          }
        });
  }

  @Test
  public void testRequestReadTwice() {
    // Derived from testBuffer values.
    final byte[] expectedBuffer1 = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
    final byte[] expectedBuffer2 = new byte[]{9, 10, 11, 12, 13, 14, 15, 16};

    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              dataSourceUnderTest.open(testDataSpec);
              doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocation) throws Throwable {
                  ByteBuffer inputBuffer = (ByteBuffer) invocation.getArguments()[0];
                  inputBuffer.put(testBuffer);
                  dataSourceUnderTest.onReadCompleted(
                      mockUrlRequest,
                      testUrlResponseInfo,
                      inputBuffer);
                  return null;
                }
              }).when(mockUrlRequest).read(any(ByteBuffer.class));

              byte[] returnedBuffer = new byte[8];
              int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 8);
              assertArrayEquals(expectedBuffer1, returnedBuffer);
              assertEquals(8, bytesRead);
              // The current buffer is kept if not completely consumed by DataSource reader.
              returnedBuffer = new byte[8];
              bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 8);
              assertArrayEquals(expectedBuffer2, returnedBuffer);
              assertEquals(8, bytesRead);
              // Should have only called read on cronet once.
              verify(mockUrlRequest, times(1)).read(any(ByteBuffer.class));
              verify(mockTransferListener, times(2)).onBytesTransferred(8);
            } catch (HttpDataSourceException e) {
              fail(e.getMessage());
            }
          }
        });
  }

  @Test
  public void testSecondRequestNoContentLength() {
    // Derived from testBuffer values.
    final byte[] expectedBuffer1 = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};

    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocation) throws Throwable {
                  ByteBuffer inputBuffer = (ByteBuffer) invocation.getArguments()[0];
                  inputBuffer.put(testBuffer);
                  dataSourceUnderTest.onReadCompleted(
                      mockUrlRequest,
                      testUrlResponseInfo,
                      inputBuffer);
                  return null;
                }
              }).when(mockUrlRequest).read(any(ByteBuffer.class));
              byte[] returnedBuffer = new byte[8];
              // First request.
              testResponseHeader.put("Content-Length", Long.toString(1L));
              testUrlResponseInfo = createUrlResponseInfo(200); // statusCode
              dataSourceUnderTest.open(testDataSpec);
              dataSourceUnderTest.read(returnedBuffer, 0, 1);
              dataSourceUnderTest.close();
              testBuffer.rewind();
              // Second request. There's no Content-Length response header.
              testResponseHeader.remove("Content-Length");
              testUrlResponseInfo = createUrlResponseInfo(200); // statusCode
              dataSourceUnderTest.open(testDataSpec);
              returnedBuffer = new byte[16];
              int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 10);
              assertEquals(10, bytesRead);
              doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocation) throws Throwable {
                  dataSourceUnderTest.onSucceeded(mockUrlRequest, testUrlResponseInfo);
                  return null;
                }
              }).when(mockUrlRequest).read(any(ByteBuffer.class));
              // Should read whats left in the buffer first.
              bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 10);
              assertEquals(6, bytesRead);
              bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 10);
              assertEquals(C.RESULT_END_OF_INPUT, bytesRead);
            } catch (HttpDataSourceException e) {
              fail(e.getMessage());
            }
          }
        });
  }

  @Test
  public void testReadWithOffset() {
    // Derived from testBuffer values.
    final byte[] expectedBuffer = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8};

    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              dataSourceUnderTest.open(testDataSpec);
              doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocation) throws Throwable {
                  ByteBuffer inputBuffer = (ByteBuffer) invocation.getArguments()[0];
                  inputBuffer.put(testBuffer);
                  dataSourceUnderTest.onReadCompleted(
                      mockUrlRequest,
                      testUrlResponseInfo,
                      inputBuffer);
                  return null;
                }
              }).when(mockUrlRequest).read(any(ByteBuffer.class));
              byte[] returnedBuffer = new byte[16];
              int bytesRead = dataSourceUnderTest.read(returnedBuffer, 8, 8);
              assertArrayEquals(expectedBuffer, returnedBuffer);
              assertEquals(8, bytesRead);
              verify(mockTransferListener).onBytesTransferred(8);
            } catch (HttpDataSourceException e) {
              fail(e.getMessage());
            }
          }
        });
  }

  @Test
  public void testReadReturnsWhatItCan() {
    // Derived from testBuffer values.
    final byte[] expectedBuffer =
        new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 0, 0, 0, 0, 0, 0, 0, 0};

    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              dataSourceUnderTest.open(testDataSpec);
              doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocation) throws Throwable {
                  ByteBuffer inputBuffer = (ByteBuffer) invocation.getArguments()[0];
                  inputBuffer.put(testBuffer);
                  dataSourceUnderTest.onReadCompleted(
                      mockUrlRequest,
                      testUrlResponseInfo,
                      inputBuffer);
                  return null;
                }
              }).when(mockUrlRequest).read(any(ByteBuffer.class));
              byte[] returnedBuffer = new byte[24];
              // It returns what it read, not necessarily filling the buffer.
              int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 24);
              assertArrayEquals(expectedBuffer, returnedBuffer);
              assertEquals(16, bytesRead);
              verify(mockTransferListener).onBytesTransferred(16);
            } catch (HttpDataSourceException e) {
              fail(e.getMessage());
            }
          }
        });
  }

  @Test
  public void testClosedMeansClosed() {
    // Derived from testBuffer values.
    final byte[] expectedBuffer = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};

    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            int bytesRead = 0;
            try {
              dataSourceUnderTest.open(testDataSpec);
              doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocation) throws Throwable {
                  ByteBuffer inputBuffer = (ByteBuffer) invocation.getArguments()[0];
                  inputBuffer.put(testBuffer);
                  dataSourceUnderTest.onReadCompleted(
                      mockUrlRequest,
                      testUrlResponseInfo,
                      inputBuffer);
                  return null;
                }
              }).when(mockUrlRequest).read(any(ByteBuffer.class));
              byte[] returnedBuffer = new byte[8];
              bytesRead += dataSourceUnderTest.read(returnedBuffer, 0, 8);
              assertArrayEquals(expectedBuffer, returnedBuffer);
              dataSourceUnderTest.close();
              assertConnectionState(CronetDataSource.ConnectionState.CLOSED);
              returnedBuffer = new byte[8];
              bytesRead += dataSourceUnderTest.read(returnedBuffer, 0, 8);
              fail("IllegalStateException expected");
            } catch (HttpDataSourceException e) {
              fail("IllegalStateException expected. Got HttpDataSourceException");
            } catch (IllegalStateException e) {
              verify(mockTransferListener).onTransferEnd();
              // 16 bytes were attempted but only 8 should have been successfully read.
              assertEquals(8, bytesRead);
            }
          }
        });
  }

  @Test
  public void testOverread() {
    // Ask for 16 bytes
    testDataSpec = new DataSpec(Uri.parse(TEST_URL), 10000, 16, null);

    // Let the response promise to give 16 bytes back.
    testResponseHeader.put("Content-Length", Long.toString(16L));

    // Derived from testBuffer values.
    final byte[] expectedBuffer1 = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
    final byte[] expectedBuffer2 = new byte[]{9, 10, 11, 12, 13, 14, 0, 0};
    final byte[] expectedBuffer3 = new byte[]{15, 16, 0, 0, 0, 0, 0, 0};

    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              dataSourceUnderTest.open(testDataSpec);
              doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocation) throws Throwable {
                  ByteBuffer inputBuffer = (ByteBuffer) invocation.getArguments()[0];
                  inputBuffer.put(testBuffer);
                  dataSourceUnderTest.onReadCompleted(
                      mockUrlRequest,
                      testUrlResponseInfo,
                      inputBuffer);
                  return null;
                }
              }).when(mockUrlRequest).read(any(ByteBuffer.class));

              byte[] returnedBuffer = new byte[8];
              int bytesRead = dataSourceUnderTest.read(returnedBuffer, 0, 8);
              assertArrayEquals(expectedBuffer1, returnedBuffer);
              assertEquals(8, bytesRead);
              // The current buffer is kept if not completely consumed by DataSource reader.
              returnedBuffer = new byte[8];
              bytesRead += dataSourceUnderTest.read(returnedBuffer, 0, 6);
              assertArrayEquals(expectedBuffer2, returnedBuffer);
              assertEquals(14, bytesRead);
              // 2 bytes left at this point.
              returnedBuffer = new byte[8];
              bytesRead += dataSourceUnderTest.read(returnedBuffer, 0, 8);
              assertArrayEquals(expectedBuffer3, returnedBuffer);
              assertEquals(16, bytesRead);
              // Should have only called read on cronet once.
              verify(mockUrlRequest, times(1)).read(any(ByteBuffer.class));
              verify(mockTransferListener, times(1)).onBytesTransferred(8);
              verify(mockTransferListener, times(1)).onBytesTransferred(6);
              verify(mockTransferListener, times(1)).onBytesTransferred(2);

              // Create a fresh new return buffer.
              returnedBuffer = new byte[16];
              // Now we already returned the 16 bytes initially asked.
              // Try to read again even though all requested 16 bytes are already returned.
              // Return C.RESULT_END_OF_INPUT
              int bytesOverRead = dataSourceUnderTest.read(returnedBuffer, 0, 16);
              // The byte array is expected to be untouched.
              byte[] expectedBuffer = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

              assertEquals(C.RESULT_END_OF_INPUT, bytesOverRead);
              assertArrayEquals(expectedBuffer, returnedBuffer);
              // C.RESULT_END_OF_INPUT should not be reported though the TransferListener.
              verify(mockTransferListener, never()).onBytesTransferred(C.RESULT_END_OF_INPUT);
              // There should still be only one call to read on cronet.
              verify(mockUrlRequest, times(1)).read(any(ByteBuffer.class));
              // Check for connection not automatically closed.
              verify(mockUrlRequest, never()).cancel();
              assertConnectionState(CronetDataSource.ConnectionState.OPEN);
              assertEquals(16, bytesRead);
            } catch (HttpDataSourceException e) {
              fail(e.getMessage());
            }
          }
        });
  }

  void createCronetDataSourceWithExtendableTimeoutConditionVariable() {
    dataSourceUnderTest = spy(
        new CronetDataSource(
            mockCronetEngine,
            mockExecutor,
            mockClock,
            mockContentTypePredicate,
            mockTransferListener,
            TEST_CONNECT_TIMEOUT_MS,
            TEST_READ_TIMEOUT_MS,
            true)); // resetTimeoutOnRedirects
  }

  @Test
  public void testConnectTimeoutWithTimeoutCheckerRunnable() {
    testConnectTimeout();
  }

  void testConnectTimeout() {
    final ConditionVariable startOperation = new ConditionVariable();
    final ConditionVariable openOperation = new ConditionVariable();
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        startOperation.open();
        return null;
      }
    }).when(mockUrlRequest).start();
    when(mockClock.elapsedRealtime()).thenReturn(1000L);
    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              Executor executor = Executors.newFixedThreadPool(2);
              openOperation.close();
              executor.execute(new Runnable() {
                @Override
                public void run() {
                  try {
                    dataSourceUnderTest.open(testDataSpec);
                    fail(); // This test expects a failure
                  } catch (HttpDataSourceException e) {
                    // Expected exception
                    assertTrue(e instanceof CronetDataSource.OpenException);
                    assertTrue(e.getCause() instanceof SocketTimeoutException);
                    assertEquals(
                        TEST_CONNECTION_STATUS,
                        ((CronetDataSource.OpenException) e)
                            .getCronetConnectionStatus().intValue());
                  }
                  openOperation.open();
                }
              });
              startOperation.block();
              assertEquals(
                  CronetDataSource.ConnectionState.OPENING,
                  dataSourceUnderTest.connectionState);
              when(mockClock.elapsedRealtime()).thenReturn(1150L);
              if (dataSourceUnderTest.timeoutCheckerRunnable != null) {
                dataSourceUnderTest.timeoutCheckerRunnable.checkTimeout();
              }
              openOperation.block();
              assertEquals(
                  CronetDataSource.ConnectionState.OPENING,
                  dataSourceUnderTest.connectionState);
              verify(mockTransferListener, never()).onTransferStart();
            } catch (Exception e) {
              fail(e.getMessage());
            }
          }
        });
  }

  @Test
  public void testConnectResponseBeforeTimeoutWithTimeoutCheckerRunnable() {
    testConnectResponseBeforeTimeout();
  }

  void testConnectResponseBeforeTimeout() {
    final ConditionVariable startOperation = new ConditionVariable();
    final ConditionVariable openOperation = new ConditionVariable();
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        startOperation.open();
        return null;
      }
    }).when(mockUrlRequest).start();
    when(mockClock.elapsedRealtime()).thenReturn(1000L);
    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              Executor executor = Executors.newFixedThreadPool(2);
              openOperation.close();
              executor.execute(new Runnable() {
                @Override
                public void run() {
                  try {
                    dataSourceUnderTest.open(testDataSpec);
                  } catch (HttpDataSourceException e) {
                    fail();
                  }
                  openOperation.open();
                }
              });
              startOperation.block();
              assertEquals(
                  CronetDataSource.ConnectionState.OPENING,
                  dataSourceUnderTest.connectionState);
              // Still ok.
              when(mockClock.elapsedRealtime()).thenReturn(1050L);
              if (dataSourceUnderTest.timeoutCheckerRunnable != null) {
                dataSourceUnderTest.timeoutCheckerRunnable.checkTimeout();
              }
              dataSourceUnderTest.onResponseStarted(
                  mockUrlRequest,
                  testUrlResponseInfo);
              assertNull(dataSourceUnderTest.timeoutCheckerRunnable);
              openOperation.block();
              assertEquals(
                  CronetDataSource.ConnectionState.OPEN,
                  dataSourceUnderTest.connectionState);
            } catch (Exception e) {
              fail(e.getMessage());
            }
          }
        });
  }

  @Test
  public void testRedirectIncreasesConnectionTimeoutWithTimeoutCheckerRunnable() {
    testRedirectIncreasesConnectionTimeout();
  }

  void testRedirectIncreasesConnectionTimeout() {
    final ConditionVariable startOperation = new ConditionVariable();
    final ConditionVariable openOperation = new ConditionVariable();
    final AtomicInteger openExceptions = new AtomicInteger(0);
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        startOperation.open();
        return null;
      }
    }).when(mockUrlRequest).start();
    when(mockClock.elapsedRealtime()).thenReturn(1000L);
    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              Executor executor = Executors.newFixedThreadPool(2);
              openOperation.close();
              executor.execute(new Runnable() {
                @Override
                public void run() {
                  try {
                    dataSourceUnderTest.open(testDataSpec);
                    fail(); // Exception expected.
                  } catch (HttpDataSourceException e) {
                    // Expected exception
                    assertTrue(e instanceof CronetDataSource.OpenException);
                    assertTrue(e.getCause() instanceof SocketTimeoutException);
                    openExceptions.getAndIncrement();
                  }
                  openOperation.open();
                }
              });
              startOperation.block();

              assertEquals(
                  CronetDataSource.ConnectionState.OPENING,
                  dataSourceUnderTest.connectionState);
              when(mockClock.elapsedRealtime()).thenReturn(1050L);
              dataSourceUnderTest.onRedirectReceived(
                  mockUrlRequest,
                  testUrlResponseInfo,
                  "RandomRedirectedUrl");
              // Original timeout is 1100 but now 1150 with 1 redirect at 1050.
              when(mockClock.elapsedRealtime()).thenReturn(1100L);
              if (dataSourceUnderTest.timeoutCheckerRunnable != null) {
                dataSourceUnderTest.timeoutCheckerRunnable.checkTimeout();
              }
              Thread.sleep(50); // Give opening thread time to run.
              assertEquals(
                  CronetDataSource.ConnectionState.OPENING,
                  dataSourceUnderTest.connectionState);
              assertEquals(0, openExceptions.get());

              // This will timeout since it's > 1150.
              when(mockClock.elapsedRealtime()).thenReturn(1160L);
              if (dataSourceUnderTest.timeoutCheckerRunnable != null) {
                dataSourceUnderTest.timeoutCheckerRunnable.checkTimeout();
              }
              openOperation.block();
              assertEquals(
                  CronetDataSource.ConnectionState.OPENING,
                  dataSourceUnderTest.connectionState);
              verify(mockTransferListener, never()).onTransferStart();
              assertEquals(1, openExceptions.get());
            } catch (Exception e) {
              fail(e.getMessage());
            }
          }
        });
  }

  @Test
  public void testMultipleRedirectConnectionTimeoutWithTimeoutCheckerRunnable() {
    testMultipleRedirectConnectionTimeout();
  }

  void testMultipleRedirectConnectionTimeout() {
    final ConditionVariable startOperation = new ConditionVariable();
    final ConditionVariable openOperation = new ConditionVariable();
    final AtomicInteger openExceptions = new AtomicInteger(0);
    doAnswer(new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        startOperation.open();
        return null;
      }
    }).when(mockUrlRequest).start();
    when(mockClock.elapsedRealtime()).thenReturn(1000L);
    new BlockingBackgroundExecutor().execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              Executor executor = Executors.newFixedThreadPool(2);
              openOperation.close();
              executor.execute(new Runnable() {
                @Override
                public void run() {
                  try {
                    dataSourceUnderTest.open(testDataSpec);
                    fail(); // Exception expected.
                  } catch (HttpDataSourceException e) {
                    // Expected exception
                    assertTrue(e instanceof CronetDataSource.OpenException);
                    assertTrue(e.getCause() instanceof SocketTimeoutException);
                    openExceptions.getAndIncrement();
                  }
                  openOperation.open();
                }
              });
              startOperation.block();

              assertEquals(
                  CronetDataSource.ConnectionState.OPENING,
                  dataSourceUnderTest.connectionState);
              when(mockClock.elapsedRealtime()).thenReturn(1050L);
              dataSourceUnderTest.onRedirectReceived(
                  mockUrlRequest,
                  testUrlResponseInfo,
                  "RandomRedirectedUrl");
              // Original timeout is 1100 but now 1150 with 1 redirect at 1050.
              when(mockClock.elapsedRealtime()).thenReturn(1100L);
              if (dataSourceUnderTest.timeoutCheckerRunnable != null) {
                dataSourceUnderTest.timeoutCheckerRunnable.checkTimeout();
              }
              Thread.sleep(50); // Give opening thread time to run.
              assertEquals(
                  CronetDataSource.ConnectionState.OPENING,
                  dataSourceUnderTest.connectionState);
              assertEquals(0, openExceptions.get());

              dataSourceUnderTest.onRedirectReceived(
                  mockUrlRequest,
                  testUrlResponseInfo,
                  "RandomRedirectedUrl");
              // Timeout is now 1200.
              when(mockClock.elapsedRealtime()).thenReturn(1190L);
              if (dataSourceUnderTest.timeoutCheckerRunnable != null) {
                dataSourceUnderTest.timeoutCheckerRunnable.checkTimeout();
              }
              Thread.sleep(50); // Give opening thread time to run.
              assertEquals(
                  CronetDataSource.ConnectionState.OPENING,
                  dataSourceUnderTest.connectionState);
              assertEquals(0, openExceptions.get());

              dataSourceUnderTest.onRedirectReceived(
                  mockUrlRequest,
                  testUrlResponseInfo,
                  "RandomRedirectedUrl");
              // Timeout is now 1290.
              when(mockClock.elapsedRealtime()).thenReturn(1250L);
              if (dataSourceUnderTest.timeoutCheckerRunnable != null) {
                dataSourceUnderTest.timeoutCheckerRunnable.checkTimeout();
              }
              Thread.sleep(50); // Give opening thread time to run.
              assertEquals(
                  CronetDataSource.ConnectionState.OPENING,
                  dataSourceUnderTest.connectionState);
              assertEquals(0, openExceptions.get());

              // This will timeout since it's > 1290.
              when(mockClock.elapsedRealtime()).thenReturn(1300L);
              if (dataSourceUnderTest.timeoutCheckerRunnable != null) {
                dataSourceUnderTest.timeoutCheckerRunnable.checkTimeout();
              }
              openOperation.block();
              assertEquals(
                  CronetDataSource.ConnectionState.OPENING,
                  dataSourceUnderTest.connectionState);
              verify(mockTransferListener, never()).onTransferStart();
              assertEquals(1, openExceptions.get());
            } catch (Exception e) {
              fail(e.getMessage());
            }
          }
        });
  }

  @Test
  public void testExceptionFromTransferListener() throws HttpDataSourceException {
    // Make mockTransferListener throw an exception in CronetDataSource.close(). Ensure that
    // the subsequent open() call succeeds.
    doThrow(new NullPointerException()).when(mockTransferListener).onTransferEnd();
    dataSourceUnderTest.open(testDataSpec);
    try {
      dataSourceUnderTest.close();
      fail("NullPointerException expected");
    } catch (NullPointerException e) {
      // expected
    }
    // Open should return successfully
    dataSourceUnderTest.open(testDataSpec);
  }

  @Test
  public void testReadFailure() throws HttpDataSourceException {
    doAnswer(
        new Answer<Object>() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            dataSourceUnderTest.onFailed(
                mockUrlRequest,
                createUrlResponseInfo(500), // statusCode
                null);
            return null;
          }
        }).when(mockUrlRequest).read(any(ByteBuffer.class));

    dataSourceUnderTest.open(testDataSpec);
    byte[] returnedBuffer = new byte[8];
    try {
      dataSourceUnderTest.read(returnedBuffer, 0, 8);
      fail("dataSourceUnderTest.read() returned, but IOException expected");
    } catch (IOException e) {
      // expected
    }
  }

  private void assertConnectionState(CronetDataSource.ConnectionState state) {
    assertEquals(state, dataSourceUnderTest.connectionState);
  }

  private static class BlockingBackgroundExecutor implements Executor {

    @Override
    public void execute(Runnable command) {
      FutureTask<?> task = new FutureTask<Void>(command, null);
      new Thread(task).start();
      try {
        task.get();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
    }

  }

}
