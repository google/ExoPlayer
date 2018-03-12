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

import com.google.android.exoplayer2.C;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Default implementation of {@link ContentMetadata}. Values are stored as byte arrays. */
public final class DefaultContentMetadata implements ContentMetadata {

  private final Map<String, byte[]> metadata;

  /** Constructs an empty {@link DefaultContentMetadata}. */
  public DefaultContentMetadata() {
    this.metadata = new HashMap<>();
  }

  /**
   * Constructs a {@link DefaultContentMetadata} by copying metadata values from {@code other} and
   * applying {@code mutations}.
   */
  public DefaultContentMetadata(DefaultContentMetadata other, ContentMetadataMutations mutations) {
    this.metadata = new HashMap<>(other.metadata);
    applyMutations(mutations);
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

  private void applyMutations(ContentMetadataMutations mutations) {
    List<String> removedValues = mutations.getRemovedValues();
    for (int i = 0; i < removedValues.size(); i++) {
      metadata.remove(removedValues.get(i));
    }
    Map<String, Object> editedValues = mutations.getEditedValues();
    for (String name : editedValues.keySet()) {
      Object value = editedValues.get(name);
      metadata.put(name, getBytes(value));
    }
  }

  private static byte[] getBytes(Object value) {
    if (value instanceof Long) {
      return ByteBuffer.allocate(8).putLong((Long) value).array();
    } else if (value instanceof String) {
      return ((String) value).getBytes(Charset.forName(C.UTF8_NAME));
    } else if (value instanceof byte[]) {
      return (byte[]) value;
    } else {
      throw new IllegalStateException();
    }
  }

}
