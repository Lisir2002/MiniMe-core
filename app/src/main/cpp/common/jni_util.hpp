// MiniMe-core Native Viewer — JNI 异常边界包装宏
// 原则：C++ 异常绝不跨 JNI。每个导出函数体用 MINIME_TRY() 开始、
// MINIME_JNI_CATCH(env, 默认返回值) 结束，捕获异常并转成错误码 + lastError。
#ifndef MINIMEME_JNI_UTIL_HPP
#define MINIMEME_JNI_UTIL_HPP

#include <jni.h>

#include <exception>
#include <string>

#include "status.hpp"

#define MINIME_TRY() try {

#define MINIME_JNI_CATCH(env, defaultRet)                                                     \
    } catch (const std::bad_alloc&) {                                                           \
        ::minime::setLastError(::minime::Status::ERR_OUT_OF_MEMORY, "out of memory");         \
        return (defaultRet);                                                                  \
    } catch (const std::exception& e) {                                                       \
        ::minime::setLastError(::minime::Status::ERR_INTERNAL, e.what());                    \
        return (defaultRet);                                                                  \
    } catch (...) {                                                                            \
        ::minime::setLastError(::minime::Status::ERR_INTERNAL, "unknown native exception");    \
        return (defaultRet);                                                                  \
    }

#endif  // MINIMEME_JNI_UTIL_HPP
