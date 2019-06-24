/*
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
 */

goog.module('exoplayer.cast.util');

/**
 * Indicates whether the logging is turned on.
 */
const enableLogging = true;

/**
 * Logs to the console if logging enabled.
 *
 * @param {!Array<*>} statements The log statements to be logged.
 */
const log = function(statements) {
  if (enableLogging) {
    console.log.apply(console, statements);
  }
};

/**
 * A comparator function for uuids.
 *
 * @typedef {function(string,string):number}
 */
let UuidComparator;

/**
 * Creates a comparator function which sorts uuids in descending order by the
 * corresponding index of the given map.
 *
 * @param {!Object<string, number>} uuidIndexMap The map with uuids as the key
 *     and the window index as the value.
 * @return {!UuidComparator} The comparator for sorting.
 */
const createUuidComparator = function(uuidIndexMap) {
  return (a, b) => {
    const indexA = uuidIndexMap[a] || -1;
    const indexB = uuidIndexMap[b] || -1;
    return indexB - indexA;
  };
};

exports = {
  log,
  createUuidComparator,
  UuidComparator,
};
