/*
 * Copyright 2018 The Android Open Source Project
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

/** Constants for calling MediaSession methods. */
public class MediaSessionConstants {

  // Test method names
  public static final String TEST_GET_SESSION_ACTIVITY = "testGetSessionActivity";
  public static final String TEST_GET_CUSTOM_LAYOUT = "testGetCustomLayout";
  public static final String TEST_WITH_CUSTOM_COMMANDS = "testWithCustomCommands";
  public static final String TEST_CONTROLLER_LISTENER_SESSION_REJECTS = "connection_sessionRejects";
  public static final String TEST_IS_SESSION_COMMAND_AVAILABLE = "testIsSessionCommandAvailable";
  public static final String TEST_COMMAND_GET_TRACKS = "testCommandGetTracksUnavailable";
  public static final String TEST_ON_VIDEO_SIZE_CHANGED = "onVideoSizeChanged";
  public static final String TEST_ON_TRACKS_CHANGED_VIDEO_TO_AUDIO_TRANSITION =
      "onTracksChanged_videoToAudioTransition";
  public static final String TEST_SET_SHOW_PLAY_BUTTON_IF_SUPPRESSED_TO_FALSE =
      "testSetShowPlayButtonIfSuppressedToFalse";
  public static final String TEST_MEDIA_CONTROLLER_COMPAT_CALLBACK_WITH_MEDIA_SESSION_TEST =
      "MediaControllerCompatCallbackWithMediaSessionTest";
  // Bundle keys
  public static final String KEY_AVAILABLE_SESSION_COMMANDS = "availableSessionCommands";
  public static final String KEY_CONTROLLER = "controllerKey";
  public static final String KEY_COMMAND_GET_TASKS_UNAVAILABLE = "commandGetTasksUnavailable";

  private MediaSessionConstants() {}
}
