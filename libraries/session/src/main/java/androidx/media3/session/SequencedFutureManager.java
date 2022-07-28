/*
 * Copyright 2019 The Android Open Source Project
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
package androidx.media3.session;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.os.Handler;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Manager for {@link SequencedFuture} that contains sequence numbers to be shared across processes.
 */
/* package */ final class SequencedFutureManager {

  private static final String TAG = "SequencedFutureManager";
  private final Object lock;

  @GuardedBy("lock")
  private int nextSequenceNumber;

  @GuardedBy("lock")
  private final ArrayMap<Integer, SequencedFuture<?>> seqToFutureMap;

  @GuardedBy("lock")
  @Nullable
  private Runnable pendingLazyReleaseCallback;

  @GuardedBy("lock")
  @Nullable
  private Handler releaseCallbackHandler;

  @GuardedBy("lock")
  private boolean isReleased;

  public SequencedFutureManager() {
    lock = new Object();
    seqToFutureMap = new ArrayMap<>();
  }

  /**
   * Obtains next sequence number without creating future. Used for methods with no return (e.g.
   * release())
   *
   * @return sequence number
   */
  public int obtainNextSequenceNumber() {
    synchronized (lock) {
      return nextSequenceNumber++;
    }
  }

  /**
   * Creates {@link SequencedFuture} with sequence number. Used to return {@link ListenableFuture}
   * for remote process call.
   *
   * @return AbstractFuture with sequence number
   */
  public <T extends @NonNull Object> SequencedFuture<T> createSequencedFuture(T resultWhenClosed) {
    synchronized (lock) {
      int seq = obtainNextSequenceNumber();
      SequencedFuture<T> result = SequencedFuture.create(seq, resultWhenClosed);
      if (isReleased) {
        result.setWithTheValueOfResultWhenClosed();
      } else {
        seqToFutureMap.put(seq, result);
      }
      return result;
    }
  }

  /**
   * Sets result of the {@link SequencedFuture} with the sequence id. Specified future will be
   * removed from the manager.
   *
   * @param seq sequence number to find future
   * @param result result to set
   */
  @SuppressWarnings("unchecked")
  public <T extends @NonNull Object> void setFutureResult(int seq, T result) {
    synchronized (lock) {
      @Nullable SequencedFuture<?> future = seqToFutureMap.remove(seq);
      if (future != null) {
        if (future.getResultWhenClosed().getClass() == result.getClass()) {
          ((SequencedFuture<T>) future).set(result);
        } else {
          Log.w(
              TAG,
              "Type mismatch, expected "
                  + future.getResultWhenClosed().getClass()
                  + ", but was "
                  + result.getClass());
        }
      }
      if (pendingLazyReleaseCallback != null && seqToFutureMap.isEmpty()) {
        release();
      }
    }
  }

  public void release() {
    List<SequencedFuture<?>> pendingResults;
    synchronized (lock) {
      isReleased = true;
      pendingResults = new ArrayList<>(seqToFutureMap.values());
      seqToFutureMap.clear();
      if (pendingLazyReleaseCallback != null) {
        checkNotNull(releaseCallbackHandler).post(pendingLazyReleaseCallback);
        pendingLazyReleaseCallback = null;
        releaseCallbackHandler = null;
      }
    }
    for (SequencedFuture<?> result : pendingResults) {
      result.setWithTheValueOfResultWhenClosed();
    }
  }

  public void lazyRelease(long timeoutMs, Runnable releaseCallback) {
    synchronized (lock) {
      Handler releaseCallbackHandler = Util.createHandlerForCurrentLooper();
      this.releaseCallbackHandler = releaseCallbackHandler;
      pendingLazyReleaseCallback = releaseCallback;
      if (seqToFutureMap.isEmpty()) {
        release();
      } else {
        releaseCallbackHandler.postDelayed(this::release, timeoutMs);
      }
    }
  }

  public static final class SequencedFuture<T extends @NonNull Object> extends AbstractFuture<T> {

    private final int sequenceNumber;
    private final T resultWhenClosed;

    private SequencedFuture(int seq, T resultWhenClosed) {
      sequenceNumber = seq;
      this.resultWhenClosed = resultWhenClosed;
    }

    @Override
    public boolean set(T value) {
      return super.set(value);
    }

    public void setWithTheValueOfResultWhenClosed() {
      set(resultWhenClosed);
    }

    public int getSequenceNumber() {
      return sequenceNumber;
    }

    public T getResultWhenClosed() {
      return resultWhenClosed;
    }

    /** Creates a new instance that can be completed or cancelled by a later method call. */
    public static <T extends @NonNull Object> SequencedFuture<T> create(
        int seq, T resultWhenClosed) {
      return new SequencedFuture<>(seq, resultWhenClosed);
    }
  }
}
