/*
 * Copyright (C) 2020 The Android Open Source Project
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
 *
 */
package com.google.android.exoplayer2.text.span;

/**
 * A styling span for horizontal text in a vertical context.
 *
 * <p>This is used in vertical text to write some characters in a horizontal orientation, known in
 * Japanese as tate-chu-yoko.
 *
 * <p>More information on <a
 * href="https://www.w3.org/TR/jlreq/#handling_of_tatechuyoko">tate-chu-yoko</a> and <a
 * href="https://developer.android.com/guide/topics/text/spans">span styling</a>.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
// NOTE: There's no Android layout support for this, so this span currently doesn't extend any
// styling superclasses (e.g. MetricAffectingSpan). The only way to render this styling is to
// extract the spans and do the layout manually.
@Deprecated
public final class HorizontalTextInVerticalContextSpan implements LanguageFeatureSpan {}
