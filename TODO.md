# TODO 跟踪清单

> 本文件跟踪项目中所有待办事项，定期清理。新增 TODO 必须在此登记，完成后移除。

## 待处理

### 1. T2I 月度用量统计
- **位置**: `feature/t2i/domain/permission/T2IPermissionPolicyEngine.kt:126`
- **描述**: monthlyUsedTokens 需要读取 DataStore `t2i_monthly_used_tokens_${yyyyMM}`
- **优先级**: P2
- **关联**: RC69+ 版本规划

### 2. 远程同步方向控制
- **位置**: `feature/workspace/presentation/remote/RemoteServerViewModel.kt:324`
- **描述**: 将 direction 传入 SyncEngine，按方向限制 upload/download/watch 行为
- **优先级**: P2

### 3. 远程同步冲突策略
- **位置**: `feature/workspace/presentation/remote/RemoteServerViewModel.kt:331`
- **描述**: 将 strategy 传入 SyncEngine，解决冲突时按策略执行
- **优先级**: P2

### 4. 远程同步周期调度
- **位置**: `feature/workspace/presentation/remote/RemoteServerViewModel.kt:337`
- **描述**: 按 intervalMinutes 启动/取消周期同步协程
- **优先级**: P2

## 已完成

（暂无）

## 规范

1. 代码中新增 `// TODO:` 注释时，必须同步在本文件登记
2. 完成 TODO 后，移除代码注释并从本文件移至"已完成"
3. 每季度清理一次，超过 6 个月未处理的 TODO 需评估是否移除或升级
4. TODO 描述需包含：位置、描述、优先级
