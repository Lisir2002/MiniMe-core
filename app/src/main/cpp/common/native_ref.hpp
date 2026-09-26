// MiniMe-core Native Viewer — 句柄（handle）基类
// 所有跨 JNI 传递的 native 对象都继承 NativeRef，以裸指针 reinterpret_cast<jlong> 传给 Kotlin。
// Kotlin 侧用 Closeable/use 保证 nativeClose(handle) 被调用，避免泄漏。
#ifndef MINIMEME_NATIVE_REF_HPP
#define MINIMEME_NATIVE_REF_HPP

#include <cstdint>

namespace minime {

// 对象类型标签，用于校验 handle 合法性（防止把一个文件句柄当代码查看句柄用）。
enum class NativeType : uint32_t {
    INVALID = 0,
    FILE_LOADER = 1,
    CODE_VIEWER = 2,
    CODE_EDITOR = 3,
    OFFICE = 4,
};

class NativeRef {
public:
    explicit NativeRef(NativeType t) : type_(t) {}
    virtual ~NativeRef() = default;

    NativeType type() const { return type_; }

    // 句柄 <-> 指针互转。jlong 在 JNI 层即 int64_t。
    static int64_t toHandle(NativeRef* p) {
        return static_cast<int64_t>(reinterpret_cast<uintptr_t>(p));
    }
    static NativeRef* fromHandle(int64_t h) {
        return reinterpret_cast<NativeRef*>(static_cast<uintptr_t>(h));
    }

private:
    NativeType type_;
};

}  // namespace minime

#endif  // MINIMEME_NATIVE_REF_HPP
