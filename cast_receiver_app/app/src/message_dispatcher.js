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
goog.module('exoplayer.cast.MessageDispatcher');

const validation = goog.require('exoplayer.cast.validation');

/**
 * A callback function which is called by an action handler to indicate when
 * processing has completed.
 *
 * @typedef {function(?PlayerState): undefined}
 */
const Callback = undefined;

/**
 * Handles an action sent by a sender app.
 *
 * @typedef {function(!Object<string,?>, number, string, !Callback): undefined}
 */
const ActionHandler = undefined;

/**
 * Dispatches messages of a cast message bus to registered action handlers.
 *
 * <p>The dispatcher listens to events of a CastMessageBus for the namespace
 * passed to the constructor. The <code>data</code> property of the event is
 * parsed as a json document and delegated to a handler registered for the given
 * method.
 */
class MessageDispatcher {
  /**
   * @param {string} namespace The message namespace.
   * @param {!cast.framework.CastReceiverContext} castReceiverContext The cast
   *     receiver manager.
   */
  constructor(namespace, castReceiverContext) {
    /** @private @const {string} */
    this.namespace_ = namespace;
    /** @private @const {!cast.framework.CastReceiverContext} */
    this.castReceiverContext_ = castReceiverContext;
    /** @private @const {!Array<!Message>} */
    this.messageQueue_ = [];
    /** @private @const {!Object} */
    this.actions_ = {};
    /** @private @const {!Object<string, number>} */
    this.senderSequences_ = {};
    /** @private @const {function(string, *)} */
    this.jsonStringifyReplacer_ = (key, value) => {
      if (value === Infinity || value === null) {
        return undefined;
      }
      return value;
    };
    this.castReceiverContext_.addCustomMessageListener(
        this.namespace_, this.onMessage.bind(this));
  }

  /**
   * Registers a handler of a given action.
   *
   * @param {string} method The method name for which to register the handler.
   * @param {!Array<!Array<string>>} argDefs The name and type of each argument
   *     or an empty array if the method has no arguments.
   * @param {!ActionHandler} handler A function to process the action.
   */
  registerActionHandler(method, argDefs, handler) {
    this.actions_[method] = {
      method,
      argDefs,
      handler,
    };
  }

  /**
   * Unregisters the handler of the given action.
   *
   * @param {string} action The action to unregister.
   */
  unregisterActionHandler(action) {
    delete this.actions_[action];
  }

  /**
   * Callback to receive messages sent by sender apps.
   *
   * @param {!cast.framework.system.Event} event The event received from the
   *     sender app.
   */
  onMessage(event) {
    console.log('message arrived from sender', this.namespace_, event);
    const message = /** @type {!ExoCastMessage} */ (event.data);
    const action = this.actions_[message.method];
    if (action) {
      const args = message.args;
      for (let i = 0; i < action.argDefs.length; i++) {
        if (!validation.validateProperty(
                args, action.argDefs[i][0], action.argDefs[i][1])) {
          console.warn('invalid method call', message);
          return;
        }
      }
      this.messageQueue_.push({
        senderId: event.senderId,
        message: message,
        handler: action.handler
      });
      if (this.messageQueue_.length === 1) {
        this.executeNext();
      } else {
        // Do nothing. An action is executing asynchronously and will call
        // executeNext when finished.
      }
    } else {
      console.warn('handler of method not found', message);
    }
  }

  /**
   * Executes the next message in the queue.
   */
  executeNext() {
    if (this.messageQueue_.length === 0) {
      return;
    }
    const head = this.messageQueue_[0];
    const message = head.message;
    const senderSequence = message.sequenceNumber;
    this.senderSequences_[head.senderId] = senderSequence;
    try {
      head.handler(message.args, senderSequence, head.senderId, (response) => {
        if (response) {
          this.send(head.senderId, response);
        }
        this.shiftPendingMessage_(head);
      });
    } catch (e) {
      this.shiftPendingMessage_(head);
      console.error('error while executing method : ' + message.method, e);
    }
  }

  /**
   * Broadcasts the sender state to all sender apps registered for the
   * given message namespace.
   *
   * @param {!PlayerState} playerState The player state to be sent.
   */
  broadcast(playerState) {
    this.castReceiverContext_.getSenders().forEach((sender) => {
      this.send(sender.id, playerState);
    });
    delete playerState.sequenceNumber;
  }

  /**
   * Sends the PlayerState to the given sender.
   *
   * @param {string} senderId The id of the sender.
   * @param {!PlayerState} playerState The message to send.
   */
  send(senderId, playerState) {
    playerState.sequenceNumber = this.senderSequences_[senderId] || -1;
    this.castReceiverContext_.sendCustomMessage(
        this.namespace_, senderId,
        // TODO(bachinger) Find a better solution.
        JSON.parse(JSON.stringify(playerState, this.jsonStringifyReplacer_)));
  }

  /**
   * Notifies the message dispatcher that a given sender has disconnected from
   * the receiver.
   *
   * @param {string} senderId The id of the sender.
   */
  notifySenderDisconnected(senderId) {
    delete this.senderSequences_[senderId];
  }

  /**
   * Shifts the pending message and executes the next if any.
   *
   * @private
   * @param {!Message} pendingMessage The pending message.
   */
  shiftPendingMessage_(pendingMessage) {
    if (pendingMessage === this.messageQueue_[0]) {
      this.messageQueue_.shift();
      this.executeNext();
    }
  }
}

/**
 * An item in the message queue.
 *
 * @record
 */
function Message() {}

/**
 * The sender id.
 *
 * @type {string}
 */
Message.prototype.senderId;

/**
 * The ExoCastMessage sent by the sender app.
 *
 * @type {!ExoCastMessage}
 */
Message.prototype.message;

/**
 * The handler function handling the message.
 *
 * @type {!ActionHandler}
 */
Message.prototype.handler;

exports = MessageDispatcher;
