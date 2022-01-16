/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.audio;

import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Exposes the android.media.Spatializer API via reflection. This is so that we can use the
 * Spatializer while the compile SDK target is set to 31.
 */
@RequiresApi(31)
/* package */ final class SpatializerDelegate {
  /** Level of support for audio spatialization. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
    SPATIALIZER_IMMERSIVE_LEVEL_MULTICHANNEL,
    SPATIALIZER_IMMERSIVE_LEVEL_NONE,
    SPATIALIZER_IMMERSIVE_LEVEL_OTHER
  })
  @interface ImmersiveAudioLevel {}

  /** See Spatializer#SPATIALIZER_IMMERSIVE_LEVEL_MULTICHANNEL */
  public static final int SPATIALIZER_IMMERSIVE_LEVEL_MULTICHANNEL = 1;
  /** See Spatializer#SPATIALIZER_IMMERSIVE_LEVEL_NONE */
  public static final int SPATIALIZER_IMMERSIVE_LEVEL_NONE = 0;
  /** See Spatializer#SPATIALIZER_IMMERSIVE_LEVEL_OTHER */
  public static final int SPATIALIZER_IMMERSIVE_LEVEL_OTHER = -1;

  /** Wrapper for Spatializer.OnSpatializerStateChangedListener */
  public interface Listener {
    /** See Spatializer.OnSpatializerStateChangedListener.onSpatializerEnabledChanged */
    void onSpatializerEnabledChanged(SpatializerDelegate spatializer, boolean enabled);

    /** See Spatializer.OnSpatializerStateChangedListener.onSpatializerAvailableChanged */
    void onSpatializerAvailableChanged(SpatializerDelegate spatializer, boolean available);
  }

  private final Object spatializer;
  private final Class<?> spatializerClass;
  private final Class<?> spatializerListenerClass;
  private final Method isEnabled;
  private final Method isAvailable;
  private final Method getImmersiveAudioLevel;
  private final Method canBeSpatialized;
  private final Method addListener;
  private final Method removeListener;
  private final Map<Listener, Object> listeners;

  /** Creates an instance. */
  public SpatializerDelegate(Context context)
      throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
          IllegalAccessException {
    Method getSpatializerMethod = AudioManager.class.getMethod("getSpatializer");
    AudioManager manager =
        Assertions.checkNotNull(
            (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE));
    spatializer = checkStateNotNull(getSpatializerMethod.invoke(manager));
    spatializerClass = Class.forName("android.media.Spatializer");
    spatializerListenerClass =
        Class.forName("android.media.Spatializer$OnSpatializerStateChangedListener");
    isEnabled = spatializerClass.getMethod("isEnabled");
    isAvailable = spatializerClass.getMethod("isAvailable");
    getImmersiveAudioLevel = spatializerClass.getMethod("getImmersiveAudioLevel");
    canBeSpatialized =
        spatializerClass.getMethod(
            "canBeSpatialized", android.media.AudioAttributes.class, AudioFormat.class);
    addListener =
        spatializerClass.getMethod(
            "addOnSpatializerStateChangedListener", Executor.class, spatializerListenerClass);
    removeListener =
        spatializerClass.getMethod(
            "removeOnSpatializerStateChangedListener", spatializerListenerClass);
    listeners = new HashMap<>();
  }

  /** Delegates to Spatializer.isEnabled() */
  public boolean isEnabled() {
    try {
      return (boolean) Util.castNonNull(isEnabled.invoke(spatializer));
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Delegates to Spatializer.isAvailable() */
  public boolean isAvailable() {
    try {
      return (boolean) Util.castNonNull(isAvailable.invoke(spatializer));
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Delegates to Spatializer.getImmersiveAudioLevel() */
  @ImmersiveAudioLevel
  public int getImmersiveAudioLevel() {
    try {
      return (int) Util.castNonNull(getImmersiveAudioLevel.invoke(spatializer));
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Delegates to Spatializer.canBeSpatialized() */
  public boolean canBeSpatialized(AudioAttributes attributes, AudioFormat format) {
    try {
      return (boolean) Util.castNonNull(canBeSpatialized.invoke(spatializer, attributes, format));
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Delegates to Spatializer.addOnSpatializerStateChangedListener() */
  public void addOnSpatializerStateChangedListener(Executor executor, Listener listener) {
    if (listeners.containsKey(listener)) {
      return;
    }
    Object listenerProxy = createSpatializerListenerProxy(listener);
    try {
      addListener.invoke(spatializer, executor, listenerProxy);
      listeners.put(listener, listenerProxy);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Delegates to Spatializer.removeOnSpatializerStateChangedListener() */
  public void removeOnSpatializerStateChangedListener(Listener listener) {
    @Nullable Object proxy = listeners.get(listener);
    if (proxy == null) {
      return;
    }
    try {
      removeListener.invoke(spatializer, proxy);
      listeners.remove(listener);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  private Object createSpatializerListenerProxy(Listener listener) {
    return Proxy.newProxyInstance(
        spatializerListenerClass.getClassLoader(),
        new Class<?>[] {spatializerListenerClass},
        new ProxySpatializerListener(this, listener));
  }

  /** Proxy-based implementation of Spatializer.OnSpatializerStateChangedListener. */
  private static final class ProxySpatializerListener implements InvocationHandler {
    private final SpatializerDelegate spatializerDelegate;
    private final Listener listener;

    private ProxySpatializerListener(SpatializerDelegate spatializerDelegate, Listener listener) {
      this.spatializerDelegate = spatializerDelegate;
      this.listener = listener;
    }

    @Override
    public Object invoke(Object o, Method method, Object[] objects) {
      String methodName = method.getName();
      Class<?>[] parameterTypes = method.getParameterTypes();
      if (methodName.equals("onSpatializerAvailableChanged")
          && parameterTypes.length == 2
          && spatializerDelegate.spatializerClass.isAssignableFrom(parameterTypes[0])
          && parameterTypes[1].equals(Boolean.TYPE)) {
        listener.onSpatializerAvailableChanged(spatializerDelegate, (boolean) objects[1]);
      } else if (methodName.equals("onSpatializerEnabledChanged")
          && parameterTypes.length == 2
          && spatializerDelegate.spatializerClass.isAssignableFrom(parameterTypes[0])
          && parameterTypes[1].equals(Boolean.TYPE)) {
        listener.onSpatializerEnabledChanged(spatializerDelegate, (boolean) objects[1]);
      }
      return this;
    }
  }
}
