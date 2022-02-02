/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.avi;

/**
 * This is referred to as a Chunk in the MS spec, but that gets confusing with AV chunks.
 * Borrowed the term from mp4 as these are similar to boxes or atoms.
 */
public class Box {
  private final int size;
  private final int type;

  Box(int type, int size) {
    this.type = type;
    this.size = size;
  }

  public int getSize() {
    return size;
  }

  public int getType() {
    return type;
  }
}
