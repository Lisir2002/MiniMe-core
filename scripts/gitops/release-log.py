#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
release-log.py — MiniMe-core 用户层发版说明自动生成器。

依据 AGENTS.md「发版规范（最高优先级）」第3条，从 Conventional Commits
（git log <prev_tag>..<cur_tag>）生成用户面向的 GitHub Release 正文草稿。

只保留用户层：叙事、价值导向、无内部术语。开发者层（Keep a Changelog 六类）
和大模型层（AGENTS.md 登记）已废弃，不再维护。

输出结构（严格按此顺序）：
  # MiniMe {版本号}（{YYYY-MM-DD}）
  > {100字以内简介 —— 由 AI/维护者补充，脚本生成占位提示}
  ## ✨ 新功能
  ## ⚡ 改进
  ## 🐛 修复
  ## ⚠️ 已知问题
  ## 📦 安装包
  **完整更新历史**：{compare 链接}

用法（仓库根执行）：
  python3 scripts/gitops/release-log.py --prev v0.0.0.14 --cur v0.0.0.15
  python3 scripts/gitops/release-log.py --version 0.0.0.15 --date 2026-09-25

设计约束：
  - 仅为用户层正文草稿，永不替代人工复核：简介价值化润色、已知问题补充仍需人工完成。
  - 自动过滤 ci/test/style/docs/chore/build 等非用户可见噪音。
  - 每条一个变更，禁止模糊表述；描述症状而非代码。
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from datetime import date
from pathlib import Path

# ---- Conventional Commits ----
TYPE_REGEX = re.compile(
    r"^(?P<type>[a-zA-Z]+)(?:\((?P<scope>[A-Za-z0-9._-]+)\))?"
    r"(?P<breaking>!)?:\s*(?P<subject>.+)$"
)

# 用户层分类：feat->新功能，perf/refactor->改进，fix->修复
USER_CATEGORY_MAP = {
    "feat": "new",
    "fix": "fix",
    "perf": "improve",
    "refactor": "improve",
    "revert": "fix",
}

# 用户层排除：纯 CI/测试/格式/文档/构建/依赖噪音，不进用户可见日志
USER_EXCLUDE = {"ci", "test", "style", "docs", "chore", "build", "deps"}


def run(cmd, repo):
    """在仓库根执行命令并返回 stdout，异常时抛错。"""
    return subprocess.run(
        cmd, cwd=str(repo), capture_output=True, text=True, check=True
    ).stdout.strip()


def get_commits(repo, prev, cur):
    """取 <prev>..<cur> 之间的提交，解析为结构体列表。"""
    rng = f"{prev}..{cur}"
    raw = run(
        ["git", "log", rng, "--pretty=format:%H%x1f%s%x1f%b%x1e"], repo
    )
    commits = []
    if not raw:
        return commits
    for entry in raw.split("\x1e"):
        entry = entry.strip()
        if not entry:
            continue
        parts = entry.split("\x1f")
        if len(parts) < 2:
            continue
        sha = parts[0]
        subject = parts[1]
        body = parts[2] if len(parts) > 2 else ""
        m = TYPE_REGEX.match(subject)
        if m:
            ctype = m.group("type").lower()
            cscope = m.group("scope") or ""
            cbreaking = bool(m.group("breaking"))
            csubject = m.group("subject")
        else:
            ctype = ""
            cscope = ""
            cbreaking = False
            csubject = subject
        commits.append({
            "sha": sha,
            "type": ctype,
            "scope": cscope,
            "breaking": cbreaking,
            "subject": csubject,
            "body": body,
        })
    return commits


def _user_category(commit):
    """判断提交属于哪个用户层分类：new / improve / fix / None(排除)。"""
    if commit["type"] in USER_EXCLUDE:
        return None
    if commit["breaking"]:
        return "new"  # 破坏性变更归入新功能（醒目标注）
    return USER_CATEGORY_MAP.get(commit["type"])


def _format_subject(commit):
    """格式化提交标题为用户语言：去除 scope 前缀，首字母大写。"""
    s = commit["subject"].strip()
    # 去除常见的开发语言前缀
    s = re.sub(r"^(feat|fix|perf|refactor|revert)(\([^)]*\))?[:：]\s*", "", s, flags=re.IGNORECASE)
    return s


def user_layer(commits, version, date_str):
    """用户层：价值导向、无内部术语的 GitHub Release 正文。"""
    categories = {"new": [], "improve": [], "fix": []}
    breaking = []

    for c in commits:
        cat = _user_category(c)
        if cat is None:
            continue
        formatted = _format_subject(c)
        if c["breaking"]:
            breaking.append(formatted)
        categories[cat].append(formatted)

    lines = [f"# MiniMe {version}（{date_str}）", ""]
    # 简介占位符（AI/维护者补充，≤100字）
    lines.append("> _（请补充 100 字以内简介：一句话说清本次更新的核心价值，用户语言，无内部术语）_")
    lines.append("")

    # 破坏性变更醒目标注
    if breaking:
        lines.append("## ⚠️ 破坏性变更")
        lines.append("")
        lines.extend(f"- ⚠️ {b}" for b in breaking)
        lines.append("")

    # ✨ 新功能
    if categories["new"]:
        lines.append("## ✨ 新功能")
        lines.append("")
        lines.extend(f"- {item}" for item in categories["new"])
        lines.append("")

    # ⚡ 改进
    if categories["improve"]:
        lines.append("## ⚡ 改进")
        lines.append("")
        lines.extend(f"- {item}" for item in categories["improve"])
        lines.append("")

    # 🐛 修复
    if categories["fix"]:
        lines.append("## 🐛 修复")
        lines.append("")
        lines.extend(f"- {item}" for item in categories["fix"])
        lines.append("")

    # ⚠️ 已知问题（占位）
    lines.append("## ⚠️ 已知问题")
    lines.append("")
    lines.append("_（无已知问题则写「无」；如有请描述症状+临时规避方法+预计修复版本）_")
    lines.append("")

    # 📦 安装包（占位，CI 会替换实际文件名和大小）
    lines.append("## 📦 安装包")
    lines.append("")
    lines.append("_（CI 构建完成后自动补充 APK 下载链接与大小）_")
    lines.append("")

    return "\n".join(lines)


def _compare_url(repo, prev, cur):
    """生成 GitHub compare 链接。"""
    try:
        remote = run(["git", "remote", "get-url", "origin"], repo)
    except Exception:
        return "（remote 不可用）"
    github = re.search(r"github\.com[:/]([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\.git)?$", remote)
    if not github:
        return "（非 GitHub 仓库）"
    return f"https://github.com/{github.group(1)}/compare/{prev}...{cur}"


def _recent(repo, n):
    """无历史 tag 时取最近 n 条提交。"""
    raw = run(["git", "log", f"-{n}", "--pretty=format:%H%x1f%s%x1f%b%x1e"], repo)
    commits = []
    for entry in raw.split("\x1e"):
        entry = entry.strip()
        if not entry:
            continue
        parts = entry.split("\x1f")
        if len(parts) < 2:
            continue
        m = TYPE_REGEX.match(parts[1])
        if m:
            commits.append({
                "sha": parts[0],
                "type": m.group("type").lower(),
                "scope": m.group("scope") or "",
                "breaking": bool(m.group("breaking")),
                "subject": m.group("subject"),
                "body": parts[2] if len(parts) > 2 else "",
            })
    return commits


def main():
    parser = argparse.ArgumentParser(
        description="MiniMe-core 用户层发版说明生成器（见 AGENTS.md「发版规范」）"
    )
    parser.add_argument("--prev", default=None, help="起始 tag（默认取最近一个 tag）")
    parser.add_argument("--cur", default="HEAD", help="结束 tag/提交（默认 HEAD）")
    parser.add_argument("--date", default=None, help="发布日期 YYYY-MM-DD（默认今天）")
    parser.add_argument("--version", default=None, help="版本号（默认 cur tag 去 v 前缀）")
    parser.add_argument("--repo", default=None, help="仓库根（默认 git 自动探测）")
    args = parser.parse_args()

    repo = Path(args.repo) if args.repo else \
        Path(run(["git", "rev-parse", "--show-toplevel"], Path.cwd()))

    # 解析 prev=最近 tag（若未给）
    prev = args.prev
    if not prev:
        tags = run(["git", "tag", "--sort=-creatordate"], repo).splitlines()
        tags = [t for t in tags if t != args.cur]
        prev = tags[0] if tags else None

    # 解析版本号
    version = args.version.lstrip("v") if args.version else None
    if not version:
        if args.cur != "HEAD":
            version = str(args.cur).lstrip("v")
        else:
            version = "Unreleased"

    date_str = args.date or date.today().isoformat()

    if prev is None:
        commits = _recent(repo, 30)
    else:
        commits = get_commits(repo, prev, args.cur)

    body = user_layer(commits, version, date_str)

    # 追加完整更新历史链接
    if prev:
        body += f"\n**完整更新历史**：{_compare_url(repo, prev, args.cur)}\n"

    print(body)


if __name__ == "__main__":
    main()
