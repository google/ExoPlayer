/*
 * Copyright (C) Sven Wischnowsky, 2020 Deutsche Telekom AG
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

package com.google.android.exoplayer2.source.dash.manifest;

public class Latency {

  int target;
  int max;
  int min;

  public Latency(int target, int max, int min){
    this.target = target;
    this.max = max;
    this.min = min;
  }

  public int getTarget() {
    return target;
  }

  public int getMax() {
    return max;
  }

  public int getMin() {
    return min;
  }
}
