#!/usr/bin/env python3
"""抓取 MiniMe-QBot 首次启动所需的离线资产（应用首启「零网络」的全部外部件）。

设计目标（设计文档 §8.2）：首启供给阶段**不联网**——App 把容器内所需的一切随包分发，
首次运行由 `QBotRuntimeInstaller` 落盘到 `files/qbot/offline/`，供给脚本在容器内本地安装。

产出（默认写入 qbot-app/src/_qbotAssets/，与 app/src/_armAssets 同为「入库/随包」二进制资产）：

  rootfs/ubuntu-22.04-arm64-rootfs.bin   glibc 容器底座（Ubuntu base arm64，gzip 内容）
  napcat/NapCat.Shell.zip                协议端本体（GitHub Releases latest）
  apt/pool.bin                           apt 依赖闭包（jammy arm64 .deb 集，gzip tar）
  qq/linuxqq-arm64.deb                   官方 QQ Linux 客户端（arm64）
  launcher/libnapcat_launcher.so         协议端启动器（预编译 glibc arm64，免容器内编译）

为什么是 glibc 底座：NapCat 官方仅支持 Ubuntu/Debian/CentOS（glibc），官方 QQ Linux 客户端
亦为 glibc 二进制；Alpine(musl) 无法加载其动态链接器。故 QBot 容器不复用主应用的 Alpine 资产，
自备一套 glibc rootfs（PRoot/loader 等与发行版无关的组件仍复用主应用 `_armAssets`）。

用法：
  python3 scripts/dev/fetch-qbot-offline-assets.py                    # 补齐缺失的全部资产
  python3 scripts/dev/fetch-qbot-offline-assets.py --only apt         # 仅 apt 依赖闭包
  python3 scripts/dev/fetch-qbot-offline-assets.py --only qq          # 仅官方 QQ 客户端
  python3 scripts/dev/fetch-qbot-offline-assets.py --only rootfs      # 仅容器底座
  python3 scripts/dev/fetch-qbot-offline-assets.py --only napcat      # 仅协议端本体
  python3 scripts/dev/fetch-qbot-offline-assets.py --only launcher    # 仅协议端启动器
  python3 scripts/dev/fetch-qbot-offline-assets.py --force            # 已存在也重新抓取

已存在的资产默认跳过（幂等）；除 `launcher`（已入库，可 `--force` 重建）外，其余体积大或需随时效
更新，**不入库**，由本脚本在构建机/CI 上抓取后随包分发（见 .github/workflows/qbot-release.yml）。

平台前提：`apt` 依赖闭包需要 Debian/Ubuntu 构建机（用 apt 以「隔离状态目录」下载 arm64 闭包，
不污染宿主 apt）；`launcher` 需要 `g++-aarch64-linux-gnu`（`apt install g++-aarch64-linux-gnu`）；
rootfs / napcat / qq 仅需网络与 curl。
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
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

# ── apt 依赖闭包 ──────────────────────────────────────────────────────────
# 目标包集合：与 provision-protocol.sh 的「联网回退」分支一致，但**不含 g++ / libc6-dev**——
# 协议端启动器改为随包预编译 .so（见 launcher 一节），容器内不再需要编译器与头文件。
#   xvfb/xauth/xdg-utils：无显示环境启动 QQ（Electron）
#   curl/ca-certificates/zip/unzip/xz-utils/jq/procps：解压与进程管理
#   其余图形/音频库：QQ（Electron）运行所需
APT_TARGETS = [
    "curl", "ca-certificates", "zip", "unzip", "jq", "xz-utils", "procps",
    "xvfb", "xauth", "xdg-utils",
    "libgtk-3-0", "libnotify4", "libnss3", "libxss1", "libxtst6",
    "libatspi2.0-0", "libuuid1", "libsecret-1-0", "libappindicator3-1",
    "libgbm1", "libasound2",
]
APT_SUITE = "jammy"
# 与容器底座同为 22.04(jammy)；apt 源按顺序回退（国内镜像优先，最后官方 ports）。
APT_MIRRORS = [
    "http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports",
    "http://mirrors.aliyun.com/ubuntu-ports",
    "http://ports.ubuntu.com/ubuntu-ports",
]
APT_POOL_NAME = "pool.bin"

# ── 官方 QQ Linux 客户端 ──────────────────────────────────────────────────
# 优先从官方 pcConfig.json 动态解析最新 arm64 deb（与供给脚本同源），解析失败回退下方镜像
# （GitHub 上的第三方归档仓，路径稳定、可用性优于腾讯 CDN 的历史版本目录）。
QQ_CONFIG_URL = "https://cdn-go.cn/qq-web/im.qq.com_new/latest/rainbow/pcConfig.json"
QQ_DEB_MIRROR_URL = (
    "https://github.com/xiaocongyu66/termux-yunzai-qqpkg/releases/download/"
    "linuxqq-3.2.30/linuxqq_3.2.30-50828_arm64.deb"
)
QQ_DEB_NAME = "linuxqq-arm64.deb"

# ── 协议端启动器 ──────────────────────────────────────────────────────────
LAUNCHER_CPP_URL = (
    "https://raw.githubusercontent.com/NapNeko/napcat-linux-launcher/refs/heads/main/launcher.cpp"
)
LAUNCHER_SO_NAME = "libnapcat_launcher.so"
# GitHub raw 直连不稳时的加速前缀（与供给脚本候选集一致）。
GH_PROXIES = ["https://ghfast.top", "https://gh-proxy.com", "https://github.moeyy.xyz"]

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


def http_download(url: str, dest: str, timeout: int = 1800) -> int:
    """流式下载到文件（大文件不驻留内存），返回字节数。"""
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    tmp = dest + ".part"
    if shutil.which("curl"):
        for attempt in range(1, 6):
            proc = subprocess.run(
                ["curl", "-fL", "--retry", "3", "--connect-timeout", "20",
                 "--max-time", str(timeout), "-A", USER_AGENT, "-o", tmp, url],
                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            )
            if proc.returncode == 0 and os.path.exists(tmp) and os.path.getsize(tmp) > 0:
                os.replace(tmp, dest)
                return os.path.getsize(dest)
            log(f"[warn] 下载失败（第 {attempt}/5 次）：{url} -> {proc.stderr.decode('utf-8', 'replace').strip()}")
            time.sleep(min(3 * attempt, 15))
        raise SystemExit(f"[错误] 下载失败：{url}")

    for attempt in range(1, 4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=timeout) as resp, open(tmp, "wb") as fh:
                shutil.copyfileobj(resp, fh)
            os.replace(tmp, dest)
            return os.path.getsize(dest)
        except Exception as exc:
            log(f"[warn] 下载失败（第 {attempt}/3 次）：{url} -> {exc}")
            time.sleep(3 * attempt)
    raise SystemExit(f"[错误] 下载失败：{url}")


def _already(path: str, force: bool) -> bool:
    if os.path.exists(path) and not force:
        log(f"[skip] 已存在（--force 可强制重抓）：{path}")
        return True
    return False


def fetch_rootfs(out_dir: str, force: bool) -> None:
    dest = os.path.join(out_dir, "rootfs", ROOTFS_NAME)
    if _already(dest, force):
        return
    log(f"[rootfs] 下载 glibc 容器底座（Ubuntu base arm64）：{ROOTFS_URL}")
    size = http_download(ROOTFS_URL, dest)
    # 校验是可解压的 rootfs tar（含 /etc 目录），避免把 HTML 错误页当资产入库。
    with tarfile.open(dest, "r:*") as tar:
        names = {n.lstrip("./") for n in tar.getnames()[:2000]}
    if "etc" not in names and "etc/" not in names:
        os.remove(dest)
        raise SystemExit(f"[错误] 底座内容异常（未找到 /etc），文件可能不是有效 rootfs：{dest}")
    log(f"[rootfs] 完成：{size / 1048576:.1f} MB -> {dest}")


def fetch_napcat(out_dir: str, force: bool) -> None:
    dest = os.path.join(out_dir, "napcat", "NapCat.Shell.zip")
    if _already(dest, force):
        return
    log("[napcat] 下载 NapCat.Shell.zip（GitHub Releases latest）")
    size = http_download(NAPCAT_URL, dest)
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
    log(f"[napcat] 完成：{size / 1048576:.1f} MB（{len(names)} 个条目）-> {dest}")


def _require_apt() -> None:
    if not shutil.which("apt-get"):
        raise SystemExit(
            "[错误] 未找到 apt-get：apt 依赖闭包只能在 Debian/Ubuntu 构建机上生成"
            "（可用 --only 跳过该类资产）。"
        )


def fetch_apt(out_dir: str, force: bool) -> None:
    """下载 Ubuntu jammy **arm64** 依赖闭包并打包为 gzip tar（`apt/pool.bin`）。

    在 amd64 构建机上以「隔离 apt 状态目录」完成：不改宿主 sources.list、不改宿主已装包，
    仅把 arm64 的包索引与 .deb 落到临时目录（`Dir::State` / `Dir::Cache` 全部重定向）。
    """
    dest = os.path.join(out_dir, "apt", APT_POOL_NAME)
    if _already(dest, force):
        return
    _require_apt()

    os.makedirs(os.path.dirname(dest), exist_ok=True)
    status_src = _extract_rootfs_dpkg_status(out_dir)
    try:
        last_err = None
        for mirror in APT_MIRRORS:
            log(f"[apt] 解析 arm64 依赖闭包：{mirror}")
            try:
                count = _apt_download_and_pack(mirror, dest, status_src)
            except SystemExit as exc:
                last_err = exc
                log(f"[warn] 该镜像不可用，换下一个：{mirror}")
                continue
            log(f"[apt] 完成：{count} 个包 / {os.path.getsize(dest) / 1048576:.1f} MB -> {dest}")
            return
        raise SystemExit(f"[错误] 所有镜像均失败：{last_err}")
    finally:
        if status_src:
            os.remove(status_src)


def _extract_rootfs_dpkg_status(out_dir: str) -> str:
    """从随包 rootfs 里取出容器实际的 dpkg 已装清单（作为依赖解析基准）。

    这是本函数的关键：apt 的闭包 = 「目标包集合」−「容器里已装的包」。若拿空清单当基准，
    apt 会把 libc6/perl/base-files 等基础包也当成待装而全部下载（体积翻倍，且容器内
    重装基础包会执行 postinst，风险高）。用真实 rootfs 的 status 才能得到**增量闭包**。
    """
    rootfs = os.path.join(out_dir, "rootfs", ROOTFS_NAME)
    if not os.path.exists(rootfs):
        log(f"[warn] 未找到容器底座 {rootfs}，改用空清单解析（会连带下载基础包，体积偏大）")
        return ""
    with tarfile.open(rootfs, "r:*") as tar:
        for member in tar:
            if member.name.lstrip("./") == "var/lib/dpkg/status":
                fh = tar.extractfile(member)
                if fh is not None:
                    tmp = tempfile.NamedTemporaryFile(prefix="qbot-dpkg-status-", delete=False)
                    tmp.write(fh.read())
                    tmp.close()
                    log(f"[apt] 已取用容器底座 dpkg 清单作为依赖基准：{tmp.name}")
                    return tmp.name
    log("[warn] 底座内未找到 var/lib/dpkg/status，改用空清单解析（体积偏大）")
    return ""


def _apt_download_and_pack(mirror: str, dest: str, status_src: str) -> int:
    """以「隔离 apt 状态」下载 arm64 增量闭包并直接打包为 [dest]（gzip tar），返回包数。

    全程重定向 `Dir::State` / `Dir::Cache` 到临时目录，不读写宿主 sources.list 与已装包状态。
    """
    with tempfile.TemporaryDirectory(prefix="qbot-apt-") as tmp:
        lists = os.path.join(tmp, "lists")
        pool = os.path.join(tmp, "pool")
        cache = os.path.join(tmp, "cache")
        os.makedirs(os.path.join(lists, "partial"), exist_ok=True)
        os.makedirs(pool, exist_ok=True)
        os.makedirs(cache, exist_ok=True)

        sources = os.path.join(tmp, "sources.list")
        with open(sources, "w") as fh:
            for suite in (APT_SUITE, f"{APT_SUITE}-updates", f"{APT_SUITE}-security"):
                fh.write(f"deb [arch=arm64] {mirror} {suite} main restricted universe multiverse\n")

        # dpkg 已装清单：优先用容器底座的真实清单（增量闭包），否则退化为空清单（全量闭包）。
        status = os.path.join(tmp, "status")
        if status_src and os.path.exists(status_src):
            shutil.copyfile(status_src, status)
        else:
            open(status, "w").close()

        opts = [
            "-o", f"Dir::Etc::sourcelist={sources}",
            "-o", "Dir::Etc::sourceparts=-",
            "-o", f"Dir::State::lists={lists}",
            "-o", f"Dir::State::status={status}",
            "-o", f"Dir::Cache={cache}",
            "-o", f"Dir::Cache::archives={pool}",
            "-o", "APT::Architecture=arm64",
            "-o", "APT::Architectures::=arm64",
            "-o", "APT::Get::List-Cleanup=0",
            "-o", "Acquire::Retries=3",
            "-o", "Debug::NoLocking=1",
        ]
        # root 直接执行；非 root 走 sudo -n（CI runner 免密 sudo 可用）。
        prefix = [] if os.geteuid() == 0 else ["sudo", "-n"]

        def run(args: list) -> None:
            proc = subprocess.run(prefix + ["apt-get"] + opts + args,
                                  stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            if proc.returncode != 0:
                raise SystemExit(
                    f"apt-get {args[0]} 失败（exit={proc.returncode}）：\n"
                    + proc.stdout.decode("utf-8", "replace")[-4000:]
                )

        run(["update"])
        run(["install", "--download-only", "--no-install-recommends", "-y"] + APT_TARGETS)

        debs = [
            os.path.join(pool, n) for n in sorted(os.listdir(pool))
            if n.endswith(".deb") and os.path.isfile(os.path.join(pool, n))
        ]
        if not debs:
            raise SystemExit("闭包为空，未下载到任何 .deb")

        tmp_tar = dest + ".part"
        # 与解压侧约定一致：条目为 `./xxx.deb`，QBotRuntimeInstaller 直接平铺释放到 offline/apt。
        with tarfile.open(tmp_tar, "w:gz") as tar:
            for deb in debs:
                tar.add(deb, arcname="./" + os.path.basename(deb))
        os.replace(tmp_tar, dest)
        return len(debs)


def resolve_qq_urls() -> list:
    """返回候选下载地址（按优先级）：官方 pcConfig.json 动态解析 → 镜像归档。

    注意：官方 CDN（qqdl.gtimg.cn）对直连**时好时坏**（实测同一地址先 200 后 403，
    带防盗链特征），故不能只信「能解析出地址」——必须逐个候选真正下载，首个成功者胜出。
    """
    urls = []
    try:
        data = http_get(QQ_CONFIG_URL, timeout=60)
        match = re.search(r'https://[^"\s]*arm64_01\.deb', data.decode("utf-8", "replace"))
        if match:
            urls.append(match.group(0))
    except SystemExit:
        log("[warn] 官方 pcConfig.json 解析失败，直接使用镜像归档地址")
    urls.append(QQ_DEB_MIRROR_URL)
    # 去重且保序
    seen = set()
    return [u for u in urls if not (u in seen or seen.add(u))]


def fetch_qq(out_dir: str, force: bool) -> None:
    dest = os.path.join(out_dir, "qq", QQ_DEB_NAME)
    if _already(dest, force):
        return

    last_err = None
    for url in resolve_qq_urls():
        log(f"[qq] 下载官方 QQ Linux 客户端（arm64）：{url}")
        try:
            size = http_download(url, dest)
        except SystemExit as exc:
            last_err = exc
            log(f"[warn] 该地址不可用，换下一个候选")
            continue
        # .deb 为 ar 归档，魔数 `!<arch>\n`；再校验控制信息里的架构（dpkg-deb 可用时）。
        with open(dest, "rb") as fh:
            valid = fh.read(8) == b"!<arch>\n"
        if not valid:
            log("[warn] 下载内容不是有效 deb（ar 魔数不匹配），换下一个候选")
            os.remove(dest)
            continue
        if shutil.which("dpkg-deb"):
            out = subprocess.run(["dpkg-deb", "-f", dest, "Architecture"],
                                 stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
            arch = out.stdout.decode().strip()
            if arch and arch != "arm64":
                log(f"[warn] 架构不符（期望 arm64，实际 {arch}），换下一个候选")
                os.remove(dest)
                continue
        if size < 50 * 1048576:
            log(f"[warn] QQ deb 体积异常偏小（{size / 1048576:.1f} MB），请确认下载是否完整")
        log(f"[qq] 完成：{size / 1048576:.1f} MB -> {dest}")
        return
    raise SystemExit(f"[错误] 所有候选地址均失败：{last_err}")


def fetch_launcher(out_dir: str, force: bool) -> None:
    """交叉编译协议端启动器（glibc arm64），免去容器内安装 g++ 及其约 58MB 依赖。"""
    dest = os.path.join(out_dir, "launcher", LAUNCHER_SO_NAME)
    if _already(dest, force):
        return
    cross_gxx = shutil.which("aarch64-linux-gnu-g++")
    if not cross_gxx:
        raise SystemExit(
            "[错误] 未找到 aarch64-linux-gnu-g++：请先 `apt-get install -y g++-aarch64-linux-gnu`"
            "（该 .so 已随仓库入库，通常无需重建）。"
        )

    src = http_get(LAUNCHER_CPP_URL)
    # 直连失败时该 URL 会返回 HTML/空内容：以是否含 C 头文件特征粗判。
    if b"#include" not in src:
        raise SystemExit("[错误] 启动器源码内容异常（未找到 #include），请稍后重试或改用加速源")

    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="qbot-launcher-") as tmp:
        cpp = os.path.join(tmp, "launcher.cpp")
        with open(cpp, "wb") as fh:
            fh.write(src)
        proc = subprocess.run(
            [cross_gxx, "-shared", "-fPIC", "-O2", cpp, "-o", dest, "-ldl"],
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        )
        if proc.returncode != 0:
            raise SystemExit("[错误] 交叉编译失败：\n" + proc.stdout.decode("utf-8", "replace")[-4000:])

    with open(dest, "rb") as fh:
        head = fh.read(20)
    # ELF64 little-endian / EM_AARCH64(0xB7) / ET_DYN(3)
    if head[:4] != b"\x7fELF" or head[4] != 2 or head[5] != 1 or head[18] != 0xB7 or head[16] != 3:
        os.remove(dest)
        raise SystemExit("[错误] 产物不是 arm64 共享库（ELF 头校验失败）")
    log(f"[launcher] 完成：{os.path.getsize(dest) / 1024:.1f} KB -> {dest}")


def main() -> int:
    parser = argparse.ArgumentParser(description="抓取 MiniMe-QBot 离线资产")
    parser.add_argument("--out", default=DEFAULT_OUT, help=f"输出目录（默认 {DEFAULT_OUT}）")
    parser.add_argument("--only", choices=("rootfs", "napcat", "apt", "qq", "launcher"),
                        help="仅抓取其中一类")
    parser.add_argument("--force", action="store_true", help="已存在也重新抓取")
    args = parser.parse_args()

    os.makedirs(args.out, exist_ok=True)
    steps = {
        "rootfs": fetch_rootfs,
        "napcat": fetch_napcat,
        "apt": fetch_apt,
        "qq": fetch_qq,
        "launcher": fetch_launcher,
    }
    for name, fn in steps.items():
        if args.only in (None, name):
            fn(args.out, args.force)

    log(f"\n完成。资产目录：{args.out}")
    log("提示：构建前请确认该目录已被 qbot-app/build.gradle.kts 的 assets.srcDir 挂载。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
