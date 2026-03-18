# Keep LSPosed module entry point
-keep class eu.hxreborn.qsboundlesstiles.QSBoundlessTilesModule { *; }

# Keep module lifecycle methods
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keep,allowobfuscation,allowoptimization public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
    public void onModuleLoaded(...);
    public void onPackageLoaded(...);
    public void onPackageReady(...);
    public void onSystemServerStarting(...);
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
