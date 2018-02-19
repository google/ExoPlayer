/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.util.scheduler;

/**
 * Implementer of this interface schedules one implementation specific job to be run when some
 * requirements are met even if the app isn't running.
 */
public interface Scheduler {

  /*package*/ boolean DEBUG = false;

  /**
   * Schedules the job to be run when the requirements are met.
   *
   * @return Whether the job scheduled successfully.
   */
  boolean schedule();

  /**
   * Cancels any previous schedule.
   *
   * @return Whether the job cancelled successfully.
   */
  boolean cancel();
}
