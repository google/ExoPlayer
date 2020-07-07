package com.google.android.exoplayer2.video;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.util.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

public class FpsMediaCodecVideoRenderer4Tunneling extends MediaCodecVideoRenderer {
  final PtsHistory ptsHistory = new PtsHistory();
  AuditorThread auditorThread = new AuditorThread(ptsHistory);
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
    return "MonVidRenderer";
  }

  @Override
  protected void resetCodecStateForFlush() {
    super.resetCodecStateForFlush();
    if (ptsQueue != null) {// We need this. It can be the following stack
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
    ptsHistory.record(presentationTimeUs);
    ptsQueue.increaseBufferCount();

    int dropCount = 0;

    long expectedTimestampUs = ptsQueue.dequeueTimestamp();
    long nextExpected = expectedTimestampUs;
    while (presentationTimeUs > nextExpected) {
      dropCount++;
      nextExpected = ptsQueue.dequeueTimestamp();
    }

    if (dropCount == 0) return; // No drop happened, continue.

    String warning = "Expected to dequeue video buffer with presentation "
        + "timestamp: " + expectedTimestampUs / 1000 + ". Instead got: " + presentationTimeUs / 1000
        + " (Processed buffers since last flush: " + ptsQueue.bufferCount + "), dropCount = " + dropCount;

    if (nextExpected == presentationTimeUs) {
      Log.w(TAG1, warning);
    } else {
      throw new IllegalStateException("PTS does not exist in the queue, I don't know what to do!");
    }
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream) throws ExoPlaybackException {
    super.onEnabled(joining, mayRenderStartOfStream);
    auditorThread.start();
  }

  @Override
  protected void onDisabled() {
    Log.i(TAG1, "onDisabled()");
    ptsHistory.dumpAll();
    auditorThread.interrupt();
    super.onDisabled();
  }

  @Override
  protected void onStarted() {
    Log.i(TAG1, "onStarted()");
    super.onStarted();
  }

  private static class PtsHistory {
    public static final int RECORD_CNT = 100; //
    private int idx;
    private long[] timeRecords = new long[RECORD_CNT * 2];
    private long[] timeRecordsBackup = new long[RECORD_CNT * 2];
    int timeRecordsBackupCount; // For last batch
    public ArrayBlockingQueue<long[]> fpsQueue = new ArrayBlockingQueue<>(1);

    public void record(long presentationTimeUs) {
      timeRecords[idx] = System.nanoTime();
      timeRecords[idx + RECORD_CNT] = presentationTimeUs / 1000;
      idx++;
      if (idx == RECORD_CNT) {
        System.arraycopy(timeRecords, 0, timeRecordsBackup, 0, RECORD_CNT * 2);
        timeRecordsBackupCount = RECORD_CNT;
        idx = 0;
        // The AuditorThread may experience starvation, this method may return false
        fpsQueue.offer(timeRecordsBackup);
      }
    }

    public void dumpAll() {
      if (idx == 0) return;// Nothing to report
      System.arraycopy(timeRecords, 0, timeRecordsBackup, 0, RECORD_CNT * 2);
      timeRecordsBackupCount = idx;
      idx = 0;
      // The AuditorThread may experience starvation, this method may return false
      fpsQueue.offer(timeRecordsBackup);
      Log.i(TAG1, "PtsHistory::dumpAll(), lastBatchCnt = " + timeRecordsBackupCount);
    }
  }

  private static class AuditorThread extends Thread {
    final PtsHistory ptsHistory;

    private AuditorThread(@Nonnull PtsHistory ptsHistory) {
      super(AuditorThread.class.getSimpleName());
      this.ptsHistory = ptsHistory;
      setPriority(Thread.MIN_PRIORITY);
    }

    @Override
    public void run() {
      while (!isInterrupted()) {
        long[] times = new long[0];
        try {
          times = ptsHistory.fpsQueue.take();
        } catch (InterruptedException e) {
          Log.i(TAG1, "Start quitting AuditorThread");
          try {
            times = ptsHistory.fpsQueue.poll(10, TimeUnit.SECONDS);
            if (times == null) times = new long[0];
          } catch (InterruptedException ex) {
            // Ignore, shall not happen
            Log.i(TAG1, "Shall not happen");
          }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("===>");
        // Use awk to handle delta
        // adb logcat com.canaldigital.ngp:I  *:S FPS:V |grep -v "===>" > sample.log
        // awk '/pts/{print $0 "\tdelta = " $10-t} !/pts/{print $0} {t=$10}' sample.log >sample_with_delta.log
        for (int i = 0; i < ptsHistory.timeRecordsBackupCount; i++) {
          sb.append(
              "\npts = " + times[i + ptsHistory.RECORD_CNT] +
                  // "\t" + sdf.format(new Date(times[i + RECORD_CNT])) +
                  "\tcb = " + times[i]);
        }
        Log.d("FPS", sb.toString());

      }

      Log.i(TAG1, "Quit AuditorThread safely");
    }
  }

  ;


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
