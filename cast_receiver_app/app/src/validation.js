/**
 * Copyright (C) 2018 The Android Open Source Project
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
 * @fileoverview A validator for messages received from sender apps.
 */

goog.module('exoplayer.cast.validation');

const {getPlaybackType, PlaybackType, RepeatMode} = goog.require('exoplayer.cast.constants');

/**
 * Media item fields.
 *
 * @enum {string}
 */
const MediaItemField = {
  UUID: 'uuid',
  MEDIA: 'media',
  MIME_TYPE: 'mimeType',
  DRM_SCHEMES: 'drmSchemes',
  TITLE: 'title',
  DESCRIPTION: 'description',
  START_POSITION_US: 'startPositionUs',
  END_POSITION_US: 'endPositionUs',
};

/**
 * DrmScheme fields.
 *
 * @enum {string}
 */
const DrmSchemeField = {
  UUID: 'uuid',
  LICENSE_SERVER_URI: 'licenseServer',
};

/**
 * UriBundle fields.
 *
 * @enum {string}
 */
const UriBundleField = {
  URI: 'uri',
  REQUEST_HEADERS: 'requestHeaders',
};

/**
 * Validates an array of media items.
 *
 * @param {!Array<!MediaItem>} mediaItems An array of media items.
 * @return {boolean} true if all media items are valid, otherwise false is
 *     returned.
 */
const validateMediaItems = function (mediaItems) {
  for (let i = 0; i < mediaItems.length; i++) {
    if (!validateMediaItem(mediaItems[i])) {
      return false;
    }
  }
  return true;
};

/**
 * Validates a queue item sent to the receiver by a sender app.
 *
 * @param {!MediaItem} mediaItem The media item.
 * @return {boolean} true if the media item is valid, false otherwise.
 */
const validateMediaItem = function (mediaItem) {
  // validate minimal properties
  if (!validateProperty(mediaItem, MediaItemField.UUID, 'string')) {
    console.log('missing mandatory uuid', mediaItem.uuid);
    return false;
  }
  if (!validateProperty(mediaItem.media, UriBundleField.URI, 'string')) {
    console.log('missing mandatory', mediaItem.media ? 'uri' : 'media');
    return false;
  }
  const mimeType = mediaItem.mimeType;
  if (!mimeType || getPlaybackType(mimeType) === PlaybackType.UNKNOWN) {
    console.log('unsupported mime type:', mimeType);
    return false;
  }
  // validate optional properties
  if (goog.isArray(mediaItem.drmSchemes)) {
    for (let i = 0; i < mediaItem.drmSchemes.length; i++) {
      let drmScheme = mediaItem.drmSchemes[i];
      if (!validateProperty(drmScheme, DrmSchemeField.UUID, 'string') ||
          !validateProperty(
              drmScheme.licenseServer, UriBundleField.URI, 'string')) {
        console.log('invalid drm scheme', drmScheme);
        return false;
      }
    }
  }
  if (!validateProperty(mediaItem, MediaItemField.START_POSITION_US, '?number')
      || !validateProperty(mediaItem, MediaItemField.END_POSITION_US, '?number')
      || !validateProperty(mediaItem, MediaItemField.TITLE, '?string')
      || !validateProperty(mediaItem, MediaItemField.DESCRIPTION, '?string')) {
    console.log('invalid type of one of startPositionUs, endPositionUs, title'
        + ' or description', mediaItem);
    return false;
  }
  return true;
};

/**
 * Validates the existence and type of a property.
 *
 * <p>Supported types: number, string, boolean, Array.
 * <p>Prefix the type with a ? to indicate that the property is optional.
 *
 * @param {?Object|?MediaItem|?UriBundle} obj The object to validate.
 * @param {string} propertyName The name of the property.
 * @param {string} type The type of the property.
 * @return {boolean} True if valid, false otherwise.
 */
const validateProperty = function (obj, propertyName, type) {
  if (typeof obj === 'undefined' || obj === null) {
    return false;
  }
  const isOptional = type.startsWith('?');
  const value = obj[propertyName];
  if (isOptional && typeof value === 'undefined') {
    return true;
  }
  type = isOptional ? type.substring(1) : type;
  switch (type) {
    case 'string':
      return typeof value === 'string' || value instanceof String;
    case 'number':
      return typeof value === 'number' && isFinite(value);
    case 'Array':
      return typeof value !== 'undefined' && typeof value === 'object'
          && value.constructor === Array;
    case 'boolean':
      return typeof value === 'boolean';
    case 'RepeatMode':
      return value === RepeatMode.OFF || value === RepeatMode.ONE ||
          value === RepeatMode.ALL;
    default:
      console.warn('Unsupported type when validating an object property. ' +
          'Supported types are string, number, boolean and Array.', type);
      return false;
  }
};

exports.validateMediaItem = validateMediaItem;
exports.validateMediaItems = validateMediaItems;
exports.validateProperty = validateProperty;

