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
package com.google.android.exoplayer2.testutil;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.Consumer;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

/** Tests for {@link AdditionalFailureInfo}. */
@RunWith(AndroidJUnit4.class)
public final class AdditionalFailureInfoTest {

  private final ExpectedException expectedException = new ExpectedException();
  private final AdditionalFailureInfo additionalFailureInfo = new AdditionalFailureInfo();

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(expectedException).around(additionalFailureInfo);

  @Test
  public void extraInfoSet_addsInfoToAssertionErrorAndSuppressesClassName() {
    // Generate an AssertionError using Truth for realism - we'll rethrow this below to test that
    // the rule catches it and rethrows a new, augmented AssertionError (with the original stack
    // trace).
    AssertionError originalAssertionError =
        assertThrows(AssertionError.class, () -> assertThat(true).isFalse());

    additionalFailureInfo.setInfo("extra info");

    // Check that the original AssertionError is removed from the chain, and its (augmented) message
    // and stack trace is set on the new one.
    expectedException.setAssertions(
        throwable -> {
          assertThat(throwable).isInstanceOf(AssertionError.class);
          assertThat(throwable)
              .hasMessageThat()
              .isEqualTo("extra info\n" + originalAssertionError.getMessage());
          assertThat(throwable).hasCauseThat().isNull();
          assertThat(throwable.getStackTrace()).isEqualTo(originalAssertionError.getStackTrace());
          assertThat(throwable.toString()).startsWith("extra info");
          assertThat(throwable.toString()).doesNotContain("AssertionError");
        });

    throw originalAssertionError;
  }

  @Test
  public void extraInfoSet_transformsEverythingElseToExceptionAddsInfoAndTruncatesStackTrace() {
    IllegalArgumentException originalException = new IllegalArgumentException();

    additionalFailureInfo.setInfo("extra info");

    expectedException.setAssertions(
        throwable -> {
          assertThat(throwable).hasMessageThat().isEqualTo("extra info");
          // Note: Deliberately not using instanceof since we don't want to allow subclasses.
          assertThat(throwable.getClass()).isEqualTo(Exception.class);
          assertThat(throwable).hasCauseThat().isSameInstanceAs(originalException);
          assertThat(throwable.getStackTrace()).hasLength(1);
          // The exception is thrown from an anonymous inner class, so just check the prefix.
          assertThat(throwable.getStackTrace()[0].getClassName())
              .startsWith(AdditionalFailureInfo.class.getCanonicalName());
        });

    throw originalException;
  }

  @Test
  public void noExtraInfoSet_assertionErrorPropagatedDirectly() {
    // Generate an AssertionError using Truth for realism.
    AssertionError originalAssertionError =
        assertThrows(AssertionError.class, () -> assertThat(true).isFalse());

    expectedException.setAssertions(
        throwable -> assertThat(throwable).isSameInstanceAs(originalAssertionError));

    throw originalAssertionError;
  }

  @Test
  public void noExtraInfoSet_otherExceptionPropagatedDirectly() {
    IllegalArgumentException originalException = new IllegalArgumentException();

    expectedException.setAssertions(
        throwable -> assertThat(throwable).isSameInstanceAs(originalException));

    throw originalException;
  }

  @Test
  public void extraInfoSetAndCleared_assertionErrorPropagatedDirectly() {
    // Generate an AssertionError using Truth for realism
    AssertionError originalAssertionError =
        assertThrows(AssertionError.class, () -> assertThat(true).isFalse());

    additionalFailureInfo.setInfo("extra info");
    additionalFailureInfo.setInfo(null);

    expectedException.setAssertions(
        throwable -> assertThat(throwable).isSameInstanceAs(originalAssertionError));

    throw originalAssertionError;
  }

  @Test
  public void extraInfoSetAndCleared_otherExceptionPropagatedDirectly() {
    IllegalArgumentException originalException = new IllegalArgumentException();

    additionalFailureInfo.setInfo("extra info");
    additionalFailureInfo.setInfo(null);

    expectedException.setAssertions(
        throwable -> assertThat(throwable).isSameInstanceAs(originalException));
    throw originalException;
  }

  /**
   * A similar rule to JUnit's existing {@link org.junit.rules.ExpectedException}, but without
   * relying on Hamcrest matchers.
   *
   * <p>JUnit's one is deprecated in favour of using {@link Assert#assertThrows}, but we need a
   * {@link Rule} because we need to assert on the exception <b>after</b> it's been transformed by
   * the {@link AdditionalFailureInfo} rule.
   */
  private static class ExpectedException implements TestRule {

    @Nullable private Consumer<Throwable> throwableAssertions;

    /**
     * Provide a callback of assertions to execute on the caught exception.
     *
     * <p>A failure in this {@link Consumer} will cause the test to fail.
     */
    public void setAssertions(Consumer<Throwable> throwableAssertions) {
      this.throwableAssertions = throwableAssertions;
    }

    @Override
    public Statement apply(Statement base, Description description) {
      return new Statement() {
        @Override
        public void evaluate() {
          Throwable expected = assertThrows(Throwable.class, base::evaluate);
          if (throwableAssertions != null) {
            throwableAssertions.accept(expected);
          }
        }
      };
    }
  }
}
