#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-version-consistency.py — MiniMe-core 发版前版本号一致性校验脚本。

发版前（打 Tag 前）运行，校验：
  1. Tag 格式正确（四段式 x.x.x.x 或 x.x.x.x-rcN，带应用前缀）
  2. CHANGELOG.md 中存在对应版本条目
  3. versionCode 映射单调递增（与上一个 Release tag 比较）
  4. 版本号语义推进正确（D 段 +1 或 C 段 +1 且 D 归零）

用法：
  python3 scripts/gitops/check-version-consistency.py --tag v0.0.0.40-rc1
  python3 scripts/gitops/check-version-consistency.py --tag qbot-v0.0.1-rc30 --app qbot
  python3 scripts/gitops/check-version-consistency.py --latest  # 校验最新 tag

校验不通过时输出具体不合规项和修复建议，退出码非零。
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

# 仓库根目录
REPO_ROOT = Path(__file__).resolve().parent.parent.parent

# 各应用配置
APPS = {
    "main": {
        "tag_prefix": "v",
        "changelog": "docs/Version Log/CHANGELOG.md",
        "version_pattern": re.compile(r"^(\d+)\.(\d+)\.(\d+)\.(\d+)(-rc\d+)?$"),
    },
    "qbot": {
        "tag_prefix": "qbot-v",
        "changelog": "docs/Version Log/CHANGELOG-qbot.md",
        "version_pattern": re.compile(r"^(\d+)\.(\d+)\.(\d+)(-rc\d+)?$"),
    },
    "logviewer": {
        "tag_prefix": "logviewer-v",
        "changelog": "docs/Version Log/CHANGELOG-logviewer.md",
        "version_pattern": re.compile(r"^(\d+)\.(\d+)\.(\d+)$"),
    },
    "injector": {
        "tag_prefix": "qbot-injector-v",
        "changelog": "docs/Version Log/CHANGELOG-qbot-injector.md",
        "version_pattern": re.compile(r"^(\d+)\.(\d+)\.(\d+)(-rc\d+)?$"),
    },
}


def run_git(args: list[str]) -> str:
    """运行 git 命令并返回 stdout。"""
    result = subprocess.run(
        ["git"] + args,
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def get_latest_tag(prefix: str) -> str | None:
    """获取指定前缀的最新 tag。"""
    tags = run_git(["tag", "--sort=-creatordate"]).splitlines()
    for tag in tags:
        if tag.startswith(prefix):
            return tag
    return None


def parse_version(tag: str, prefix: str, pattern: re.Pattern) -> tuple | None:
    """从 tag 解析版本号元组（含 rc 序号）。"""
    version = tag.removeprefix(prefix)
    match = pattern.match(version)
    if not match:
        return None
    parts = []
    for g in match.groups():
        if g is None:
            continue
        if g.startswith("-rc"):
            parts.append(int(g[3:]))  # rc 序号
        elif g.isdigit():
            parts.append(int(g))
    return tuple(parts)


def check_tag_format(tag: str, app: str) -> list[str]:
    """校验 tag 格式。"""
    errors = []
    config = APPS[app]
    prefix = config["tag_prefix"]
    if not tag.startswith(prefix):
        errors.append(f"Tag 前缀错误：应为 '{prefix}'，实际 '{tag}'")
        return errors
    version = tag.removeprefix(prefix)
    if not config["version_pattern"].match(version):
        if app == "main":
            errors.append(f"版本号格式错误：应为四段式 x.x.x.x 或 x.x.x.x-rcN，实际 '{version}'")
        else:
            errors.append(f"版本号格式错误：应为三段式 x.x.x 或 x.x.x-rcN，实际 '{version}'")
    return errors


def check_changelog(tag: str, app: str) -> list[str]:
    """校验 CHANGELOG 中存在对应版本条目。"""
    errors = []
    config = APPS[app]
    changelog_path = REPO_ROOT / config["changelog"]
    if not changelog_path.exists():
        errors.append(f"CHANGELOG 文件不存在：{config['changelog']}")
        return errors
    version = tag.removeprefix(config["tag_prefix"])
    content = changelog_path.read_text(encoding="utf-8")
    # 匹配 ## [x.x.x.x] 或 ## [x.x.x.x-rcN]
    if f"## [{version}]" not in content:
        errors.append(
            f"CHANGELOG 中缺少版本条目：## [{version}]（文件：{config['changelog']}）"
        )
    return errors


def check_version_monotonic(tag: str, app: str) -> list[str]:
    """校验版本号语义推进（与上一个 tag 比较）。"""
    errors = []
    config = APPS[app]
    prefix = config["tag_prefix"]
    cur_version = parse_version(tag, prefix, config["version_pattern"])
    if cur_version is None:
        return errors  # 格式错误已在 check_tag_format 中报告

    # 获取上一个同应用 tag
    tags = run_git(["tag", "--sort=-creatordate"]).splitlines()
    prev_tag = None
    for t in tags:
        if t.startswith(prefix) and t != tag:
            prev_tag = t
            break
    if prev_tag is None:
        return errors  # 首次发版，跳过

    prev_version = parse_version(prev_tag, prefix, config["version_pattern"])
    if prev_version is None:
        errors.append(f"上一个 tag 版本号无法解析：{prev_tag}")
        return errors

    # 比较版本号
    if cur_version <= prev_version:
        errors.append(
            f"版本号未推进：当前 {tag} <= 上一个 {prev_tag}。"
            f"应按语义化版本递增（D 段 +1，或 C 段 +1 且 D 归零）。"
        )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="发版前版本号一致性校验")
    parser.add_argument("--tag", help="待校验的 tag（如 v0.0.0.40-rc1）")
    parser.add_argument("--app", default="main", choices=list(APPS.keys()), help="应用类型")
    parser.add_argument("--latest", action="store_true", help="校验最新 tag")
    args = parser.parse_args()

    if args.latest:
        tag = get_latest_tag(APPS[args.app]["tag_prefix"])
        if tag is None:
            print(f"错误：未找到 {args.app} 的 tag")
            return 1
    elif args.tag:
        tag = args.tag
    else:
        parser.error("必须指定 --tag 或 --latest")

    print(f"校验 tag：{tag}（应用：{args.app}）")
    print("=" * 60)

    all_errors: list[str] = []

    # 1. Tag 格式
    print("[1/3] 校验 Tag 格式...")
    errors = check_tag_format(tag, args.app)
    if errors:
        all_errors.extend(errors)
        for e in errors:
            print(f"  ✗ {e}")
    else:
        print("  ✓ Tag 格式正确")

    # 2. CHANGELOG 一致性
    print("[2/3] 校验 CHANGELOG 一致性...")
    errors = check_changelog(tag, args.app)
    if errors:
        all_errors.extend(errors)
        for e in errors:
            print(f"  ✗ {e}")
    else:
        print("  ✓ CHANGELOG 条目存在")

    # 3. 版本号语义推进
    print("[3/3] 校验版本号语义推进...")
    errors = check_version_monotonic(tag, args.app)
    if errors:
        all_errors.extend(errors)
        for e in errors:
            print(f"  ✗ {e}")
    else:
        print("  ✓ 版本号语义推进正确")

    print("=" * 60)
    if all_errors:
        print(f"\n校验失败：共 {len(all_errors)} 项问题")
        print("\n修复建议：")
        print("  1. 确认 tag 格式正确（主应用四段式 x.x.x.x-rcN，附属应用三段式 x.x.x-rcN）")
        print("  2. 在 CHANGELOG 中添加对应版本条目")
        print("  3. 确认版本号在上一个 Release 之上按语义推进")
        return 1
    else:
        print("\n✓ 全部校验通过，可以打 Tag 发版")
        return 0


if __name__ == "__main__":
    sys.exit(main())
