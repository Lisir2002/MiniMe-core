plugins {
    // Feature 模块：Agent 中枢（presentation + 工具实现）。
    // 架构规则 #1：只依赖 :core:* 与 :newui；Hilt 图仍在 :app 聚合（本模块不 apply hilt）。
    // 说明：feature.agent 被全仓 import 944 次、与 feature.terminal/proxy/workspace 深度耦合，
    // 完整下沉需在可编译迭代中分阶段进行；本模块为其落点，工具抽象/状态机已下沉 :core:agent-workflow。
    id("mini-me.feature")
}

android {
    namespace = "com.mini.me_core.feature.agent"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:security"))
    implementation(project(":core:agent-workflow"))
    implementation(project(":core:container"))
    implementation(project(":newui"))
}
