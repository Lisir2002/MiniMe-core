# AppTemplate ProGuard 规则
# 模版应用核心类不被混淆（JS Bridge 反射调用需要）

-keep class com.minime.template.** { *; }
-keepclassmembers class com.minime.template.** { *; }

# Kotlin 序列化
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

# 保留 JavascriptInterface 注解的方法
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# WebViewAssetLoader
-keep class androidx.webkit.** { *; }
