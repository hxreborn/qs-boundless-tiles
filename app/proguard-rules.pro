# Keep LSPosed module entry point
-keep class eu.hxreborn.qsboundlesstiles.QSBoundlessTilesModule { *; }

# Keep all hooker classes and their methods
-keep @io.github.libxposed.api.annotations.XposedHooker class * { *; }

# Keep libxposed API annotations
-keep class io.github.libxposed.api.annotations.** { *; }
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations

# Prevent R8 from removing hook methods
-keepclassmembers class * {
    @io.github.libxposed.api.annotations.BeforeInvocation <methods>;
    @io.github.libxposed.api.annotations.AfterInvocation <methods>;
}

# Xposed module class pattern
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowobfuscation,allowoptimization public class * extends io.github.libxposed.api.XposedModule {
    public <init>(...);
    public void onPackageLoaded(...);
    public void onSystemServerLoaded(...);
}

# Kotlin intrinsics optimization
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}
-assumenosideeffects class java.util.Objects {
    public static ** requireNonNull(...);
}

# Strip debug logs in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Keep preferences manager for cross-process access
-keep class eu.hxreborn.qsboundlesstiles.prefs.PrefsManager { *; }

# Keep UI classes
-keep class eu.hxreborn.qsboundlesstiles.ui.** { *; }
-keep class eu.hxreborn.qsboundlesstiles.scanner.** { *; }

# Obfuscation
-repackageclasses
-allowaccessmodification
