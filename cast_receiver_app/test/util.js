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
 * @fileoverview Description of this file.
 */

goog.module('exoplayer.cast.test.util');
goog.setTestOnly();

/**
 * The queue of sample media items
 *
 * @type {!Array<!MediaItem>}
 */
const queue = [
  {
    uuid: 'uuid0',
    media: {
      uri: 'http://example.com',
    },
    mimeType: 'video/*',
  },
  {
    uuid: 'uuid1',
    media: {
      uri: 'http://example1.com',
    },
    mimeType: 'application/dash+xml',
  },
  {
    uuid: 'uuid2',
    media: {
      uri: 'http://example2.com',
    },
    mimeType: 'video/*',
  },
  {
    uuid: 'uuid3',
    media: {
      uri: 'http://example3.com',
    },
    mimeType: 'application/dash+xml',
  },
  {
    uuid: 'uuid4',
    media: {
      uri: 'http://example4.com',
    },
    mimeType: 'video/*',
  },
  {
    uuid: 'uuid5',
    media: {
      uri: 'http://example5.com',
    },
    mimeType: 'application/dash+xml',
  },
];

/**
 * Asserts whether the map of uuids is complete and points to the correct
 * indices.
 *
 * @param {!Object<string, number>} uuidIndexMap The uuid to index map.
 * @param {!Array<!MediaItem>} queue The media item queue.
 */
const assertUuidIndexMap = (uuidIndexMap, queue) => {
  assertEquals(queue.length, Object.entries(uuidIndexMap).length);
  queue.forEach((mediaItem, index) => {
    assertEquals(uuidIndexMap[mediaItem.uuid], index);
  });
};

exports.queue = queue;
exports.assertUuidIndexMap = assertUuidIndexMap;
