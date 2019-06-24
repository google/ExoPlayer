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

/**
 * @fileoverview Externs of the Shaka configuration.
 *
 * @externs
 */

/**
 * The drm configuration for the Shaka player.
 *
 * @record
 */
class DrmConfiguration {
  constructor() {
    /**
     * A map of license servers with the UUID of the drm system as the key and the
     * license uri as the value.
     *
     * @type {!Object<string, string>}
     */
    this.servers;
  }
}

/**
 * The configuration of the Shaka player.
 *
 * @record
 */
class PlayerConfiguration {
  constructor() {
    /**
     * The preferred audio language.
     *
     * @type {string}
     */
    this.preferredAudioLanguage;

    /**
     * The preferred text language.
     *
     * @type {string}
     */
    this.preferredTextLanguage;

    /**
     * The drm configuration.
     *
     * @type {?DrmConfiguration}
     */
    this.drm;
  }
}
