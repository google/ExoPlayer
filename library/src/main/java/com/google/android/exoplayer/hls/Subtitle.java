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
 * Subtitle media tag.
 */
public final class Subtitle {

  public final String name;
  public final String uri;
  public final String language;
  public final boolean isDefault;
  public final boolean autoSelect;

  public Subtitle(String name, String uri, String language, boolean isDefault, boolean autoSelect) {
    this.name = name;
    this.uri = uri;
    this.language = language;
    this.autoSelect = autoSelect;
    this.isDefault = isDefault;
  }

}
