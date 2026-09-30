#!/usr/bin/env python3
"""生成 MiniMe-QBot 环境注入包的清单 `manifest.json`（注入器与 QBot 的共用事实源）。

背景（见 docs/qbot-injector-design.md）：
  QBot 的体积型运行资产（rootfs / NapCat / apt 依赖池 / QQ 客户端 / 启动器）不再随主应用
  分发，改由一个**独立注入器 APK** 一次性写入双方可读的公共目录；主应用只保留一份极小的
  「期望清单」用于校验。

本脚本是清单的唯一生成者，同一次运行写出两份**字节完全一致**的清单：

  qbot-app/src/_qbotAssets/manifest.json        注入器随包读取（资产来源目录）
  qbot-app/src/main/assets/qbot/pack-manifest.json  QBot 随包读取（期望清单，体积极小）

清单里有两个版本概念，职责**必须分清**：

  contractVersion  人工维护的「契约号」（本脚本常量）。只在**资产种类或目录布局变化**
                   （增删资产、改落盘路径、改 kind）时才 +1。QBot 用它判断注入包能否被
                   自己使用：契约相等即接受，**与资产内容无关**。
  packVersion      资产内容的哈希指纹（非人工维护）：把各资产的 sha256 按 name 排序拼接后
                   再取 sha256 前 12 位，前缀 `p-`。只用于标识「这是哪一版注入包」，
                   **不再作为 QBot 的准入条件**——否则环境资源一更新，用户就必须连 QBot
                   一起更新，注入器「环境只下载一次」的意义就没了。

因此：注入器单独更新资源（如 QQ 升级）时，只要契约号不变，旧版 QBot 仍可直接使用新注入包。

用法（仓库根执行）：
  python3 scripts/dev/gen-qbot-pack-manifest.py
  python3 scripts/dev/gen-qbot-pack-manifest.py --print-version   # 仅打印 packVersion
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import tarfile

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS_DIR = os.path.join(REPO_ROOT, "qbot-app", "src", "_qbotAssets")
QBOT_MANIFEST = os.path.join(REPO_ROOT, "qbot-app", "src", "main", "assets", "qbot", "pack-manifest.json")

# 人工维护的「契约号」：只在**资产种类或目录布局变化**（增删资产、改落盘路径、改 kind）时 +1。
# QBot 用它判断注入包能否被自己使用（契约相等即接受，与资产内容无关）。
CONTRACT_VERSION = "1"

# 资产定义（顺序即清单顺序）：
#   name      资产标识（两侧契约，勿随意改名）
#   source    注入器随包资产路径（相对 _qbotAssets）
#   path      注入包内的落盘路径（相对 pack 根）；aptPool 为解包后的目录
#   kind      file=整文件拷贝；tar_gz_debs=gzip tar，解包出 *.deb 到目录
ASSETS = [
    {"name": "rootfs", "source": "rootfs/ubuntu-22.04-arm64-rootfs.bin", "path": "rootfs/ubuntu-22.04-arm64-rootfs.bin", "kind": "file"},
    {"name": "napcat", "source": "napcat/NapCat.Shell.zip", "path": "napcat/NapCat.Shell.zip", "kind": "file"},
    {"name": "aptPool", "source": "apt/pool.bin", "path": "offline/apt", "kind": "tar_gz_debs"},
    {"name": "qqDeb", "source": "qq/linuxqq-arm64.deb", "path": "offline/linuxqq-arm64.deb", "kind": "file"},
    {"name": "launcher", "source": "launcher/libnapcat_launcher.so", "path": "offline/libnapcat_launcher.so", "kind": "file"},
]


def sha256_file(path: str, chunk: int = 1024 * 1024) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as fh:
        while True:
            block = fh.read(chunk)
            if not block:
                break
            digest.update(block)
    return digest.hexdigest()


def deb_count(pool_path: str) -> int:
    """统计 pool.bin（gzip tar）内的 .deb 条目数。"""
    count = 0
    with tarfile.open(pool_path, "r:*") as tar:
        for member in tar:
            if member.isfile() and member.name.endswith(".deb"):
                count += 1
    return count


def derive_pack_version(entries: list) -> str:
    """packVersion = "p-" + sha256(各资产 sha256 按 name 排序拼接).hexdigest()[:12]。"""
    joined = "".join(f"{e['name']}:{e['sha256']}" for e in sorted(entries, key=lambda e: e["name"]))
    return "p-" + hashlib.sha256(joined.encode("utf-8")).hexdigest()[:12]


def build_manifest() -> dict:
    entries = []
    for asset in ASSETS:
        src = os.path.join(ASSETS_DIR, asset["source"])
        if not os.path.isfile(src):
            raise SystemExit(
                f"[错误] 缺少注入资产：{src}\n"
                f"       请先运行：python3 scripts/dev/fetch-qbot-offline-assets.py"
            )
        if asset["kind"] == "tar_gz_debs":
            entry = {
                "name": asset["name"],
                "source": asset["source"],
                "path": asset["path"],
                "kind": asset["kind"],
                "count": deb_count(src),
                "sha256": sha256_file(src),
            }
        else:
            entry = {
                "name": asset["name"],
                "source": asset["source"],
                "path": asset["path"],
                "kind": asset["kind"],
                "size": os.path.getsize(src),
                "sha256": sha256_file(src),
            }
        entries.append(entry)

    return {
        "contractVersion": CONTRACT_VERSION,
        "packVersion": derive_pack_version(entries),
        "assets": entries,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="生成 MiniMe-QBot 注入包清单 manifest.json")
    parser.add_argument("--print-version", action="store_true", help="仅打印 packVersion")
    args = parser.parse_args()

    manifest = build_manifest()

    if args.print_version:
        print(manifest["packVersion"])
        return 0

    text = json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=False) + "\n"

    os.makedirs(os.path.dirname(os.path.join(ASSETS_DIR, "manifest.json")), exist_ok=True)
    with open(os.path.join(ASSETS_DIR, "manifest.json"), "w", encoding="utf-8") as fh:
        fh.write(text)

    os.makedirs(os.path.dirname(QBOT_MANIFEST), exist_ok=True)
    with open(QBOT_MANIFEST, "w", encoding="utf-8") as fh:
        fh.write(text)

    print(f"[manifest] contractVersion={manifest['contractVersion']} packVersion={manifest['packVersion']}")
    for e in manifest["assets"]:
        size = e.get("size")
        detail = f"{size} bytes" if size is not None else f"{e.get('count')} 个 deb"
        print(f"[manifest]   {e['name']:<8} {e['path']} ({detail})")
    print(f"[manifest] 已写出：{os.path.join(ASSETS_DIR, 'manifest.json')}")
    print(f"[manifest] 已写出：{QBOT_MANIFEST}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
