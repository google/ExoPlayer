# Proguard rules specific to the common module.

# Don't warn about checkerframework and Kotlin annotations
-dontwarn org.checkerframework.**
-dontwarn kotlin.annotations.jvm.**
-dontwarn javax.annotation.**

# From https://github.com/google/guava/wiki/UsingProGuardWithGuava
-dontwarn java.lang.ClassValue
-dontwarn java.lang.SafeVarargs
-dontwarn javax.lang.model.element.Modifier
-dontwarn sun.misc.Unsafe

# Don't warn about Guava's compile-only dependencies.
# These lines are needed for ProGuard but not R8.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Workaround for https://issuetracker.google.com/issues/112297269
# This is needed for ProGuard but not R8.
-keepclassmembernames class com.google.common.base.Function { *; }
