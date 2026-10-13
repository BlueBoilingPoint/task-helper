# ============================================================
# 定时任务助手 ProGuard / R8 规则
# release 构建会启用 R8 混淆（minifyEnabled true），以下规则保证
# Room、系统组件与反射路径在混淆后仍能正常工作。
# ============================================================

# ---- Room：保留实体、DAO、数据库及生成类（含 _Impl 的列名映射）----
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Entity { *; }
-keep interface * extends androidx.room.Dao
-keepclassmembers class * extends androidx.room.Dao { *; }
-dontwarn androidx.room.paging.**

# ---- AndroidManifest 引用的组件：被 aapt 自动 keep，这里再显式兜底 ----
-keep public class com.taskhelper.App
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.accessibilityservice.AccessibilityService

# ---- 被 @Keep 标注的类 / 成员 ----
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * { @androidx.annotation.Keep <methods>; }
-keepclasseswithmembers class * { @androidx.annotation.Keep <fields>; }

# ---- 协程 / Kotlin 基础（防止极端 shrink 误删）----
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**
-dontwarn kotlinx.coroutines.internal.**

# ---- 序列化/反射相关的安全兜底 ----
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
