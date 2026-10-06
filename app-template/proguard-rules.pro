# AppTemplate ProGuard 规则
# 模版应用核心类不被混淆（JS Bridge 反射调用需要）

# 保留所有模版应用类（Bridge 注入和反射需要）
-keep class com.minime.template.** { *; }
-keepclassmembers class com.minime.template.** { *; }

# 特别保留 AppBridge（通过 addJavascriptInterface 注入）
-keep class com.minime.template.bridge.AppBridge {
    public <methods>;
    @android.webkit.JavascriptInterface <methods>;
}

# 保留所有 BridgeModule 子类
-keep class * extends com.minime.template.bridge.BridgeModule { *; }

# Kotlin 序列化
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Signature

# 保留 JavascriptInterface 注解（关键！R8 可能移除该注解导致 WebView 无法识别方法）
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# WebViewAssetLoader
-keep class androidx.webkit.** { *; }

# 保留 BuildConfig（版本号动态读取）
-keep class com.minime.template.BuildConfig { *; }
