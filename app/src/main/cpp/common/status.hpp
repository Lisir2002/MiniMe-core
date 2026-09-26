// MiniMe-core Native Viewer — 统一状态码与错误消息机制
// 设计：C++ 异常绝不跨越 JNI 边界。所有内部失败要么返回 Status 错误码，
// 要么通过 setLastError() 记录消息后返回错误码；Kotlin 侧通过 nativeGetLastError() 取回。
#ifndef MINIMEME_STATUS_HPP
#define MINIMEME_STATUS_HPP

#include <string>
#include <thread>

namespace minime {

// 统一错误码（与设计文档 §4.1 一致）。0 表示成功。
enum class Status : int {
    OK = 0,
    ERR_FILE_NOT_FOUND = 1,
    ERR_PERMISSION_DENIED = 2,
    ERR_UNSUPPORTED_ENCODING = 3,
    ERR_PARSE_FAILED = 4,
    ERR_UNSUPPORTED_FORMAT = 5,
    ERR_OUT_OF_MEMORY = 6,
    ERR_INVALID_HANDLE = 7,
    ERR_SAVE_FAILED = 8,
    ERR_OUT_OF_RANGE = 9,
    ERR_INTERNAL = 10,
};

inline const char* statusName(Status s) {
    switch (s) {
        case Status::OK:                    return "OK";
        case Status::ERR_FILE_NOT_FOUND:    return "FILE_NOT_FOUND";
        case Status::ERR_PERMISSION_DENIED: return "PERMISSION_DENIED";
        case Status::ERR_UNSUPPORTED_ENCODING: return "UNSUPPORTED_ENCODING";
        case Status::ERR_PARSE_FAILED:      return "PARSE_FAILED";
        case Status::ERR_UNSUPPORTED_FORMAT: return "UNSUPPORTED_FORMAT";
        case Status::ERR_OUT_OF_MEMORY:     return "OUT_OF_MEMORY";
        case Status::ERR_INVALID_HANDLE:    return "INVALID_HANDLE";
        case Status::ERR_SAVE_FAILED:       return "SAVE_FAILED";
        case Status::ERR_OUT_OF_RANGE:      return "OUT_OF_RANGE";
        case Status::ERR_INTERNAL:          return "INTERNAL";
    }
    return "UNKNOWN";
}

// 线程局部的最近一次错误消息。JNI 调用通常在单一线程完成一次完整请求，
// 因此 thread_local 足够且线程安全。
inline std::string& lastErrorRef() {
    static thread_local std::string msg;
    return msg;
}

inline void setLastError(const std::string& msg) {
    lastErrorRef() = msg;
}

inline void setLastError(Status s, const std::string& detail) {
    lastErrorRef() = std::string(statusName(s)) + ": " + detail;
}

inline const std::string& getLastError() {
    return lastErrorRef();
}

inline void clearLastError() {
    lastErrorRef().clear();
}

}  // namespace minime

#endif  // MINIMEME_STATUS_HPP
