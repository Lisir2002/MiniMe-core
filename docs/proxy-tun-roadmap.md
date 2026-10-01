# 代理 TUN 模式技术约束与未来方向

## 背景

需求：用 Android `VpnService` 建立 TUN 接口，把系统级流量导入 mihomo，实现全局系统代理（不只是 App 内 OkHttp / WebView / 容器 env 三路代理）。

阶段四（P3）经技术调研后**决定跳过完整 TUN 模式**，本文记录约束与未来方向，避免重复调研。

## 当前架构

- mihomo 以**独立 CLI 二进制子进程**运行（下载 `mihomo-android-arm64` 资产，`ProcessBuilder` 拉起）。
- App 进程通过 `127.0.0.1:7890`（mixed-port）与 `127.0.0.1:9090`（external-controller）与之通信。
- 代理生效范围：App 内 OkHttp `ProxySelector`、WebView 代理、容器 `http_proxy` env 注入。**不覆盖系统全局流量**（如其他 App、未走代理的 socket）。

## TUN 内核侧可行性（已确认）

mihomo TUN 配置支持 `file-descriptor: int` ——「Use existing TUN fd (Android/iOS VPN frameworks)」。
即内核**能消费一个外部传入的已建立 TUN fd**，不必自己 open `/dev/net/tun`（非 root 下该设备不可达）。

```yaml
tun:
  enable: true
  stack: gvisor        # 或 mixed
  file-descriptor: 42  # 外部传入的 fd
  auto-route: false    # Android VpnService 已负责路由，内核不要再跑 ip route
  dns-hijack: [any:53]
  mtu: 9000
```

## 核心冲突点：fd 无法跨进程传给子进程

`VpnService.Builder.establish()` 返回 `ParcelFileDescriptor`，其 fd **默认带 `FD_CLOEXEC`**，子进程 `execve` 时自动关闭。Java/`ProcessBuilder` 也没有干净途径把一个普通 fd 作为非 CLOEXEC 继承给子进程。

主流方案（ClashMetaForAndroid、sing-box for Android）都是把 mihomo/sing-box 编译成 **gomobile AAR 库、跑在 App 进程内**，fd 同进程直接传入内核。我们不是这种架构，因此 fd 传不进子进程。

## 三条路线对比

| 方案 | 原理 | 结论 |
|---|---|---|
| A. native 桥接 fork/exec 传 fd | 写 C/JNI `.so`，fork 时保留 VpnService fd（清 CLOEXEC），再 exec mihomo，config 传 `file-descriptor` | 理论可行，但需维护 native 桥、真机调试成本高，暂不做 |
| B. tun2socks 用户态栈 | App 进程从 VpnService fd 读 IP 包 → Kotlin 用户态 TCP/IP 栈 → 经 mihomo SOCKS5 转发 → 写回 fd | 等于重写 hev-socks5-tunnel，工作量极大 |
| C. 降级 VpnService + addHttpProxy | 仅用 VpnService 声明 VPN，`Builder.addHttpProxy()` 指 7890 | 弱：Android 29+ 多数 App 不遵守系统 HTTP 代理，体验差 |

## 决策

**跳过完整 TUN。** 当前三路代理（OkHttp / WebView / 容器 env）已覆盖核心场景；完整 TUN 投入产出比低。

## 未来方向

当内核接入层从「独立 CLI 子进程」演进为 **gomobile AAR 库（同进程）** 时：
1. `VpnService.establish()` 拿到的 `ParcelFileDescriptor` 可在同进程直接交给 mihomo；
2. config 用 `tun.enable=true` + `tun.file-descriptor=<fd>` + `auto-route=false`；
3. 即可实现完整全局 TUN，无需 native 桥接。

这是把 mihomo 改造为库化接入时的自然副产品，不在本次阶段范围。
