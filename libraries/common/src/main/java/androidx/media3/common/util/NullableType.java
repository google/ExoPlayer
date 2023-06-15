/*
 * Copyright 2023 The Android Open Source Project
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierNickname;
import javax.annotation.meta.When;

/**
 * Annotation for specifying a nullable type.
 *
 * <p>Unlike {@link androidx.annotation.Nullable} used elsewhere in the library, this annotation can
 * be used on {@link ElementType#TYPE_USE} locations like generic type parameters and array element
 * types.
 */
@UnstableApi
@Documented
@Retention(RetentionPolicy.CLASS)
@Nonnull(when = When.MAYBE)
@Target({ElementType.TYPE_USE})
@TypeQualifierNickname
public @interface NullableType {}
