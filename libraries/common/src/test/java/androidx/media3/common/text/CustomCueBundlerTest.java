/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.common.text;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toCollection;

import android.os.Bundle;
import android.text.SpannableString;
import android.util.Pair;
import androidx.media3.extractor.text.CueDecoder;
import androidx.media3.extractor.text.CueEncoder;
import androidx.media3.test.utils.truth.SpannedSubject;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.ClassPath;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test of {@link Cue} serialization and deserialization using {@link CueEncoder} and {@link
 * CueDecoder} with a {@code text} that contains custom (i.e. non-Android native) spans.
 */
@RunWith(AndroidJUnit4.class)
public class CustomCueBundlerTest {

  private static final RubySpan RUBY_SPAN =
      new RubySpan("ruby text", TextAnnotation.POSITION_AFTER);
  private static final TextEmphasisSpan TEXT_EMPHASIS_SPAN =
      new TextEmphasisSpan(
          TextEmphasisSpan.MARK_SHAPE_CIRCLE,
          TextEmphasisSpan.MARK_FILL_FILLED,
          TextAnnotation.POSITION_AFTER);
  private static final HorizontalTextInVerticalContextSpan
      HORIZONTAL_TEXT_IN_VERTICAL_CONTEXT_SPAN = new HorizontalTextInVerticalContextSpan();
  private static final ImmutableMap<Object, Pair<Integer, Integer>> ALL_SPANS_TO_START_END_INDEX =
      ImmutableMap.of(
          RUBY_SPAN, new Pair<>(1, 2),
          TEXT_EMPHASIS_SPAN, new Pair<>(2, 3),
          HORIZONTAL_TEXT_IN_VERTICAL_CONTEXT_SPAN, new Pair<>(5, 7));

  @Test
  public void serializingSpannableWithAllCustomSpans() {
    SpannableString spannableString = new SpannableString("test string");
    for (Map.Entry<Object, Pair<Integer, Integer>> spanToStartEndIndex :
        ALL_SPANS_TO_START_END_INDEX.entrySet()) {
      spannableString.setSpan(
          spanToStartEndIndex.getKey(),
          spanToStartEndIndex.getValue().first,
          spanToStartEndIndex.getValue().second,
          /* flags= */ 0);
    }

    ArrayList<Bundle> bundles = CustomSpanBundler.bundleCustomSpans(spannableString);

    assertThat(bundles).hasSize(ALL_SPANS_TO_START_END_INDEX.size());

    SpannableString result = new SpannableString("test string");
    for (Bundle bundle : bundles) {
      CustomSpanBundler.unbundleAndApplyCustomSpan(bundle, result);
    }
    SpannedSubject.assertThat(result)
        .hasRubySpanBetween(
            ALL_SPANS_TO_START_END_INDEX.get(RUBY_SPAN).first,
            ALL_SPANS_TO_START_END_INDEX.get(RUBY_SPAN).second)
        .withTextAndPosition(RUBY_SPAN.rubyText, RUBY_SPAN.position);
    SpannedSubject.assertThat(result)
        .hasTextEmphasisSpanBetween(
            ALL_SPANS_TO_START_END_INDEX.get(TEXT_EMPHASIS_SPAN).first,
            ALL_SPANS_TO_START_END_INDEX.get(TEXT_EMPHASIS_SPAN).second)
        .withMarkAndPosition(
            TEXT_EMPHASIS_SPAN.markShape, TEXT_EMPHASIS_SPAN.markFill, TEXT_EMPHASIS_SPAN.position);
    SpannedSubject.assertThat(result)
        .hasHorizontalTextInVerticalContextSpanBetween(
            ALL_SPANS_TO_START_END_INDEX.get(HORIZONTAL_TEXT_IN_VERTICAL_CONTEXT_SPAN).first,
            ALL_SPANS_TO_START_END_INDEX.get(HORIZONTAL_TEXT_IN_VERTICAL_CONTEXT_SPAN).second);
  }

  @Test
  public void noUnsupportedCustomSpanTypes() throws Exception {
    Set<String> supportedSpanClassNames =
        ALL_SPANS_TO_START_END_INDEX.keySet().stream()
            .map(s -> s.getClass().getName())
            .collect(toCollection(HashSet::new));
    ClassPath classPath = ClassPath.from(getClass().getClassLoader());
    for (ClassPath.ClassInfo classInfo : classPath.getAllClasses()) {
      if (classInfo.getPackageName().equals("androidx.media3.common.text")
          && classInfo.getName().endsWith("Span")) {
        Class<?> clazz = classInfo.load();
        if (!clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers())) {
          assertThat(supportedSpanClassNames).contains(classInfo.getName());
        }
      }
    }
  }
}
