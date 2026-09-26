// MiniMe-core Native Viewer — file_loader 实现
// 编码转换全部自包含（不依赖 iconv：bionic iconv 仅支持 UTF-8/UTF-16，不含 GBK）。
#include "file_loader.hpp"

#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>

#include <cstdint>
#include <cstring>
#include <fstream>

namespace minime {

namespace {

constexpr int64_t kMmapThreshold = 50LL * 1024 * 1024;  // >50MB 走 mmap

void appendUtf8(std::string& out, uint32_t cp) {
    if (cp <= 0x7F) {
        out.push_back(static_cast<char>(cp));
    } else if (cp <= 0x7FF) {
        out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    } else if (cp <= 0xFFFF) {
        out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    } else {
        out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    }
}

bool isValidUtf8(const char* data, size_t len) {
    size_t i = 0;
    while (i < len) {
        unsigned char c = static_cast<unsigned char>(data[i]);
        size_t extra = 0;
        uint32_t codepoint = 0;
        if (c <= 0x7F) { ++i; continue; }
        else if ((c & 0xE0) == 0xC0) { extra = 1; codepoint = c & 0x1F; if (c < 0xC2) return false; }
        else if ((c & 0xF0) == 0xE0) { extra = 2; codepoint = c & 0x0F; }
        else if ((c & 0xF8) == 0xF0) { extra = 3; codepoint = c & 0x07; if (c > 0xF4) return false; }
        else return false;
        if (i + extra >= len) return false;
        for (size_t k = 1; k <= extra; ++k) {
            unsigned char t = static_cast<unsigned char>(data[i + k]);
            if ((t & 0xC0) != 0x80) return false;
            codepoint = (codepoint << 6) | (t & 0x3F);
        }
        if (codepoint >= 0xD800 && codepoint <= 0xDFFF) return false;
        if (codepoint > 0x10FFFF) return false;
        if (extra == 1 && codepoint < 0x80) return false;
        if (extra == 2 && codepoint < 0x800) return false;
        if (extra == 3 && codepoint < 0x10000) return false;
        i += extra + 1;
    }
    return true;
}

int countCjkPairs(const char* data, size_t len) {
    int hits = 0;
    for (size_t i = 0; i + 1 < len; ++i) {
        unsigned char a = static_cast<unsigned char>(data[i]);
        unsigned char b = static_cast<unsigned char>(data[i + 1]);
        if (a >= 0x81 && a <= 0xFE && b >= 0x40 && b <= 0xFE) { ++hits; ++i; }
    }
    return hits;
}

std::string decodeUtf16(std::string_view raw, bool bigEndian) {
    std::string out;
    out.reserve(raw.size());
    size_t i = 0;
    while (i + 1 < raw.size()) {
        uint16_t u = bigEndian ? ((static_cast<uint16_t>(static_cast<unsigned char>(raw[i])) << 8) |
                                  static_cast<unsigned char>(raw[i + 1]))
                               : (static_cast<unsigned char>(raw[i]) |
                                  (static_cast<uint16_t>(static_cast<unsigned char>(raw[i + 1])) << 8));
        i += 2;
        uint32_t cp;
        if (u >= 0xD800 && u <= 0xDBFF && i + 1 < raw.size()) {
            uint16_t lo = bigEndian ? ((static_cast<uint16_t>(static_cast<unsigned char>(raw[i])) << 8) |
                                       static_cast<unsigned char>(raw[i + 1]))
                                    : (static_cast<unsigned char>(raw[i]) |
                                       (static_cast<uint16_t>(static_cast<unsigned char>(raw[i + 1])) << 8));
            i += 2;
            cp = 0x10000u + ((u - 0xD800u) << 10) + (lo - 0xDC00u);
        } else {
            cp = u;
        }
        appendUtf8(out, cp);
    }
    return out;
}

std::string decodeLatin1(std::string_view raw) {
    std::string out;
    out.reserve(raw.size() * 2);
    for (unsigned char c : raw) appendUtf8(out, c);
    return out;
}

}  // namespace

// GBK/GB18030 → UTF-8：由独立映射表提供（gbk_table.cpp）。
std::string decodeGbk(const std::string& raw);

FileLoader::FileLoader() : NativeRef(NativeType::FILE_LOADER) {}
FileLoader::~FileLoader() { close(); }

Status FileLoader::mapRaw(const std::string& path) {
    close();
    int fd = ::open(path.c_str(), O_RDONLY);
    if (fd < 0) {
        setLastError(Status::ERR_FILE_NOT_FOUND, path);
        return Status::ERR_FILE_NOT_FOUND;
    }
    struct stat sb;
    if (::fstat(fd, &sb) != 0) { ::close(fd); return Status::ERR_FILE_NOT_FOUND; }
    info_.size = sb.st_size;
    info_.readOnly = (::access(path.c_str(), W_OK) != 0);

    if (sb.st_size > kMmapThreshold) {
        void* p = ::mmap(nullptr, sb.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
        if (p != MAP_FAILED) {
            mapped_ = p;
            mappedLen_ = static_cast<size_t>(sb.st_size);
            rawView_ = std::string_view(static_cast<const char*>(p), mappedLen_);
            info_.mmapBacked = true;
        }
    }
    ::close(fd);

    if (!info_.mmapBacked) {
        std::ifstream file(path, std::ios::binary);
        if (!file.is_open()) return Status::ERR_FILE_NOT_FOUND;
        rawHeap_.assign(std::istreambuf_iterator<char>(file), std::istreambuf_iterator<char>());
        rawView_ = rawHeap_;
    }
    info_.largeFileMode = info_.size > 100LL * 1024 * 1024;
    return Status::OK;
}

Status FileLoader::open(const std::string& path) {
    info_.path = path;
    Status st = mapRaw(path);
    if (st != Status::OK) return st;

    size_t bomLen = 0;
    info_.encoding = detectEncoding(rawView_, bomLen);
    st = decodeToUtf8(rawView_, bomLen, content_);
    if (st != Status::OK) return st;
    normalizeAndIndex();
    info_.lineCount = static_cast<int64_t>(lineOffsets_.size() > 1 ? lineOffsets_.size() - 1 : 0);
    return Status::OK;
}

Status FileLoader::setEncoding(Encoding enc) {
    if (rawView_.empty() && mapped_ == nullptr) {
        setLastError(Status::ERR_INVALID_HANDLE, "not open");
        return Status::ERR_INVALID_HANDLE;
    }
    info_.encoding = enc;
    size_t bomLen = 0;
    // 手动切换时仍需跳过 BOM（若存在）。
    const unsigned char* d = reinterpret_cast<const unsigned char*>(rawView_.data());
    size_t n = rawView_.size();
    if (n >= 3 && d[0] == 0xEF && d[1] == 0xBB && d[2] == 0xBF) bomLen = 3;
    else if (n >= 2 && d[0] == 0xFF && d[1] == 0xFE) bomLen = 2;
    else if (n >= 2 && d[0] == 0xFE && d[1] == 0xFF) bomLen = 2;

    Status st = decodeToUtf8(rawView_, bomLen, content_);
    if (st != Status::OK) return st;
    normalizeAndIndex();
    info_.lineCount = static_cast<int64_t>(lineOffsets_.size() > 1 ? lineOffsets_.size() - 1 : 0);
    return Status::OK;
}

void FileLoader::close() {
    content_.clear();
    lineOffsets_.clear();
    rawHeap_.clear();
    if (mapped_ != nullptr) {
        ::munmap(mapped_, mappedLen_);
        mapped_ = nullptr;
        mappedLen_ = 0;
    }
    rawView_ = {};
    info_ = FileInfo();
}

Encoding FileLoader::detectEncoding(std::string_view raw, size_t& bomLen) const {
    bomLen = 0;
    const unsigned char* d = reinterpret_cast<const unsigned char*>(raw.data());
    size_t n = raw.size();
    if (n >= 3 && d[0] == 0xEF && d[1] == 0xBB && d[2] == 0xBF) { bomLen = 3; return Encoding::UTF8; }
    if (n >= 2 && d[0] == 0xFF && d[1] == 0xFE) { bomLen = 2; return Encoding::UTF16LE; }
    if (n >= 2 && d[0] == 0xFE && d[1] == 0xFF) { bomLen = 2; return Encoding::UTF16BE; }
    size_t sampleLen = n < 8192 ? n : 8192;
    if (isValidUtf8(raw.data(), sampleLen)) return Encoding::UTF8;
    if (countCjkPairs(raw.data(), sampleLen) >= 4) return Encoding::GB18030;
    return Encoding::LATIN1;
}

Status FileLoader::decodeToUtf8(std::string_view raw, size_t bomLen, std::string& out) const {
    out.clear();
    if (bomLen >= raw.size()) return Status::OK;
    std::string_view body = raw.substr(bomLen);
    switch (info_.encoding) {
        case Encoding::UTF8: out = std::string(body); break;
        case Encoding::UTF16LE: out = decodeUtf16(body, false); break;
        case Encoding::UTF16BE: out = decodeUtf16(body, true); break;
        case Encoding::GB18030: out = decodeGbk(std::string(body)); break;
        case Encoding::LATIN1: out = decodeLatin1(body); break;
    }
    return Status::OK;
}

void FileLoader::normalizeAndIndex() {
    lineOffsets_.clear();
    lineOffsets_.push_back(0);
    if (content_.empty()) return;
    LineEnding le = LineEnding::LF;
    if (content_.find("\r\n") != std::string::npos) le = LineEnding::CRLF;
    else if (content_.find('\n') != std::string::npos) le = LineEnding::LF;
    else if (content_.find('\r') != std::string::npos) le = LineEnding::CR;
    info_.lineEnding = le;

    std::string normalized;
    normalized.reserve(content_.size());
    size_t i = 0;
    const size_t n = content_.size();
    while (i < n) {
        char c = content_[i];
        if (c == '\r') {
            if (i + 1 < n && content_[i + 1] == '\n') ++i;
            normalized.push_back('\n');
            lineOffsets_.push_back(static_cast<uint32_t>(normalized.size()));
            ++i;
        } else if (c == '\n') {
            normalized.push_back('\n');
            lineOffsets_.push_back(static_cast<uint32_t>(normalized.size()));
            ++i;
        } else {
            normalized.push_back(c);
            ++i;
        }
    }
    if (!normalized.empty() && normalized.back() != '\n') {
        lineOffsets_.push_back(static_cast<uint32_t>(normalized.size()));
    }
    content_.swap(normalized);
}

std::vector<std::string> FileLoader::readLines(int64_t start, int64_t end) {
    std::vector<std::string> result;
    const int64_t total = info_.lineCount;
    if (total <= 0) return result;
    if (start < 0) start = 0;
    if (end > total) end = total;
    if (start >= end) return result;
    result.reserve(static_cast<size_t>(end - start));
    for (int64_t line = start; line < end; ++line) {
        uint32_t s = lineOffsets_[static_cast<size_t>(line)];
        uint32_t e = lineOffsets_[static_cast<size_t>(line) + 1];
        if (e > s && content_[e - 1] == '\n') --e;
        result.emplace_back(content_, s, e - s);
    }
    return result;
}

int FileLoader::writeLinesToBuffer(int64_t start, int64_t end, char* buf, size_t bufCap,
                                    size_t& outBytes) {
    outBytes = 0;
    const int64_t total = info_.lineCount;
    if (total <= 0) { setLastError(Status::ERR_OUT_OF_RANGE, "empty"); return 0; }
    if (start < 0) start = 0;
    if (end > total) end = total;
    if (start >= end) { setLastError(Status::ERR_OUT_OF_RANGE, "empty range"); return 0; }

    int count = 0;
    for (int64_t line = start; line < end; ++line) {
        uint32_t s = lineOffsets_[static_cast<size_t>(line)];
        uint32_t e = lineOffsets_[static_cast<size_t>(line) + 1];
        if (e > s && content_[e - 1] == '\n') --e;
        uint32_t len = e - s;
        size_t need = 4 + len;
        if (outBytes + need > bufCap) {
            setLastError(Status::ERR_OUT_OF_RANGE, "buffer too small");
            return count;  // 已写入的部分返回，Kotlin 按行数解析
        }
        // 4 字节小端长度前缀
        buf[outBytes + 0] = static_cast<char>(len & 0xFF);
        buf[outBytes + 1] = static_cast<char>((len >> 8) & 0xFF);
        buf[outBytes + 2] = static_cast<char>((len >> 16) & 0xFF);
        buf[outBytes + 3] = static_cast<char>((len >> 24) & 0xFF);
        std::memcpy(buf + outBytes + 4, content_.data() + s, len);
        outBytes += need;
        ++count;
    }
    return count;
}

}  // namespace minime
