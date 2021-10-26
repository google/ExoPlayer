/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.source;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.UnstableApi;

/** Thrown if the input format was not recognized. */
@UnstableApi
public class UnrecognizedInputFormatException extends ParserException {

  /** The {@link Uri} from which the unrecognized data was read. */
  public final Uri uri;

  /**
   * @param message The detail message for the exception.
   * @param uri The {@link Uri} from which the unrecognized data was read.
   */
  public UnrecognizedInputFormatException(String message, Uri uri) {
    super(message, /* cause= */ null, /* contentIsMalformed= */ false, C.DATA_TYPE_MEDIA);
    this.uri = uri;
  }
}
