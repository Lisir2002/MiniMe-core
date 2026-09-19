# 把 /etc/passwd 中 root 的 shell 从 /bin/sh 改成 /bin/bash
# （busybox adduser 的默认 sh；sed 一次到位）。
if grep -q '^root:' /etc/passwd 2>/dev/null; then
  sed -i 's|^root:\([^:]*\):\([^:]*\):\([^:]*\):\([^:]*\):\([^:]*\):\([^:]*\):/bin/sh$|root:\1:\2:\3:\4:\5:\6:/bin/bash|' /etc/passwd 2>/dev/null || true
fi
# 新用户默认 shell / 兜底环境变量
[ -f /etc/default/useradd ] || echo 'SHELL=/bin/bash' > /etc/default/useradd 2>/dev/null || true
# PS1 前缀钩子：无论 .bashrc 里原先 PS1 如何设定，都在最前面加上 "> "
# 用 PROMPT_COMMAND 确保渲染前补前缀；去重函数防止嵌套补两次
mkdir -p /root
cat >> /root/.bashrc <<'BASHRC_EOF'
__apply_prompt_prefix() {
  case "$PS1" in
    '> '*) : ;;
    *) PS1='> '$PS1 ;;
  esac
}
case ";${PROMPT_COMMAND:-};" in
  *";__apply_prompt_prefix;"*) : ;;
  *) PROMPT_COMMAND="__apply_prompt_prefix${PROMPT_COMMAND:+;$PROMPT_COMMAND}" ;;
esac
BASHRC_EOF
# 系统级兜底（其他用户 / 非交互登录）
cat >> /etc/profile <<'PROFILE_EOF'
__apply_prompt_prefix() {
  case "$PS1" in
    '> '*) : ;;
    *) PS1='> '$PS1 ;;
  esac
}
case ";${PROMPT_COMMAND:-};" in
  *";__apply_prompt_prefix;"*) : ;;
  *) PROMPT_COMMAND="__apply_prompt_prefix${PROMPT_COMMAND:+;$PROMPT_COMMAND}" ;;
esac
PROFILE_EOF
