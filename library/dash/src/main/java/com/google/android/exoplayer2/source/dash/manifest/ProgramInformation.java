/*
 * Copyright (C) 2018 The Android Open Source Project
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

public class ProgramInformation {
  /**
   * The title for the media presentation.
   */
  public final String title;

  /**
   * Information about the original source of the media presentation.
   */
  public final String source;

  /**
   * A copyright statement for the media presentation.
   */
  public final String copyright;

  public ProgramInformation(String title, String source, String copyright) {
    this.title = title;
    this.source = source;
    this.copyright = copyright;
  }
}
