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
 * @fileoverview Unit tests for queue manipulations.
 */

goog.module('exoplayer.cast.test.validation');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const validation = goog.require('exoplayer.cast.validation');

/**
 * Creates a sample drm media for validation tests.
 *
 * @return {!Object} A dummy media item with a drm scheme.
 */
const createDrmMedia = function() {
  return {
    uuid: 'string',
    media: {
      uri: 'string',
    },
    mimeType: 'application/dash+xml',
    drmSchemes: [
      {
        uuid: 'string',
        licenseServer: {
          uri: 'string',
          requestHeaders: {
            'string': 'string',
          },
        },
      },
    ],
  };
};

testSuite({

  /** Tests minimal valid media item. */
  testValidateMediaItem_minimal() {
    const mediaItem = {
      uuid: 'string',
      media: {
        uri: 'string',
      },
      mimeType: 'application/dash+xml',
    };
    assertTrue(validation.validateMediaItem(mediaItem));

    const uuid = mediaItem.uuid;
    delete mediaItem.uuid;
    assertFalse(validation.validateMediaItem(mediaItem));
    mediaItem.uuid = uuid;
    assertTrue(validation.validateMediaItem(mediaItem));

    const mimeType = mediaItem.mimeType;
    delete mediaItem.mimeType;
    assertFalse(validation.validateMediaItem(mediaItem));
    mediaItem.mimeType = mimeType;
    assertTrue(validation.validateMediaItem(mediaItem));

    const media = mediaItem.media;
    delete mediaItem.media;
    assertFalse(validation.validateMediaItem(mediaItem));
    mediaItem.media = media;
    assertTrue(validation.validateMediaItem(mediaItem));

    const uri = mediaItem.media.uri;
    delete mediaItem.media.uri;
    assertFalse(validation.validateMediaItem(mediaItem));
    mediaItem.media.uri = uri;
    assertTrue(validation.validateMediaItem(mediaItem));
  },

  /** Tests media item drm property validation. */
  testValidateMediaItem_drmSchemes() {
    const mediaItem = createDrmMedia();
    assertTrue(validation.validateMediaItem(mediaItem));

    const uuid = mediaItem.drmSchemes[0].uuid;
    delete mediaItem.drmSchemes[0].uuid;
    assertFalse(validation.validateMediaItem(mediaItem));
    mediaItem.drmSchemes[0].uuid = uuid;
    assertTrue(validation.validateMediaItem(mediaItem));

    const licenseServer = mediaItem.drmSchemes[0].licenseServer;
    delete mediaItem.drmSchemes[0].licenseServer;
    assertFalse(validation.validateMediaItem(mediaItem));
    mediaItem.drmSchemes[0].licenseServer = licenseServer;
    assertTrue(validation.validateMediaItem(mediaItem));

    const uri = mediaItem.drmSchemes[0].licenseServer.uri;
    delete mediaItem.drmSchemes[0].licenseServer.uri;
    assertFalse(validation.validateMediaItem(mediaItem));
    mediaItem.drmSchemes[0].licenseServer.uri = uri;
    assertTrue(validation.validateMediaItem(mediaItem));
  },

  /** Tests validation of startPositionUs and endPositionUs. */
  testValidateMediaItem_endAndStartPositionUs() {
    const mediaItem = createDrmMedia();

    mediaItem.endPositionUs = 0;
    mediaItem.startPositionUs = 120 * 1000;
    assertTrue(validation.validateMediaItem(mediaItem));

    mediaItem.endPositionUs = '0';
    assertFalse(validation.validateMediaItem(mediaItem));

    mediaItem.endPositionUs = 0;
    assertTrue(validation.validateMediaItem(mediaItem));

    mediaItem.startPositionUs = true;
    assertFalse(validation.validateMediaItem(mediaItem));
  },

  /** Tests validation of the title. */
  testValidateMediaItem_title() {
    const mediaItem = createDrmMedia();

    mediaItem.title = '0';
    assertTrue(validation.validateMediaItem(mediaItem));

    mediaItem.title = 0;
    assertFalse(validation.validateMediaItem(mediaItem));
  },

  /** Tests validation of the description. */
  testValidateMediaItem_description() {
    const mediaItem = createDrmMedia();

    mediaItem.description = '0';
    assertTrue(validation.validateMediaItem(mediaItem));

    mediaItem.description = 0;
    assertFalse(validation.validateMediaItem(mediaItem));
  },

  /** Tests validating property of type string. */
  testValidateProperty_string() {
    const obj = {
      field: 'string',
    };
    assertTrue(validation.validateProperty(obj, 'field', 'string'));
    assertTrue(validation.validateProperty(obj, 'field', '?string'));

    obj.field = 0;
    assertFalse(validation.validateProperty(obj, 'field', 'string'));
    assertFalse(validation.validateProperty(obj, 'field', '?string'));

    obj.field = true;
    assertFalse(validation.validateProperty(obj, 'field', 'string'));
    assertFalse(validation.validateProperty(obj, 'field', '?string'));

    obj.field = {};
    assertFalse(validation.validateProperty(obj, 'field', 'string'));
    assertFalse(validation.validateProperty(obj, 'field', '?string'));

    delete obj.field;
    assertFalse(validation.validateProperty(obj, 'field', 'string'));
    assertTrue(validation.validateProperty(obj, 'field', '?string'));
  },

  /** Tests validating property of type number. */
  testValidateProperty_number() {
    const obj = {
      field: 0,
    };
    assertTrue(validation.validateProperty(obj, 'field', 'number'));
    assertTrue(validation.validateProperty(obj, 'field', '?number'));

    obj.field = '0';
    assertFalse(validation.validateProperty(obj, 'field', 'number'));
    assertFalse(validation.validateProperty(obj, 'field', '?number'));

    obj.field = true;
    assertFalse(validation.validateProperty(obj, 'field', 'number'));
    assertFalse(validation.validateProperty(obj, 'field', '?number'));

    obj.field = {};
    assertFalse(validation.validateProperty(obj, 'field', 'number'));
    assertFalse(validation.validateProperty(obj, 'field', '?number'));

    delete obj.field;
    assertFalse(validation.validateProperty(obj, 'field', 'number'));
    assertTrue(validation.validateProperty(obj, 'field', '?number'));
  },

  /** Tests validating property of type boolean. */
  testValidateProperty_boolean() {
    const obj = {
      field: true,
    };
    assertTrue(validation.validateProperty(obj, 'field', 'boolean'));
    assertTrue(validation.validateProperty(obj, 'field', '?boolean'));

    obj.field = '0';
    assertFalse(validation.validateProperty(obj, 'field', 'boolean'));
    assertFalse(validation.validateProperty(obj, 'field', '?boolean'));

    obj.field = 1000;
    assertFalse(validation.validateProperty(obj, 'field', 'boolean'));
    assertFalse(validation.validateProperty(obj, 'field', '?boolean'));

    obj.field = [true];
    assertFalse(validation.validateProperty(obj, 'field', 'boolean'));
    assertFalse(validation.validateProperty(obj, 'field', '?boolean'));

    delete obj.field;
    assertFalse(validation.validateProperty(obj, 'field', 'boolean'));
    assertTrue(validation.validateProperty(obj, 'field', '?boolean'));
  },

  /** Tests validating property of type array. */
  testValidateProperty_array() {
    const obj = {
      field: [],
    };
    assertTrue(validation.validateProperty(obj, 'field', 'Array'));
    assertTrue(validation.validateProperty(obj, 'field', '?Array'));

    obj.field = '0';
    assertFalse(validation.validateProperty(obj, 'field', 'Array'));
    assertFalse(validation.validateProperty(obj, 'field', '?Array'));

    obj.field = 1000;
    assertFalse(validation.validateProperty(obj, 'field', 'Array'));
    assertFalse(validation.validateProperty(obj, 'field', '?Array'));

    obj.field = true;
    assertFalse(validation.validateProperty(obj, 'field', 'Array'));
    assertFalse(validation.validateProperty(obj, 'field', '?Array'));

    delete obj.field;
    assertFalse(validation.validateProperty(obj, 'field', 'Array'));
    assertTrue(validation.validateProperty(obj, 'field', '?Array'));
  },

  /** Tests validating properties of type RepeatMode */
  testValidateProperty_repeatMode() {
    const obj = {
      off: 'OFF',
      one: 'ONE',
      all: 'ALL',
      invalid: 'invalid',
    };
    assertTrue(validation.validateProperty(obj, 'off', 'RepeatMode'));
    assertTrue(validation.validateProperty(obj, 'one', 'RepeatMode'));
    assertTrue(validation.validateProperty(obj, 'all', 'RepeatMode'));
    assertFalse(validation.validateProperty(obj, 'invalid', 'RepeatMode'));
  },
});
