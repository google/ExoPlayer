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

goog.module('exoplayer.cast.ConfigurationFactory');

const {DRM_SYSTEMS} = goog.require('exoplayer.cast.constants');

const EMPTY_DRM_CONFIGURATION =
    /** @type {!DrmConfiguration} */ (Object.freeze({
      servers: {},
    }));

/**
 * Creates the configuration of the Shaka player.
 */
class ConfigurationFactory {
  /**
   * Creates the Shaka player configuration.
   *
   * @param {!MediaItem} mediaItem The media item for which to create the
   *     configuration.
   * @param {!TrackSelectionParameters} trackSelectionParameters The track
   *     selection parameters.
   * @return {!PlayerConfiguration} The shaka player configuration.
   */
  createConfiguration(mediaItem, trackSelectionParameters) {
    const configuration = /** @type {!PlayerConfiguration} */ ({});
    this.mapLanguageConfiguration(trackSelectionParameters, configuration);
    this.mapDrmConfiguration_(mediaItem, configuration);
    return configuration;
  }

  /**
   * Maps the preferred audio and text language from the track selection
   * parameters to the configuration.
   *
   * @param {!TrackSelectionParameters} trackSelectionParameters The selection
   *     parameters.
   * @param {!PlayerConfiguration} playerConfiguration The player configuration.
   */
  mapLanguageConfiguration(trackSelectionParameters, playerConfiguration) {
    playerConfiguration.preferredAudioLanguage =
        trackSelectionParameters.preferredAudioLanguage || '';
    playerConfiguration.preferredTextLanguage =
        trackSelectionParameters.preferredTextLanguage || '';
  }

  /**
   * Maps the drm configuration from the media item to the configuration. If no
   * drm is specified for the given media item, null is assigned.
   *
   * @private
   * @param {!MediaItem} mediaItem The media item.
   * @param {!PlayerConfiguration} playerConfiguration The player configuration.
   */
  mapDrmConfiguration_(mediaItem, playerConfiguration) {
    if (!mediaItem.drmSchemes) {
      playerConfiguration.drm = EMPTY_DRM_CONFIGURATION;
      return;
    }
    const drmConfiguration = /** @type {!DrmConfiguration} */({
      servers: {},
    });
    let hasDrmServer = false;
    mediaItem.drmSchemes.forEach((scheme) => {
      const drmSystem = DRM_SYSTEMS[scheme.uuid];
      if (drmSystem && scheme.licenseServer && scheme.licenseServer.uri) {
        hasDrmServer = true;
        drmConfiguration.servers[drmSystem] = scheme.licenseServer.uri;
      }
    });
    playerConfiguration.drm =
        hasDrmServer ? drmConfiguration : EMPTY_DRM_CONFIGURATION;
  }
}

exports = ConfigurationFactory;
