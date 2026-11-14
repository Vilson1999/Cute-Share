# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Optimize passes and code shrinking
-optimizationpasses 5
-allowaccessmodification
-repackageclasses 'o'

# Strip source file, line number table, and unused metadata
-dontusemixedcaseclassnames
-renamesourcefileattribute ""
-keepattributes !SourceFile,!LineNumberTable,!EnclosingMethod,!InnerClasses,!Signature,!Deprecated,!AnnotationDefault,!RuntimeVisibleAnnotations,!RuntimeInvisibleAnnotations

# Strip logging calls across release and debug
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
