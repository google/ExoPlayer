/**
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
 *
 * @fileoverview Unit tests for the message dispatcher.
 */

goog.module('exoplayer.cast.test.messagedispatcher');
goog.setTestOnly();

const MessageDispatcher = goog.require('exoplayer.cast.MessageDispatcher');
const mocks = goog.require('exoplayer.cast.test.mocks');
const testSuite = goog.require('goog.testing.testSuite');

let contextMock;
let messageDispatcher;

testSuite({
  setUp() {
    mocks.setUp();
    contextMock = mocks.createCastReceiverContextFake();
    messageDispatcher = new MessageDispatcher(
        'urn:x-cast:com.google.exoplayer.cast', contextMock);
  },

  /** Test marshalling Infinity */
  testStringifyInfinity() {
    const senderId = 'sender0';
    const name = 'Federico Vespucci';
    messageDispatcher.send(senderId, {name: name, duration: Infinity});

    const msg = mocks.state().outputMessages[senderId][0];
    assertUndefined(msg.duration);
    assertFalse(msg.hasOwnProperty('duration'));
    assertEquals(name, msg.name);
    assertTrue(msg.hasOwnProperty('name'));
  }
});
