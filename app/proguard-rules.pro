# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# disable obfuscation
-dontobfuscate

# Keep JNI interface
-keep class com.osfans.trime.core.* { *; }
# JGit (git config sync) uses reflection and service loaders
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**
-dontwarn org.slf4j.**
-dontwarn javax.**
-dontwarn java.lang.management.**
-dontwarn org.ietf.jgss.**

# remove kotlin null checks
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkNotNull(...);
    static void checkExpressionValueIsNotNull(...);
    static void checkNotNullExpressionValue(...);
    static void checkReturnedValueIsNotNull(...);
    static void checkFieldIsNotNull(...);
    static void checkParameterIsNotNull(...);
    static void checkNotNullParameter(...);
}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# androidx.security:security-crypto 依赖的 Google Tink 引用了 errorprone 的编译期注解，
# 运行时根本用不到，但 R8 会当成缺类直接失败（2026-09-11 CI 上踩到）。
# 注意：debug 包不混淆，所以本地装得上不代表能出 release 包。
-dontwarn com.google.errorprone.annotations.**
