# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# 保留应用程序的基本信息
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# 不混淆本应用的类
-keep class com.bigtwo.game.** { *; }
-keep class view.** { *; }
-keep class models.** { *; }
-keep class controller.** { *; }
-keep class utils.** { *; }

# AndroidX支持库规则
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-keep class com.google.android.material.** { *; }
-keep interface com.google.android.material.** { *; }

# 常见的Android组件不进行混淆
-keep class * extends android.app.Activity
-keep class * extends android.app.Application
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider
-keep class * extends android.app.backup.BackupAgentHelper
-keep class * extends android.preference.Preference
-keep class * extends android.view.View 