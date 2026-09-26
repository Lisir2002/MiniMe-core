# MiniMe-core Native Viewer / Editor core (libminimeviewer)
# 顶层 CMake。
cmake_minimum_required(VERSION 3.22.1)
project(minimeviewer LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ── tree-sitter 核心（静态库）──────────────────────────────────────────
set(TREESITTER_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/treesitter/thirdparty/tree-sitter)
set(TS_CORE_SOURCES
    ${TREESITTER_ROOT}/lib/src/alloc.c
    ${TREESITTER_ROOT}/lib/src/get_changed_ranges.c
    ${TREESITTER_ROOT}/lib/src/language.c
    ${TREESITTER_ROOT}/lib/src/lexer.c
    ${TREESITTER_ROOT}/lib/src/lib.c
    ${TREESITTER_ROOT}/lib/src/node.c
    ${TREESITTER_ROOT}/lib/src/parser.c
    ${TREESITTER_ROOT}/lib/src/query.c
    ${TREESITTER_ROOT}/lib/src/stack.c
    ${TREESITTER_ROOT}/lib/src/subtree.c
    ${TREESITTER_ROOT}/lib/src/tree.c
    ${TREESITTER_ROOT}/lib/src/tree_cursor.c
)
add_library(tree-sitter STATIC ${TS_CORE_SOURCES})
target_include_directories(tree-sitter PUBLIC ${TREESITTER_ROOT}/lib/include)
# 第三方生成代码：关闭警告，避免 -Werror 类问题
target_compile_options(tree-sitter PRIVATE -w)

# ── tree-sitter grammar（每个一个静态库）─────────────────────────────
# 用法：ts_grammar(<name> <src_dir> [scanner.c])
function(ts_grammar name src_dir)
    set(sources ${src_dir}/parser.c)
    foreach(f ${ARGN})
        list(APPEND sources ${src_dir}/${f})
    endforeach()
    add_library(ts_${name} STATIC ${sources})
    target_include_directories(ts_${name}
        PUBLIC ${src_dir}                       # 让 parser.c 找到 tree_sitter/parser.h
        PRIVATE ${TREESITTER_ROOT}/lib/include)
    target_compile_options(ts_${name} PRIVATE -w)
endfunction()

set(TS_G ${CMAKE_CURRENT_SOURCE_DIR}/treesitter/thirdparty)
# typescript / tsx / xml 整仓 vendor（保留仓库内 common/ 共享 scanner.h）。
set(TS_REPO_TS ${TS_G}/tree-sitter-typescript-repo)
set(TS_REPO_XML ${TS_G}/tree-sitter-xml-repo)
# 语法清单：ts_grammar(<name> <src_dir> [scanner.c])。SQL 暂缺（仓库未提交 parser.c，延后）。
ts_grammar(kotlin     ${TS_G}/tree-sitter-kotlin/src     scanner.c)
ts_grammar(java       ${TS_G}/tree-sitter-java/src)
ts_grammar(python     ${TS_G}/tree-sitter-python/src    scanner.c)
ts_grammar(javascript ${TS_G}/tree-sitter-javascript/src scanner.c)
ts_grammar(typescript ${TS_REPO_TS}/typescript/src      scanner.c)
ts_grammar(tsx        ${TS_REPO_TS}/tsx/src             scanner.c)
ts_grammar(c          ${TS_G}/tree-sitter-c/src)
ts_grammar(cpp        ${TS_G}/tree-sitter-cpp/src       scanner.c)
ts_grammar(go         ${TS_G}/tree-sitter-go/src)
ts_grammar(rust       ${TS_G}/tree-sitter-rust/src      scanner.c)
ts_grammar(json       ${TS_G}/tree-sitter-json/src)
ts_grammar(xml       ${TS_REPO_XML}/xml/src            scanner.c)
ts_grammar(html       ${TS_G}/tree-sitter-html/src      scanner.c)
ts_grammar(yaml       ${TS_G}/tree-sitter-yaml/src      scanner.c)
ts_grammar(bash       ${TS_G}/tree-sitter-bash/src      scanner.c)
ts_grammar(markdown   ${TS_G}/tree-sitter-markdown/src   scanner.c)

# ── 主模块源文件 ──────────────────────────────────────────────────────
set(MINIMEVIEWER_SOURCES
    jni_bridge.cpp
    core/file_loader.cpp
    core/gbk_table.cpp
    treesitter/ts_parser.cpp
)

add_library(minimeviewer SHARED ${MINIMEVIEWER_SOURCES})
target_include_directories(minimeviewer PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(minimeviewer PRIVATE
    tree-sitter
    ts_kotlin ts_java ts_python ts_javascript ts_typescript ts_tsx
    ts_c ts_cpp ts_go ts_rust ts_json ts_xml ts_html ts_yaml ts_bash ts_markdown
    log android)

if(CMAKE_BUILD_TYPE STREQUAL "Release" OR CMAKE_BUILD_TYPE STREQUAL "RelWithDebInfo")
    # 体积优先：-Oz + LTO + 死代码消除，grammar 体积可再降 15-20%
    target_compile_options(minimeviewer PRIVATE -Oz -fno-stack-protector -ffunction-sections -fdata-sections)
    target_link_options(minimeviewer PRIVATE
        -Oz
        -flto=thin
        -Wl,--gc-sections
        -Wl,--exclude-libs,ALL
        -Wl,--icf=all
    )
    # grammar 静态库也应用 LTO（需编译时带 -flto）
    target_compile_options(tree-sitter PRIVATE -Oz -flto=thin)
    get_property(_grammars DIRECTORY PROPERTY BUILDSYSTEM_TARGETS)
    foreach(_t ${_grammars})
        if(_t MATCHES "^ts_")
            target_compile_options(${_t} PRIVATE -Oz -flto=thin)
        endif()
    endforeach()
endif()
