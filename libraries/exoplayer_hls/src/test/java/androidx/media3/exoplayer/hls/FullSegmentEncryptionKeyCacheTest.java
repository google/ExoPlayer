/*
 * Copyright (C) 2019 The Android Open Source Project
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
 *
 */
package androidx.media3.exoplayer.hls;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link FullSegmentEncryptionKeyCache}. */
@RunWith(AndroidJUnit4.class)
public class FullSegmentEncryptionKeyCacheTest {

  private final Uri firstUri = Uri.parse("https://www.google.com");
  private final Uri secondUri = Uri.parse("https://www.abc.xyz");
  private final byte[] encryptionKey = {5, 6, 7, 8};

  @Test
  public void putThenGetAndContains() {
    FullSegmentEncryptionKeyCache cache = new FullSegmentEncryptionKeyCache(/* maxSize= */ 5);
    cache.put(firstUri, encryptionKey);
    assertThat(cache.get(firstUri)).isEqualTo(encryptionKey);
    assertThat(cache.get(secondUri)).isNull();
    assertThat(cache.containsUri(firstUri)).isTrue();
    assertThat(cache.containsUri(secondUri)).isFalse();
  }

  @Test
  public void getNullReturnsNull() {
    FullSegmentEncryptionKeyCache cache = new FullSegmentEncryptionKeyCache(/* maxSize= */ 5);
    cache.put(firstUri, encryptionKey);
    assertThat(cache.get(null)).isNull();
  }

  @Test
  public void putNullKeyThrowsException() {
    FullSegmentEncryptionKeyCache cache = new FullSegmentEncryptionKeyCache(/* maxSize= */ 5);
    try {
      cache.put(null, encryptionKey);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void putNullValueThrowsException() {
    FullSegmentEncryptionKeyCache cache = new FullSegmentEncryptionKeyCache(/* maxSize= */ 5);
    try {
      cache.put(firstUri, null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void containsNullThrowsException() {
    FullSegmentEncryptionKeyCache cache = new FullSegmentEncryptionKeyCache(/* maxSize= */ 5);
    try {
      cache.containsUri(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void removeNullThrowsException() {
    FullSegmentEncryptionKeyCache cache = new FullSegmentEncryptionKeyCache(/* maxSize= */ 5);
    try {
      cache.remove(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void oldestElementRemoved() {
    FullSegmentEncryptionKeyCache cache = new FullSegmentEncryptionKeyCache(/* maxSize= */ 2);

    cache.put(firstUri, encryptionKey);
    cache.put(secondUri, new byte[] {1, 2, 3, 4});
    cache.put(Uri.parse("www.nest.com"), new byte[] {1, 2, 3, 4});

    assertThat(cache.containsUri(firstUri)).isFalse();
    assertThat(cache.containsUri(secondUri)).isTrue();
  }

  /**
   * Elements need to be removed and reinserted, rather than just updated, to change their position
   * in the removal queue.
   */
  @Test
  public void updatingElementDoesntChangeAgeForRemoval() {
    FullSegmentEncryptionKeyCache cache = new FullSegmentEncryptionKeyCache(/* maxSize= */ 2);

    cache.put(firstUri, encryptionKey);
    cache.put(secondUri, new byte[] {1, 2, 3, 4});
    // Update firstUri element
    cache.put(firstUri, new byte[] {10, 11, 12, 12});
    cache.put(Uri.parse("www.nest.com"), new byte[] {1, 2, 3, 4});

    // firstUri is still removed before secondUri, despite the update
    assertThat(cache.containsUri(firstUri)).isFalse();
    assertThat(cache.containsUri(secondUri)).isTrue();
  }
}
