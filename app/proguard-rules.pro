# AAR Production R8 / ProGuard Rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Android Entry Points (Application, AccessibilityService, TileService, MainActivity)
-keep public class com.aar.reader.AarApplication { *; }
-keep public class com.aar.reader.service.AutoReaderAccessibilityService { *; }
-keep public class com.aar.reader.service.AutoReaderTileService { *; }
-keep public class com.aar.reader.ui.MainActivity { *; }

# Keep FSM State Models & DataStore Preferences Models
-keep class com.aar.reader.service.model.** { *; }
-keep class com.aar.reader.data.UserPreferences { *; }

# Kotlin Coroutines & StateFlow
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# AndroidX DataStore
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

-dontwarn org.robolectric.**
