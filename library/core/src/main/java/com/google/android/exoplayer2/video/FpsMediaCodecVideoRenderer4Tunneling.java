package com.google.android.exoplayer2.video;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.util.Log;

public class FpsMediaCodecVideoRenderer4Tunneling extends MediaCodecVideoRenderer {
  PtsExpectedQueue ptsQueue = new PtsExpectedQueue();

  private static final String TAG1 = "DROP-MON";

  public FpsMediaCodecVideoRenderer4Tunneling(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      long allowedJoiningTimeMs,
      boolean enableDecoderFallback,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify) {
    super(
        context,
        mediaCodecSelector,
        allowedJoiningTimeMs,
        enableDecoderFallback,
        eventHandler,
        eventListener,
        maxDroppedFramesToNotify);
  }

  @Override
  public String getName() {
    return "FpsMonVidRend";
  }

  @Override
  protected void resetCodecStateForFlush() {
    super.resetCodecStateForFlush();
    if (ptsQueue != null) {// We need this. This might be called from super's constructor
      ptsQueue.clearTimestamps();
    }
  }

  @Override
  protected void onInputFormatChanged(FormatHolder formatHolder) throws ExoPlaybackException {
    super.onInputFormatChanged(formatHolder);
    // TODO: figure out why!
    // Ensure timestamps of buffers queued after this format change are never inserted into the
    // queue of expected output timestamps before those of buffers that have already been queued.
    ptsQueue.freeze();
  }

  @Override
  protected void onQueueInputBuffer(DecoderInputBuffer buffer) throws ExoPlaybackException {
    super.onQueueInputBuffer(buffer);
    ptsQueue.insertTimestamp(buffer.timeUs);
    ptsQueue.maybeShiftTimestampsList();
  }

  @Override
  protected void onProcessedOutputBuffer(long presentationTimeUs) {
    super.onProcessedOutputBuffer(presentationTimeUs);
    ptsQueue.increaseBufferCount();

    int dropCount = 0;

    long expectedTimestampUs = ptsQueue.dequeueTimestamp();
    long nextExpected = expectedTimestampUs;
    while (presentationTimeUs > nextExpected) {
      dropCount++;
      nextExpected = ptsQueue.dequeueTimestamp();
    }

    if (dropCount == 0) return; // No drop happened, continue.

    updateDroppedBufferCounters(dropCount);

    String warning = "Expected to dequeue video buffer with presentation "
        + "timestamp: " + expectedTimestampUs / 1000 + ". Instead got: " + presentationTimeUs / 1000
        + " (Processed buffers since last flush: " + ptsQueue.bufferCount + "), dropCount = " + dropCount;

    if (nextExpected == presentationTimeUs) {
      Log.w(TAG1, warning);
    } else {
      throw new IllegalStateException("PTS does not exist in the queue, I don't know what to do!");
    }
  }

  private static class PtsExpectedQueue {
    private int startIndex;
    private int queueSize;
    private int bufferCount;
    private int minimumInsertIndex;

    private static final int ARRAY_SIZE = 1000;
    private final long[] timestampsList = new long[ARRAY_SIZE];

    public void clearTimestamps() {
      startIndex = 0;
      queueSize = 0;
      bufferCount = 0;
      minimumInsertIndex = 0;
    }

    public void increaseBufferCount() {
      bufferCount++;
    }

    public void insertTimestamp(long presentationTimeUs) {
      for (int i = startIndex + queueSize - 1; i >= minimumInsertIndex; i--) {
        if (presentationTimeUs >= timestampsList[i]) {
          timestampsList[i + 1] = presentationTimeUs;
          queueSize++;
          return;
        }
        timestampsList[i + 1] = timestampsList[i];
      }
      timestampsList[minimumInsertIndex] = presentationTimeUs;
      queueSize++;
    }

    public void maybeShiftTimestampsList() {
      if (startIndex + queueSize == ARRAY_SIZE) {
        System.arraycopy(timestampsList, startIndex, timestampsList, 0, queueSize);
        minimumInsertIndex -= startIndex;
        startIndex = 0;
      }
    }

    public long dequeueTimestamp() {
      queueSize--;
      startIndex++;
      minimumInsertIndex = Math.max(minimumInsertIndex, startIndex);
      return timestampsList[startIndex - 1];
    }

    public void freeze() {
      minimumInsertIndex = startIndex + queueSize;
    }
  }
}
