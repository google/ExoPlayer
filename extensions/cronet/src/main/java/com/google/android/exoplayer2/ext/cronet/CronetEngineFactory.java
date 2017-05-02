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
package com.google.android.exoplayer2.ext.cronet;

import android.content.Context;
import org.chromium.net.CronetEngine;

/**
 * A factory class which creates or reuses a {@link CronetEngine}.
 */
public final class CronetEngineFactory {

  private final Context context;

  private CronetEngine cronetEngine = null;

  /**
   * Creates the factory for a {@link CronetEngine}.
   * @param context The application context.
   */
  public CronetEngineFactory(Context context) {
    this.context = context;
  }

  /* package */ CronetEngine createCronetEngine() {
    if (cronetEngine == null) {
      cronetEngine = new CronetEngine.Builder(context).build();
    }
    return cronetEngine;
  }

}
