#!/usr/bin/env python3
"""抓取 MiniMe-QBot 首次启动所需的离线资产（glibc 运行时底座 + 协议端本体）。

产出（默认写入 qbot-app/src/_qbotAssets/，与 app/src/_armAssets 同为「入库二进制资产」）：
  rootfs/ubuntu-22.04-arm64-rootfs.bin      glibc 容器底座（Ubuntu base，官方 arm64 rootfs，gzip 内容）
  napcat/NapCat.Shell.zip                    协议端本体（GitHub Releases latest）

为什么是 glibc 底座：NapCat 官方仅支持 Ubuntu/Debian/CentOS（glibc），官方 QQ Linux 客户端
亦为 glibc 二进制；Alpine(musl) 无法加载其动态链接器。故 QBot 容器不复用主应用的 Alpine 资产，
自备一套 glibc rootfs（PRoot/loader 等与发行版无关的组件仍复用主应用 `_armAssets`）。

这两个目录会被 qbot-app/build.gradle.kts 挂进 assets；App 首启时由 QBotRuntimeInstaller
落盘，供给脚本再在容器内安装系统依赖与 QQ 客户端（此二者体积大且随版本更新，仍走网络）。

用法：
  python3 scripts/dev/fetch-qbot-offline-assets.py                  # 全量抓取
  python3 scripts/dev/fetch-qbot-offline-assets.py --only rootfs    # 仅容器底座
  python3 scripts/dev/fetch-qbot-offline-assets.py --only napcat    # 仅协议端本体

说明：本脚本在构建机上执行（需 python3 + 终端可达对应镜像 / GitHub），产出物入库；
CI/打包不需要运行它，只需要仓库里已存在的资产。升级底座或 NapCat 时重跑本脚本。
"""

import argparse
import os
import shutil
import subprocess
import sys
import tarfile
import time
import urllib.request
import zipfile

USER_AGENT = "Mozilla/5.0 (compatible; MiniMeQBotAssetFetch/1.0)"

# 容器底座：Ubuntu base 官方 arm64 rootfs（glibc）。`22.04/release/` 为最新点版本的稳定符号链接。
# 与 QBotRuntimeInstaller 的 ROOTFS 资产路径 / INSTALL_VERSION 联动，改动需同步。
ROOTFS_URL = "https://cdimage.ubuntu.com/ubuntu-base/releases/22.04/release/ubuntu-base-22.04-base-arm64.tar.gz"
# 注意：文件名**不得以 `.gz` 结尾**。打包工具会把 `.gz` 资产自动解压并去掉后缀，
# 导致 APK 内资产名与 QBotRuntimeInstaller 预期的路径不符、运行环境安装失败；
# 故用 `.bin` 后缀（内容仍是 gzip，运行期按魔数嗅探）。
ROOTFS_NAME = "ubuntu-22.04-arm64-rootfs.bin"

# 协议端本体：GitHub Releases latest（与上游 napcat-linux-installer 一致）。
NAPCAT_URL = "https://github.com/NapNeko/NapCatQQ/releases/latest/download/NapCat.Shell.zip"

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_OUT = os.path.join(REPO_ROOT, "qbot-app", "src", "_qbotAssets")


def log(msg: str) -> None:
    print(msg, flush=True)


def http_get(url: str, timeout: int = 600) -> bytes:
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


def fetch_rootfs(out_dir: str) -> None:
    log(f"[rootfs] 下载 glibc 容器底座（Ubuntu base arm64）：{ROOTFS_URL}")
    data = http_get(ROOTFS_URL)
    dest_dir = os.path.join(out_dir, "rootfs")
    os.makedirs(dest_dir, exist_ok=True)
    dest = os.path.join(dest_dir, ROOTFS_NAME)
    with open(dest, "wb") as fh:
        fh.write(data)
    # 校验是可解压的 rootfs tar（含 /etc 目录），避免把 HTML 错误页当资产入库。
    with tarfile.open(dest, "r:*") as tar:
        names = {n.lstrip("./") for n in tar.getnames()[:2000]}
    if "etc" not in names and "etc/" not in names:
        raise SystemExit(f"[错误] 底座内容异常（未找到 /etc），文件可能不是有效 rootfs：{dest}")
    log(f"[rootfs] 完成：{len(data) / 1048576:.1f} MB -> {dest}")


def fetch_napcat(out_dir: str) -> None:
    log("[napcat] 下载 NapCat.Shell.zip（GitHub Releases latest）")
    data = http_get(NAPCAT_URL)
    dest_dir = os.path.join(out_dir, "napcat")
    os.makedirs(dest_dir, exist_ok=True)
    dest = os.path.join(dest_dir, "NapCat.Shell.zip")
    with open(dest, "wb") as fh:
        fh.write(data)
    # 校验是有效 zip（含 napcat 入口），避免把 GitHub 错误页当资产入库。
    try:
        with zipfile.ZipFile(dest) as zf:
            bad = zf.testzip()
            if bad is not None:
                raise SystemExit(f"[错误] NapCat 压缩包损坏：{bad}")
            names = zf.namelist()
    except zipfile.BadZipFile as exc:
        raise SystemExit(f"[错误] NapCat 资产不是有效 zip：{exc}")
    if not any(n.endswith("napcat.mjs") for n in names):
        raise SystemExit("[错误] NapCat 资产缺少 napcat.mjs 入口，可能不是 Shell 包")
    log(f"[napcat] 完成：{len(data) / 1048576:.1f} MB（{len(names)} 个条目）-> {dest}")


def main() -> int:
    parser = argparse.ArgumentParser(description="抓取 MiniMe-QBot 离线资产")
    parser.add_argument("--out", default=DEFAULT_OUT, help=f"输出目录（默认 {DEFAULT_OUT}）")
    parser.add_argument("--only", choices=("rootfs", "napcat"), help="仅抓取其中一类")
    args = parser.parse_args()

    os.makedirs(args.out, exist_ok=True)
    if args.only in (None, "rootfs"):
        fetch_rootfs(args.out)
    if args.only in (None, "napcat"):
        fetch_napcat(args.out)

    log(f"\n完成。资产目录：{args.out}")
    log("提示：构建前请确认该目录已被 qbot-app/build.gradle.kts 的 assets.srcDir 挂载。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
