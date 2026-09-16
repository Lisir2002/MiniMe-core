#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
release-log.py — MiniMe-core 三层发版日志自动生成器。

依据 AGENTS.md「版本日志（发版必做 · 三层写法规约）」，从同一份 Conventional Commits
来源（git log <prev_tag>..<cur_tag>）按受众渲染成三层不同语体：

  --layer user   用户层：用户视角，按「新增 / 优化 / 修复」状态分类小标题列条目（无内部术语）
  --layer dev    开发者层：Keep a Changelog 六类（新增/变更/废弃/删除/修复/安全），带 scope 与 sha
  --layer ai     大模型层：结构化、机器可解析，聚焦 AI 工作流影响（工具/prompt/schema/接口）
  --layer all    三层合并（发版说明/ GitHub Release 正文 = 完整三层日志分节并列）

用法（仓库根执行）：
  python3 scripts/gitops/release-log.py --layer user            # 用默认 cur=HEAD，prev=最近 tag
  python3 scripts/gitops/release-log.py --layer dev --prev v0.0.0.1 --cur v0.0.0.2
  python3 scripts/gitops/release-log.py --layer ai --date 2026-09-14

可通过 PATH 环境变量 `GITOPS_REPO` 覆盖仓库根（默认 git rev-parse --show-toplevel）。

设计约束：
  - 三层平铺（## 用户层 / ## 开发者层 / ## 大模型层），不折叠、不写营销修辞。
  - 用户层、开发者层内部保留「新增/变更/删除/修复」等状态分类小标题（###），空分类不输出。
  - Breaking 判定仍需人工复核；脚本只做按 Conventional type 归类，不替人润色。
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

# Conventional type -> 开发者层状态分类（中文，对应 Keep a Changelog 六类）
DEV_MAP = {
    "feat": "新增",
    "fix": "修复",
    "perf": "变更",
    "refactor": "变更",
    "chore": "变更",
    "build": "变更",
    "deps": "变更",
    "docs": "变更",
    "revert": "变更",
    "ci": None,          # 纯 CI 噪音，不进开发者层
    "test": None,        # 纯测试噪音
    "style": None,       # 纯格式噪音
}

# 开发者层分类输出顺序（空分类不输出）
DEV_ORDER = ["新增", "变更", "废弃", "删除", "修复", "安全"]

# 用户层状态分类（面向用户：功能/性能/修复/UI 重构都属用户可感知的样式与交互变化）
USER_GROUPS = [
    ("feat", "新增"),
    ("perf", "优化"),
    ("refactor", "优化"),
    ("fix", "修复"),
]
USER_EXCLUDE = {"ci", "test", "style", "docs", "chore", "build", "deps"}

# AI 层：与 AI 工作流强相关，重点标注
AI_RELEVANT_TYPES = {"feat", "fix", "refactor", "perf"}
BREAKING_MARKERS = ("BREAKING CHANGE", "BREAKING-CHANGE", "breaking change")
AI_HINT_PATTERNS = (
    "tool", "工具", "prompt", "提示词", "schema", "mcp", "协议",
    "interface", "接口", "参数", "迁移", "database", "数据库",
)


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
    for sha, subject, body in _parse_raw(raw):
        m = TYPE_REGEX.match(subject)
        if not m:
            commits.append({
                "sha": sha[:8], "type": None, "scope": None,
                "subject": subject, "body": body, "breaking": False,
            })
            continue
        type_ = m.group("type").lower()
        body_lower = body.lower()
        breaking = bool(m.group("breaking")) or any(
            mk.lower() in body_lower for mk in BREAKING_MARKERS
        )
        commits.append({
            "sha": sha[:8], "type": type_, "scope": m.group("scope"),
            "subject": m.group("subject").strip(), "body": body,
            "breaking": breaking,
        })
    return commits


def _parse_raw(raw):
    """把 '%H%x1f%s%x1f%b%x1e' 输出切成 (sha, subject, body) 元组。"""
    for block in raw.split("\x1e"):
        block = block.strip("\n")
        if not block:
            continue
        parts = block.split("\x1f")
        sha = parts[0].strip()
        if len(parts) >= 3:
            yield sha[:8], parts[1], parts[2]
        elif len(parts) == 2:
            yield sha[:8], parts[1], ""
        else:
            yield sha[:8], parts[0] if len(parts) == 1 else "", ""


def format_entry(c):
    """开发者层单条：`- [scope] subject (short-sha)`，breaking 加 ⚠️。"""
    scope = f"`[{c['scope']}]` " if c["scope"] else ""
    marker = "⚠️ " if c["breaking"] else ""
    return f"- {marker}{scope}{c['subject']} ({c['sha']})"


def _render_groups(buckets, order, empty_hint=None):
    """按 order 输出非空分类：### 分类名 + 条目；全空时返回 empty_hint。"""
    lines = []
    for cat in order:
        entries = buckets.get(cat, [])
        if not entries:
            continue
        if lines:
            lines.append("")
        lines.append(f"### {cat}")
        lines.append("")
        lines.extend(entries)
    if not lines:
        return empty_hint or "- 无变更"
    return "\n".join(lines).rstrip()


def dev_layer(commits, version, date_str, repo):
    """第 2 层：开发者状态分类（新增/变更/废弃/删除/修复/安全），条目带 scope 与 sha。"""
    buckets = {k: [] for k in DEV_ORDER}
    other = []
    for c in commits:
        cat = DEV_MAP.get(c["type"])
        if c["type"] is None:
            other.append(format_entry(c))
        elif cat is None:
            continue  # 过滤纯 CI/test/style 噪音
        else:
            buckets[cat].append(format_entry(c))

    out = _render_groups(buckets, DEV_ORDER, empty_hint="")
    if other:
        out += ("\n\n" if out else "") + "### 待归类\n\n" + "\n".join(other)
    # 硬性要求：有提交就不许出现「无变更」；分类全落空时把所有提交列待归类
    if not any(buckets.values()) and not other and commits:
        out = "### 待归类\n\n" + "\n".join(format_entry(c) for c in commits)
    return out


def user_layer(commits, version, date_str):
    """第 1 层：用户视角，按 新增/优化/修复 状态分类，条目不带 sha、无内部术语。
    硬性要求：本区间有提交时绝不写「无用户可见变更」——分类落空时把非噪音条目原样列出。"""
    buckets = {"新增": [], "优化": [], "修复": []}
    fallbacks = []
    for c in commits:
        matched = False
        for type_, label in USER_GROUPS:
            if c["type"] == type_:
                buckets[label].append(f"- {c['subject']}")
                matched = True
                break
        if not matched:
            # 硬性要求：有提交就不许出现「无变更」——任何未命中新增/优化/修复的
            # 提交（含 ci/docs/chore 等）都原样列入「其他」，保证用户层不空。
            fallbacks.append(f"- {c['subject']}")
    body = _render_groups(buckets, ["新增", "优化", "修复"], empty_hint="")
    if fallbacks and not any(buckets.values()):
        body = "### 其他\n\n" + "\n".join(fallbacks)
    elif fallbacks:
        body += "\n\n### 其他\n\n" + "\n".join(fallbacks)
    return body


def _changed_paths(repo, prev, cur):
    """返回 <prev>..<cur> 变更的文件路径（用于 AI 层推断影响面）。"""
    try:
        out = run(["git", "diff", "--name-only", f"{prev}..{cur}"], repo)
    except Exception:
        return ""
    return out


def _commit_files(repo, sha):
    """单个提交改动的文件路径列表（短 sha 也可解析）。"""
    try:
        out = run(["git", "show", "--name-only", "--pretty=format:", sha], repo)
    except Exception:
        return []
    return [p for p in out.splitlines() if p.strip()]


# AI 层（面向 agent）按 Conventional type 归到的处置小标题
AI_GROUPS = [
    ("fix", "修复"),
    ("feat", "新增"),
    ("perf", "优化"),
    ("refactor", "变更"),
    ("revert", "回退"),
    ("docs", "文档"),
    ("build", "构建"),
    ("ci", "CI"),
    ("chore", "杂项"),
]


def ai_layer(commits, version, date_str, changed_paths, repo):
    """第 3 层（面向 agent）：逐条列问题出处 + 文件/代码定位 + 处置方向，
    永不写「无变更/无内容」。只要本区间有提交，就必须落到文件级。"""
    # 按 AI_GROUPS 分桶，保持顺序
    buckets = {label: [] for _, label in AI_GROUPS}
    other = []
    for c in commits:
        files = _commit_files(repo, c["sha"])
        # 处置方向：取 commit body 的非空要点行（commit message 里写的根因/方案）
        notes = [ln.strip() for ln in (c.get("body") or "").splitlines() if ln.strip()]
        entry_lines = [f"- `{c['sha']}` {c['subject']}"]
        if c["scope"]:
            entry_lines[-1] += f"  _(scope: {c['scope']})_"
        if notes:
            entry_lines.append("  - 出处 / 方案：")
            entry_lines.extend(f"    - {n}" for n in notes)
        if files:
            entry_lines.append("  - 涉及文件：")
            entry_lines.extend(f"    - `{p}`" for p in files)
        elif not notes:
            entry_lines.append("  - 涉及文件：见 `git show " + c["sha"] + "`")
        block = "\n".join(entry_lines)

        label = next((lb for ty, lb in AI_GROUPS if ty == c["type"]), None)
        if label and label in buckets:
            buckets[label].append(block)
        else:
            other.append(block)

    lines = []
    for label in [lb for _, lb in AI_GROUPS]:
        entries = buckets.get(label, [])
        if not entries:
            continue
        if lines:
            lines.append("")
        lines.append(f"### {label}")
        lines.append("")
        lines.extend(entries)
    if other:
        lines.append("")
        lines.append("### 待归类")
        lines.append("")
        lines.extend(other)

    # 末尾补一行整体影响面（客观、非占位）
    all_files = [p for p in changed_paths.splitlines() if p.strip()]
    lines.append("")
    lines.append("### 影响面")
    lines.append("")
    lines.append(f"- 本区间共 {len(commits)} 个提交，改动 {len(all_files)} 个文件：")
    lines.extend(f"  - `{p}`" for p in all_files)
    return "\n".join(lines)


def _compare_url(repo, version):
    try:
        remote = run(["git", "remote", "get-url", "origin"], repo)
    except Exception:
        return "（remote 不可用）"
    # 归一化 git@github.com:owner/repo.git 或 https://github.com/owner/repo
    github = re.search(r"github\.com[:/]([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\.git)?$", remote)
    return f"https://github.com/{github.group(1)}/compare/{version}"


def main():
    parser = argparse.ArgumentParser(
        description="MiniMe-core 三层发版日志生成器（见 AGENTS.md「版本日志」）"
    )
    parser.add_argument("--layer", choices=["user", "dev", "ai", "all"], required=True,
                        help="输出哪一层：user=GitHub Release / dev=CHANGELOG / ai=AGENTS 结构化 / all=三层合并（发版说明）")
    parser.add_argument("--prev", default=None, help="起始 tag（默认取最近一个 tag 或 HEAD 之外）")
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

    changed_paths = ""
    if prev is None:
        # 无任何历史 tag：只能列出 HEAD 最近的若干提交
        commits = _recent(repo, 30)
    else:
        commits = get_commits(repo, prev, args.cur)
        changed_paths = _changed_paths(repo, prev, args.cur)

    if args.layer == "user":
        print(user_layer(commits, version, date_str))
    elif args.layer == "dev":
        print(dev_layer(commits, version, date_str, repo))
    elif args.layer == "ai":
        print(ai_layer(commits, version, date_str, changed_paths, repo))
    elif args.layer == "all":
        # 发版说明规则：GitHub Release 正文 = 三层平铺，不折叠、不加价值导向修辞。
        sections = [
            "## 用户层",
            "",
            user_layer(commits, version, date_str),
            "",
            "## 开发者层",
            "",
            dev_layer(commits, version, date_str, repo),
            "",
            "## 大模型层",
            "",
            ai_layer(commits, version, date_str, changed_paths, repo),
        ]
        print("\n".join(sections))


def _recent(repo, n):
    """无历史 tag 时的兜底：取最近 n 条提交。"""
    raw = run(
        ["git", "log", "--max-count", str(n), "--pretty=format:%H%x1f%s%x1f%b%x1e"],
        repo,
    )
    commits = []
    for sha, subject, body in _parse_raw(raw):
        m = TYPE_REGEX.match(subject)
        commits.append({
            "sha": sha[:8],
            "type": m.group("type").lower() if m else None,
            "scope": m.group("scope") if m else None,
            "subject": (m.group("subject").strip() if m else subject),
            "body": body,
            "breaking": bool(m.group("breaking")) if m else False,
        })
    return commits


if __name__ == "__main__":
    main()