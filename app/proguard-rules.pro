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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile
-keep class ** extends androidx.navigation.Navigator
-keep class ** implements org.ocpsoft.prettytime.TimeUnit

# Room database rules
-keep class androidx.room.** { *; }
-keep class com.mymemo.app.room.** { *; }
-keep class * implements androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.migration.Migration
-keep class * implements androidx.room.TypeConverter

# KSP generated classes
-keep class com.mymemo.app.room.dao.** { *; }
-keep class * extends androidx.room.RoomDatabase$Companion

# Glide rules
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.AppGlideModule
-keep public class * extends com.bumptech.glide.Registry$ModelLoaderFactory
-keep public class * extends com.bumptech.glide.load.model.ModelLoader