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
package com.google.android.exoplayer2.upstream.cache;

import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.cache.Cache.CacheException;
import com.google.android.exoplayer2.util.Assertions;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Default implementation of {@link ContentMetadata}. Values are stored as byte arrays. */
public final class DefaultContentMetadata implements ContentMetadata {

  /** Listener for metadata change event. */
  public interface ChangeListener {
    /**
     * Called when any metadata value is changed or removed.
     *
     * @param contentMetadata The reporting instance.
     * @param metadataValues All metadata name, value pairs. It shouldn't be accessed out of this
     *     method.
     */
    void onChange(DefaultContentMetadata contentMetadata, Map<String, byte[]> metadataValues)
        throws CacheException;
  }

  private final Map<String, byte[]> metadata;
  @Nullable private ChangeListener changeListener;

  public DefaultContentMetadata() {
    this.metadata = new HashMap<>();
  }

  /**
   * Constructs a {@link DefaultContentMetadata} using name, value pairs data which was passed to
   * {@link ChangeListener#onChange(DefaultContentMetadata, Map)}.
   *
   * @param metadata Initial name, value pairs.
   */
  public DefaultContentMetadata(Map<String, byte[]> metadata) {
    this.metadata = new HashMap<>(metadata);
  }

  /** Sets a {@link ChangeListener}. This method can be called once. */
  public void setChangeListener(ChangeListener changeListener) {
    Assertions.checkState(this.changeListener == null);
    this.changeListener = Assertions.checkNotNull(changeListener);
  }

  @Override
  public final Editor edit() {
    return new EditorImpl();
  }

  @Override
  public final byte[] get(String name, byte[] defaultValue) {
    synchronized (metadata) {
      if (metadata.containsKey(name)) {
        return metadata.get(name);
      } else {
        return defaultValue;
      }
    }
  }

  @Override
  public final String get(String name, String defaultValue) {
    synchronized (metadata) {
      if (metadata.containsKey(name)) {
        byte[] bytes = metadata.get(name);
        return new String(bytes, Charset.forName(C.UTF8_NAME));
      } else {
        return defaultValue;
      }
    }
  }

  @Override
  public final long get(String name, long defaultValue) {
    synchronized (metadata) {
      if (metadata.containsKey(name)) {
        byte[] bytes = metadata.get(name);
        return ByteBuffer.wrap(bytes).getLong();
      } else {
        return defaultValue;
      }
    }
  }

  @Override
  public final boolean contains(String name) {
    synchronized (metadata) {
      return metadata.containsKey(name);
    }
  }

  private void apply(ArrayList<String> removedValues, Map<String, byte[]> editedValues)
      throws CacheException {
    synchronized (metadata) {
      for (int i = 0; i < removedValues.size(); i++) {
        metadata.remove(removedValues.get(i));
      }
      metadata.putAll(editedValues);
      if (changeListener != null) {
        changeListener.onChange(this, Collections.unmodifiableMap(metadata));
      }
    }
  }

  private class EditorImpl implements Editor {

    private final Map<String, byte[]> editedValues;
    private final ArrayList<String> removedValues;

    private EditorImpl() {
      editedValues = new HashMap<>();
      removedValues = new ArrayList<>();
    }

    @Override
    public Editor set(String name, String value) {
      set(name, value.getBytes());
      return this;
    }

    @Override
    public Editor set(String name, long value) {
      set(name, ByteBuffer.allocate(8).putLong(value).array());
      return this;
    }

    @Override
    public Editor set(String name, byte[] value) {
      editedValues.put(name, Assertions.checkNotNull(value));
      removedValues.remove(name);
      return this;
    }

    @Override
    public Editor remove(String name) {
      removedValues.add(name);
      editedValues.remove(name);
      return this;
    }

    @Override
    public void commit() throws CacheException {
      apply(removedValues, editedValues);
      removedValues.clear();
      editedValues.clear();
    }
  }
}
