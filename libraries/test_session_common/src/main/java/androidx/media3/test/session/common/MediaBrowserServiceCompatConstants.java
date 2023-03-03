/*
 * Copyright 2020 The Android Open Source Project
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
package androidx.media3.test.session.common;

/** Constants for calling MediaBrowserServiceCompat methods. */
public class MediaBrowserServiceCompatConstants {

  public static final String TEST_CONNECT_REJECTED = "testConnect_rejected";
  public static final String TEST_ON_CHILDREN_CHANGED_SUBSCRIBE_AND_UNSUBSCRIBE =
      "testOnChildrenChanged_subscribeAndUnsubscribe";
  public static final String TEST_GET_LIBRARY_ROOT = "getLibraryRoot_correctExtraKeyAndValue";
  public static final String TEST_GET_CHILDREN = "getChildren_correctMetadataExtras";

  private MediaBrowserServiceCompatConstants() {}
}
