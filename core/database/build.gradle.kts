plugins {
    // Android Library：数据层——SQLDelight 多库 + SQLCipher 全盘加密 + 迁移/自愈。
    // 当前 SQLDelight .sq 与生成代码仍挂在 :app 的 sqldelight{} 块（6 库拓扑已在 DataLayerModule），
    // 本模块为数据层下沉的落点：后续把 engine/ migration/ sqldelight/ 迁入即完成模块化。
    id("mini-me.core-android")
}

android {
    namespace = "com.mini.me_core.core.database"
}

dependencies {
    implementation(project(":core:model"))
}
