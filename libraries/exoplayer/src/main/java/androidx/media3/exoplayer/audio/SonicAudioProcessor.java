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
package androidx.media3.exoplayer.audio;

import androidx.media3.common.util.UnstableApi;

/**
 * @deprecated Use {@link androidx.media3.common.audio.SonicAudioProcessor}.
 */
@Deprecated
@UnstableApi
// TODO(b/261567371): Move `final` from common SonicAudioProcessor method signatures to class
// signature when removing this file.
public final class SonicAudioProcessor extends androidx.media3.common.audio.SonicAudioProcessor {}
