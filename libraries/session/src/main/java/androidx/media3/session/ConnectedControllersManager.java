/*
 * Copyright 2019 The Android Open Source Project
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
package androidx.media3.session;

import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.postOrRun;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.media3.common.Player;
import androidx.media3.session.MediaSession.ControllerInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Manager that holds {@link ControllerInfo} of connected {@link MediaController controllers}.
 *
 * <p>The generic {@code T} denotes a key of connected {@link MediaController controllers}, and it
 * can be either {@link android.os.IBinder} or {@link
 * androidx.media.MediaSessionManager.RemoteUserInfo}.
 *
 * <p>This class is thread-safe.
 */
/* package */ final class ConnectedControllersManager<T extends @NonNull Object> {

  /** An asynchronous controller command function. */
  public interface AsyncCommand {

    /**
     * Runs the asynchronous command.
     *
     * @return A {@link ListenableFuture} to listen for the command completion.
     */
    ListenableFuture<Void> run();
  }

  private final Object lock;

  @GuardedBy("lock")
  private final ArrayMap<T, ControllerInfo> controllerInfoMap = new ArrayMap<>();

  @GuardedBy("lock")
  private final ArrayMap<ControllerInfo, ConnectedControllerRecord<T>> controllerRecords =
      new ArrayMap<>();

  private final WeakReference<MediaSessionImpl> sessionImpl;

  public ConnectedControllersManager(MediaSessionImpl session) {
    // Initialize default values.
    lock = new Object();

    // Initialize members with params.
    sessionImpl = new WeakReference<>(session);
  }

  public void addController(
      T controllerKey,
      ControllerInfo controllerInfo,
      SessionCommands sessionCommands,
      Player.Commands playerCommands) {
    synchronized (lock) {
      @Nullable ControllerInfo savedInfo = getController(controllerKey);
      if (savedInfo == null) {
        controllerInfoMap.put(controllerKey, controllerInfo);
        controllerRecords.put(
            controllerInfo,
            new ConnectedControllerRecord<>(
                controllerKey, new SequencedFutureManager(), sessionCommands, playerCommands));
      } else {
        // already exist. Only update allowed commands.
        ConnectedControllerRecord<T> record = checkStateNotNull(controllerRecords.get(savedInfo));
        record.sessionCommands = sessionCommands;
        record.playerCommands = playerCommands;
      }
    }
  }

  public void updateCommandsFromSession(
      ControllerInfo controllerInfo,
      SessionCommands sessionCommands,
      Player.Commands playerCommands) {
    synchronized (lock) {
      @Nullable ConnectedControllerRecord<T> record = controllerRecords.get(controllerInfo);
      if (record != null) {
        record.sessionCommands = sessionCommands;
        record.playerCommands = playerCommands;
      }
    }
  }

  @Nullable
  public Player.Commands getAvailablePlayerCommands(ControllerInfo controllerInfo) {
    synchronized (lock) {
      @Nullable ConnectedControllerRecord<T> record = controllerRecords.get(controllerInfo);
      if (record != null) {
        return record.playerCommands;
      }
    }
    return null;
  }

  public void removeController(T controllerKey) {
    @Nullable ControllerInfo controllerInfo = getController(controllerKey);
    if (controllerInfo != null) {
      removeController(controllerInfo);
    }
  }

  public void removeController(ControllerInfo controllerInfo) {
    @Nullable /*Type*/ ConnectedControllerRecord<T> record;
    synchronized (lock) {
      record = controllerRecords.remove(controllerInfo);
      if (record == null) {
        return;
      }
      controllerInfoMap.remove(record.controllerKey);
    }

    record.sequencedFutureManager.release();
    @Nullable MediaSessionImpl sessionImpl = this.sessionImpl.get();
    if (sessionImpl == null || sessionImpl.isReleased()) {
      return;
    }
    postOrRun(
        sessionImpl.getApplicationHandler(),
        () -> {
          if (sessionImpl.isReleased()) {
            return;
          }
          sessionImpl.onDisconnectedOnHandler(controllerInfo);
        });
  }

  public ImmutableList<ControllerInfo> getConnectedControllers() {
    synchronized (lock) {
      return ImmutableList.copyOf(controllerInfoMap.values());
    }
  }

  public boolean isConnected(ControllerInfo controllerInfo) {
    synchronized (lock) {
      return controllerRecords.get(controllerInfo) != null;
    }
  }

  /**
   * Gets the sequenced future manager.
   *
   * @param controllerInfo controller info
   * @return sequenced future manager. Can be {@code null} if the controller was null or
   *     disconnected.
   */
  @Nullable
  public SequencedFutureManager getSequencedFutureManager(ControllerInfo controllerInfo) {
    @Nullable ConnectedControllerRecord<T> info;
    synchronized (lock) {
      info = controllerRecords.get(controllerInfo);
    }
    return info != null ? info.sequencedFutureManager : null;
  }

  /**
   * Gets the sequenced future manager.
   *
   * @param controllerKey key
   * @return sequenced future manager. Can be {@code null} if the controller was null or
   *     disconnected.
   */
  @Nullable
  public SequencedFutureManager getSequencedFutureManager(T controllerKey) {
    @Nullable ConnectedControllerRecord<T> info;
    synchronized (lock) {
      @Nullable ControllerInfo controllerInfo = getController(controllerKey);
      info = controllerInfo != null ? controllerRecords.get(controllerInfo) : null;
    }
    return info != null ? info.sequencedFutureManager : null;
  }

  public boolean isSessionCommandAvailable(ControllerInfo controllerInfo, SessionCommand command) {
    @Nullable ConnectedControllerRecord<T> info;
    synchronized (lock) {
      info = controllerRecords.get(controllerInfo);
    }
    return info != null && info.sessionCommands.contains(command);
  }

  public boolean isSessionCommandAvailable(
      ControllerInfo controllerInfo, @SessionCommand.CommandCode int commandCode) {
    @Nullable ConnectedControllerRecord<T> info;
    synchronized (lock) {
      info = controllerRecords.get(controllerInfo);
    }
    return info != null && info.sessionCommands.contains(commandCode);
  }

  public boolean isPlayerCommandAvailable(
      ControllerInfo controllerInfo, @Player.Command int commandCode) {
    @Nullable ConnectedControllerRecord<T> info;
    synchronized (lock) {
      info = controllerRecords.get(controllerInfo);
    }
    @Nullable MediaSessionImpl sessionImpl = this.sessionImpl.get();
    return info != null
        && info.playerCommands.contains(commandCode)
        && sessionImpl != null
        && sessionImpl.getPlayerWrapper().getAvailableCommands().contains(commandCode);
  }

  @Nullable
  public ControllerInfo getController(T controllerKey) {
    synchronized (lock) {
      return controllerInfoMap.get(controllerKey);
    }
  }

  public void addToCommandQueue(ControllerInfo controllerInfo, AsyncCommand asyncCommand) {
    synchronized (lock) {
      @Nullable ConnectedControllerRecord<T> info = controllerRecords.get(controllerInfo);
      if (info != null) {
        info.commandQueue.add(asyncCommand);
      }
    }
  }

  public void flushCommandQueue(ControllerInfo controllerInfo) {
    synchronized (lock) {
      @Nullable ConnectedControllerRecord<T> info = controllerRecords.get(controllerInfo);
      if (info == null || info.commandQueueIsFlushing || info.commandQueue.isEmpty()) {
        return;
      }
      info.commandQueueIsFlushing = true;
      flushCommandQueue(info);
    }
  }

  @GuardedBy("lock")
  private void flushCommandQueue(ConnectedControllerRecord<T> info) {
    @Nullable MediaSessionImpl sessionImpl = this.sessionImpl.get();
    if (sessionImpl == null) {
      return;
    }
    AtomicBoolean continueRunning = new AtomicBoolean(true);
    while (continueRunning.get()) {
      continueRunning.set(false);
      @Nullable AsyncCommand asyncCommand = info.commandQueue.poll();
      if (asyncCommand == null) {
        info.commandQueueIsFlushing = false;
        return;
      }
      AtomicBoolean commandExecuting = new AtomicBoolean(true);
      postOrRun(
          sessionImpl.getApplicationHandler(),
          sessionImpl.callWithControllerForCurrentRequestSet(
              getController(info.controllerKey),
              () ->
                  asyncCommand
                      .run()
                      .addListener(
                          () -> {
                            synchronized (lock) {
                              if (!commandExecuting.get()) {
                                flushCommandQueue(info);
                              } else {
                                continueRunning.set(true);
                              }
                            }
                          },
                          MoreExecutors.directExecutor())));
      commandExecuting.set(false);
    }
  }

  private static final class ConnectedControllerRecord<T> {

    public final T controllerKey;
    public final SequencedFutureManager sequencedFutureManager;
    public final Deque<AsyncCommand> commandQueue;

    public SessionCommands sessionCommands;
    public Player.Commands playerCommands;
    public boolean commandQueueIsFlushing;

    public ConnectedControllerRecord(
        T controllerKey,
        SequencedFutureManager sequencedFutureManager,
        SessionCommands sessionCommands,
        Player.Commands playerCommands) {
      this.controllerKey = controllerKey;
      this.sequencedFutureManager = sequencedFutureManager;
      this.sessionCommands = sessionCommands;
      this.playerCommands = playerCommands;
      this.commandQueue = new ArrayDeque<>();
    }
  }
}
