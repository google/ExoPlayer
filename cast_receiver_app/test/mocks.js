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
 * @fileoverview Mocks for testing cast components.
 */

goog.module('exoplayer.cast.test.mocks');
goog.setTestOnly();

const NetworkingEngine = goog.require('shaka.net.NetworkingEngine');

let mockState;
let manifest;

/**
 * Initializes the state of the mocks. Needs to be called in the setUp method of
 * the unit test.
 */
const setUp = function() {
  mockState = {
    outputMessages: {},
    listeners: {},
    loadedUri: null,
    preferredTextLanguage: '',
    preferredAudioLanguage: '',
    configuration: null,
    responseFilter: null,
    isSilent: false,
    customMessageListener: undefined,
    mediaElementState: {
      removedAttributes: [],
    },
    manifestState: {
      isLive: false,
      windowDuration: 20,
      startTime: 0,
      delay: 10,
    },
    getManifest: () => manifest,
    setManifest: (m) => {
      manifest = m;
    },
    shakaError: {
      severity: /** CRITICAL */ 2,
      code: /** not 7000 (LOAD_INTERUPTED) */ 3,
      category: /** any */ 1,
    },
    simulateLoad: simulateLoadSuccess,
    /** @type {function(boolean)} */
    setShakaThrowsOnLoad: (doThrow) => {
      mockState.simulateLoad = doThrow ? throwShakaError : simulateLoadSuccess;
    },
    simulateUnload: simulateUnloadSuccess,
    /** @type {function(boolean)} */
    setShakaThrowsOnUnload: (doThrow) => {
      mockState.simulateUnload =
          doThrow ? throwShakaError : simulateUnloadSuccess;
    },
    onSenderConnected: undefined,
    onSenderDisconnected: undefined,
  };
  manifest = {
    periods: [{startTime: mockState.manifestState.startTime}],
    presentationTimeline: {
      getDuration: () => mockState.manifestState.windowDuration,
      isLive: () => mockState.manifestState.isLive,
      getSegmentAvailabilityStart: () => 0,
      getSegmentAvailabilityEnd: () => mockState.manifestState.windowDuration,
      getSeekRangeStart: () => 0,
      getSeekRangeEnd: () => mockState.manifestState.windowDuration -
          mockState.manifestState.delay,
    },
  };
};

/**
 * Simulates a successful `shakaPlayer.load` call.
 *
 * @param {string} uri The uri to load.
 */
const simulateLoadSuccess = (uri) => {
  mockState.loadedUri = uri;
  notifyListeners('streaming');
};

/** Simulates a successful `shakaPlayer.unload` call. */
const simulateUnloadSuccess = () => {
  mockState.loadedUri = undefined;
  notifyListeners('unloading');
};

/** @throws {!ShakaError} Thrown in any case. */
const throwShakaError = () => {
  throw mockState.shakaError;
};


/**
 * Adds a fake event listener.
 *
 * @param {string} type The type of the listener.
 * @param {function(!Object)} listener The callback listener.
 */
const addEventListener = function(type, listener) {
  mockState.listeners[type] = mockState.listeners[type] || [];
  mockState.listeners[type].push(listener);
};

/**
 * Notifies the fake listeners of the given type.
 *
 * @param {string} type The type of the listener to notify.
 */
const notifyListeners = function(type) {
  if (mockState.isSilent || !mockState.listeners[type]) {
    return;
  }
  for (let i = 0; i < mockState.listeners[type].length; i++) {
    mockState.listeners[type][i]({
      type: type
    });
  }
};

/**
 * Creates an observable for which listeners can be added.
 *
 * @return {!Object} An observable object.
 */
const createObservable = () => {
  return {
    addEventListener: (type, listener) => {
      addEventListener(type, listener);
    },
  };
};

/**
 * Creates a fake for the shaka player.
 *
 * @return {!shaka.Player} A shaka player mock object.
 */
const createShakaFake = () => {
  const shakaFake = /** @type {!shaka.Player} */(createObservable());
  const mediaElement = createMediaElementFake();
  /**
   * @return {!HTMLMediaElement} A media element.
   */
  shakaFake.getMediaElement = () => mediaElement;
  shakaFake.getAudioLanguages = () => [];
  shakaFake.getVariantTracks = () => [];
  shakaFake.configure = (configuration) => {
    mockState.configuration = configuration;
    return true;
  };
  shakaFake.selectTextLanguage = (language) => {
    mockState.preferredTextLanguage = language;
  };
  shakaFake.selectAudioLanguage = (language) => {
    mockState.preferredAudioLanguage = language;
  };
  shakaFake.getManifest = () => manifest;
  shakaFake.unload = async () => mockState.simulateUnload();
  shakaFake.load = async (uri) => mockState.simulateLoad(uri);
  shakaFake.getNetworkingEngine = () => {
    return /** @type {!NetworkingEngine} */ ({
      registerResponseFilter: (responseFilter) => {
        mockState.responseFilter = responseFilter;
      },
      unregisterResponseFilter: (responseFilter) => {
        if (mockState.responseFilter !== responseFilter) {
          throw new Error('unregistering invalid response filter');
        } else {
          mockState.responseFilter = null;
        }
      },
    });
  };
  return shakaFake;
};

/**
 * Creates a fake for a media element.
 *
 * @return {!HTMLMediaElement} A media element fake.
 */
const createMediaElementFake = () => {
  const mediaElementFake = /** @type {!HTMLMediaElement} */(createObservable());
  mediaElementFake.load = () => {
    // Do nothing.
  };
  mediaElementFake.play = () => {
    mediaElementFake.paused = false;
    notifyListeners('playing');
    return Promise.resolve();
  };
  mediaElementFake.pause = () => {
    mediaElementFake.paused = true;
    notifyListeners('pause');
  };
  mediaElementFake.seekable = /** @type {!TimeRanges} */({
    length: 1,
    start: (index) => mockState.manifestState.startTime,
    end: (index) => mockState.manifestState.windowDuration,
  });
  mediaElementFake.removeAttribute = (name) => {
    mockState.mediaElementState.removedAttributes.push(name);
    if (name === 'src') {
      mockState.loadedUri = null;
    }
  };
  mediaElementFake.hasAttribute = (name) => {
    return name === 'src' && !!mockState.loadedUri;
  };
  mediaElementFake.buffered = /** @type {!TimeRanges} */ ({
    length: 0,
    start: (index) => null,
    end: (index) => null,
  });
  mediaElementFake.paused = true;
  return mediaElementFake;
};

/**
 * Creates a cast receiver manager fake.
 *
 * @return {!Object} A cast receiver manager fake.
 */
const createCastReceiverContextFake = () => {
  return {
    addCustomMessageListener: (namespace, listener) => {
      mockState.customMessageListener = listener;
    },
    sendCustomMessage: (namespace, senderId, message) => {
      mockState.outputMessages[senderId] =
          mockState.outputMessages[senderId] || [];
      mockState.outputMessages[senderId].push(message);
    },
    addEventListener: (eventName, listener) => {
      switch (eventName) {
        case 'sender_connected':
          mockState.onSenderConnected = listener;
          break;
        case 'sender_disconnected':
          mockState.onSenderDisconnected = listener;
          break;
      }
    },
    getSenders: () => [{id: 'sender0'}],
    start: () => {},
  };
};

/**
 * Returns the state of the mocks.
 *
 * @return {?Object}
 */
const state = () => mockState;

exports.createCastReceiverContextFake = createCastReceiverContextFake;
exports.createShakaFake = createShakaFake;
exports.notifyListeners = notifyListeners;
exports.setUp = setUp;
exports.state = state;
