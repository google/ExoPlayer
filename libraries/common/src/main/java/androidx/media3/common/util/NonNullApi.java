/*
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
 */
package androidx.media3.common.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;
import kotlin.annotations.jvm.MigrationStatus;
import kotlin.annotations.jvm.UnderMigration;

/**
 * Annotation to declare all type usages in the annotated instance as {@link Nonnull}, unless
 * explicitly marked with a nullable annotation.
 */
// MigrationStatus.STRICT is marked as deprecated because it's considered experimental
@SuppressWarnings("deprecation")
@Nonnull
@TypeQualifierDefault(ElementType.TYPE_USE)
@UnderMigration(status = MigrationStatus.STRICT)
@Retention(RetentionPolicy.CLASS)
@UnstableApi
public @interface NonNullApi {}
