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

public class AlternateMedia {
  public static final int TYPE_VIDEO = 0;
  public static final int TYPE_AUDIO = 1;

  public final int index;
  public final int type;
  public final String groupID;
  public final String language;
  public final String name;
  public final boolean deflt;
  public final boolean autoSelect;
  public final String url;

  public AlternateMedia(int index, int type, String groupID, String name,
                        boolean deflt, boolean autoSelect, String language,
                        String url) {
    this.index = index;
    this.type = type;
    this.groupID = groupID;
    this.name = name;
    this.autoSelect = autoSelect;
    this.deflt = deflt;
    this.language = language;
    this.url = url;
  }
}
