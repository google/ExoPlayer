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
package com.google.android.exoplayer.text.tx3g;

import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.SimpleSubtitleParser;
import com.google.android.exoplayer.text.Subtitle;

/**
 * A subtitle parser for tx3g.
 * <p>
 * Currently only supports parsing of a single text track.
 */
public final class Tx3gParser extends SimpleSubtitleParser {

  public Tx3gParser() {
    super("Tx3gParser");
  }

  @Override
  protected Subtitle decode(byte[] bytes, int length) {
    String cueText = new String(bytes, 0, length);
    return new Tx3gSubtitle(new Cue(cueText));
  }

}
