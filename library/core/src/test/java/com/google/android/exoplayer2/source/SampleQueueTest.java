/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import static com.google.android.exoplayer2.C.BUFFER_FLAG_ENCRYPTED;
import static com.google.android.exoplayer2.C.BUFFER_FLAG_KEY_FRAME;
import static com.google.android.exoplayer2.C.RESULT_BUFFER_READ;
import static com.google.android.exoplayer2.C.RESULT_FORMAT_READ;
import static com.google.android.exoplayer2.C.RESULT_NOTHING_READ;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.Long.MIN_VALUE;
import static java.util.Arrays.copyOfRange;
import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Mockito.when;

import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/** Test for {@link SampleQueue}. */
@RunWith(AndroidJUnit4.class)
public final class SampleQueueTest {

  private static final int ALLOCATION_SIZE = 16;

  private static final Format FORMAT_1 = Format.createSampleFormat("1", "mimeType", 0);
  private static final Format FORMAT_2 = Format.createSampleFormat("2", "mimeType", 0);
  private static final Format FORMAT_1_COPY = Format.createSampleFormat("1", "mimeType", 0);
  private static final Format FORMAT_SPLICED = Format.createSampleFormat("spliced", "mimeType", 0);
  private static final Format FORMAT_ENCRYPTED =
      Format.createSampleFormat(
          /* id= */ "encrypted",
          "mimeType",
          /* codecs= */ null,
          /* bitrate= */ Format.NO_VALUE,
          new DrmInitData());
  private static final byte[] DATA = TestUtil.buildTestData(ALLOCATION_SIZE * 10);

  /*
   * SAMPLE_SIZES and SAMPLE_OFFSETS are intended to test various boundary cases (with
   * respect to the allocation size). SAMPLE_OFFSETS values are defined as the backward offsets
   * (as expected by SampleQueue.sampleMetadata) assuming that DATA has been written to the
   * sampleQueue in full. The allocations are filled as follows, where | indicates a boundary
   * between allocations and x indicates a byte that doesn't belong to a sample:
   *
   * x<s1>|x<s2>x|x<s3>|<s4>x|<s5>|<s6|s6>|x<s7|s7>x|<s8>
   */
  private static final int[] SAMPLE_SIZES =
      new int[] {
        ALLOCATION_SIZE - 1,
        ALLOCATION_SIZE - 2,
        ALLOCATION_SIZE - 1,
        ALLOCATION_SIZE - 1,
        ALLOCATION_SIZE,
        ALLOCATION_SIZE * 2,
        ALLOCATION_SIZE * 2 - 2,
        ALLOCATION_SIZE
      };
  private static final int[] SAMPLE_OFFSETS =
      new int[] {
        ALLOCATION_SIZE * 9,
        ALLOCATION_SIZE * 8 + 1,
        ALLOCATION_SIZE * 7,
        ALLOCATION_SIZE * 6 + 1,
        ALLOCATION_SIZE * 5,
        ALLOCATION_SIZE * 3,
        ALLOCATION_SIZE + 1,
        0
      };
  private static final long[] SAMPLE_TIMESTAMPS =
      new long[] {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000};
  private static final long LAST_SAMPLE_TIMESTAMP = SAMPLE_TIMESTAMPS[SAMPLE_TIMESTAMPS.length - 1];
  private static final int[] SAMPLE_FLAGS =
      new int[] {C.BUFFER_FLAG_KEY_FRAME, 0, 0, 0, C.BUFFER_FLAG_KEY_FRAME, 0, 0, 0};
  private static final Format[] SAMPLE_FORMATS =
      new Format[] {FORMAT_1, FORMAT_1, FORMAT_1, FORMAT_1, FORMAT_2, FORMAT_2, FORMAT_2, FORMAT_2};
  private static final int DATA_SECOND_KEYFRAME_INDEX = 4;

  private static final int[] ENCRYPTED_SAMPLES_FLAGS =
      new int[] {
        C.BUFFER_FLAG_KEY_FRAME, C.BUFFER_FLAG_ENCRYPTED, 0, C.BUFFER_FLAG_ENCRYPTED,
      };
  private static final long[] ENCRYPTED_SAMPLE_TIMESTAMPS = new long[] {0, 1000, 2000, 3000};
  private static final Format[] ENCRYPTED_SAMPLE_FORMATS =
      new Format[] {FORMAT_ENCRYPTED, FORMAT_ENCRYPTED, FORMAT_1, FORMAT_ENCRYPTED};
  /** Encrypted samples require the encryption preamble. */
  private static final int[] ENCRYPTED_SAMPLE_SIZES = new int[] {1, 3, 1, 3};

  private static final int[] ENCRYPTED_SAMPLE_OFFSETS = new int[] {7, 4, 3, 0};
  private static final byte[] ENCRYPTED_SAMPLE_DATA = new byte[] {1, 1, 1, 1, 1, 1, 1, 1};

  private static final TrackOutput.CryptoData DUMMY_CRYPTO_DATA =
      new TrackOutput.CryptoData(C.CRYPTO_MODE_AES_CTR, new byte[16], 0, 0);

  private Allocator allocator;
  private DrmSessionManager<ExoMediaCrypto> mockDrmSessionManager;
  private DrmSession<ExoMediaCrypto> mockDrmSession;
  private SampleQueue sampleQueue;
  private FormatHolder formatHolder;
  private DecoderInputBuffer inputBuffer;

  @Before
  @SuppressWarnings("unchecked")
  public void setUp() {
    allocator = new DefaultAllocator(false, ALLOCATION_SIZE);
    mockDrmSessionManager =
        (DrmSessionManager<ExoMediaCrypto>) Mockito.mock(DrmSessionManager.class);
    mockDrmSession = (DrmSession<ExoMediaCrypto>) Mockito.mock(DrmSession.class);
    when(mockDrmSessionManager.acquireSession(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(mockDrmSession);
    sampleQueue = new SampleQueue(
        allocator,
        /* playbackLooper= */ Assertions.checkNotNull(Looper.myLooper()),
        mockDrmSessionManager);
    formatHolder = new FormatHolder();
    inputBuffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
  }

  @After
  public void tearDown() {
    allocator = null;
    sampleQueue = null;
    formatHolder = null;
    inputBuffer = null;
  }

  @Test
  public void testCapacityIncreases() {
    int numberOfSamplesToInput = 3 * SampleQueue.SAMPLE_CAPACITY_INCREMENT + 1;
    sampleQueue.format(FORMAT_1);
    sampleQueue.sampleData(
        new ParsableByteArray(numberOfSamplesToInput), /* length= */ numberOfSamplesToInput);
    for (int i = 0; i < numberOfSamplesToInput; i++) {
      sampleQueue.sampleMetadata(
          /* timeUs= */ i * 1000,
          /* flags= */ C.BUFFER_FLAG_KEY_FRAME,
          /* size= */ 1,
          /* offset= */ numberOfSamplesToInput - i - 1,
          /* cryptoData= */ null);
    }

    assertReadFormat(/* formatRequired= */ false, FORMAT_1);
    for (int i = 0; i < numberOfSamplesToInput; i++) {
      assertReadSample(
          /* timeUs= */ i * 1000,
          /* isKeyFrame= */ true,
          /* isEncrypted= */ false,
          /* sampleData= */ new byte[1],
          /* offset= */ 0,
          /* length= */ 1);
    }
    assertReadNothing(/* formatRequired= */ false);
  }

  @Test
  public void testResetReleasesAllocations() {
    writeTestData();
    assertAllocationCount(10);
    sampleQueue.reset();
    assertAllocationCount(0);
  }

  @Test
  public void testReadWithoutWrite() {
    assertNoSamplesToRead(null);
  }

  @Test
  public void testEqualFormatsDeduplicated() {
    sampleQueue.format(FORMAT_1);
    assertReadFormat(false, FORMAT_1);
    // If the same format is written then it should not cause a format change on the read side.
    sampleQueue.format(FORMAT_1);
    assertNoSamplesToRead(FORMAT_1);
    // The same applies for a format that's equal (but a different object).
    sampleQueue.format(FORMAT_1_COPY);
    assertNoSamplesToRead(FORMAT_1);
  }

  @Test
  public void testMultipleFormatsDeduplicated() {
    sampleQueue.format(FORMAT_1);
    sampleQueue.sampleData(new ParsableByteArray(DATA), ALLOCATION_SIZE);
    sampleQueue.sampleMetadata(0, C.BUFFER_FLAG_KEY_FRAME, ALLOCATION_SIZE, 0, null);
    // Writing multiple formats should not cause a format change on the read side, provided the last
    // format to be written is equal to the format of the previous sample.
    sampleQueue.format(FORMAT_2);
    sampleQueue.format(FORMAT_1_COPY);
    sampleQueue.sampleData(new ParsableByteArray(DATA), ALLOCATION_SIZE);
    sampleQueue.sampleMetadata(1000, C.BUFFER_FLAG_KEY_FRAME, ALLOCATION_SIZE, 0, null);

    assertReadFormat(false, FORMAT_1);
    assertReadSample(0, true, /* isEncrypted= */ false, DATA, 0, ALLOCATION_SIZE);
    // Assert the second sample is read without a format change.
    assertReadSample(1000, true, /* isEncrypted= */ false, DATA, 0, ALLOCATION_SIZE);

    // The same applies if the queue is empty when the formats are written.
    sampleQueue.format(FORMAT_2);
    sampleQueue.format(FORMAT_1);
    sampleQueue.sampleData(new ParsableByteArray(DATA), ALLOCATION_SIZE);
    sampleQueue.sampleMetadata(2000, C.BUFFER_FLAG_KEY_FRAME, ALLOCATION_SIZE, 0, null);

    // Assert the third sample is read without a format change.
    assertReadSample(2000, true, /* isEncrypted= */ false, DATA, 0, ALLOCATION_SIZE);
  }

  @Test
  public void testReadSingleSamples() {
    sampleQueue.sampleData(new ParsableByteArray(DATA), ALLOCATION_SIZE);

    assertAllocationCount(1);
    // Nothing to read.
    assertNoSamplesToRead(null);

    sampleQueue.format(FORMAT_1);

    // Read the format.
    assertReadFormat(false, FORMAT_1);
    // Nothing to read.
    assertNoSamplesToRead(FORMAT_1);

    sampleQueue.sampleMetadata(1000, C.BUFFER_FLAG_KEY_FRAME, ALLOCATION_SIZE, 0, null);

    // If formatRequired, should read the format rather than the sample.
    assertReadFormat(true, FORMAT_1);
    // Otherwise should read the sample.
    assertReadSample(1000, true, /* isEncrypted= */ false, DATA, 0, ALLOCATION_SIZE);
    // Allocation should still be held.
    assertAllocationCount(1);
    sampleQueue.discardToRead();
    // The allocation should have been released.
    assertAllocationCount(0);

    // Nothing to read.
    assertNoSamplesToRead(FORMAT_1);

    // Write a second sample followed by one byte that does not belong to it.
    sampleQueue.sampleData(new ParsableByteArray(DATA), ALLOCATION_SIZE);
    sampleQueue.sampleMetadata(2000, 0, ALLOCATION_SIZE - 1, 1, null);

    // If formatRequired, should read the format rather than the sample.
    assertReadFormat(true, FORMAT_1);
    // Read the sample.
    assertReadSample(2000, false, /* isEncrypted= */ false, DATA, 0, ALLOCATION_SIZE - 1);
    // Allocation should still be held.
    assertAllocationCount(1);
    sampleQueue.discardToRead();
    // The last byte written to the sample queue may belong to a sample whose metadata has yet to be
    // written, so an allocation should still be held.
    assertAllocationCount(1);

    // Write metadata for a third sample containing the remaining byte.
    sampleQueue.sampleMetadata(3000, 0, 1, 0, null);

    // If formatRequired, should read the format rather than the sample.
    assertReadFormat(true, FORMAT_1);
    // Read the sample.
    assertReadSample(3000, false, /* isEncrypted= */ false, DATA, ALLOCATION_SIZE - 1, 1);
    // Allocation should still be held.
    assertAllocationCount(1);
    sampleQueue.discardToRead();
    // The allocation should have been released.
    assertAllocationCount(0);
  }

  @Test
  public void testReadMultiSamples() {
    writeTestData();
    assertThat(sampleQueue.getLargestQueuedTimestampUs()).isEqualTo(LAST_SAMPLE_TIMESTAMP);
    assertAllocationCount(10);
    assertReadTestData();
    assertAllocationCount(10);
    sampleQueue.discardToRead();
    assertAllocationCount(0);
  }

  @Test
  public void testReadMultiSamplesTwice() {
    writeTestData();
    writeTestData();
    assertAllocationCount(20);
    assertReadTestData(FORMAT_2);
    assertReadTestData(FORMAT_2);
    assertAllocationCount(20);
    sampleQueue.discardToRead();
    assertAllocationCount(0);
  }

  @Test
  public void testReadMultiWithSeek() {
    writeTestData();
    assertReadTestData();
    assertThat(sampleQueue.getFirstIndex()).isEqualTo(0);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(8);
    assertAllocationCount(10);

    sampleQueue.seekTo(0);
    assertAllocationCount(10);
    // Read again.
    assertThat(sampleQueue.getFirstIndex()).isEqualTo(0);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(0);
    assertReadTestData();
  }

  @Test
  public void testEmptyQueueReturnsLoadingFinished() {
    sampleQueue.sampleData(new ParsableByteArray(DATA), DATA.length);
    assertThat(sampleQueue.isReady(/* loadingFinished= */ false)).isFalse();
    assertThat(sampleQueue.isReady(/* loadingFinished= */ true)).isTrue();
  }

  @Test
  public void testIsReadyWithUpstreamFormatOnlyReturnsTrue() {
    sampleQueue.format(FORMAT_ENCRYPTED);
    assertThat(sampleQueue.isReady(/* loadingFinished= */ false)).isTrue();
  }

  @Test
  public void testIsReadyReturnsTrueForValidDrmSession() {
    writeTestDataWithEncryptedSections();
    assertReadFormat(/* formatRequired= */ false, FORMAT_ENCRYPTED);
    assertThat(sampleQueue.isReady(/* loadingFinished= */ false)).isFalse();
    when(mockDrmSession.getState()).thenReturn(DrmSession.STATE_OPENED_WITH_KEYS);
    assertThat(sampleQueue.isReady(/* loadingFinished= */ false)).isTrue();
  }

  @Test
  public void testIsReadyReturnsTrueForClearSampleAndPlayClearSamplesWithoutKeysIsTrue() {
    when(mockDrmSession.playClearSamplesWithoutKeys()).thenReturn(true);
    // We recreate the queue to ensure the mock DRM session manager flags are taken into account.
    sampleQueue = new SampleQueue(
        allocator,
        /* playbackLooper= */ Assertions.checkNotNull(Looper.myLooper()),
        mockDrmSessionManager);
    writeTestDataWithEncryptedSections();
    assertThat(sampleQueue.isReady(/* loadingFinished= */ false)).isTrue();
  }

  @Test
  public void testReadEncryptedSectionsWaitsForKeys() {
    when(mockDrmSession.getState()).thenReturn(DrmSession.STATE_OPENED);
    writeTestDataWithEncryptedSections();

    assertReadFormat(/* formatRequired= */ false, FORMAT_ENCRYPTED);
    assertReadNothing(/* formatRequired= */ false);
    assertThat(inputBuffer.waitingForKeys).isTrue();
    when(mockDrmSession.getState()).thenReturn(DrmSession.STATE_OPENED_WITH_KEYS);
    assertReadEncryptedSample(/* sampleIndex= */ 0);
    assertThat(inputBuffer.waitingForKeys).isFalse();
  }

  @Test
  public void testReadEncryptedSectionsPopulatesDrmSession() {
    when(mockDrmSession.getState()).thenReturn(DrmSession.STATE_OPENED_WITH_KEYS);
    writeTestDataWithEncryptedSections();

    int result =
        sampleQueue.read(
            formatHolder,
            inputBuffer,
            /* formatRequired= */ false,
            /* loadingFinished= */ false,
            /* decodeOnlyUntilUs= */ 0);
    assertThat(result).isEqualTo(RESULT_FORMAT_READ);
    assertThat(formatHolder.drmSession).isSameInstanceAs(mockDrmSession);
    assertReadEncryptedSample(/* sampleIndex= */ 0);
    assertReadEncryptedSample(/* sampleIndex= */ 1);
    formatHolder.clear();
    assertThat(formatHolder.drmSession).isNull();
    result =
        sampleQueue.read(
            formatHolder,
            inputBuffer,
            /* formatRequired= */ false,
            /* loadingFinished= */ false,
            /* decodeOnlyUntilUs= */ 0);
    assertThat(result).isEqualTo(RESULT_FORMAT_READ);
    assertThat(formatHolder.drmSession).isNull();
    assertReadEncryptedSample(/* sampleIndex= */ 2);
    result =
        sampleQueue.read(
            formatHolder,
            inputBuffer,
            /* formatRequired= */ false,
            /* loadingFinished= */ false,
            /* decodeOnlyUntilUs= */ 0);
    assertThat(result).isEqualTo(RESULT_FORMAT_READ);
    assertThat(formatHolder.drmSession).isSameInstanceAs(mockDrmSession);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testAllowPlaceholderSessionPopulatesDrmSession() {
    when(mockDrmSession.getState()).thenReturn(DrmSession.STATE_OPENED_WITH_KEYS);
    DrmSession<ExoMediaCrypto> mockPlaceholderDrmSession =
        (DrmSession<ExoMediaCrypto>) Mockito.mock(DrmSession.class);
    when(mockPlaceholderDrmSession.getState()).thenReturn(DrmSession.STATE_OPENED_WITH_KEYS);
    when(mockDrmSessionManager.acquirePlaceholderSession(
            ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
        .thenReturn(mockPlaceholderDrmSession);
    writeTestDataWithEncryptedSections();

    int result =
        sampleQueue.read(
            formatHolder,
            inputBuffer,
            /* formatRequired= */ false,
            /* loadingFinished= */ false,
            /* decodeOnlyUntilUs= */ 0);
    assertThat(result).isEqualTo(RESULT_FORMAT_READ);
    assertThat(formatHolder.drmSession).isSameInstanceAs(mockDrmSession);
    assertReadEncryptedSample(/* sampleIndex= */ 0);
    assertReadEncryptedSample(/* sampleIndex= */ 1);
    formatHolder.clear();
    assertThat(formatHolder.drmSession).isNull();
    result =
        sampleQueue.read(
            formatHolder,
            inputBuffer,
            /* formatRequired= */ false,
            /* loadingFinished= */ false,
            /* decodeOnlyUntilUs= */ 0);
    assertThat(result).isEqualTo(RESULT_FORMAT_READ);
    assertThat(formatHolder.drmSession).isSameInstanceAs(mockPlaceholderDrmSession);
    assertReadEncryptedSample(/* sampleIndex= */ 2);
    result =
        sampleQueue.read(
            formatHolder,
            inputBuffer,
            /* formatRequired= */ false,
            /* loadingFinished= */ false,
            /* decodeOnlyUntilUs= */ 0);
    assertThat(result).isEqualTo(RESULT_FORMAT_READ);
    assertThat(formatHolder.drmSession).isSameInstanceAs(mockDrmSession);
    assertReadEncryptedSample(/* sampleIndex= */ 3);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testTrailingCryptoInfoInitializationVectorBytesZeroed() {
    when(mockDrmSession.getState()).thenReturn(DrmSession.STATE_OPENED_WITH_KEYS);
    DrmSession<ExoMediaCrypto> mockPlaceholderDrmSession =
        (DrmSession<ExoMediaCrypto>) Mockito.mock(DrmSession.class);
    when(mockPlaceholderDrmSession.getState()).thenReturn(DrmSession.STATE_OPENED_WITH_KEYS);
    when(mockDrmSessionManager.acquirePlaceholderSession(
            ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
        .thenReturn(mockPlaceholderDrmSession);

    writeFormat(ENCRYPTED_SAMPLE_FORMATS[0]);
    byte[] sampleData = new byte[] {0, 1, 2};
    byte[] initializationVector = new byte[] {7, 6, 5, 4, 3, 2, 1, 0};
    byte[] encryptedSampleData =
        TestUtil.joinByteArrays(
            new byte[] {
              0x08, // subsampleEncryption = false (1 bit), ivSize = 8 (7 bits).
            },
            initializationVector,
            sampleData);
    writeSample(
        encryptedSampleData, /* timestampUs= */ 0, BUFFER_FLAG_KEY_FRAME | BUFFER_FLAG_ENCRYPTED);

    int result =
        sampleQueue.read(
            formatHolder,
            inputBuffer,
            /* formatRequired= */ false,
            /* loadingFinished= */ false,
            /* decodeOnlyUntilUs= */ 0);
    assertThat(result).isEqualTo(RESULT_FORMAT_READ);

    // Fill cryptoInfo.iv with non-zero data. When the 8 byte initialization vector is written into
    // it, we expect the trailing 8 bytes to be zeroed.
    inputBuffer.cryptoInfo.iv = new byte[16];
    Arrays.fill(inputBuffer.cryptoInfo.iv, (byte) 1);

    result =
        sampleQueue.read(
            formatHolder,
            inputBuffer,
            /* formatRequired= */ false,
            /* loadingFinished= */ false,
            /* decodeOnlyUntilUs= */ 0);
    assertThat(result).isEqualTo(RESULT_BUFFER_READ);

    // Assert cryptoInfo.iv contains the 8-byte initialization vector and that the trailing 8 bytes
    // have been zeroed.
    byte[] expectedInitializationVector = Arrays.copyOf(initializationVector, 16);
    assertArrayEquals(expectedInitializationVector, inputBuffer.cryptoInfo.iv);
  }

  @Test
  public void testReadWithErrorSessionReadsNothingAndThrows() throws IOException {
    when(mockDrmSession.getState()).thenReturn(DrmSession.STATE_OPENED);
    writeTestDataWithEncryptedSections();

    assertReadFormat(/* formatRequired= */ false, FORMAT_ENCRYPTED);
    assertReadNothing(/* formatRequired= */ false);
    sampleQueue.maybeThrowError();
    when(mockDrmSession.getState()).thenReturn(DrmSession.STATE_ERROR);
    when(mockDrmSession.getError()).thenReturn(new DrmSession.DrmSessionException(new Exception()));
    assertReadNothing(/* formatRequired= */ false);
    try {
      sampleQueue.maybeThrowError();
      Assert.fail();
    } catch (IOException e) {
      // Expected.
    }
    when(mockDrmSession.getState()).thenReturn(DrmSession.STATE_OPENED_WITH_KEYS);
    assertReadEncryptedSample(/* sampleIndex= */ 0);
  }

  @Test
  public void testAllowPlayClearSamplesWithoutKeysReadsClearSamples() {
    when(mockDrmSession.playClearSamplesWithoutKeys()).thenReturn(true);
    // We recreate the queue to ensure the mock DRM session manager flags are taken into account.
    sampleQueue = new SampleQueue(
        allocator,
        /* playbackLooper= */ Assertions.checkNotNull(Looper.myLooper()),
        mockDrmSessionManager);
    when(mockDrmSession.getState()).thenReturn(DrmSession.STATE_OPENED);
    writeTestDataWithEncryptedSections();

    assertReadFormat(/* formatRequired= */ false, FORMAT_ENCRYPTED);
    assertReadEncryptedSample(/* sampleIndex= */ 0);
  }

  @Test
  public void testSeekAfterDiscard() {
    writeTestData();
    assertReadTestData();
    sampleQueue.discardToRead();
    assertThat(sampleQueue.getFirstIndex()).isEqualTo(8);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(8);
    assertAllocationCount(0);

    sampleQueue.seekTo(0);
    assertAllocationCount(0);
    // Can't read again.
    assertThat(sampleQueue.getFirstIndex()).isEqualTo(8);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(8);
    assertReadEndOfStream(false);
  }

  @Test
  public void testAdvanceToEnd() {
    writeTestData();
    sampleQueue.advanceToEnd();
    assertAllocationCount(10);
    sampleQueue.discardToRead();
    assertAllocationCount(0);
    // Despite skipping all samples, we should still read the last format, since this is the
    // expected format for a subsequent sample.
    assertReadFormat(false, FORMAT_2);
    // Once the format has been read, there's nothing else to read.
    assertNoSamplesToRead(FORMAT_2);
  }

  @Test
  public void testAdvanceToEndRetainsUnassignedData() {
    sampleQueue.format(FORMAT_1);
    sampleQueue.sampleData(new ParsableByteArray(DATA), ALLOCATION_SIZE);
    sampleQueue.advanceToEnd();
    assertAllocationCount(1);
    sampleQueue.discardToRead();
    // Skipping shouldn't discard data that may belong to a sample whose metadata has yet to be
    // written.
    assertAllocationCount(1);
    // We should be able to read the format.
    assertReadFormat(false, FORMAT_1);
    // Once the format has been read, there's nothing else to read.
    assertNoSamplesToRead(FORMAT_1);

    sampleQueue.sampleMetadata(0, C.BUFFER_FLAG_KEY_FRAME, ALLOCATION_SIZE, 0, null);
    // Once the metadata has been written, check the sample can be read as expected.
    assertReadSample(0, true, /* isEncrypted= */ false, DATA, 0, ALLOCATION_SIZE);
    assertNoSamplesToRead(FORMAT_1);
    assertAllocationCount(1);
    sampleQueue.discardToRead();
    assertAllocationCount(0);
  }

  @Test
  public void testAdvanceToBeforeBuffer() {
    writeTestData();
    int skipCount = sampleQueue.advanceTo(SAMPLE_TIMESTAMPS[0] - 1);
    // Should have no effect (we're already at the first frame).
    assertThat(skipCount).isEqualTo(0);
    assertReadTestData();
    assertNoSamplesToRead(FORMAT_2);
  }

  @Test
  public void testAdvanceToStartOfBuffer() {
    writeTestData();
    int skipCount = sampleQueue.advanceTo(SAMPLE_TIMESTAMPS[0]);
    // Should have no effect (we're already at the first frame).
    assertThat(skipCount).isEqualTo(0);
    assertReadTestData();
    assertNoSamplesToRead(FORMAT_2);
  }

  @Test
  public void testAdvanceToEndOfBuffer() {
    writeTestData();
    int skipCount = sampleQueue.advanceTo(LAST_SAMPLE_TIMESTAMP);
    // Should advance to 2nd keyframe (the 4th frame).
    assertThat(skipCount).isEqualTo(4);
    assertReadTestData(null, DATA_SECOND_KEYFRAME_INDEX);
    assertNoSamplesToRead(FORMAT_2);
  }

  @Test
  public void testAdvanceToAfterBuffer() {
    writeTestData();
    int skipCount = sampleQueue.advanceTo(LAST_SAMPLE_TIMESTAMP + 1);
    // Should advance to 2nd keyframe (the 4th frame).
    assertThat(skipCount).isEqualTo(4);
    assertReadTestData(null, DATA_SECOND_KEYFRAME_INDEX);
    assertNoSamplesToRead(FORMAT_2);
  }

  @Test
  public void testSeekToBeforeBuffer() {
    writeTestData();
    boolean success = sampleQueue.seekTo(SAMPLE_TIMESTAMPS[0] - 1, false);
    assertThat(success).isFalse();
    assertThat(sampleQueue.getReadIndex()).isEqualTo(0);
    assertReadTestData();
    assertNoSamplesToRead(FORMAT_2);
  }

  @Test
  public void testSeekToStartOfBuffer() {
    writeTestData();
    boolean success = sampleQueue.seekTo(SAMPLE_TIMESTAMPS[0], false);
    assertThat(success).isTrue();
    assertThat(sampleQueue.getReadIndex()).isEqualTo(0);
    assertReadTestData();
    assertNoSamplesToRead(FORMAT_2);
  }

  @Test
  public void testSeekToEndOfBuffer() {
    writeTestData();
    boolean success = sampleQueue.seekTo(LAST_SAMPLE_TIMESTAMP, false);
    assertThat(success).isTrue();
    assertThat(sampleQueue.getReadIndex()).isEqualTo(4);
    assertReadTestData(null, DATA_SECOND_KEYFRAME_INDEX);
    assertNoSamplesToRead(FORMAT_2);
  }

  @Test
  public void testSeekToAfterBuffer() {
    writeTestData();
    boolean success = sampleQueue.seekTo(LAST_SAMPLE_TIMESTAMP + 1, false);
    assertThat(success).isFalse();
    assertThat(sampleQueue.getReadIndex()).isEqualTo(0);
    assertReadTestData();
    assertNoSamplesToRead(FORMAT_2);
  }

  @Test
  public void testSeekToAfterBufferAllowed() {
    writeTestData();
    boolean success = sampleQueue.seekTo(LAST_SAMPLE_TIMESTAMP + 1, true);
    assertThat(success).isTrue();
    assertThat(sampleQueue.getReadIndex()).isEqualTo(4);
    assertReadTestData(null, DATA_SECOND_KEYFRAME_INDEX);
    assertNoSamplesToRead(FORMAT_2);
  }

  @Test
  public void testSeekToEndAndBackToStart() {
    writeTestData();
    boolean success = sampleQueue.seekTo(LAST_SAMPLE_TIMESTAMP, false);
    assertThat(success).isTrue();
    assertThat(sampleQueue.getReadIndex()).isEqualTo(4);
    assertReadTestData(null, DATA_SECOND_KEYFRAME_INDEX);
    assertNoSamplesToRead(FORMAT_2);
    // Seek back to the start.
    success = sampleQueue.seekTo(SAMPLE_TIMESTAMPS[0], false);
    assertThat(success).isTrue();
    assertThat(sampleQueue.getReadIndex()).isEqualTo(0);
    assertReadTestData();
    assertNoSamplesToRead(FORMAT_2);
  }

  @Test
  public void testDiscardToEnd() {
    writeTestData();
    // Should discard everything.
    sampleQueue.discardToEnd();
    assertThat(sampleQueue.getFirstIndex()).isEqualTo(8);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(8);
    assertAllocationCount(0);
    // We should still be able to read the upstream format.
    assertReadFormat(false, FORMAT_2);
    // We should be able to write and read subsequent samples.
    writeTestData();
    assertReadTestData(FORMAT_2);
  }

  @Test
  public void testDiscardToStopAtReadPosition() {
    writeTestData();
    // Shouldn't discard anything.
    sampleQueue.discardTo(LAST_SAMPLE_TIMESTAMP, false, true);
    assertThat(sampleQueue.getFirstIndex()).isEqualTo(0);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(0);
    assertAllocationCount(10);
    // Read the first sample.
    assertReadTestData(null, 0, 1);
    // Shouldn't discard anything.
    sampleQueue.discardTo(SAMPLE_TIMESTAMPS[1] - 1, false, true);
    assertThat(sampleQueue.getFirstIndex()).isEqualTo(0);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(1);
    assertAllocationCount(10);
    // Should discard the read sample.
    sampleQueue.discardTo(SAMPLE_TIMESTAMPS[1], false, true);
    assertThat(sampleQueue.getFirstIndex()).isEqualTo(1);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(1);
    assertAllocationCount(9);
    // Shouldn't discard anything.
    sampleQueue.discardTo(LAST_SAMPLE_TIMESTAMP, false, true);
    assertThat(sampleQueue.getFirstIndex()).isEqualTo(1);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(1);
    assertAllocationCount(9);
    // Should be able to read the remaining samples.
    assertReadTestData(FORMAT_1, 1, 7);
    assertThat(sampleQueue.getFirstIndex()).isEqualTo(1);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(8);
    // Should discard up to the second last sample
    sampleQueue.discardTo(LAST_SAMPLE_TIMESTAMP - 1, false, true);
    assertThat(sampleQueue.getFirstIndex()).isEqualTo(6);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(8);
    assertAllocationCount(3);
    // Should discard up the last sample
    sampleQueue.discardTo(LAST_SAMPLE_TIMESTAMP, false, true);
    assertThat(sampleQueue.getFirstIndex()).isEqualTo(7);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(8);
    assertAllocationCount(1);
  }

  @Test
  public void testDiscardToDontStopAtReadPosition() {
    writeTestData();
    // Shouldn't discard anything.
    sampleQueue.discardTo(SAMPLE_TIMESTAMPS[1] - 1, false, false);
    assertThat(sampleQueue.getFirstIndex()).isEqualTo(0);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(0);
    assertAllocationCount(10);
    // Should discard the first sample.
    sampleQueue.discardTo(SAMPLE_TIMESTAMPS[1], false, false);
    assertThat(sampleQueue.getFirstIndex()).isEqualTo(1);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(1);
    assertAllocationCount(9);
    // Should be able to read the remaining samples.
    assertReadTestData(FORMAT_1, 1, 7);
  }

  @Test
  public void testDiscardUpstream() {
    writeTestData();
    sampleQueue.discardUpstreamSamples(8);
    assertAllocationCount(10);
    sampleQueue.discardUpstreamSamples(7);
    assertAllocationCount(9);
    sampleQueue.discardUpstreamSamples(6);
    assertAllocationCount(7);
    sampleQueue.discardUpstreamSamples(5);
    assertAllocationCount(5);
    sampleQueue.discardUpstreamSamples(4);
    assertAllocationCount(4);
    sampleQueue.discardUpstreamSamples(3);
    assertAllocationCount(3);
    sampleQueue.discardUpstreamSamples(2);
    assertAllocationCount(2);
    sampleQueue.discardUpstreamSamples(1);
    assertAllocationCount(1);
    sampleQueue.discardUpstreamSamples(0);
    assertAllocationCount(0);
    assertReadFormat(false, FORMAT_2);
    assertNoSamplesToRead(FORMAT_2);
  }

  @Test
  public void testDiscardUpstreamMulti() {
    writeTestData();
    sampleQueue.discardUpstreamSamples(4);
    assertAllocationCount(4);
    sampleQueue.discardUpstreamSamples(0);
    assertAllocationCount(0);
    assertReadFormat(false, FORMAT_2);
    assertNoSamplesToRead(FORMAT_2);
  }

  @Test
  public void testDiscardUpstreamBeforeRead() {
    writeTestData();
    sampleQueue.discardUpstreamSamples(4);
    assertAllocationCount(4);
    assertReadTestData(null, 0, 4);
    assertReadFormat(false, FORMAT_2);
    assertNoSamplesToRead(FORMAT_2);
  }

  @Test
  public void testDiscardUpstreamAfterRead() {
    writeTestData();
    assertReadTestData(null, 0, 3);
    sampleQueue.discardUpstreamSamples(8);
    assertAllocationCount(10);
    sampleQueue.discardToRead();
    assertAllocationCount(7);
    sampleQueue.discardUpstreamSamples(7);
    assertAllocationCount(6);
    sampleQueue.discardUpstreamSamples(6);
    assertAllocationCount(4);
    sampleQueue.discardUpstreamSamples(5);
    assertAllocationCount(2);
    sampleQueue.discardUpstreamSamples(4);
    assertAllocationCount(1);
    sampleQueue.discardUpstreamSamples(3);
    assertAllocationCount(0);
    assertReadFormat(false, FORMAT_2);
    assertNoSamplesToRead(FORMAT_2);
  }

  @Test
  public void testLargestQueuedTimestampWithDiscardUpstream() {
    writeTestData();
    assertThat(sampleQueue.getLargestQueuedTimestampUs()).isEqualTo(LAST_SAMPLE_TIMESTAMP);
    sampleQueue.discardUpstreamSamples(SAMPLE_TIMESTAMPS.length - 1);
    // Discarding from upstream should reduce the largest timestamp.
    assertThat(sampleQueue.getLargestQueuedTimestampUs())
        .isEqualTo(SAMPLE_TIMESTAMPS[SAMPLE_TIMESTAMPS.length - 2]);
    sampleQueue.discardUpstreamSamples(0);
    // Discarding everything from upstream without reading should unset the largest timestamp.
    assertThat(sampleQueue.getLargestQueuedTimestampUs()).isEqualTo(MIN_VALUE);
  }

  @Test
  public void testLargestQueuedTimestampWithDiscardUpstreamDecodeOrder() {
    long[] decodeOrderTimestamps = new long[] {0, 3000, 2000, 1000, 4000, 7000, 6000, 5000};
    writeTestData(
        DATA, SAMPLE_SIZES, SAMPLE_OFFSETS, decodeOrderTimestamps, SAMPLE_FORMATS, SAMPLE_FLAGS);
    assertThat(sampleQueue.getLargestQueuedTimestampUs()).isEqualTo(7000);
    sampleQueue.discardUpstreamSamples(SAMPLE_TIMESTAMPS.length - 2);
    // Discarding the last two samples should not change the largest timestamp, due to the decode
    // ordering of the timestamps.
    assertThat(sampleQueue.getLargestQueuedTimestampUs()).isEqualTo(7000);
    sampleQueue.discardUpstreamSamples(SAMPLE_TIMESTAMPS.length - 3);
    // Once a third sample is discarded, the largest timestamp should have changed.
    assertThat(sampleQueue.getLargestQueuedTimestampUs()).isEqualTo(4000);
    sampleQueue.discardUpstreamSamples(0);
    // Discarding everything from upstream without reading should unset the largest timestamp.
    assertThat(sampleQueue.getLargestQueuedTimestampUs()).isEqualTo(MIN_VALUE);
  }
  
  @Test
  public void testLargestQueuedTimestampWithRead() {
    writeTestData();
    assertThat(sampleQueue.getLargestQueuedTimestampUs()).isEqualTo(LAST_SAMPLE_TIMESTAMP);
    assertReadTestData();
    // Reading everything should not reduce the largest timestamp.
    assertThat(sampleQueue.getLargestQueuedTimestampUs()).isEqualTo(LAST_SAMPLE_TIMESTAMP);
  }

  @Test
  public void testSetSampleOffsetBeforeData() {
    long sampleOffsetUs = 1000;
    sampleQueue.setSampleOffsetUs(sampleOffsetUs);
    writeTestData();
    assertReadTestData(
        /* startFormat= */ null, /* firstSampleIndex= */ 0, /* sampleCount= */ 8, sampleOffsetUs);
    assertReadEndOfStream(/* formatRequired= */ false);
  }

  @Test
  public void testSetSampleOffsetBetweenSamples() {
    writeTestData();
    long sampleOffsetUs = 1000;
    sampleQueue.setSampleOffsetUs(sampleOffsetUs);

    // Write a final sample now the offset is set.
    long unadjustedTimestampUs = LAST_SAMPLE_TIMESTAMP + 1234;
    writeSample(DATA, unadjustedTimestampUs, /* sampleFlags= */ 0);

    assertReadTestData();
    // We expect to read the format adjusted to account for the sample offset, followed by the final
    // sample and then the end of stream.
    assertReadFormat(
        /* formatRequired= */ false, FORMAT_2.copyWithSubsampleOffsetUs(sampleOffsetUs));
    assertReadSample(
        unadjustedTimestampUs + sampleOffsetUs,
        /* isKeyFrame= */ false,
        /* isEncrypted= */ false,
        DATA,
        /* offset= */ 0,
        DATA.length);
    assertReadEndOfStream(/* formatRequired= */ false);
  }

  @Test
  public void testAdjustUpstreamFormat() {
    String label = "label";
    sampleQueue =
        new SampleQueue(
            allocator,
            /* playbackLooper= */ Assertions.checkNotNull(Looper.myLooper()),
            mockDrmSessionManager) {
          @Override
          public Format getAdjustedUpstreamFormat(Format format) {
            return super.getAdjustedUpstreamFormat(format.copyWithLabel(label));
          }
        };

    writeFormat(FORMAT_1);
    assertReadFormat(/* formatRequired= */ false, FORMAT_1.copyWithLabel(label));
    assertReadEndOfStream(/* formatRequired= */ false);
  }

  @Test
  public void testInvalidateUpstreamFormatAdjustment() {
    AtomicReference<String> label = new AtomicReference<>("label1");
    sampleQueue =
        new SampleQueue(
            allocator,
            /* playbackLooper= */ Assertions.checkNotNull(Looper.myLooper()),
            mockDrmSessionManager) {
          @Override
          public Format getAdjustedUpstreamFormat(Format format) {
            return super.getAdjustedUpstreamFormat(format.copyWithLabel(label.get()));
          }
        };

    writeFormat(FORMAT_1);
    writeSample(DATA, /* timestampUs= */ 0, BUFFER_FLAG_KEY_FRAME);

    // Make a change that'll affect the SampleQueue's format adjustment, and invalidate it.
    label.set("label2");
    sampleQueue.invalidateUpstreamFormatAdjustment();

    writeSample(DATA, /* timestampUs= */ 1, /* sampleFlags= */ 0);

    assertReadFormat(/* formatRequired= */ false, FORMAT_1.copyWithLabel("label1"));
    assertReadSample(
        /* timeUs= */ 0,
        /* isKeyFrame= */ true,
        /* isEncrypted= */ false,
        DATA,
        /* offset= */ 0,
        DATA.length);
    assertReadFormat(/* formatRequired= */ false, FORMAT_1.copyWithLabel("label2"));
    assertReadSample(
        /* timeUs= */ 1,
        /* isKeyFrame= */ false,
        /* isEncrypted= */ false,
        DATA,
        /* offset= */ 0,
        DATA.length);
    assertReadEndOfStream(/* formatRequired= */ false);
  }

  @Test
  public void testSplice() {
    writeTestData();
    sampleQueue.splice();
    // Splice should succeed, replacing the last 4 samples with the sample being written.
    long spliceSampleTimeUs = SAMPLE_TIMESTAMPS[4];
    writeFormat(FORMAT_SPLICED);
    writeSample(DATA, spliceSampleTimeUs, C.BUFFER_FLAG_KEY_FRAME);
    assertReadTestData(null, 0, 4);
    assertReadFormat(false, FORMAT_SPLICED);
    assertReadSample(spliceSampleTimeUs, true, /* isEncrypted= */ false, DATA, 0, DATA.length);
    assertReadEndOfStream(false);
  }

  @Test
  public void testSpliceAfterRead() {
    writeTestData();
    assertReadTestData(null, 0, 4);
    sampleQueue.splice();
    // Splice should fail, leaving the last 4 samples unchanged.
    long spliceSampleTimeUs = SAMPLE_TIMESTAMPS[3];
    writeFormat(FORMAT_SPLICED);
    writeSample(DATA, spliceSampleTimeUs, C.BUFFER_FLAG_KEY_FRAME);
    assertReadTestData(SAMPLE_FORMATS[3], 4, 4);
    assertReadEndOfStream(false);

    sampleQueue.seekTo(0);
    assertReadTestData(null, 0, 4);
    sampleQueue.splice();
    // Splice should succeed, replacing the last 4 samples with the sample being written
    spliceSampleTimeUs = SAMPLE_TIMESTAMPS[3] + 1;
    writeFormat(FORMAT_SPLICED);
    writeSample(DATA, spliceSampleTimeUs, C.BUFFER_FLAG_KEY_FRAME);
    assertReadFormat(false, FORMAT_SPLICED);
    assertReadSample(spliceSampleTimeUs, true, /* isEncrypted= */ false, DATA, 0, DATA.length);
    assertReadEndOfStream(false);
  }

  @Test
  public void testSpliceWithSampleOffset() {
    long sampleOffsetUs = 30000;
    sampleQueue.setSampleOffsetUs(sampleOffsetUs);
    writeTestData();
    sampleQueue.splice();
    // Splice should succeed, replacing the last 4 samples with the sample being written.
    long spliceSampleTimeUs = SAMPLE_TIMESTAMPS[4];
    writeFormat(FORMAT_SPLICED);
    writeSample(DATA, spliceSampleTimeUs, C.BUFFER_FLAG_KEY_FRAME);
    assertReadTestData(null, 0, 4, sampleOffsetUs);
    assertReadFormat(false, FORMAT_SPLICED.copyWithSubsampleOffsetUs(sampleOffsetUs));
    assertReadSample(
        spliceSampleTimeUs + sampleOffsetUs, true, /* isEncrypted= */ false, DATA, 0, DATA.length);
    assertReadEndOfStream(false);
  }

  // Internal methods.

  /**
   * Writes standard test data to {@code sampleQueue}.
   */
  private void writeTestData() {
    writeTestData(
        DATA, SAMPLE_SIZES, SAMPLE_OFFSETS, SAMPLE_TIMESTAMPS, SAMPLE_FORMATS, SAMPLE_FLAGS);
  }

  private void writeTestDataWithEncryptedSections() {
    writeTestData(
        ENCRYPTED_SAMPLE_DATA,
        ENCRYPTED_SAMPLE_SIZES,
        ENCRYPTED_SAMPLE_OFFSETS,
        ENCRYPTED_SAMPLE_TIMESTAMPS,
        ENCRYPTED_SAMPLE_FORMATS,
        ENCRYPTED_SAMPLES_FLAGS);
  }

  /**
   * Writes the specified test data to {@code sampleQueue}.
   */
  @SuppressWarnings("ReferenceEquality")
  private void writeTestData(byte[] data, int[] sampleSizes, int[] sampleOffsets,
      long[] sampleTimestamps, Format[] sampleFormats, int[] sampleFlags) {
    sampleQueue.sampleData(new ParsableByteArray(data), data.length);
    Format format = null;
    for (int i = 0; i < sampleTimestamps.length; i++) {
      if (sampleFormats[i] != format) {
        sampleQueue.format(sampleFormats[i]);
        format = sampleFormats[i];
      }
      sampleQueue.sampleMetadata(
          sampleTimestamps[i],
          sampleFlags[i],
          sampleSizes[i],
          sampleOffsets[i],
          (sampleFlags[i] & C.BUFFER_FLAG_ENCRYPTED) != 0 ? DUMMY_CRYPTO_DATA : null);
    }
  }

  /** Writes a {@link Format} to the {@code sampleQueue}. */
  private void writeFormat(Format format) {
    sampleQueue.format(format);
  }

  /** Writes a single sample to {@code sampleQueue}. */
  private void writeSample(byte[] data, long timestampUs, int sampleFlags) {
    sampleQueue.sampleData(new ParsableByteArray(data), data.length);
    sampleQueue.sampleMetadata(
        timestampUs,
        sampleFlags,
        data.length,
        /* offset= */ 0,
        (sampleFlags & C.BUFFER_FLAG_ENCRYPTED) != 0 ? DUMMY_CRYPTO_DATA : null);
  }

  /**
   * Asserts correct reading of standard test data from {@code sampleQueue}.
   */
  private void assertReadTestData() {
    assertReadTestData(null, 0);
  }

  /**
   * Asserts correct reading of standard test data from {@code sampleQueue}.
   *
   * @param startFormat The format of the last sample previously read from {@code sampleQueue}.
   */
  private void assertReadTestData(Format startFormat) {
    assertReadTestData(startFormat, 0);
  }

  /**
   * Asserts correct reading of standard test data from {@code sampleQueue}.
   *
   * @param startFormat The format of the last sample previously read from {@code sampleQueue}.
   * @param firstSampleIndex The index of the first sample that's expected to be read.
   */
  private void assertReadTestData(Format startFormat, int firstSampleIndex) {
    assertReadTestData(startFormat, firstSampleIndex, SAMPLE_TIMESTAMPS.length - firstSampleIndex);
  }

  /**
   * Asserts correct reading of standard test data from {@code sampleQueue}.
   *
   * @param startFormat The format of the last sample previously read from {@code sampleQueue}.
   * @param firstSampleIndex The index of the first sample that's expected to be read.
   * @param sampleCount The number of samples to read.
   */
  private void assertReadTestData(Format startFormat, int firstSampleIndex, int sampleCount) {
    assertReadTestData(startFormat, firstSampleIndex, sampleCount, 0);
  }

  /**
   * Asserts correct reading of standard test data from {@code sampleQueue}.
   *
   * @param startFormat The format of the last sample previously read from {@code sampleQueue}.
   * @param firstSampleIndex The index of the first sample that's expected to be read.
   * @param sampleCount The number of samples to read.
   * @param sampleOffsetUs The expected sample offset.
   */
  private void assertReadTestData(
      Format startFormat, int firstSampleIndex, int sampleCount, long sampleOffsetUs) {
    Format format = adjustFormat(startFormat, sampleOffsetUs);
    for (int i = firstSampleIndex; i < firstSampleIndex + sampleCount; i++) {
      // Use equals() on the read side despite using referential equality on the write side, since
      // sampleQueue de-duplicates written formats using equals().
      Format testSampleFormat = adjustFormat(SAMPLE_FORMATS[i], sampleOffsetUs);
      if (!testSampleFormat.equals(format)) {
        // If the format has changed, we should read it.
        assertReadFormat(false, testSampleFormat);
        format = testSampleFormat;
      }
      // If we require the format, we should always read it.
      assertReadFormat(true, testSampleFormat);
      // Assert the sample is as expected.
      assertReadSample(
          SAMPLE_TIMESTAMPS[i] + sampleOffsetUs,
          (SAMPLE_FLAGS[i] & C.BUFFER_FLAG_KEY_FRAME) != 0,
          /* isEncrypted= */ false,
          DATA,
          DATA.length - SAMPLE_OFFSETS[i] - SAMPLE_SIZES[i],
          SAMPLE_SIZES[i]);
    }
  }

  /**
   * Asserts {@link SampleQueue#read} is behaving correctly, given there are no samples to read and
   * the last format to be written to the sample queue is {@code endFormat}.
   *
   * @param endFormat The last format to be written to the sample queue, or null of no format has
   *     been written.
   */
  private void assertNoSamplesToRead(Format endFormat) {
    // If not formatRequired or loadingFinished, should read nothing.
    assertReadNothing(false);
    // If formatRequired, should read the end format if set, else read nothing.
    if (endFormat == null) {
      assertReadNothing(true);
    } else {
      assertReadFormat(true, endFormat);
    }
    // If loadingFinished, should read end of stream.
    assertReadEndOfStream(false);
    assertReadEndOfStream(true);
    // Having read end of stream should not affect other cases.
    assertReadNothing(false);
    if (endFormat == null) {
      assertReadNothing(true);
    } else {
      assertReadFormat(true, endFormat);
    }
  }

  /**
   * Asserts {@link SampleQueue#read} returns {@link C#RESULT_NOTHING_READ}.
   *
   * @param formatRequired The value of {@code formatRequired} passed to {@link SampleQueue#read}.
   */
  private void assertReadNothing(boolean formatRequired) {
    clearFormatHolderAndInputBuffer();
    int result =
        sampleQueue.read(
            formatHolder,
            inputBuffer,
            formatRequired,
            /* loadingFinished= */ false,
            /* decodeOnlyUntilUs= */ 0);
    assertThat(result).isEqualTo(RESULT_NOTHING_READ);
    // formatHolder should not be populated.
    assertThat(formatHolder.format).isNull();
    // inputBuffer should not be populated.
    assertInputBufferContainsNoSampleData();
    assertInputBufferHasNoDefaultFlagsSet();
  }

  /**
   * Asserts {@link SampleQueue#read} returns {@link C#RESULT_BUFFER_READ} and that the {@link
   * DecoderInputBuffer#isEndOfStream()} is set.
   *
   * @param formatRequired The value of {@code formatRequired} passed to {@link SampleQueue#read}.
   */
  private void assertReadEndOfStream(boolean formatRequired) {
    clearFormatHolderAndInputBuffer();
    int result =
        sampleQueue.read(
            formatHolder,
            inputBuffer,
            formatRequired,
            /* loadingFinished= */ true,
            /* decodeOnlyUntilUs= */ 0);
    assertThat(result).isEqualTo(RESULT_BUFFER_READ);
    // formatHolder should not be populated.
    assertThat(formatHolder.format).isNull();
    // inputBuffer should not contain sample data, but end of stream flag should be set.
    assertInputBufferContainsNoSampleData();
    assertThat(inputBuffer.isEndOfStream()).isTrue();
    assertThat(inputBuffer.isDecodeOnly()).isFalse();
    assertThat(inputBuffer.isEncrypted()).isFalse();
  }

  /**
   * Asserts {@link SampleQueue#read} returns {@link C#RESULT_FORMAT_READ} and that the format
   * holder is filled with a {@link Format} that equals {@code format}.
   *
   * @param formatRequired The value of {@code formatRequired} passed to {@link SampleQueue#read}.
   * @param format The expected format.
   */
  private void assertReadFormat(boolean formatRequired, Format format) {
    clearFormatHolderAndInputBuffer();
    int result =
        sampleQueue.read(
            formatHolder,
            inputBuffer,
            formatRequired,
            /* loadingFinished= */ false,
            /* decodeOnlyUntilUs= */ 0);
    assertThat(result).isEqualTo(RESULT_FORMAT_READ);
    // formatHolder should be populated.
    assertThat(formatHolder.format).isEqualTo(format);
    // inputBuffer should not be populated.
    assertInputBufferContainsNoSampleData();
    assertInputBufferHasNoDefaultFlagsSet();
  }

  private void assertReadEncryptedSample(int sampleIndex) {
    byte[] sampleData = new byte[ENCRYPTED_SAMPLE_SIZES[sampleIndex]];
    Arrays.fill(sampleData, (byte) 1);
    boolean isKeyFrame = (ENCRYPTED_SAMPLES_FLAGS[sampleIndex] & C.BUFFER_FLAG_KEY_FRAME) != 0;
    boolean isEncrypted = (ENCRYPTED_SAMPLES_FLAGS[sampleIndex] & C.BUFFER_FLAG_ENCRYPTED) != 0;
    assertReadSample(
        ENCRYPTED_SAMPLE_TIMESTAMPS[sampleIndex],
        isKeyFrame,
        isEncrypted,
        sampleData,
        /* offset= */ 0,
        ENCRYPTED_SAMPLE_SIZES[sampleIndex] - (isEncrypted ? 2 : 0));
  }

  /**
   * Asserts {@link SampleQueue#read} returns {@link C#RESULT_BUFFER_READ} and that the buffer is
   * filled with the specified sample data.
   *
   * @param timeUs The expected buffer timestamp.
   * @param isKeyFrame The expected keyframe flag.
   * @param isEncrypted The expected encrypted flag.
   * @param sampleData An array containing the expected sample data.
   * @param offset The offset in {@code sampleData} of the expected sample data.
   * @param length The length of the expected sample data.
   */
  private void assertReadSample(
      long timeUs,
      boolean isKeyFrame,
      boolean isEncrypted,
      byte[] sampleData,
      int offset,
      int length) {
    clearFormatHolderAndInputBuffer();
    int result =
        sampleQueue.read(
            formatHolder,
            inputBuffer,
            /* formatRequired= */ false,
            /* loadingFinished= */ false,
            /* decodeOnlyUntilUs= */ 0);
    assertThat(result).isEqualTo(RESULT_BUFFER_READ);
    // formatHolder should not be populated.
    assertThat(formatHolder.format).isNull();
    // inputBuffer should be populated.
    assertThat(inputBuffer.timeUs).isEqualTo(timeUs);
    assertThat(inputBuffer.isKeyFrame()).isEqualTo(isKeyFrame);
    assertThat(inputBuffer.isDecodeOnly()).isFalse();
    assertThat(inputBuffer.isEncrypted()).isEqualTo(isEncrypted);
    inputBuffer.flip();
    assertThat(inputBuffer.data.limit()).isEqualTo(length);
    byte[] readData = new byte[length];
    inputBuffer.data.get(readData);
    assertThat(readData).isEqualTo(copyOfRange(sampleData, offset, offset + length));
  }

  /**
   * Asserts the number of allocations currently in use by {@code sampleQueue}.
   *
   * @param count The expected number of allocations.
   */
  private void assertAllocationCount(int count) {
    assertThat(allocator.getTotalBytesAllocated()).isEqualTo(ALLOCATION_SIZE * count);
  }

  /**
   * Asserts {@code inputBuffer} does not contain any sample data.
   */
  private void assertInputBufferContainsNoSampleData() {
    if (inputBuffer.data == null) {
      return;
    }
    inputBuffer.flip();
    assertThat(inputBuffer.data.limit()).isEqualTo(0);
  }

  private void assertInputBufferHasNoDefaultFlagsSet() {
    assertThat(inputBuffer.isEndOfStream()).isFalse();
    assertThat(inputBuffer.isDecodeOnly()).isFalse();
    assertThat(inputBuffer.isEncrypted()).isFalse();
  }

  private void clearFormatHolderAndInputBuffer() {
    formatHolder.format = null;
    inputBuffer.clear();
  }

  private static Format adjustFormat(@Nullable Format format, long sampleOffsetUs) {
    return format == null || sampleOffsetUs == 0
        ? format
        : format.copyWithSubsampleOffsetUs(sampleOffsetUs);
  }
}
