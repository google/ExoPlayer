/*
 * Copyright 2019 The Android Open Source Project
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
package androidx.media3.session;

import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;

/** Implementation of {@link MediaLibraryService}. */
/* package */ class MediaLibraryServiceImpl extends MediaSessionServiceImpl {

  @Override
  @Nullable
  public IBinder onBind(@Nullable Intent intent) {
    if (intent == null) {
      return null;
    }
    if (MediaLibraryService.SERVICE_INTERFACE.equals(intent.getAction())) {
      return getServiceBinder();
    }
    return super.onBind(intent);
  }
}
