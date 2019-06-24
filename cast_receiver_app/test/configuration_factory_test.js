/*
 * Copyright (C) 2019 The Android Open Source Project
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

goog.module('exoplayer.cast.test.configurationfactory');
goog.setTestOnly();

const ConfigurationFactory = goog.require('exoplayer.cast.ConfigurationFactory');
const testSuite = goog.require('goog.testing.testSuite');
const util = goog.require('exoplayer.cast.test.util');

let configurationFactory;

testSuite({
  setUp() {
    configurationFactory = new ConfigurationFactory();
  },

  /** Tests creating the most basic configuration. */
  testCreateBasicConfiguration() {
    /** @type {!TrackSelectionParameters} */
    const selectionParameters = /** @type {!TrackSelectionParameters} */ ({
      preferredAudioLanguage: 'en',
      preferredTextLanguage: 'it',
    });
    const configuration = configurationFactory.createConfiguration(
        util.queue.slice(0, 1), selectionParameters);
    assertEquals('en', configuration.preferredAudioLanguage);
    assertEquals('it', configuration.preferredTextLanguage);
    // Assert empty drm configuration as default.
    assertArrayEquals(['servers'], Object.keys(configuration.drm));
    assertArrayEquals([], Object.keys(configuration.drm.servers));
  },

  /** Tests defaults for undefined audio and text languages. */
  testCreateBasicConfiguration_languagesUndefined() {
    const configuration = configurationFactory.createConfiguration(
        util.queue.slice(0, 1), /** @type {!TrackSelectionParameters} */ ({}));
    assertEquals('', configuration.preferredAudioLanguage);
    assertEquals('', configuration.preferredTextLanguage);
  },

  /** Tests creating a drm configuration */
  testCreateDrmConfiguration() {
    /** @type {!MediaItem} */
    const mediaItem = util.queue[1];
    mediaItem.drmSchemes = [
      {
        uuid: 'edef8ba9-79d6-4ace-a3c8-27dcd51d21ed',
        licenseServer: {
          uri: 'drm-uri0',
        },
      },
      {
        uuid: '9a04f079-9840-4286-ab92-e65be0885f95',
        licenseServer: {
          uri: 'drm-uri1',
        },
      },
      {
        uuid: 'unsupported-drm-uuid',
        licenseServer: {
          uri: 'drm-uri2',
        },
      },
    ];
    const configuration =
        configurationFactory.createConfiguration(mediaItem, {});
    assertEquals('drm-uri0', configuration.drm.servers['com.widevine.alpha']);
    assertEquals(
        'drm-uri1', configuration.drm.servers['com.microsoft.playready']);
    assertEquals(2, Object.entries(configuration.drm.servers).length);
  }
});
