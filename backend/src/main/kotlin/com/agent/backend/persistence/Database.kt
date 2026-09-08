package com.agent.backend.persistence

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object Sessions : Table("sessions") {
    val id = varchar("id", 64)
    val token = varchar("token", 128)
    val deviceJson = text("device_json")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Messages : Table("messages") {
    val id = varchar("id", 64)
    val sessionId = varchar("session_id", 64)
    val role = varchar("role", 32)
    val content = text("content")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Executions : Table("executions") {
    val id = varchar("id", 64)
    val toolId = varchar("tool_id", 128)
    val status = varchar("status", 32)
    val resultJson = text("result_json").nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object CachedTools : Table("cached_tools") {
    val id = varchar("id", 64)
    val definitionJson = text("definition_json")
    override val primaryKey = PrimaryKey(id)
}

fun initDatabase(path: String): Database {
    File(path).parentFile?.mkdirs()
    val db = Database.connect("jdbc:sqlite:$path", driver = "org.sqlite.JDBC")
    transaction(db) {
        SchemaUtils.create(Sessions, Messages, Executions, CachedTools)
    }
    return db
}
