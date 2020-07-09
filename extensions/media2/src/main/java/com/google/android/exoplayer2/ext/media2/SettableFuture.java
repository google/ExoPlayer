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

package com.google.android.exoplayer2.ext.media2;

import androidx.concurrent.futures.CallbackToFutureAdapter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A replacement of com.google.common.util.concurrent.SettableFuture with CallbackToFutureAdapter to
 * avoid the dependency on Guava.
 */
@SuppressWarnings("ShouldNotSubclass")
/* package */ class SettableFuture<V> implements ListenableFuture<V> {
  static <V> SettableFuture<V> create() {
    return new SettableFuture<>();
  }

  private final ListenableFuture<V> future;
  private final CallbackToFutureAdapter.Completer<V> completer;

  SettableFuture() {
    AtomicReference<CallbackToFutureAdapter.Completer<V>> completerRef = new AtomicReference<>();
    future =
        CallbackToFutureAdapter.getFuture(
            completer -> {
              completerRef.set(completer);
              return null;
            });
    completer = Assertions.checkNotNull(completerRef.get());
  }

  @Override
  public void addListener(Runnable listener, Executor executor) {
    future.addListener(listener, executor);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return future.cancel(mayInterruptIfRunning);
  }

  @Override
  public boolean isCancelled() {
    return future.isCancelled();
  }

  @Override
  public boolean isDone() {
    return future.isDone();
  }

  @Override
  public V get() throws ExecutionException, InterruptedException {
    return future.get();
  }

  @Override
  public V get(long timeout, TimeUnit unit)
      throws ExecutionException, InterruptedException, TimeoutException {
    return future.get(timeout, unit);
  }

  void set(V value) {
    completer.set(value);
  }

  void setException(Throwable throwable) {
    completer.setException(throwable);
  }
}
