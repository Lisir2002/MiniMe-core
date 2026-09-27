package com.mini.me_core.core.ui

/**
 * 全局滚动位置管理器：按页面 key 保存/恢复滚动位置。
 * 内存级存储（进程内有效），用于页面间导航时保留滚动位置。
 */
object ScrollPositionManager {
    private data class Position(val index: Int, val offset: Int)
    private val positions = mutableMapOf<String, Position>()

    fun save(key: String, index: Int, offset: Int) {
        positions[key] = Position(index, offset)
    }

    fun get(key: String): Pair<Int, Int>? =
        positions[key]?.let { it.index to it.offset }

    fun clear(key: String) {
        positions.remove(key)
    }
}
