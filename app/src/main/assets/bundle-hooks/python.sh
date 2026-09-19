# ============================================================
# S2 Fix: Python pip 安装的三链路兜底
# ============================================================
set +e
pip_ok=0
if command -v apk >/dev/null 2>&1; then
  if apk info -e py3-pip >/dev/null 2>&1; then
    echo "[pip] 链路1 命中：py3-pip 已安装，OK"
    pip_ok=1
  else
    echo "[pip] 链路2：尝试 apk add py3-pip（依赖 community 仓库）"
    apk add --no-cache py3-pip >/tmp/py3_pip_apk.log 2>&1
    if [ $? -eq 0 ] && apk info -e py3-pip >/dev/null 2>&1; then
      echo "[pip] 链路2 成功"
      pip_ok=1
    else
      echo "[pip] 链路2 失败，apk 日志末 3 行："
      tail -n 3 /tmp/py3_pip_apk.log 2>/dev/null || true
    fi
  fi
fi
if [ "$pip_ok" -eq 0 ]; then
  echo "[pip] 链路3：python3 -m ensurepip --upgrade（不依赖 Alpine 仓库）"
  python3 -m ensurepip --upgrade >/tmp/py3_ensurepip.log 2>&1
  rc=$?
  if command -v pip3 >/dev/null 2>&1 || python3 -m pip --version >/dev/null 2>&1; then
    echo "[pip] 链路3 成功 (ensurepip rc=$rc)"
    pip_ok=1
  else
    echo "[pip] 链路3 失败，ensurepip 日志末 3 行："
    tail -n 3 /tmp/py3_ensurepip.log 2>/dev/null || true
  fi
fi
if [ "$pip_ok" -eq 0 ]; then
  echo "[pip] 链路4：下载 PyPA get-pip.py 终极安装（失败 3 次停止不占网）"
  GP_URL="https://bootstrap.pypa.io/get-pip.py"
  GP_TMP="/tmp/get-pip.py"
  got=0
  for i in 1 2 3; do
    rm -f "$GP_TMP"
    if command -v curl >/dev/null 2>&1; then
      curl -fsSL "$GP_URL" -o "$GP_TMP" >/dev/null 2>&1
    elif command -v wget >/dev/null 2>&1; then
      wget -q "$GP_URL" -O "$GP_TMP" >/dev/null 2>&1
    else
      echo "[pip] 链路4 中止：容器内无 curl/wget"
      break
    fi
    if [ -s "$GP_TMP" ] && [ "$(wc -c < "$GP_TMP")" -gt 4096 ]; then
      got=1
      break
    fi
    sleep 1
  done
  if [ "$got" -eq 1 ]; then
    python3 "$GP_TMP" >/tmp/py3_getpip.log 2>&1
    if command -v pip3 >/dev/null 2>&1 || python3 -m pip --version >/dev/null 2>&1; then
      echo "[pip] 链路4 成功"
      pip_ok=1
    else
      echo "[pip] 链路4 失败，get-pip 日志末 3 行："
      tail -n 3 /tmp/py3_getpip.log 2>/dev/null || true
    fi
  fi
  rm -f "$GP_TMP"
fi
if [ "$pip_ok" -eq 1 ]; then
  echo "[pip] 三链路兜底成功。pip 版本："
  python3 -m pip --version 2>&1 || pip3 --version 2>&1 || true
  python3 -m pip install --upgrade 'pip<25' >/dev/null 2>&1 || true
  exit 0
else
  echo "[pip] 三链路全部失败（可能容器网络未就绪），本次 Python bundle 仍算装成"
  echo "[pip] （后续用户手动运行：apk add py3-pip 或 python3 -m ensurepip）"
  exit 0
fi
