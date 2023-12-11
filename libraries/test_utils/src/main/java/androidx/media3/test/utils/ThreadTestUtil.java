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
package androidx.media3.test.utils;

import android.os.Looper;
import androidx.annotation.GuardedBy;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ArrayListMultimap;

/** Static utility to coordinate threads in testing environments. */
@UnstableApi
public final class ThreadTestUtil {

  @GuardedBy("blockedThreadConditions")
  private static final ArrayListMultimap<Looper, ConditionVariable> blockedThreadConditions =
      ArrayListMultimap.create();

  /**
   * Registers that the current thread will be blocked with the provided {@link ConditionVariable}
   * until the specified {@link Looper} reports to have made progress via {@link
   * #unblockThreadsWaitingForProgressOnCurrentLooper()}.
   *
   * @param conditionVariable The {@link ConditionVariable} that will block the current thread.
   * @param looper The {@link Looper} that must report progress to unblock the current thread. Must
   *     not be the {@link Looper} of the current thread.
   */
  public static void registerThreadIsBlockedUntilProgressOnLooper(
      ConditionVariable conditionVariable, Looper looper) {
    Assertions.checkArgument(looper != Looper.myLooper());
    synchronized (blockedThreadConditions) {
      blockedThreadConditions.put(looper, conditionVariable);
    }
  }

  /** Unblocks any threads that are waiting for progress on the current {@link Looper} thread. */
  public static void unblockThreadsWaitingForProgressOnCurrentLooper() {
    Looper myLooper = Assertions.checkNotNull(Looper.myLooper());
    synchronized (blockedThreadConditions) {
      for (ConditionVariable condition : blockedThreadConditions.removeAll(myLooper)) {
        condition.open();
      }
    }
  }

  private ThreadTestUtil() {}
}
