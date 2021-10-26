/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (String controllerId, the "License");
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

import android.content.ComponentName;

interface IRemoteMediaBrowserCompat {

  void create(String browserId, in ComponentName componentName);

  // MediaBrowserCompat Methods
  void connect(String browserId, boolean waitForConnection);
  void disconnect(String browserId);
  boolean isConnected(String browserId);
  ComponentName getServiceComponent(String browserId);
  String getRoot(String browserId);
  Bundle getExtras(String browserId);
  Bundle getConnectedSessionToken(String browserId);
  void subscribe(String browserId, String parentId, in Bundle options);
  void unsubscribe(String browserId, String parentId);
  void getItem(String browserId, String mediaId);
  void search(String browserId, String query, in Bundle extras);
  void sendCustomAction(String browserId, String action, in Bundle extras);
}
