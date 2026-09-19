plugins {
    // Android Library 核心模块：凭据加密（MasterKey→DEK→AES-GCM）+ crypto + 凭据 domain 模型。
    // 依赖规则：只依赖 :core:model；对 feature.workspace / datalayer 的耦合已反转为
    // SecurityAuditRecorder / CredentialStateStore 端口（见 SecurityPorts.kt），实现由 :app 注入。
    id("mini-me.core-android")
}

android {
    namespace = "com.mini.me_core.core.security"
}

dependencies {
    // 跨模块纯领域模型 + 日志 facade + 审计常量。
    implementation(project(":core:model"))
    // HostKeyManager 依赖 SSH 主机密钥校验（与 :app 同版本）。
    implementation("com.hierynomus:sshj:0.38.0")
    // @Inject/@Singleton（JSR-330）+ @ApplicationContext qualifier。
    // Hilt 图仍在 :app 聚合；本模块仅引入注解 API，不跑 ksp。
    implementation("javax.inject:javax.inject:1")
    implementation("com.google.dagger:hilt-android:2.56.1")
}
