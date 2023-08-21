/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.common.util;

import java.util.Iterator;

/** A primitive long iterator used for generating sequences of timestamps. */
@UnstableApi
public interface TimestampIterator {

  /** Returns whether there is another element. */
  boolean hasNext();

  /** Returns the next timestamp. */
  long next();

  /** Creates TimestampIterator */
  static TimestampIterator createFromLongIterator(Iterator<Long> iterator) {
    return new TimestampIterator() {
      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public long next() {
        return iterator.next();
      }
    };
  }
}
