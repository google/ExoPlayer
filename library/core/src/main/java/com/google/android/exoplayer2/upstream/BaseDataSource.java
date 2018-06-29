/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream;

import java.util.ArrayList;

/**
 * Base {@link DataSource} implementation to keep a list of {@link TransferListener}s.
 *
 * <p>Subclasses must call {@link #transferStarted(DataSpec)}, {@link #bytesTransferred(int)}, and
 * {@link #transferEnded()} to inform listeners of data transfers.
 */
public abstract class BaseDataSource implements DataSource {

  private final @DataSource.Type int type;
  private final ArrayList<TransferListener<? super DataSource>> listeners;

  /**
   * Creates base data source for a data source of the specified type.
   *
   * @param type The {@link DataSource.Type} of the data source.
   */
  protected BaseDataSource(@DataSource.Type int type) {
    this.type = type;
    this.listeners = new ArrayList<>(/* initialCapacity= */ 1);
  }

  @Override
  public final void addTransferListener(TransferListener<? super DataSource> transferListener) {
    listeners.add(transferListener);
  }

  /**
   * Notifies listeners that data transfer for the specified {@link DataSpec} started.
   *
   * @param dataSpec {@link DataSpec} describing the data being transferred.
   */
  protected final void transferStarted(DataSpec dataSpec) {
    for (int i = 0; i < listeners.size(); i++) {
      listeners.get(i).onTransferStart(/* source= */ this, dataSpec);
    }
  }

  /**
   * Notifies listeners that bytes were transferred.
   *
   * @param bytesTransferred The number of bytes transferred since the previous call to this method
   *     (or if the first call, since the transfer was started).
   */
  protected final void bytesTransferred(int bytesTransferred) {
    for (int i = 0; i < listeners.size(); i++) {
      listeners.get(i).onBytesTransferred(/* source= */ this, bytesTransferred);
    }
  }

  /** Notifies listeners that a transfer ended. */
  protected final void transferEnded() {
    for (int i = 0; i < listeners.size(); i++) {
      listeners.get(i).onTransferEnd(/* source= */ this);
    }
  }
}
