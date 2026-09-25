#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-release-format.py — MiniMe-core 发版格式校验脚本。

发版后运行，校验 GitHub Release 的标题和正文是否符合 AGENTS.md 发版规范。
校验不通过时输出具体不合规项和修复建议，退出码非零。

用法：
  python3 scripts/gitops/check-release-format.py --tag v0.0.0.19
  python3 scripts/gitops/check-release-format.py --latest
  python3 scripts/gitops/check-release-format.py --tag logviewer-v0.0.8 --app logviewer

校验项：
  1. 标题格式：{软件名} v{版本} — {更新概括}（概括≤20字）
  2. 正文无占位符：请补充、请提炼、_（
  3. 条目格式：- **{4-9字小标题}**：{20-40字说明}
  4. 无 emoji
  5. 无内部技术术语（类名、文件名、包名）
  6. 分类正确：新功能/改进/修复/移除
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

# 软件名配置
APP_NAMES = {
    "main": "MiniMe-core",
    "logviewer": "MiniMe Logs",
}

# emoji 正则（覆盖常见 emoji 范围）
EMOJI_REGEX = re.compile(
    "["
    "\U0001F300-\U0001F9FF"  # 杂项符号和象形文字
    "\U0001FA00-\U0001FA6F"  # 扩展象形文字
    "\U00002600-\U000027BF"  # 杂项符号/装饰符号
    "\U0001F1E0-\U0001F1FF"  # 国旗
    "]"
)

# 占位符正则
PLACEHOLDER_REGEX = re.compile(r"请补充|请提炼|_\（|_TODO_|FIXME")

# 条目格式正则：- **{4-9字}**：{20-40字}
ITEM_REGEX = re.compile(r"^- \*\*(.+?)\*\*[：:](.+)$")

# 内部技术术语正则
INTERNAL_TERM_REGEX = re.compile(
    r"\b[A-Z][a-z]+(?:[A-Z][a-z]+)+\.(kt|java|py)\b"  # 类名.后缀
    r"|\bcom\.mini\.\w+\b"  # 包名
    r"|\b[A-Z][a-z]+(?:[A-Z][a-z]+){2,}\b"  # 大驼峰类名（3词以上）
    r"|\.gradle\b|\.yml\b|\.yaml\b"  # 配置文件后缀
)

# 标题格式正则
TITLE_REGEX = re.compile(r"^(.+?) v(\d+\.\d+\.\d+\.\d+) — (.+)$")


def run_git(args, repo):
    """执行git命令。"""
    return subprocess.run(
        ["git"] + args, cwd=str(repo), capture_output=True, text=True, check=True
    ).stdout.strip()


def get_release_by_tag(tag, repo):
    """通过GitHub API获取Release信息。"""
    token = os.environ.get("GITHUB_TOKEN", "")
    if not token:
        # 尝试从git config获取
        try:
            remote = run_git(["remote", "get-url", "origin"], repo)
            if "github.com" in remote and "@" in remote:
                token = remote.split("@")[0].split("://")[-1]
        except Exception:
            pass

    if not token:
        print("::error::未找到 GITHUB_TOKEN，无法访问 GitHub API")
        sys.exit(1)

    # 从remote解析owner/repo
    remote = run_git(["remote", "get-url", "origin"], repo)
    m = re.search(r"github\.com[:/]([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\.git)?$", remote)
    if not m:
        print(f"::error::无法从 remote 解析仓库地址: {remote}")
        sys.exit(1)
    repo_full = m.group(1)

    cmd = [
        "curl", "-s",
        "-H", f"Authorization: token {token}",
        f"https://api.github.com/repos/{repo_full}/releases/tags/{tag}",
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"::error::GitHub API 请求失败: {result.stderr}")
        sys.exit(1)

    data = json.loads(result.stdout)
    if "message" in data and data["message"] == "Not Found":
        print(f"::error::未找到 tag={tag} 的 Release")
        sys.exit(1)
    return data


def get_latest_release(repo, app="main"):
    """获取最新Release。"""
    token = os.environ.get("GITHUB_TOKEN", "")
    remote = run_git(["remote", "get-url", "origin"], repo)
    m = re.search(r"github\.com[:/]([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\.git)?$", remote)
    repo_full = m.group(1)

    prefix = "logviewer-v" if app == "logviewer" else "v"
    cmd = [
        "curl", "-s",
        "-H", f"Authorization: token {token}",
        f"https://api.github.com/repos/{repo_full}/releases?per_page=30",
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    releases = json.loads(result.stdout)

    for r in releases:
        if r["tag_name"].startswith(prefix) and "-rc" not in r["tag_name"]:
            return r
    print(f"::error::未找到 {app} 应用的最新正式 Release")
    sys.exit(1)


def check_title(title, app):
    """校验标题格式。"""
    errors = []
    expected_name = APP_NAMES[app]

    if not title:
        errors.append("标题为空")
        return errors

    m = TITLE_REGEX.match(title)
    if not m:
        errors.append(
            f"标题格式不符合规范，应为「{expected_name} v{{版本}} — {{更新概括}}」\n"
            f"  当前: {title}"
        )
        return errors

    name, version, summary = m.groups()

    if name != expected_name:
        errors.append(
            f"标题软件名错误：应为「{expected_name}」，当前为「{name}」"
        )

    if len(summary) > 20:
        errors.append(
            f"标题更新概括超过20字（当前{len(summary)}字）：{summary}"
        )

    if len(summary) < 4:
        errors.append(
            f"标题更新概括过短（当前{len(summary)}字），建议4-20字：{summary}"
        )

    return errors


def check_body(body):
    """校验正文格式。"""
    errors = []
    warnings = []

    if not body:
        errors.append("正文为空")
        return errors, warnings

    # 1. 占位符检查
    placeholders = PLACEHOLDER_REGEX.findall(body)
    if placeholders:
        errors.append(
            f"正文包含 {len(placeholders)} 处占位符残留：{placeholders[:3]}"
        )

    # 2. emoji检查
    emojis = EMOJI_REGEX.findall(body)
    if emojis:
        errors.append(f"正文包含 {len(emojis)} 个 emoji")

    # 3. 条目格式检查
    lines = body.split("\n")
    item_count = 0
    bad_items = []
    for i, line in enumerate(lines, 1):
        line = line.strip()
        if not line.startswith("- **"):
            continue
        item_count += 1
        m = ITEM_REGEX.match(line)
        if not m:
            bad_items.append(f"第{i}行：条目格式不符合「**小标题**：说明」规范")
            continue
        title, desc = m.groups()
        if not (4 <= len(title) <= 9):
            bad_items.append(
                f"第{i}行：小标题「{title}」字数为{len(title)}，应为4-9字"
            )
        if not (20 <= len(desc) <= 40):
            bad_items.append(
                f"第{i}行：说明「{desc[:20]}...」字数为{len(desc)}，应为20-40字"
            )

    if bad_items:
        errors.append(f"{len(bad_items)} 条条目格式不合规：")
        errors.extend(f"  - {b}" for b in bad_items[:5])
        if len(bad_items) > 5:
            errors.append(f"  ... 还有 {len(bad_items)-5} 条")

    if item_count == 0:
        warnings.append("正文未检测到任何条目，可能格式异常")

    # 4. 内部术语检查
    terms = INTERNAL_TERM_REGEX.findall(body)
    if terms:
        warnings.append(f"正文可能包含 {len(terms)} 处内部技术术语，请人工复核")

    # 5. 分类检查
    valid_categories = ["新功能", "改进", "修复", "移除", "破坏性变更", "已知问题", "安全"]
    found_categories = re.findall(r"### (.+)", body)
    for cat in found_categories:
        if cat.strip() not in valid_categories:
            warnings.append(f"未知分类「{cat}」，标准分类为：{valid_categories}")

    return errors, warnings


def main():
    parser = argparse.ArgumentParser(description="MiniMe-core 发版格式校验")
    parser.add_argument("--tag", help="校验指定 tag 的 Release")
    parser.add_argument("--latest", action="store_true", help="校验最新正式 Release")
    parser.add_argument("--app", choices=["main", "logviewer"], default="main",
                        help="应用类型（main=主应用, logviewer=附属应用）")
    parser.add_argument("--repo", default=None, help="仓库根路径")
    args = parser.parse_args()

    repo = Path(args.repo) if args.repo else \
        Path(run_git(["rev-parse", "--show-toplevel"], Path.cwd()))

    # 获取Release
    if args.latest:
        release = get_latest_release(repo, args.app)
    elif args.tag:
        release = get_release_by_tag(args.tag, repo)
    else:
        print("::error::请指定 --tag 或 --latest")
        sys.exit(1)

    title = release.get("name", "")
    body = release.get("body", "") or ""
    tag = release.get("tag_name", "")

    print(f"=== 发版格式校验: {tag} ===")
    print(f"标题: {title}")
    print(f"正文长度: {len(body)} 字符")
    print()

    all_errors = []
    all_warnings = []

    # 校验标题
    print("--- 标题校验 ---")
    title_errors = check_title(title, args.app)
    if title_errors:
        all_errors.extend(title_errors)
        for e in title_errors:
            print(f"  ✗ {e}")
    else:
        print("  ✓ 标题格式正确")
    print()

    # 校验正文
    print("--- 正文校验 ---")
    body_errors, body_warnings = check_body(body)
    all_errors.extend(body_errors)
    all_warnings.extend(body_warnings)
    if body_errors:
        for e in body_errors:
            print(f"  ✗ {e}")
    else:
        print("  ✓ 正文无硬错误")
    if body_warnings:
        for w in body_warnings:
            print(f"  ⚠ {w}")
    print()

    # 总结
    print("=== 校验结果 ===")
    if all_errors:
        print(f"✗ 校验失败：{len(all_errors)} 个错误，{len(all_warnings)} 个警告")
        print()
        print("修复建议：")
        print("  1. 更新 Release 标题为「{软件名} v{版本} — {更新概括}」格式")
        print("  2. 替换正文中的所有占位符（请补充/请提炼）")
        print("  3. 修正条目格式为「**4-9字小标题**：20-40字说明」")
        print("  4. 移除所有 emoji")
        print("  5. 过滤内部技术术语（类名、文件名、包名）")
        sys.exit(1)
    else:
        print(f"✓ 校验通过：0 个错误，{len(all_warnings)} 个警告")
        if all_warnings:
            print("  警告项建议人工复核，但不阻塞发版")
        sys.exit(0)


if __name__ == "__main__":
    main()
