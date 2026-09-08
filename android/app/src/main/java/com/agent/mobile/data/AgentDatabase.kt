package com.agent.mobile.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tools")
data class ToolEntity(
    @PrimaryKey val id: String,
    val definitionJson: String,
    val updatedAt: Long,
)

@Entity(tableName = "executions")
data class ExecutionEntity(
    @PrimaryKey val id: String,
    val toolId: String,
    val status: String,
    val logJson: String,
    val resultJson: String?,
    val createdAt: Long,
)

@Dao
interface ToolDao {
    @Query("SELECT * FROM tools ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ToolEntity>>

    @Query("SELECT * FROM tools")
    suspend fun getAll(): List<ToolEntity>

    @Query("SELECT * FROM tools WHERE id = :id")
    suspend fun getById(id: String): ToolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ToolEntity)

    @Query("DELETE FROM tools WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ExecutionDao {
    @Query("SELECT * FROM executions ORDER BY createdAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<ExecutionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ExecutionEntity)
}

@Database(entities = [ToolEntity::class, ExecutionEntity::class], version = 1, exportSchema = false)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun toolDao(): ToolDao
    abstract fun executionDao(): ExecutionDao
}
