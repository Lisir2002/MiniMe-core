#!/usr/bin/env python3
"""抓取 MiniMe-QBot 首次启动所需的离线资产，消除首启的网络下载。

产出（默认写入 qbot-app/src/_qbotAssets/，与 app/src/_armAssets 同为「入库二进制资产」）：
  alpine-repo/aarch64/APKINDEX.tar.gz + *.apk   离线 Alpine apk 源（aarch64，含全部传递依赖）
  napcat/NapCat.Shell.zip                       协议端本体（GitHub Releases latest）

这两个目录会被 qbot-app/build.gradle.kts 挂进 assets；App 首启时由 QBotRuntimeInstaller
落盘，供给脚本再以 `apk add --no-network` 离线安装，无需联网。

用法：
  python3 scripts/dev/fetch-qbot-offline-assets.py                  # 全量抓取
  python3 scripts/dev/fetch-qbot-offline-assets.py --only alpine    # 仅 Alpine 依赖源
  python3 scripts/dev/fetch-qbot-offline-assets.py --only napcat    # 仅协议端本体

说明：本脚本在构建机上执行（需 python3 + 终端可达 aliyun 镜像 / GitHub），产出物入库；
CI/打包不需要运行它，只需要仓库里已存在的资产。更新依赖列表或升级 NapCat 时重跑本脚本。
"""

import argparse
import io
import os
import re
import shutil
import subprocess
import sys
import tarfile
import time
import urllib.request

USER_AGENT = "Mozilla/5.0 (compatible; MiniMeQBotAssetFetch/1.0)"

# ── 配置（与 qbot-app 运行时契约保持一致，改动需同步 provision 脚本 / QBotRuntimeInstaller）──
ALPINE_BRANCH = "v3.21"
ALPINE_MIRROR = "http://mirrors.aliyun.com/alpine"
ALPINE_REPOS = ("main", "community")
ARCH = "aarch64"

# 首启离线安装的依赖集合。与 provision-protocol.sh 的 `apk add` 清单严格一致：
#   nodejs/npm         NapCat（Node/TS）运行前提
#   xvfb + 相关       无显示环境下启动官方 QQ 客户端
#   fontconfig/tzdata QQ 渲染与本地时间
#   sqlite-libs       NapCat 会话数据库
#   ca-certificates   HTTPS 下载（QQ 客户端）所需
#   curl/bash/tar/xz/unzip/procps  脚本运行与解压
PACKAGES = (
    "nodejs", "npm",
    "curl", "bash", "tar", "xz", "unzip", "procps",
    "xvfb", "fontconfig", "tzdata", "sqlite-libs", "ca-certificates",
)

# 协议端本体：GitHub Releases latest（与上游 napcat-linux-installer 一致）。
NAPCAT_URL = "https://github.com/NapNeko/NapCatQQ/releases/latest/download/NapCat.Shell.zip"

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_OUT = os.path.join(REPO_ROOT, "qbot-app", "src", "_qbotAssets")


def log(msg: str) -> None:
    print(msg, flush=True)


def http_get(url: str, timeout: int = 300) -> bytes:
    """下载 URL 内容；优先用 curl（对镜像/代理兼容性最好），缺失时回退 urllib。"""
    if shutil.which("curl"):
        # 公共镜像/前置代理可能瞬时限流（403），多轮退避重试足够骑过抖动。
        for attempt in range(1, 9):
            proc = subprocess.run(
                ["curl", "-fsSL", "--max-time", str(timeout), "-A", USER_AGENT, "-o", "-", url],
                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            )
            if proc.returncode == 0 and proc.stdout:
                return proc.stdout
            log(f"[warn] 拉取失败（第 {attempt}/8 次）：{url} -> {proc.stderr.decode('utf-8', 'replace').strip()}")
            time.sleep(min(2 * attempt, 10))
        raise SystemExit(f"[错误] 拉取失败：{url}")

    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    last = None
    for attempt in range(1, 4):
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return resp.read()
        except Exception as exc:  # 网络抖动/镜像瞬时限流：退避重试
            last = exc
            log(f"[warn] 拉取失败（第 {attempt}/3 次）：{url} -> {exc}")
            time.sleep(2 * attempt)
    raise SystemExit(f"[错误] 拉取失败：{url} -> {last}")


def _version_key(version: str):
    """Alpine 版本号近似排序键（epoch:ver-rN），用于在同名多版本中取最高。"""
    m = re.match(r"^(?:(\d+):)?([^-]+)(?:-r(\d+))?$", version)
    if not m:
        return (0, [], 0)
    epoch = int(m.group(1) or 0)
    parts = []
    for token in re.findall(r"\d+|[A-Za-z]+", m.group(2)):
        parts.append((1, int(token), "") if token.isdigit() else (0, 0, token))
    return (epoch, parts, int(m.group(3) or 0))


def parse_apkindex(raw: bytes, repo: str) -> list:
    """解析 APKINDEX（tar.gz 内成员名为 APKINDEX 的文本）为记录列表。"""
    with tarfile.open(fileobj=io.BytesIO(raw), mode="r:gz") as tar:
        member = next(m for m in tar.getmembers() if m.name.endswith("APKINDEX"))
        text = tar.extractfile(member).read().decode("utf-8", "replace")

    records = []
    for block in text.split("\n\n"):
        block = block.strip("\n")
        if not block:
            continue
        rec = {"_repo": repo, "_raw": block}
        for line in block.splitlines():
            if len(line) >= 2 and line[1] == ":":
                rec[line[0]] = line[2:]
        if "P" in rec and "V" in rec:
            records.append(rec)
    return records


def installed_in_base_rootfs() -> set:
    """读取内置 Alpine rootfs 的已装包集合，避免把基础镜像里已有的包再打进离线源。"""
    rootfs = os.path.join(REPO_ROOT, "app", "src", "_armAssets", "container", "arm", "alpine-rootfs.bin")
    if not os.path.isfile(rootfs):
        log("[alpine] 未找到基础 rootfs 资产，跳过已装包剔除")
        return set()
    with tarfile.open(rootfs, "r:*") as tar:
        member = next((m for m in tar.getmembers() if m.name.endswith("lib/apk/db/installed")), None)
        if member is None:
            return set()
        text = tar.extractfile(member).read().decode("utf-8", "replace")

    names = set()
    for block in text.split("\n\n"):
        for line in block.splitlines():
            if line.startswith("P:"):
                names.add(line[2:].strip())
    log(f"[alpine] 基础 rootfs 已装 {len(names)} 个包，将从离线源中剔除")
    return names


def resolve(requested, records, presatisfied) -> dict:
    """解析请求包及其全部传递依赖，返回 {包名: 记录}。基础镜像已装包视为已满足。"""
    by_name = {}
    by_provide = {}
    for rec in records:
        name = rec["P"]
        cur = by_name.get(name)
        if cur is None or _version_key(rec["V"]) > _version_key(cur["V"]):
            by_name[name] = rec
        for provide in rec.get("p", "").split():
            cap = provide.split("=")[0]
            if not cap:
                continue
            cur = by_provide.get(cap)
            if cur is None or _version_key(rec["V"]) > _version_key(cur["V"]):
                by_provide[cap] = rec

    # 基础镜像已装包 → 其包名与它提供的虚拟能力都视为已满足。
    seen = set()
    for name in presatisfied:
        seen.add(name)
        rec = by_name.get(name)
        if rec is not None:
            for provide in rec.get("p", "").split():
                seen.add(provide.split("=")[0])

    selected = {}
    queue = list(requested)
    while queue:
        dep = queue.pop()
        if dep in seen:
            continue
        seen.add(dep)
        rec = by_name.get(dep) or by_provide.get(dep)
        if rec is None:
            raise SystemExit(f"[错误] 依赖无法解析：{dep}（镜像索引缺失该包/虚拟能力）")
        selected[rec["P"]] = rec
        for token in rec.get("D", "").split():
            if token.startswith("!"):
                continue
            cap = re.split(r"[<>=~]", token)[0]
            if cap and cap not in seen:
                queue.append(cap)
    return selected


def fetch_alpine(out_dir: str) -> None:
    log(f"[alpine] 拉取 {ALPINE_BRANCH}/{ARCH} 索引：{', '.join(ALPINE_REPOS)}")
    records = []
    for repo in ALPINE_REPOS:
        url = f"{ALPINE_MIRROR}/{ALPINE_BRANCH}/{repo}/{ARCH}/APKINDEX.tar.gz"
        raw = http_get(url)
        log(f"[alpine]   {repo}: APKINDEX {len(raw) / 1024:.0f} KB")
        records.extend(parse_apkindex(raw, repo))

    selected = resolve(PACKAGES, records, installed_in_base_rootfs())
    log(f"[alpine] 请求 {len(PACKAGES)} 个包，闭包共 {len(selected)} 个")

    repo_dir = os.path.join(out_dir, "alpine-repo", ARCH)
    os.makedirs(repo_dir, exist_ok=True)
    # 清掉上一次残留的 .apk，避免旧包混入本地源。
    for name in os.listdir(repo_dir):
        if name.endswith(".apk"):
            os.remove(os.path.join(repo_dir, name))

    total = 0
    for i, (name, rec) in enumerate(sorted(selected.items()), 1):
        filename = f"{name}-{rec['V']}.apk"
        url = f"{ALPINE_MIRROR}/{ALPINE_BRANCH}/{rec['_repo']}/{ARCH}/{filename}"
        data = http_get(url)
        with open(os.path.join(repo_dir, filename), "wb") as fh:
            fh.write(data)
        total += len(data)
        log(f"[alpine]   ({i}/{len(selected)}) {filename} {len(data) / 1024:.0f} KB")

    # 重建本地源索引：仅保留已选中包的原始记录（含官方 C: 校验和，签名校验仍生效）。
    index_text = "\n\n".join(rec["_raw"] for _, rec in sorted(selected.items())) + "\n"
    index_path = os.path.join(repo_dir, "APKINDEX.tar.gz")
    with tarfile.open(index_path, "w:gz") as tar:
        payload = index_text.encode("utf-8")
        info = tarfile.TarInfo("APKINDEX")
        info.size = len(payload)
        info.mtime = 0
        tar.addfile(info, io.BytesIO(payload))

    log(f"[alpine] 完成：{len(selected)} 个包，合计 {total / 1048576:.1f} MB -> {repo_dir}")


def fetch_napcat(out_dir: str) -> None:
    log("[napcat] 下载 NapCat.Shell.zip（GitHub Releases latest）")
    data = http_get(NAPCAT_URL, timeout=300)
    dest_dir = os.path.join(out_dir, "napcat")
    os.makedirs(dest_dir, exist_ok=True)
    dest = os.path.join(dest_dir, "NapCat.Shell.zip")
    with open(dest, "wb") as fh:
        fh.write(data)
    log(f"[napcat] 完成：{len(data) / 1048576:.1f} MB -> {dest}")


def main() -> int:
    parser = argparse.ArgumentParser(description="抓取 MiniMe-QBot 离线资产")
    parser.add_argument("--out", default=DEFAULT_OUT, help=f"输出目录（默认 {DEFAULT_OUT}）")
    parser.add_argument("--only", choices=("alpine", "napcat"), help="仅抓取其中一类")
    args = parser.parse_args()

    os.makedirs(args.out, exist_ok=True)
    if args.only in (None, "alpine"):
        fetch_alpine(args.out)
    if args.only in (None, "napcat"):
        fetch_napcat(args.out)

    log(f"\n完成。资产目录：{args.out}")
    log("提示：构建前请确认该目录已被 qbot-app/build.gradle.kts 的 assets.srcDir 挂载。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
