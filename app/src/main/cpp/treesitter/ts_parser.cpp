// MiniMe-core Native Viewer — ts_parser 实现
#include "ts_parser.hpp"

#include <cstring>
#include <tree_sitter/api.h>

namespace minime {

// 各 grammar 的语言构造函数（由 grammar 静态库导出）。
extern "C" {
const TSLanguage* tree_sitter_kotlin(void);
const TSLanguage* tree_sitter_java(void);
const TSLanguage* tree_sitter_python(void);
const TSLanguage* tree_sitter_javascript(void);
const TSLanguage* tree_sitter_typescript(void);
const TSLanguage* tree_sitter_tsx(void);
const TSLanguage* tree_sitter_c(void);
const TSLanguage* tree_sitter_cpp(void);
const TSLanguage* tree_sitter_go(void);
const TSLanguage* tree_sitter_rust(void);
const TSLanguage* tree_sitter_json(void);
const TSLanguage* tree_sitter_xml(void);
const TSLanguage* tree_sitter_html(void);
const TSLanguage* tree_sitter_yaml(void);
const TSLanguage* tree_sitter_bash(void);
const TSLanguage* tree_sitter_markdown(void);
}

namespace {

using LangFn = const TSLanguage* (*)(void);

struct LangEntry {
    const char* scope;
    LangFn fn;
};

// scope 别名表（扩展名/常用名 → grammar）。
const LangEntry kLangs[] = {
    {"kotlin", tree_sitter_kotlin},
    {"kt", tree_sitter_kotlin},
    {"java", tree_sitter_java},
    {"python", tree_sitter_python}, {"py", tree_sitter_python},
    {"javascript", tree_sitter_javascript}, {"js", tree_sitter_javascript},
    {"typescript", tree_sitter_typescript}, {"ts", tree_sitter_typescript},
    {"tsx", tree_sitter_tsx},
    {"c", tree_sitter_c},
    {"cpp", tree_sitter_cpp}, {"cc", tree_sitter_cpp}, {"cxx", tree_sitter_cpp},
    {"go", tree_sitter_go},
    {"rust", tree_sitter_rust}, {"rs", tree_sitter_rust},
    {"json", tree_sitter_json},
    {"xml", tree_sitter_xml},
    {"html", tree_sitter_html},
    {"yaml", tree_sitter_yaml}, {"yml", tree_sitter_yaml},
    {"bash", tree_sitter_bash}, {"sh", tree_sitter_bash},
    {"markdown", tree_sitter_markdown}, {"md", tree_sitter_markdown},
};

// 依据节点类型名猜测高亮类别（通用启发式，与具体 grammar 解耦）。
HighlightCategory classify(const char* type) {
    if (!type) return HighlightCategory::NONE;
    std::string t = type;
    // 简单子串匹配
    auto has = [&](const char* s) { return t.find(s) != std::string::npos; };
    if (has("comment")) return HighlightCategory::COMMENT;
    if (has("string") || has("char_literal") || has("string_literal") || has("interpreted")) return HighlightCategory::STRING;
    if (has("number") || has("integer") || has("float")) return HighlightCategory::NUMBER;
    if (has("boolean") || has("true") || has("false")) return HighlightCategory::CONSTANT;
    if (has("keyword")) return HighlightCategory::KEYWORD;
    if (has("type_identifier") || has("primitive_type") || has("type")) return HighlightCategory::TYPE;
    if (has("function") || has("method")) return HighlightCategory::FUNCTION;
    if (has("field") || has("property")) return HighlightCategory::PROPERTY;
    if (has("attribute") || has("annotation")) return HighlightCategory::ANNOTATION;
    if (has("namespace") || has("module")) return HighlightCategory::NAMESPACE;
    if (has("operator")) return HighlightCategory::OPERATOR;
    return HighlightCategory::NONE;
}

uint32_t lineOfByte(const std::string& src, uint32_t byte) {
    uint32_t line = 0;
    for (uint32_t i = 0; i < byte && i < src.size(); ++i) {
        if (src[i] == '\n') ++line;
    }
    return line;
}

}  // namespace

TsParser::TsParser() : NativeRef(NativeType::CODE_VIEWER) {
    parser_ = ts_parser_new();
}

TsParser::~TsParser() {
    clearResult();
    if (tree_) ts_tree_delete(tree_);
    if (parser_) ts_parser_delete(parser_);
}

Status TsParser::setLanguage(const std::string& scope) {
    for (const auto& e : kLangs) {
        if (scope == e.scope) {
            language_ = e.fn();
            if (ts_parser_set_language(parser_, language_)) {
                setLastError(Status::ERR_PARSE_FAILED, "set_language failed: " + scope);
                return Status::ERR_PARSE_FAILED;
            }
            return Status::OK;
        }
    }
    setLastError(Status::ERR_UNSUPPORTED_FORMAT, "unknown language: " + scope);
    return Status::ERR_UNSUPPORTED_FORMAT;
}

void TsParser::clearResult() {
    spans_.clear();
    folds_.clear();
    outline_.clear();
}

Status TsParser::parse(const std::string& source) {
    if (!language_) {
        setLastError(Status::ERR_INVALID_HANDLE, "language not set");
        return Status::ERR_INVALID_HANDLE;
    }
    clearResult();
    source_ = source;

    if (tree_) { ts_tree_delete(tree_); tree_ = nullptr; }
    tree_ = ts_parser_parse_string(parser_, nullptr, source_.data(), source_.size());
    if (!tree_) {
        setLastError(Status::ERR_PARSE_FAILED, "parse failed");
        return Status::ERR_PARSE_FAILED;
    }

    TSNode root = ts_tree_root_node(tree_);
    TSTreeCursor cursor = ts_tree_cursor_new(root);

    // 深度优先遍历：叶子 token（无命名子节点）输出高亮 span；
    // 跨行命名节点输出 fold；函数/类定义输出 outline。
    bool descended = true;
    while (true) {
        TSNode node = ts_tree_cursor_current_node(&cursor);
        const char* type = ts_node_type(node);
        uint32_t startByte = ts_node_start_byte(node);
        uint32_t endByte = ts_node_end_byte(node);

        // 叶子 token：命名节点且无命名子节点
        uint32_t namedChildren = 0;
        uint32_t nc = ts_node_named_child_count(node);
        namedChildren = nc;
        if (ts_node_is_named(node) && namedChildren == 0) {
            HighlightCategory cat = classify(type);
            if (cat != HighlightCategory::NONE) {
                spans_.push_back({startByte, endByte, cat});
            }
        }

        // 折叠：跨行命名块（≥2 行）
        if (ts_node_is_named(node) && namedChildren > 0) {
            uint32_t sl = lineOfByte(source_, startByte);
            uint32_t el = lineOfByte(source_, endByte);
            if (el > sl) {
                uint8_t kind = 0;
                if (std::string(type).find("comment") != std::string::npos) kind = 2;
                else if (std::string(type).find("function") != std::string::npos ||
                         std::string(type).find("method") != std::string::npos) kind = 1;
                folds_.push_back({sl, el, kind});
            }
            // outline：函数/类定义
            std::string t = type ? type : "";
            if (t.find("function") != std::string::npos || t.find("method") != std::string::npos ||
                t.find("class") != std::string::npos || t.find("struct") != std::string::npos ||
                t.find("interface") != std::string::npos) {
                // 取第一个命名标识符子节点的文本作为符号名
                uint32_t cnt = ts_node_named_child_count(node);
                std::string name;
                for (uint32_t i = 0; i < cnt; ++i) {
                    TSNode ch = ts_node_named_child(node, i);
                    const char* ct = ts_node_type(ch);
                    if (ct && (std::string(ct).find("identifier") != std::string::npos ||
                               std::string(ct).find("type_identifier") != std::string::npos)) {
                        uint32_t s = ts_node_start_byte(ch), e = ts_node_end_byte(ch);
                        name = source_.substr(s, e - s);
                        break;
                    }
                }
                if (!name.empty()) {
                    uint8_t kind = (t.find("class") != std::string::npos ||
                                    t.find("struct") != std::string::npos ||
                                    t.find("interface") != std::string::npos) ? 1 : 0;
                    outline_.push_back({name, sl, el, kind});
                }
            }
        }

        // 下降 / 兄弟
        if (descended && ts_tree_cursor_goto_first_child(&cursor)) {
            continue;
        }
        while (!ts_tree_cursor_goto_next_sibling(&cursor)) {
            if (!ts_tree_cursor_goto_parent(&cursor)) {
                goto done;
            }
        }
        descended = true;
    }
done:
    ts_tree_cursor_delete(&cursor);
    return Status::OK;
}

}  // namespace minime
