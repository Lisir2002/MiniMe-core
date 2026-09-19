# —— QEMU x86 translator 安装后的一次性部署：
#   1. 提供 /usr/local/bin/qemu-x86_64 稳定入口（Alpine 包装到 /usr/bin/qemu-aarch64 族，
#      /usr/bin/qemu-x86_64 存在则直接软链；不在则尝试查找带版本号的同名二进制）。
#   2. 安装 /usr/local/bin/minime-wrap-android-buildtools：扫描 ANDROID_HOME/build-tools
#      下所有 x86/x86_64 ELF，生成 qemu-x86_64 调用的 shell wrapper，解决 PRoot 无 binfmt
#      导致「Exec format error」的根本问题。脚本幂等：已是 wrapper 或已是 aarch64 ELF 不处理。
set +e
QEMU_TARGET="/usr/local/bin/qemu-x86_64"
mkdir -p /usr/local/bin
if [ -x /usr/bin/qemu-x86_64 ]; then
  ln -sf /usr/bin/qemu-x86_64 "$QEMU_TARGET"
else
  ALT="$(command -v qemu-x86_64 2>/dev/null || true)"
  if [ -n "$ALT" ] && [ -x "$ALT" ]; then
    ln -sf "$ALT" "$QEMU_TARGET"
  else
    FOUND="$(find /usr /opt -maxdepth 5 -type f -name 'qemu-x86_64*' -executable 2>/dev/null | head -1)"
    if [ -n "$FOUND" ]; then
      ln -sf "$FOUND" "$QEMU_TARGET"
    fi
  fi
fi

cat > /usr/local/bin/minime-wrap-android-buildtools <<'WRAPPER_SH_EOF'
#!/bin/sh
# 扫描 ANDROID_HOME/build-tools 下所有版本目录，把 x86/x86_64 静态/动态 ELF 改成同名 qemu wrapper。
# 原二进制改名 <name>.x86bin（若已存在则跳过），新同名脚本 exec /usr/local/bin/qemu-x86_64 <原二进制> "$@"。
set +e
QEMU="${MINIME_QEMU_X86:-/usr/local/bin/qemu-x86_64}"
if [ ! -x "$QEMU" ]; then
  echo "[ERROR] qemu-x86_64 未找到：$QEMU。请确认「x86 构建转译器」bundle 已安装。"
  exit 2
fi
SDK="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
if [ -z "$SDK" ] || [ ! -d "$SDK/build-tools" ]; then
  echo "[WARN] 未发现 ANDROID_HOME/build-tools：SDK=$SDK。未安装或未导出环境变量，跳过 wrapper。"
  exit 0
fi
wrap_one() {
  local f="$1"
  [ -f "$f" ] || return 0
  [ -x "$f" ] || return 0
  [ -L "$f" ] && return 0
  # 已经是 wrapper（第一行含 qemu-x86_64）则跳过
  if head -n 3 "$f" 2>/dev/null | grep -q 'qemu-x86_64' ; then return 0; fi
  local arch
  arch="$(file -b --mime-type - < "$f" 2>/dev/null)"
  case "$arch" in
    application/x-executable|application/x-pie-executable|application/x-sharedlib) : ;;
    *) return 0 ;;
  esac
  local elf_hdr
  elf_hdr="$(head -c 20 "$f" 2>/dev/null | od -An -tx1 | tr -d ' \n')"
  # ELF magic (7f454c46) + EI_CLASS=2(64位) + EI_DATA=1(LSB) + EI_MACHINE=003e(x86_64) / 0003(i386)
  case "$elf_hdr" in
    7f454c4602010100*3e00*) : ok x86_64 ;;
    7f454c4601010100*0300*) : ok i386 ;;
    *) return 0 ;;
  esac
  local bin="$f.x86bin"
  if [ -e "$bin" ]; then
    echo "[skip] 已存在备份，跳过：$f"
    return 0
  fi
  mv "$f" "$bin" || { echo "[fail] mv $f -> $bin"; return 0; }
  cat > "$f" <<WRAP_EOF
#!/bin/sh
exec "$QEMU" "$bin" "\$@"
WRAP_EOF
  chmod +x "$f"
  echo "[wrap] $f -> qemu-x86_64 $bin"
}
TOTAL=0
for d in "$SDK"/build-tools/*/; do
  [ -d "$d" ] || continue
  echo "[scan] $d"
  # cmdline-tools / build-tools 常见二进制（x86 版）：aapt2 aapt zipalign split-select aidl dexdump d8 apksigner libLTO.so etc.
  for candidate in \
    aapt2 aapt zipalign split-select aidl dexdump llvm-rs-cc \
    d8 apkanalyzer avdmanager lint screenshot2 sdkmanager jobb \
    libaapt2.so libbcc.so libLLVM.so libcutils.so; do
    if [ -f "$d$candidate" ]; then wrap_one "$d$candidate"; TOTAL="$(($TOTAL + 1))"; fi
  done
  # lib/ 下 x86_64 子目录常见动态库（aapt2 加载用）：软链到上层同名可能被 qemu 需要；这里仅做 wrapper，保持路径不动
  if [ -d "${d}lib" ]; then
    find "${d}lib" -type f -not -name '*.x86bin' 2>/dev/null | while read -r so; do wrap_one "$so"; done
  fi
done
echo "[done] 共扫描/尝试包装 $TOTAL 个候选文件。"
WRAPPER_SH_EOF
chmod +x /usr/local/bin/minime-wrap-android-buildtools

if command -v /usr/local/bin/minime-wrap-android-buildtools >/dev/null 2>&1; then
  echo "[hook OK] 已安装 minime-wrap-android-buildtools。"
else
  echo "[hook FAIL] minime-wrap-android-buildtools 未写成功。"
  exit 1
fi
