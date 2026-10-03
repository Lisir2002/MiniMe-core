#!/usr/bin/env python3
"""
CI 预检查脚本：在编译前自动拦截常见错误，避免等到 Lint/编译阶段才失败。

检查项：
1. 所有模块专用 strings_*.xml 必须在根元素添加 translatable="false"
   （中文应用，模块专用字符串不需要国际化，防止 MissingTranslation 错误）
2. 禁止存在 values-en 目录（应用面向中文用户，统一不做英文翻译）
3. 禁止在 Composable 中直接调用 LocalContext.current.getString()
   （必须用 stringResource()，防止 LocalContextGetResourceValueCall Lint 错误）
4. 检查 @SuppressLint 注解是否有对应的 import

用法：python3 scripts/ci/pre-build-check.py
退出码：0=全部通过，1=有错误
"""

import os
import re
import sys

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
VALUES_DIR = os.path.join(PROJECT_ROOT, "app/src/main/res/values")
JAVA_SRC_DIR = os.path.join(PROJECT_ROOT, "app/src/main/java")

errors = []
warnings = []


def check_strings_translatable():
    """检查所有模块专用 strings_*.xml 是否有 translatable="false" """
    if not os.path.isdir(VALUES_DIR):
        return

    for filename in os.listdir(VALUES_DIR):
        if not filename.startswith("strings_") or not filename.endswith(".xml"):
            continue
        if filename == "strings.xml":
            continue

        filepath = os.path.join(VALUES_DIR, filename)
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()

        # 检查前3行是否有 translatable="false"
        first_lines = "\n".join(content.split("\n")[:3])
        if 'translatable="false"' not in first_lines:
            errors.append(
                f"[strings] {filename} 缺少 translatable=\"false\" 属性\n"
                f"  修复：将 <resources> 改为 <resources translatable=\"false\">\n"
                f"  原因：模块专用字符串不需要国际化，防止 MissingTranslation Lint 错误"
            )


def check_no_values_en():
    """检查是否存在 values-en 目录（中文应用不需要英文翻译）"""
    res_dir = os.path.join(PROJECT_ROOT, "app/src/main/res")
    values_en_dir = os.path.join(res_dir, "values-en")

    if os.path.isdir(values_en_dir):
        errors.append(
            f"[i18n] 存在 values-en 目录: {values_en_dir}\n"
            f"  修复：删除 values-en 目录\n"
            f"  原因：应用面向中文用户，所有模块 strings 已标记 translatable=false，"
            f"保留英文翻译会导致 ExtraTranslation Lint 错误"
        )


def check_compose_local_context_getstring():
    """检查 Kotlin 文件中是否在 Composable 中直接调用 LocalContext.current.getString() """
    if not os.path.isdir(JAVA_SRC_DIR):
        return

    # 匹配 LocalContext.current.getString( 或 LocalContext.current.getText(
    pattern = re.compile(r'LocalContext\.current\.(getString|getText|getQuantityString)\(')

    for root, dirs, files in os.walk(JAVA_SRC_DIR):
        for filename in files:
            if not filename.endswith(".kt"):
                continue
            filepath = os.path.join(root, filename)
            rel_path = os.path.relpath(filepath, PROJECT_ROOT)

            with open(filepath, "r", encoding="utf-8") as f:
                lines = f.readlines()

            for i, line in enumerate(lines, 1):
                if pattern.search(line):
                    # 检查是否在 @SuppressLint 注解的函数内（简单检查前后5行）
                    start = max(0, i - 10)
                    context = "".join(lines[start:i])
                    if "@SuppressLint" in context and "LocalContextGetResourceValueCall" in context:
                        warnings.append(
                            f"[compose] {rel_path}:{i} 使用了 LocalContext.current.getString()，"
                            f"但有 @SuppressLint 抑制（建议优先用 stringResource()）"
                        )
                    else:
                        errors.append(
                            f"[compose] {rel_path}:{i} 在 Composable 中直接调用 LocalContext.current.getString()\n"
                            f"  修复：改用 stringResource(R.string.xxx) 获取无参数资源；\n"
                            f"        带参数的资源用 val context = LocalContext.current 在 Composable 顶层获取，"
                            f"然后在 lambda 中使用 context.getString()\n"
                            f"  原因：触发 LocalContextGetResourceValueCall Lint 错误"
                        )


def check_suppress_lint_import():
    """检查使用 @SuppressLint 的文件是否有对应的 import """
    if not os.path.isdir(JAVA_SRC_DIR):
        return

    for root, dirs, files in os.walk(JAVA_SRC_DIR):
        for filename in files:
            if not filename.endswith(".kt"):
                continue
            filepath = os.path.join(root, filename)
            rel_path = os.path.relpath(filepath, PROJECT_ROOT)

            with open(filepath, "r", encoding="utf-8") as f:
                content = f.read()

            if "@SuppressLint" in content:
                if "import android.annotation.SuppressLint" not in content:
                    errors.append(
                        f"[import] {rel_path} 使用了 @SuppressLint 但缺少 import\n"
                        f"  修复：添加 import android.annotation.SuppressLint\n"
                        f"  原因：编译时会报 Unresolved reference: SuppressLint 错误"
                    )


def check_keystore_gitignore():
    """检查 keystore 相关文件是否在 .gitignore 中（防止密钥泄露）"""
    gitignore_path = os.path.join(PROJECT_ROOT, ".gitignore")
    if not os.path.isfile(gitignore_path):
        warnings.append("[security] .gitignore 文件不存在")
        return

    with open(gitignore_path, "r", encoding="utf-8") as f:
        content = f.read()

    keystore_patterns = ["*.jks", "*.keystore", "keystore.properties"]
    for pattern in keystore_patterns:
        if pattern not in content:
            warnings.append(
                f"[security] .gitignore 中缺少 {pattern} 规则（防止签名密钥泄露到仓库）"
            )


def main():
    print("=" * 60)
    print("CI 预检查（编译前自动拦截常见错误）")
    print("=" * 60)
    print()

    check_strings_translatable()
    check_no_values_en()
    check_compose_local_context_getstring()
    check_suppress_lint_import()
    check_keystore_gitignore()

    if warnings:
        print(f"⚠️  警告 ({len(warnings)} 项):")
        print("-" * 40)
        for w in warnings:
            print(f"  {w}")
        print()

    if errors:
        print(f"❌ 错误 ({len(errors)} 项):")
        print("-" * 40)
        for e in errors:
            print(f"  {e}")
            print()
        print(f"预检查失败：发现 {len(errors)} 个错误，请修复后再继续构建。")
        sys.exit(1)
    else:
        print("✅ 预检查全部通过！")
        if warnings:
            print(f"   （有 {len(warnings)} 个警告，不阻断构建但建议处理）")
        sys.exit(0)


if __name__ == "__main__":
    main()
