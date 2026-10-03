#!/usr/bin/env python3
"""
发版前强制验证清单脚本：打 tag 前必须全部通过，防止带病发版。

检查项：
1. 工作区干净（无未提交的修改）
2. 当前分支是 main
3. 本地代码与远程 main 同步（无未推送的提交）
4. CHANGELOG 已更新（包含当前版本号）
5. 版本号格式正确（vX.Y.Z 四段式，无 rc/beta/alpha/dev 后缀）
6. 预检查脚本通过（scripts/ci/pre-build-check.py）
7. 本地 release 编译通过（compileReleaseKotlin）
8. 本地 release 单元测试通过（testReleaseUnitTest）
9. 本地 release Lint 通过（lintRelease）

用法：python3 scripts/ci/pre-release-check.py --version v0.0.0.48
退出码：0=全部通过，可以发版；1=有错误，禁止发版
"""

import argparse
import os
import re
import subprocess
import sys

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CHANGELOG_PATH = os.path.join(PROJECT_ROOT, "docs/Version Log/CHANGELOG.md")

errors = []
warnings = []


def run_cmd(cmd, cwd=None, timeout=300):
    """运行命令并返回 (returncode, stdout, stderr) """
    try:
        result = subprocess.run(
            cmd, cwd=cwd or PROJECT_ROOT,
            capture_output=True, text=True, timeout=timeout
        )
        return result.returncode, result.stdout, result.stderr
    except subprocess.TimeoutExpired:
        return -1, "", "命令超时"


def check_git_clean():
    """检查工作区是否干净"""
    rc, stdout, stderr = run_cmd(["git", "status", "--porcelain"])
    if rc != 0:
        errors.append(f"[git] 无法获取 git 状态: {stderr}")
        return
    if stdout.strip():
        errors.append(
            f"[git] 工作区有未提交的修改:\n{stdout}\n"
            f"  修复：先提交或 stash 所有修改，确保工作区干净"
        )


def check_branch_main():
    """检查当前分支是否是 main"""
    rc, stdout, stderr = run_cmd(["git", "branch", "--show-current"])
    if rc != 0:
        errors.append(f"[git] 无法获取当前分支: {stderr}")
        return
    branch = stdout.strip()
    if branch != "main":
        errors.append(
            f"[git] 当前分支是 {branch}，不是 main\n"
            f"  修复：切换到 main 分支后再发版"
        )


def check_remote_sync():
    """检查本地代码与远程 main 是否同步"""
    run_cmd(["git", "fetch", "origin", "main"])
    rc, stdout, stderr = run_cmd(["git", "rev-list", "--left-right", "--count", "origin/main...HEAD"])
    if rc != 0:
        errors.append(f"[git] 无法比较本地与远程: {stderr}")
        return
    parts = stdout.strip().split()
    if len(parts) == 2:
        behind, ahead = int(parts[0]), int(parts[1])
        if ahead > 0:
            errors.append(
                f"[git] 本地有 {ahead} 个提交未推送到远程\n"
                f"  修复：先 git push 推送所有提交"
            )
        if behind > 0:
            errors.append(
                f"[git] 本地落后远程 {behind} 个提交\n"
                f"  修复：先 git pull 拉取最新代码"
            )


def check_changelog_updated(version):
    """检查 CHANGELOG 是否已更新（包含当前版本号）"""
    if not os.path.isfile(CHANGELOG_PATH):
        errors.append(f"[changelog] CHANGELOG 文件不存在: {CHANGELOG_PATH}")
        return

    with open(CHANGELOG_PATH, "r", encoding="utf-8") as f:
        content = f.read()

    # 检查是否包含版本号（支持 v0.0.0.48 或 0.0.0.48 格式）
    version_no_v = version.lstrip("v")
    if version not in content and version_no_v not in content:
        errors.append(
            f"[changelog] CHANGELOG 中未找到版本号 {version}\n"
            f"  修复：更新 docs/Version Log/CHANGELOG.md，添加 {version} 的更新说明"
        )


def check_version_format(version):
    """检查版本号格式是否正确"""
    # 四段式版本号 vX.Y.Z，无预发布后缀
    pattern = r'^v\d+\.\d+\.\d+\.\d+$'
    if not re.match(pattern, version):
        errors.append(
            f"[version] 版本号格式错误: {version}\n"
            f"  要求：vX.Y.Z 四段式（如 v0.0.0.48），禁止 rc/beta/alpha/dev 等预发布后缀\n"
            f"  原因：主应用只发正式版，不发预发布版"
        )


def check_pre_build_check():
    """运行预检查脚本"""
    script_path = os.path.join(PROJECT_ROOT, "scripts/ci/pre-build-check.py")
    if not os.path.isfile(script_path):
        warnings.append("[pre-check] 预检查脚本不存在，跳过")
        return

    rc, stdout, stderr = run_cmd(["python3", script_path], timeout=60)
    if rc != 0:
        errors.append(
            f"[pre-check] 预检查脚本失败（退出码 {rc}）\n"
            f"  输出：{stdout[-500:] if stdout else stderr[-500:]}"
        )


def check_local_compile():
    """检查本地 release 编译是否通过"""
    print("  正在本地编译 release（compileReleaseKotlin）...")
    env = os.environ.copy()
    env["JAVA_HOME"] = "/home/user/Doubao/chats/38444836735346434/jdk17/jdk-17.0.20.1+1"
    env["GRADLE_OPTS"] = "-Xmx1536m -XX:MaxMetaspaceSize=512m"

    rc, stdout, stderr = run_cmd(
        ["./gradlew", ":app:compileReleaseKotlin", "--no-daemon", "-q"],
        timeout=600
    )
    if rc != 0:
        errors.append(
            f"[compile] 本地 release 编译失败（退出码 {rc}）\n"
            f"  修复：修复编译错误后再发版\n"
            f"  错误摘要：{(stdout + stderr)[-500:]}"
        )
    else:
        print("  ✅ 本地 release 编译通过")


def check_local_unit_test():
    """检查本地 release 单元测试是否通过"""
    print("  正在运行本地 release 单元测试（testReleaseUnitTest）...")
    env = os.environ.copy()
    env["JAVA_HOME"] = "/home/user/Doubao/chats/38444836735346434/jdk17/jdk-17.0.20.1+1"
    env["GRADLE_OPTS"] = "-Xmx1536m -XX:MaxMetaspaceSize=512m"

    rc, stdout, stderr = run_cmd(
        ["./gradlew", ":app:testReleaseUnitTest", "--no-daemon", "-q"],
        timeout=600
    )
    if rc != 0:
        errors.append(
            f"[test] 本地 release 单元测试失败（退出码 {rc}）\n"
            f"  修复：修复测试失败后再发版\n"
            f"  错误摘要：{(stdout + stderr)[-500:]}"
        )
    else:
        print("  ✅ 本地 release 单元测试通过")


def check_local_lint():
    """检查本地 release Lint 是否通过"""
    print("  正在运行本地 release Lint（lintRelease）...")
    env = os.environ.copy()
    env["JAVA_HOME"] = "/home/user/Doubao/chats/38444836735346434/jdk17/jdk-17.0.20.1+1"
    env["GRADLE_OPTS"] = "-Xmx1536m -XX:MaxMetaspaceSize=512m"

    rc, stdout, stderr = run_cmd(
        ["./gradlew", ":app:lintRelease", "--no-daemon", "-q"],
        timeout=900
    )
    if rc != 0:
        errors.append(
            f"[lint] 本地 release Lint 失败（退出码 {rc}）\n"
            f"  修复：修复 Lint 错误后再发版\n"
            f"  错误摘要：{(stdout + stderr)[-500:]}"
        )
    else:
        print("  ✅ 本地 release Lint 通过")


def main():
    parser = argparse.ArgumentParser(description="发版前强制验证清单")
    parser.add_argument("--version", required=True, help="版本号（如 v0.0.0.48）")
    parser.add_argument("--skip-local-build", action="store_true",
                        help="跳过本地编译/测试/Lint（仅用于快速检查，不推荐）")
    args = parser.parse_args()

    print("=" * 60)
    print(f"发版前强制验证清单 - {args.version}")
    print("=" * 60)
    print()

    # 快速检查项
    print("【快速检查】")
    check_git_clean()
    check_branch_main()
    check_remote_sync()
    check_changelog_updated(args.version)
    check_version_format(args.version)
    check_pre_build_check()
    print()

    # 本地构建验证（耗时较长）
    if not args.skip_local_build:
        print("【本地构建验证（耗时较长，约5-15分钟）】")
        check_local_compile()
        check_local_unit_test()
        check_local_lint()
        print()
    else:
        warnings.append("已跳过本地构建验证（--skip-local-build），不推荐用于正式发版")

    # 输出结果
    if warnings:
        print(f"⚠️  警告 ({len(warnings)} 项):")
        print("-" * 40)
        for w in warnings:
            print(f"  {w}")
        print()

    if errors:
        print(f"❌ 错误 ({len(errors)} 项):")
        print("-" * 40)
        for i, e in enumerate(errors, 1):
            print(f"  {i}. {e}")
            print()
        print(f"⛔ 发版前验证失败：发现 {len(errors)} 个错误，禁止发版！")
        print("   请修复所有错误后重新运行此脚本验证。")
        sys.exit(1)
    else:
        print("✅ 发版前验证全部通过！可以安全发版。")
        if warnings:
            print(f"   （有 {len(warnings)} 个警告，不阻断发版但建议处理）")
        print()
        print(f"下一步：git tag -a {args.version} -m \"...\" && git push origin {args.version}")
        sys.exit(0)


if __name__ == "__main__":
    main()
