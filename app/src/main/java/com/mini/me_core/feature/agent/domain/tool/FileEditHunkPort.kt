package com.mini.me_core.feature.agent.domain.tool

import com.mini.me_core.feature.agent.data.local.entity.FileEditHunkEntity

interface FileEditHunkPort {
    suspend fun insert(entity: FileEditHunkEntity)
}
