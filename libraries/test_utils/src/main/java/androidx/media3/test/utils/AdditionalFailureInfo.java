/*
 * Copyright (C) 2020 The Android Open Source Project
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
package androidx.media3.test.utils;

import androidx.annotation.Nullable;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.truth.Truth;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A JUnit {@link Rule} that attaches additional info to any errors/exceptions thrown by the test.
 *
 * <p>This is useful for tests where the line-number from a stacktrace doesn't provide enough detail
 * about the failure, for example when an assertion fails inside a loop.
 *
 * <p>This can be preferable to many calls to {@link Truth#assertWithMessage(String)} because it
 * will also add info to errors/exceptions that bubble out from the system-under-test.
 *
 * <p>Includes special handling for {@link AssertionError} to ensure that test failures are
 * correctly distinguished from test errors (all other errors/exceptions).
 */
@UnstableApi
public final class AdditionalFailureInfo implements TestRule {

  private final AtomicReference<@NullableType String> info;

  public AdditionalFailureInfo() {
    info = new AtomicReference<>();
  }

  /**
   * Sets the additional info to be added to any test failures. Pass {@code null} to skip adding any
   * additional info.
   *
   * <p>Can be called from any thread.
   */
  public void setInfo(@Nullable String info) {
    this.info.set(info);
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try {
          base.evaluate();
        } catch (Throwable e) {
          @Nullable String resourceInfo = AdditionalFailureInfo.this.info.get();
          if (resourceInfo == null) {
            throw e;
          } else if (e instanceof AssertionError) {
            // Deliberately prune the AssertionError from the causal chain and "replace" it with
            // this new one by copying the stacktrace from old to new (so it looks like the new
            // one was thrown from where the old one was thrown).
            DiscreteAssertionError assertionError =
                new DiscreteAssertionError(resourceInfo + "\n" + e.getMessage(), e.getCause());
            assertionError.setStackTrace(e.getStackTrace());
            throw assertionError;
          } else {
            Exception exception = new Exception(resourceInfo, e);
            StackTraceElement[] stackTrace = exception.getStackTrace();
            // Only include the top line of the stack trace (this method) - the rest will be
            // uninteresting JUnit framework calls that obscure the true test failure.
            if (stackTrace.length > 0) {
              exception.setStackTrace(
                  Util.nullSafeArrayCopyOfRange(stackTrace, /* from= */ 0, /* to= */ 1));
            }
            throw exception;
          }
        }
      }
    };
  }

  /** An {@link AssertionError} that doesn't print its class name in stack traces. */
  @SuppressWarnings("OverrideThrowableToString")
  private static class DiscreteAssertionError extends AssertionError {

    public DiscreteAssertionError(@Nullable String message, @Nullable Throwable cause) {
      super(message, cause);
    }

    // To avoid printing the class name before the message. Inspired by Truth:
    // https://github.com/google/truth/blob/152f3936/core/src/main/java/com/google/common/truth/Platform.java#L186-L192
    @Override
    public String toString() {
      @Nullable String message = getLocalizedMessage();
      return message != null ? message : super.toString();
    }
  }
}
