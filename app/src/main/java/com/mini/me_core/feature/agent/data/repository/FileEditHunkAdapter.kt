package com.mini.me_core.feature.agent.data.repository

import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.feature.agent.data.local.entity.FileEditHunkEntity
import com.mini.me_core.feature.agent.domain.tool.FileEditHunkPort
import javax.inject.Inject

class FileEditHunkAdapter @Inject constructor(private val v2: AgentRepository) : FileEditHunkPort {
    override suspend fun insert(entity: FileEditHunkEntity) {
        v2.insertFileEditHunk(entity.id, entity.sessionId, entity.filePath, entity.operation,
            entity.hunk, entity.oldContent, entity.newContent, entity.createdAtMs)
    }
}
