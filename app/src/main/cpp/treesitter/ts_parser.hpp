// MiniMe-core Native Viewer — tree-sitter 封装（ts_parser）
// 设计文档 §3.2：setLanguage / 全量 parse、AST 遍历计算高亮 span、折叠区域、符号大纲。
// 语义类别与主题解耦：native 只输出类别 ID，颜色由 Kotlin 侧 CodeTheme 映射。
#ifndef MINIMEME_TS_PARSER_HPP
#define MINIMEME_TS_PARSER_HPP

#include <cstdint>
#include <string>
#include <vector>

#include "common/status.hpp"
#include "common/native_ref.hpp"

// 前置声明 tree-sitter C 类型，避免在这里暴露 api.h。
typedef struct TSParser TSParser;
typedef struct TSTree TSTree;
typedef struct TSLanguage TSLanguage;

namespace minime {

// 高亮语义类别（与设计文档 §3.2 一致，与颜色解耦）。
enum class HighlightCategory : uint8_t {
    NONE = 0,
    KEYWORD = 1,
    TYPE = 2,
    FUNCTION = 3,
    STRING = 4,
    NUMBER = 5,
    COMMENT = 6,
    OPERATOR = 7,
    PROPERTY = 8,
    VARIABLE = 9,
    CONSTANT = 10,
    TAG = 11,
    ATTRIBUTE = 12,
    PARAMETER = 13,
    ANNOTATION = 14,
    NAMESPACE = 15,
    PUNCTUATION = 16,
    TEXT = 17,
};

struct HighlightSpan {
    uint32_t startByte;
    uint32_t endByte;
    HighlightCategory category;
};

struct FoldRegion {
    uint32_t startLine;
    uint32_t endLine;
    uint8_t kind;  // 0=block 1=function 2=comment
};

struct SymbolNode {
    std::string name;
    uint32_t startLine;
    uint32_t endLine;
    uint8_t kind;  // 0=function 1=class/type 2=other
};

class TsParser : public NativeRef {
public:
    TsParser();
    ~TsParser() override;

    TsParser(const TsParser&) = delete;
    TsParser& operator=(const TsParser&) = delete;

    // 按 scope（如 "kotlin"/"python"/"cpp"）选择语言。未知 scope 返回 ERR_UNSUPPORTED_FORMAT。
    Status setLanguage(const std::string& scope);

    // 全量解析 UTF-8 源码，计算高亮 spans。折叠/大纲可随后通过 getter 获取。
    Status parse(const std::string& source);

    const std::vector<HighlightSpan>& spans() const { return spans_; }
    const std::vector<FoldRegion>& folds() const { return folds_; }
    const std::vector<SymbolNode>& outline() const { return outline_; }
    bool languageIsSet() const { return language_ != nullptr; }

private:
    TSParser* parser_ = nullptr;
    TSTree* tree_ = nullptr;
    const TSLanguage* language_ = nullptr;
    std::string source_;

    std::vector<HighlightSpan> spans_;
    std::vector<FoldRegion> folds_;
    std::vector<SymbolNode> outline_;

    void clearResult();
};

}  // namespace minime

#endif  // MINIMEME_TS_PARSER_HPP
