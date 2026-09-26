// MiniMe-core Native Viewer / Editor core — JNI 桥接入口
// 职责（设计文档 §4）：仅做参数转换与结果封送，不写业务逻辑。
//  - 全局 JavaVM 缓存（JNI_OnLoad）
//  - 统一错误码 + nativeGetLastError
//  - 句柄管理（jlong 指针 + nativeClose）
//  - C++ 异常绝不跨 JNI：所有导出函数包一层 try/catch，失败转错误码

#include <jni.h>

#include <new>
#include <stdexcept>
#include <string>

#include "common/status.hpp"
#include "common/native_ref.hpp"
#include "common/jni_util.hpp"
#include "core/file_loader.hpp"
#include "treesitter/ts_parser.hpp"

namespace {

// 缓存全局 JavaVM，供后续在非 JNI 线程（如 native 内部线程）Attach/Detach 时使用。
JavaVM* g_jvm = nullptr;

const char* kBridgeClassName =
        "com/mini/me_core/core/viewer/native/NativeViewerBridge";

// 把最近一次错误以 jstring 返回；无错误时返回空串。
jstring lastErrorToJString(JNIEnv* env) {
    const std::string& msg = minime::getLastError();
    if (msg.empty()) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(msg.c_str());
}

// 从句柄取回 FileLoader*，校验类型。失败返回 nullptr 并设置错误。
minime::FileLoader* asFileLoader(jlong handle) {
    if (handle == 0) {
        minime::setLastError(minime::Status::ERR_INVALID_HANDLE, "null handle");
        return nullptr;
    }
    minime::NativeRef* ref = minime::NativeRef::fromHandle(handle);
    if (ref->type() != minime::NativeType::FILE_LOADER) {
        minime::setLastError(minime::Status::ERR_INVALID_HANDLE, "not a file handle");
        return nullptr;
    }
    return static_cast<minime::FileLoader*>(ref);
}

// 代码查看会话：聚合 FileLoader + TsParser。
struct CodeViewerSession : public minime::NativeRef {
    minime::FileLoader loader;
    minime::TsParser parser;
    CodeViewerSession() : minime::NativeRef(minime::NativeType::CODE_VIEWER) {}
};

CodeViewerSession* asViewer(jlong handle) {
    if (handle == 0) return nullptr;
    minime::NativeRef* ref = minime::NativeRef::fromHandle(handle);
    if (ref->type() != minime::NativeType::CODE_VIEWER) {
        minime::setLastError(minime::Status::ERR_INVALID_HANDLE, "not a viewer handle");
        return nullptr;
    }
    return static_cast<CodeViewerSession*>(ref);
}

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_jvm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* /*vm*/, void* /*reserved*/) {
    g_jvm = nullptr;
}

// ── 通用 ──────────────────────────────────────────────────────────────

JNIEXPORT jstring JNICALL
Java_com_mini_me_core_core_viewer_native_NativeViewerBridge_nativeGetVersion(
        JNIEnv* env, jclass /*clazz*/) {
    return env->NewStringUTF("minimeviewer-native/0.1.0");
}

JNIEXPORT jstring JNICALL
Java_com_mini_me_core_core_viewer_native_NativeViewerBridge_nativeGetLastError(
        JNIEnv* env, jclass /*clazz*/) {
    return lastErrorToJString(env);
}

// 显式释放任意 native 句柄。所有跨 JNI 对象都派生自 NativeRef，虚析构保证正确销毁。
// 重复 close / 关闭无效句柄安全返回（容错，便于 Kotlin Closeable 幂等 close）。
JNIEXPORT void JNICALL
Java_com_mini_me_core_core_viewer_native_NativeViewerBridge_nativeClose(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong handle) {
    if (handle == 0) {
        return;
    }
    minime::NativeRef* ref = minime::NativeRef::fromHandle(handle);
    delete ref;
}

// ── 文件加载 ───────────────────────────────────────────────────────────

JNIEXPORT jlong JNICALL
Java_com_mini_me_core_core_viewer_native_NativeViewerBridge_nativeOpenFile(
        JNIEnv* env, jclass /*clazz*/, jstring path) {
    MINIME_TRY();
    const char* pathUtf = env->GetStringUTFChars(path, nullptr);
    auto* loader = new minime::FileLoader();
    minime::Status st = loader->open(pathUtf);
    env->ReleaseStringUTFChars(path, pathUtf);
    if (st != minime::Status::OK) {
        delete loader;
        return 0;
    }
    return minime::NativeRef::toHandle(loader);
    MINIME_JNI_CATCH(env, 0);
}

// 文件元信息以 JSON 字符串返回（Kotlin 侧用 kotlinx.serialization 解析）。
JNIEXPORT jstring JNICALL
Java_com_mini_me_core_core_viewer_native_NativeViewerBridge_nativeGetFileInfo(
        JNIEnv* env, jclass /*clazz*/, jlong handle) {
    MINIME_TRY();
    minime::FileLoader* loader = asFileLoader(handle);
    if (!loader) {
        return env->NewStringUTF("{}");
    }
    const minime::FileInfo& info = loader->info();
    std::string json = "{";
    json += "\"lineCount\":" + std::to_string(info.lineCount) + ",";
    json += "\"size\":" + std::to_string(info.size) + ",";
    json += "\"encoding\":" + std::to_string(static_cast<int>(info.encoding)) + ",";
    json += "\"lineEnding\":" + std::to_string(static_cast<int>(info.lineEnding)) + ",";
    json += "\"largeFileMode\":" + std::string(info.largeFileMode ? "true" : "false");
    json += "}";
    return env->NewStringUTF(json.c_str());
    MINIME_JNI_CATCH(env, env->NewStringUTF("{}"));
}

JNIEXPORT jlong JNICALL
Java_com_mini_me_core_core_viewer_native_NativeViewerBridge_nativeGetLineCount(
        JNIEnv* env, jclass /*clazz*/, jlong handle) {
    MINIME_TRY();
    minime::FileLoader* loader = asFileLoader(handle);
    if (!loader) return 0;
    return loader->info().lineCount;
    MINIME_JNI_CATCH(env, 0);
}

JNIEXPORT jobjectArray JNICALL
Java_com_mini_me_core_core_viewer_native_NativeViewerBridge_nativeReadLines(
        JNIEnv* env, jclass /*clazz*/, jlong handle, jlong start, jlong end) {
    MINIME_TRY();
    minime::FileLoader* loader = asFileLoader(handle);
    if (!loader) return nullptr;
    std::vector<std::string> lines = loader->readLines(start, end);
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray arr = env->NewObjectArray(static_cast<jsize>(lines.size()), stringClass,
                                           nullptr);
    for (jsize i = 0; i < static_cast<jsize>(lines.size()); ++i) {
        env->SetObjectArrayElement(arr, i, env->NewStringUTF(lines[i].c_str()));
    }
    return arr;
    MINIME_JNI_CATCH(env, nullptr);
}

JNIEXPORT void JNICALL
Java_com_mini_me_core_core_viewer_native_NativeViewerBridge_nativeCloseFile(
        JNIEnv* env, jclass /*clazz*/, jlong handle) {
    if (handle == 0) return;
    minime::NativeRef* ref = minime::NativeRef::fromHandle(handle);
    delete ref;
}

// 零拷贝读取：行文本按 [4 字节小端长度][UTF-8 字节] 写入 Kotlin 传入的 DirectByteBuffer。
// 返回写入行数；Kotlin 从 buffer 起始按长度前缀逐行解析。
JNIEXPORT jint JNICALL
Java_com_mini_me_core_core_viewer_native_NativeViewerBridge_nativeReadLinesDirect(
        JNIEnv* env, jclass /*clazz*/, jlong handle, jlong start, jlong end,
        jobject buffer) {
    MINIME_TRY();
    minime::FileLoader* loader = asFileLoader(handle);
    if (!loader) return 0;
    char* addr = static_cast<char*>(env->GetDirectBufferAddress(buffer));
    jlong cap = env->GetDirectBufferCapacity(buffer);
    if (!addr || cap <= 0) {
        minime::setLastError(minime::Status::ERR_INVALID_HANDLE, "not a direct buffer");
        return 0;
    }
    size_t outBytes = 0;
    int rows = loader->writeLinesToBuffer(start, end, addr, static_cast<size_t>(cap), outBytes);
    // 通过可读的 outBytes 约定回传：写入行数即为返回值；Kotlin 按行数消费缓冲。
    return rows;
    MINIME_JNI_CATCH(env, 0);
}

// 手动切换编码（GBK/UTF-8/UTF-16）重新解码。
JNIEXPORT jboolean JNICALL
Java_com_mini_me_core_core_viewer_native_NativeViewerBridge_nativeSetEncoding(
        JNIEnv* env, jclass /*clazz*/, jlong handle, jint encoding) {
    MINIME_TRY();
    minime::FileLoader* loader = asFileLoader(handle);
    if (!loader) return JNI_FALSE;
    minime::Status st = loader->setEncoding(static_cast<minime::Encoding>(encoding));
    return st == minime::Status::OK ? JNI_TRUE : JNI_FALSE;
    MINIME_JNI_CATCH(env, JNI_FALSE);
}

// ── 代码查看（只读）─────────────────────────────────────────────────────
// 打开文件 + 选择语法 + 全量解析。languageHint 如 "kotlin"/"python"/"cpp"，空串则按扩展名留空。
JNIEXPORT jlong JNICALL
Java_com_mini_me_core_core_viewer_native_NativeViewerBridge_nativeOpenCodeViewer(
        JNIEnv* env, jclass /*clazz*/, jstring path, jstring languageHint) {
    MINIME_TRY();
    const char* pathUtf = env->GetStringUTFChars(path, nullptr);
    const char* langUtf = languageHint ? env->GetStringUTFChars(languageHint, nullptr) : "";
    auto* s = new CodeViewerSession();
    minime::Status st = s->loader.open(pathUtf);
    if (st == minime::Status::OK && langUtf && langUtf[0] != '\0') {
        s->parser.setLanguage(langUtf);
    }
    if (st == minime::Status::OK && s->parser.languageIsSet()) {
        s->parser.parse(s->loader.content());
    }
    env->ReleaseStringUTFChars(path, pathUtf);
    if (languageHint) env->ReleaseStringUTFChars(languageHint, langUtf);
    if (st != minime::Status::OK) { delete s; return 0; }
    return minime::NativeRef::toHandle(s);
    MINIME_JNI_CATCH(env, 0);
}

// 高亮 spans：扁平数组 [startByte, endByte, category, ...]。
JNIEXPORT jintArray JNICALL
Java_com_mini_me_core_core_viewer_native_NativeViewerBridge_nativeGetSpans(
        JNIEnv* env, jclass /*clazz*/, jlong handle) {
    MINIME_TRY();
    CodeViewerSession* s = asViewer(handle);
    if (!s) return nullptr;
    const auto& spans = s->parser.spans();
    jintArray arr = env->NewIntArray(static_cast<jsize>(spans.size() * 3));
    if (!spans.empty()) {
        jint* buf = env->GetIntArrayElements(arr, nullptr);
        for (size_t i = 0; i < spans.size(); ++i) {
            buf[i*3+0] = static_cast<jint>(spans[i].startByte);
            buf[i*3+1] = static_cast<jint>(spans[i].endByte);
            buf[i*3+2] = static_cast<jint>(spans[i].category);
        }
        env->ReleaseIntArrayElements(arr, buf, 0);
    }
    return arr;
    MINIME_JNI_CATCH(env, nullptr);
}

// 折叠区域：扁平数组 [startLine, endLine, kind, ...]。
JNIEXPORT jintArray JNICALL
Java_com_mini_me_core_core_viewer_native_NativeViewerBridge_nativeGetFolds(
        JNIEnv* env, jclass /*clazz*/, jlong handle) {
    MINIME_TRY();
    CodeViewerSession* s = asViewer(handle);
    if (!s) return nullptr;
    const auto& folds = s->parser.folds();
    jintArray arr = env->NewIntArray(static_cast<jsize>(folds.size() * 3));
    jint* buf = env->GetIntArrayElements(arr, nullptr);
    for (size_t i = 0; i < folds.size(); ++i) {
        buf[i*3+0] = static_cast<jint>(folds[i].startLine);
        buf[i*3+1] = static_cast<jint>(folds[i].endLine);
        buf[i*3+2] = static_cast<jint>(folds[i].kind);
    }
    env->ReleaseIntArrayElements(arr, buf, 0);
    return arr;
    MINIME_JNI_CATCH(env, nullptr);
}

// 符号大纲：JSON 字符串返回。
JNIEXPORT jstring JNICALL
Java_com_mini_me_core_core_viewer_native_NativeViewerBridge_nativeGetOutline(
        JNIEnv* env, jclass /*clazz*/, jlong handle) {
    MINIME_TRY();
    CodeViewerSession* s = asViewer(handle);
    if (!s) return env->NewStringUTF("[]");
    const auto& ol = s->parser.outline();
    std::string json = "[";
    for (size_t i = 0; i < ol.size(); ++i) {
        if (i) json += ",";
        json += "{\"name\":\"" + ol[i].name + "\",";
        json += "\"startLine\":" + std::to_string(ol[i].startLine) + ",";
        json += "\"endLine\":" + std::to_string(ol[i].endLine) + ",";
        json += "\"kind\":" + std::to_string(ol[i].kind) + "}";
    }
    json += "]";
    return env->NewStringUTF(json.c_str());
    MINIME_JNI_CATCH(env, env->NewStringUTF("[]"));
}

JNIEXPORT void JNICALL
Java_com_mini_me_core_core_viewer_native_NativeViewerBridge_nativeCloseCodeViewer(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong handle) {
    if (handle == 0) return;
    delete minime::NativeRef::fromHandle(handle);
}

}  // extern "C"
