// MiniMe-core Native Viewer — 文件加载模块（file_loader）
// 设计文档 §3.1 + §2.3：流式读取、编码检测、换行统一、行偏移索引、大文件策略；
// >50MB 用 mmap 零拷贝映射，行索引用 uint32_t 紧凑数组，支持手动切换编码重解码。
#ifndef MINIMEME_FILE_LOADER_HPP
#define MINIMEME_FILE_LOADER_HPP

#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

#include "common/status.hpp"
#include "common/native_ref.hpp"

namespace minime {

enum class Encoding : int {
    UTF8 = 0,
    UTF16LE = 1,
    UTF16BE = 2,
    GB18030 = 3,   // GBK/GB2312 超集，启发式命中时使用
    LATIN1 = 4,    // 兜底
};

enum class LineEnding : int {
    LF = 0,
    CRLF = 1,
    CR = 2,
};

struct FileInfo {
    std::string path;
    int64_t size = 0;                 // 文件字节大小（原始）
    Encoding encoding = Encoding::UTF8;
    LineEnding lineEnding = LineEnding::LF;
    bool readOnly = false;
    int64_t lineCount = 0;
    bool largeFileMode = false;       // >100MB：只读大文件模式，禁用全量搜索
    bool mmapBacked = false;          // >50MB：mmap 映射，不占堆
};

class FileLoader : public NativeRef {
public:
    FileLoader();
    ~FileLoader() override;

    FileLoader(const FileLoader&) = delete;
    FileLoader& operator=(const FileLoader&) = delete;

    // 打开文件并构建行索引。成功返回 Status::OK。
    Status open(const std::string& path);
    void close();

    // 手动切换编码（解决自动检测误判）：按指定编码重新解码并重建行索引。
    Status setEncoding(Encoding enc);

    // 按行范围读取（含 start，不含 end）。行号从 0 开始；越界自动截断。
    std::vector<std::string> readLines(int64_t start, int64_t end);

    // 零拷贝写行到调用方提供的扁平缓冲（DirectByteBuffer）：
    // 每行以 4 字节小端长度前缀 + UTF-8 字节序列连续写入。返回行数；写出总字节数写入 outBytes。
    // buf/ bufCap 为 Kotlin 侧 DirectByteBuffer 起始地址与容量。缓冲不足时返回 ERR_OUT_OF_RANGE。
    int writeLinesToBuffer(int64_t start, int64_t end, char* buf, size_t bufCap, size_t& outBytes);

    const std::string& content() const { return content_; }
    const FileInfo& info() const { return info_; }

private:
    // 读取原始字节到 rawView（mmap 时为映射视图，否则堆缓冲 rawHeap_）。
    Status mapRaw(const std::string& path);
    Encoding detectEncoding(std::string_view raw, size_t& bomLen) const;
    Status decodeToUtf8(std::string_view raw, size_t bomLen, std::string& out) const;
    void normalizeAndIndex();

    FileInfo info_;
    std::string content_;                  // 解码后的 UTF-8 全文
    std::string rawHeap_;                  // <50MB 堆缓冲
    void* mapped_ = nullptr;               // mmap 映射地址
    size_t mappedLen_ = 0;
    std::string_view rawView_;             // 原始字节视图（mmap 或堆）
    std::vector<uint32_t> lineOffsets_;    // 紧凑行偏移；末项为 content_.size()
};

}  // namespace minime

#endif  // MINIMEME_FILE_LOADER_HPP
