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
package androidx.media3.common.util;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import androidx.annotation.RequiresOptIn;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Signifies that a public API (class, method or field) is subject to incompatible changes, or even
 * removal, in a future release.
 *
 * <p>The presence of this annotation implies nothing about the quality or performance of the API in
 * question, only the fact that it is not "API-frozen."
 *
 * <p>This library follows <a href="https://semver.org/">semantic versioning</a> and the stable API
 * forms the 'public' API for the purposes of the versioning rules. Therefore an API bearing this
 * annotation is exempt from any compatibility guarantees implied by the semantic versioning rules.
 *
 * <p>It is generally safe for applications to depend on unstable APIs, at the cost of some extra
 * work during upgrades. However it is generally inadvisable for libraries (which get included on
 * users' CLASSPATHs, outside the library developers' control) to do so.
 *
 * <h2>Requesting additions to the stable API</h2>
 *
 * The Media3 stable API (i.e. those public API symbols that are not annotated with this annotation)
 * is designed to allow developers to achieve common media-related tasks. If you have a use-case
 * that you are unable to achieve using the stable API, and think you should be able to, please file
 * an issue on our <a href="https://github.com/androidx/media/issues">GitHub issue tracker</a> with
 * the full context of what you're doing, and what symbols you would need to be part of the stable
 * API. We will consider each request on a case-by-case basis.
 *
 * <h2>Opting in to use unstable APIs</h2>
 *
 * <p>By default usages of APIs annotated with this annotation generate lint errors in Gradle and
 * Android Studio, in order to alert developers to the risk of breaking changes.
 *
 * <p>Individual usage sites or whole packages can be opted-in to suppress the lint error by using
 * the {@link androidx.annotation.OptIn} annotation.
 *
 * <p>In a Java class:
 *
 * <pre>{@code
 * import androidx.annotation.OptIn;
 * import androidx.media3.common.util.UnstableApi;
 * ...
 * @OptIn(markerClass = UnstableApi.class)
 * private void methodUsingUnstableApis() { ... }
 * }</pre>
 *
 * <p>In a {@code package-info.java} file, to opt-in a whole package:
 *
 * <pre>{@code
 * @OptIn(markerClass = UnstableApi.class)
 * package name.of.your.package;
 *
 * import androidx.annotation.OptIn;
 * import androidx.media3.common.util.UnstableApi;
 * }</pre>
 *
 * <p>In Kotlin:
 *
 * <pre>{@code
 * import androidx.annotation.OptIn
 * import androidx.media3.common.util.UnstableApi
 * ...
 * @OptIn(UnstableApi::class)
 * private fun methodUsingUnstableApis() { ... }
 * }</pre>
 *
 * <p>Whole projects can be opted-in by suppressing the specific lint error in their <a
 * href="https://developer.android.com/studio/write/lint#pref">{@code lint.xml} file</a>:
 *
 * <pre>{@code
 * <?xml version="1.0" encoding="utf-8"?>
 * <lint>
 *   <issue id="UnsafeOptInUsageError">
 *     <ignore
 *         regexp='\(markerClass = androidx\.media3\.common\.util\.UnstableApi\.class\)' />
 *   </issue>
 * </lint>
 * }</pre>
 */
@Documented
@Retention(CLASS)
@Target({TYPE, METHOD, CONSTRUCTOR, FIELD})
@UnstableApi
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
public @interface UnstableApi {}
