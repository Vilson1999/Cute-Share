# Cute Share ProGuard / R8 Optimization Rules
# Optimize bytecode and strip unused classes & methods to minimize APK size

-dontobfuscate
-keepattributes SourceFile,LineNumberTable,*Annotation*

# Keep Compose runtime annotations
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# Keep app data models & ViewModel
-keep class com.example.model.** { *; }
-keep class com.example.data.** { *; }
-keep class com.example.ui.MainViewModel { *; }

# Allow stripping unused metadata
-dontwarn **
-dontnote **
