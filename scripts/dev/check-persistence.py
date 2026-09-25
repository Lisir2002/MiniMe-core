#!/usr/bin/env python3
"""
数据持久化静态检查脚本

检查项目中常见的数据持久化 bug：
1. putJson() 写入后用 getString() 读取（读写列不匹配）
2. @Singleton 类的 MutableStateFlow 初始值硬编码默认值（未从磁盘恢复）
3. exportConfig/snapshot 方法中读取类型与写入不一致

用法：
    python3 scripts/dev/check-persistence.py [--fix]

退出码：
    0 - 无问题
    1 - 发现问题
"""

import os
import re
import sys
from pathlib import Path
from collections import defaultdict

PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
SRC_DIR = PROJECT_ROOT / "app" / "src" / "main" / "java"

# 颜色输出
RED = "\033[91m"
YELLOW = "\033[93m"
GREEN = "\033[92m"
RESET = "\033[0m"
BOLD = "\033[1m"

# 已知误报白名单：这些类的 MutableStateFlow 是运行时状态，不需要持久化
# 格式：(相对路径, 行号关键词) 或 仅相对路径
SINGLETON_STATEFLOW_WHITELIST = {
    "RemoteSshConnection.kt",           # SSH 活连接状态，冷启动后确实断开
    "SkillStateRepository.kt",          # refreshTrigger 刷新计数器，非持久化
    "RealProgressAggregator.kt",        # 安装进度聚合器，运行时状态
    "ExecutionModeHolder.kt",           # 由 MiniMeCore 启动时 setMode 恢复（外部注入）
    "TerminalSessionManager.kt",        # PTY 会话 revision 计数器，运行时状态
    "RemoteTerminalSessionManager.kt",  # 远程 PTY 会话，运行时状态
    "McpServerManager.kt",              # init 块中从 KVStore 恢复（脚本检测不到 init 块内的赋值）
    "ClashProxyManager.kt",             # init 块中从 repository 恢复
    "FtpServerManager.kt",              # init 块中从 KVStore 恢复
    "BrowserController.kt",             # 浏览器运行时状态
    "LinuxContainerEngine.kt",          # 已修复：init 块中从磁盘标记恢复
}

issues = []
warnings = []


def find_kt_files():
    """递归查找所有 .kt 文件"""
    return list(SRC_DIR.rglob("*.kt"))


def read_file(path):
    """读取文件内容"""
    try:
        return path.read_text(encoding="utf-8")
    except Exception:
        return ""


def check_putjson_getstring_mismatch(files):
    """
    检查规则1：putJson() 写入后用 getString() 读取
    
    在同一个类中，如果存在 putJson(NS, KEY, ...) 调用，
    则检查是否存在 getString(NS, KEY) 调用（应该用 getJson）。
    """
    print(f"\n{BOLD}[检查1] KVStore putJson/getString 读写类型匹配{RESET}")
    
    for filepath in files:
        content = read_file(filepath)
        if not content:
            continue
        
        # 找出所有 putJson 调用的 (namespace, key) 对
        putjson_keys = set()
        # 匹配 kv.putJson(NS, KEY, ...) 或 kv.putJson(namespace, key, ...)
        for match in re.finditer(r'putJson\(\s*(\w+)\s*,\s*(\w+)\s*,', content):
            ns_var = match.group(1)
            key_var = match.group(2)
            putjson_keys.add((ns_var, key_var))
        
        if not putjson_keys:
            continue
        
        # 检查是否有对应的 getString 调用
        for ns_var, key_var in putjson_keys:
            # 匹配 getString(NS, KEY) 或 getString(namespace, key)
            pattern = rf'getString\(\s*{re.escape(ns_var)}\s*,\s*{re.escape(key_var)}\s*\)'
            if re.search(pattern, content):
                rel_path = filepath.relative_to(PROJECT_ROOT)
                issues.append(
                    f"  {RED}[ERROR]{RESET} {rel_path}: "
                    f"putJson({ns_var}, {key_var}) 写入后用 getString() 读取，"
                    f"应改用 getJson()"
                )
        
        # 同时检查是否有正确的 getJson 调用
        for ns_var, key_var in putjson_keys:
            pattern = rf'getJson\(\s*{re.escape(ns_var)}\s*,\s*{re.escape(key_var)}\s*\)'
            if not re.search(pattern, content):
                rel_path = filepath.relative_to(PROJECT_ROOT)
                warnings.append(
                    f"  {YELLOW}[WARN]{RESET} {rel_path}: "
                    f"putJson({ns_var}, {key_var}) 未找到对应的 getJson() 读取，"
                    f"请确认读取方式正确"
                )
    
    if not any("putJson" in read_file(f) for f in files):
        print(f"  {GREEN}未发现 putJson 调用{RESET}")
    elif not issues:
        print(f"  {GREEN}所有 putJson 调用均有正确的 getJson 读取{RESET}")


def check_singleton_stateflow_init(files):
    """
    检查规则2：@Singleton 类的 MutableStateFlow 初始值是否硬编码默认值
    
    这是一个启发式检查，可能有误报，需要人工确认。
    """
    print(f"\n{BOLD}[检查2] @Singleton MutableStateFlow 初始值从磁盘恢复{RESET}")
    
    for filepath in files:
        content = read_file(filepath)
        if not content:
            continue
        
        # 检查是否是 @Singleton 类
        if not re.search(r'@Singleton', content):
            continue
        
        # 找出所有 MutableStateFlow(...) 调用
        for match in re.finditer(r'MutableStateFlow\(([^)]+)\)', content):
            init_value = match.group(1).strip()
            
            # 跳过明显是从磁盘加载的情况
            if any(kw in init_value for kw in ['loadFrom', 'load(', 'read(', 'get(', 'prefs.', 'kv.', 'db.', 'file.', 'File(', 'DEFAULT', 'emptyList', 'emptyMap', 'emptySet', '""', "''", '0', 'false', 'true', 'null', '0L', '0.0', '0.0f']):
                # 如果是 DEFAULT/emptyList 等，可能是合理的默认值，但需要确认是否有 init 块恢复
                if any(kw in init_value for kw in ['loadFrom', 'load(', 'read(', 'prefs.', 'kv.', 'db.', 'file.']):
                    continue  # 明确从磁盘加载，跳过
            
            # 检查类中是否有 init 块或构造函数中从磁盘恢复的逻辑
            has_init_recovery = bool(re.search(r'init\s*\{', content))
            has_load_method = bool(re.search(r'fun\s+(load|restore|recover|initFrom|readFrom)', content))
            
            if not has_init_recovery and not has_load_method:
                rel_path = filepath.relative_to(PROJECT_ROOT)
                # 跳过白名单中的已知运行时状态类
                if filepath.name in SINGLETON_STATEFLOW_WHITELIST:
                    continue
                line_num = content[:match.start()].count('\n') + 1
                warnings.append(
                    f"  {YELLOW}[WARN]{RESET} {rel_path}:{line_num}: "
                    f"@Singleton 类 MutableStateFlow({init_value}) 初始值可能硬编码，"
                    f"且未发现 init 块或 load 方法从磁盘恢复"
                )
    
    if not warnings:
        print(f"  {GREEN}未发现明显的硬编码初始值问题{RESET}")


def check_export_config_types(files):
    """
    检查规则3：exportConfig/snapshot 方法中读取类型与写入一致
    
    启发式检查：在 exportConfig/snapshot 方法中，如果用 getString 读取，
    检查该键是否在类中用 putBool/putInt 写入。
    """
    print(f"\n{BOLD}[检查3] exportConfig/snapshot 读取类型一致性{RESET}")
    
    for filepath in files:
        content = read_file(filepath)
        if not content:
            continue
        
        # 找出所有 putBool/putInt 调用的键
        putbool_keys = set()
        putint_keys = set()
        for match in re.finditer(r'putBool\(\s*(\w+)\s*,\s*(\w+)\s*,', content):
            putbool_keys.add((match.group(1), match.group(2)))
        for match in re.finditer(r'putInt\(\s*(\w+)\s*,\s*(\w+)\s*,', content):
            putint_keys.add((match.group(1), match.group(2)))
        
        if not putbool_keys and not putint_keys:
            continue
        
        # 查找 exportConfig/snapshot 方法
        export_methods = re.findall(r'fun\s+(exportConfig|snapshot|toJson|toMap)\s*\([^)]*\)\s*[:{]', content)
        if not export_methods:
            continue
        
        # 在 export 方法中检查是否用 getString 读取 putBool/putInt 的键
        for ns_var, key_var in putbool_keys | putint_keys:
            pattern = rf'getString\(\s*{re.escape(ns_var)}\s*,\s*{re.escape(key_var)}\s*\)'
            if re.search(pattern, content):
                rel_path = filepath.relative_to(PROJECT_ROOT)
                write_type = "putBool" if (ns_var, key_var) in putbool_keys else "putInt"
                issues.append(
                    f"  {RED}[ERROR]{RESET} {rel_path}: "
                    f"键 {key_var} 用 {write_type}() 写入，但 export/snapshot 中用 getString() 读取，"
                    f"应改用 get{'Bool' if write_type == 'putBool' else 'Int'}()"
                )
    
    if not issues:
        print(f"  {GREEN}未发现 exportConfig 类型不匹配问题{RESET}")


def main():
    print(f"{BOLD}=== 数据持久化静态检查 ==={RESET}")
    print(f"项目根目录: {PROJECT_ROOT}")
    
    files = find_kt_files()
    print(f"扫描文件数: {len(files)}")
    
    check_putjson_getstring_mismatch(files)
    check_singleton_stateflow_init(files)
    check_export_config_types(files)
    
    # 输出结果
    print(f"\n{BOLD}=== 检查结果 ==={RESET}")
    
    if issues:
        print(f"\n{RED}{BOLD}错误 ({len(issues)}):{RESET}")
        for issue in issues:
            print(issue)
    
    if warnings:
        print(f"\n{YELLOW}{BOLD}警告 ({len(warnings)}):{RESET}")
        for warning in warnings:
            print(warning)
    
    if not issues and not warnings:
        print(f"\n{GREEN}{BOLD}✓ 所有检查通过，未发现数据持久化问题{RESET}")
        return 0
    
    if issues:
        print(f"\n{RED}{BOLD}✗ 发现 {len(issues)} 个错误，{len(warnings)} 个警告{RESET}")
        return 1
    
    print(f"\n{YELLOW}{BOLD}⚠ 发现 {len(warnings)} 个警告（需人工确认）{RESET}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
