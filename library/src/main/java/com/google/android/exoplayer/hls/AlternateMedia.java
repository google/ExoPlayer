/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.hls;

/**
 * Alternate media tag.
 */
public final class AlternateMedia {
  public static final int TYPE_VIDEO = 0;
  public static final int TYPE_AUDIO = 1;
  public static final int TYPE_SUBTITLES = 2;
  public static final int TYPE_CLOSED_CAPTIONS = 3;

  public final int index;
  public final int type;
  public final String name;
  public final String uri;
  public final String groupID;
  public final String language;
  public final boolean isDefault;
  public final boolean autoSelect;

  public AlternateMedia(int index, int type, String name, String uri, String groupID, String language, boolean isDefault, boolean autoSelect) {
    this.index = index;
    this.type = type;
    this.name = name;
    this.uri = uri;
    this.groupID = groupID;
    this.language = language;
    this.autoSelect = autoSelect;
    this.isDefault = isDefault;
  }
}
