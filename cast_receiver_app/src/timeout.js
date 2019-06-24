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
 */

goog.module('exoplayer.cast.Timeout');

/**
 * A timeout which can be cancelled.
 */
class Timeout {
  constructor() {
    /** @private {?number} */
    this.timeout_ = null;
  }
  /**
   * Returns a promise which resolves when the duration of time defined by
   * delayMs has elapsed and cancel() has not been called earlier.
   *
   * If the timeout is already set, the former timeout is cancelled and a new
   * one is started.
   *
   * @param {number} delayMs The delay after which to resolve or a non-positive
   *     value if it should never resolve.
   * @return {!Promise<undefined>} Resolves after the given delayMs or never
   *     for a non-positive delay.
   */
  postDelayed(delayMs) {
    this.cancel();
    return new Promise((resolve, reject) => {
      if (delayMs <= 0) {
        return;
      }
      this.timeout_ = setTimeout(() => {
        if (this.timeout_) {
          this.timeout_ = null;
          resolve();
        }
      }, delayMs);
    });
  }

  /** Cancels the timeout. */
  cancel() {
    if (this.timeout_) {
      clearTimeout(this.timeout_);
      this.timeout_ = null;
    }
  }

  /** @return {boolean} true if the timeout is currently ongoing. */
  isOngoing() {
    return this.timeout_ !== null;
  }
}

exports = Timeout;
