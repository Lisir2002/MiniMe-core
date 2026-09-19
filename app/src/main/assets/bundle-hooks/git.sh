# 配置 credential.helper=store：把 ~/.git-credentials 放在宿主共享目录
# /data/data/<pkg>/files/git/ 下（由 ContainerEngine 参数 -H /root/.git-credentials -> 该路径映射），
# 跨 rootfs 升级不丢。
git config --global credential.helper store 2>/dev/null || true
