#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
release-log.py — MiniMe-core 发版说明自动生成器。

依据 AGENTS.md「发版说明格式（用户面向 · 唯一规约 · 硬性约束）」，
从 Conventional Commits（git log <prev_tag>..<cur_tag>）生成用户面向的
GitHub Release 正文草稿。

统一格式：CHANGELOG.md、独立版本文档、GitHub Release 正文三处格式一致。

输出结构（严格按此顺序，无内容的分类省略）：
  > {100字以内简介 —— 由 AI/维护者补充，脚本生成占位提示}
  ### 新功能
  ### 改进
  ### 移除
  ### 修复
  ### 安全
  ### 已知问题

条目格式：- **{4-9字小标题}**：{20-40字简练说明}。

用法（仓库根执行）：
  python3 scripts/gitops/release-log.py --prev v0.0.0.14 --cur v0.0.0.15
  python3 scripts/gitops/release-log.py --version 0.0.0.15 --date 2026-09-25

设计约束：
  - 仅为正文草稿，永不替代人工复核：简介价值化润色、条目小标题提炼仍需人工完成。
  - 自动过滤 ci/test/style/docs/chore/build 等非用户可见噪音。
  - 每条一个变更，禁止模糊表述；描述症状而非代码。
  - 禁止 emoji，禁止安装包链接，禁止完整更新历史链接（GitHub 自动展示）。
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

# 内部术语 -> 用户语言 映射（自动过滤技术细节）
INTERNAL_TERM_MAP = {
    r"\bBrowserController\b": "浏览器控制器",
    r"\bProviderEditorScreen\b": "供应商编辑页面",
    r"\bModelMetadataService\b": "模型元数据服务",
    r"\bContextCompactor\b": "上下文压缩器",
    r"\bMainActivity\b": "主界面",
    r"\bSplash\w*\b": "启动动画",
    r"\bParticle\w*\b": "粒子效果",
    r"\bGlassShatter\w*\b": "玻璃破碎效果",
    r"\bAPI\b": "接口",
    r"\bSDK\b": "开发工具包",
    r"\bUI\b": "界面",
    r"\bUX\b": "体验",
    r"\bDB\b": "数据库",
    r"\bJSON\b": "数据格式",
    r"\bHTTP\b": "网络请求",
    r"\bHTTPS\b": "加密网络请求",
    r"\bURL\b": "网址",
    r"\bCI\b": "持续集成",
    r"\.kt\b": "",
    r"\.java\b": "",
    r"\.py\b": "",
    r"\.gradle\b": "",
    r"\.yml\b": "",
    r"\.yaml\b": "",
    r"\.md\b": "",
    r"\bcom\.mini\.\w+\b": "",
}

TITLE_KEYWORDS = [
    "启动动画", "浏览器", "供应商", "模型", "终端", "容器", "设置", "外观",
    "主题", "组件", "弹窗", "输入框", "顶栏", "底栏", "标签",
    "发版", "标题", "日志", "持久化", "数据", "配置", "导出", "导入",
    "搜索", "历史", "状态", "修复", "优化", "重构", "增强", "升级",
    "反检测", "定位", "测速", "上下文", "参数", "采样", "能力",
    "图标", "动画", "粒子", "破碎", "聚合", "跳过", "倒计时",
    "远程", "挂载", "重连", "冷启动", "初始化",
    "规范", "流程", "约束", "校验", "门禁",
]


def _filter_internal_terms(text):
    """过滤内部技术术语，替换为用户语言。"""
    for pattern, replacement in INTERNAL_TERM_MAP.items():
        text = re.sub(pattern, replacement, text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def _extract_title(subject):
    """从commit subject中自动提取4-9字小标题。"""
    s = _filter_internal_terms(subject)
    for kw in TITLE_KEYWORDS:
        if kw in s:
            idx = s.index(kw)
            start = max(0, idx - 2)
            end = min(len(s), idx + len(kw) + 2)
            candidate = re.sub(r'[：:，,。.\s]', '', s[start:end])
            if 4 <= len(candidate) <= 9:
                return candidate
            if len(candidate) > 9:
                return candidate[:9]
            start = max(0, idx - 4)
            candidate = re.sub(r'[：:，,。.\s]', '', s[start:end])
            return candidate[:9] if len(candidate) > 9 else candidate
    s_clean = re.sub(r'[：:，,。.\s]', '', s)
    if len(s_clean) <= 9:
        return s_clean if len(s_clean) >= 4 else s_clean[:4] + "优化"
    return s_clean[:9]


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
    """格式化提交标题为用户语言：去除 scope 前缀，过滤内部术语。"""
    s = commit["subject"].strip()
    s = re.sub(r"^(feat|fix|perf|refactor|revert)(\([^)]*\))?[:：]\s*", "", s, flags=re.IGNORECASE)
    s = _filter_internal_terms(s)
    return s


def _format_description(subject):
    """将commit subject转为20-40字的用户面向说明句。"""
    s = _filter_internal_terms(subject)
    # 确保句子完整，不足20字时补充
    if len(s) < 20:
        s = s + "，提升用户体验与产品质量。"
    if len(s) > 40:
        s = s[:39] + "…"
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
        title = _extract_title(formatted)
        desc = _format_description(formatted)
        entry = f"- **{title}**：{desc}"
        if c["breaking"]:
            breaking.append(entry)
        categories[cat].append(entry)

    lines = []
    # 简介占位符（AI/维护者补充，≤100字）
    lines.append("> _（请补充 100 字以内简介：一句话说清本次更新的核心价值，用户语言，无内部术语）_")
    lines.append("")

    # 破坏性变更醒目标注
    if breaking:
        lines.append("### 破坏性变更")
        lines.append("")
        lines.extend(breaking)
        lines.append("")

    # 新功能
    if categories["new"]:
        lines.append("### 新功能")
        lines.append("")
        lines.extend(categories["new"])
        lines.append("")

    # 改进
    if categories["improve"]:
        lines.append("### 改进")
        lines.append("")
        lines.extend(categories["improve"])
        lines.append("")

    # 修复
    if categories["fix"]:
        lines.append("### 修复")
        lines.append("")
        lines.extend(categories["fix"])
        lines.append("")

    # 已知问题（占位）
    lines.append("### 已知问题")
    lines.append("")
    lines.append("_（无已知问题则写「无」；如有请描述症状+临时规避方法+预计修复版本）_")
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
        description="MiniMe-core 发版说明生成器（见 AGENTS.md「发版说明格式」）"
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

    print(body)


if __name__ == "__main__":
    main()
