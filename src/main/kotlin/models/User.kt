package com.example.models

import com.example.enums.Role
import org.jetbrains.exposed.v1.core.Table

data class User(
    val id: Long,
    val email: String,
    val password: String, // ВАЖНО: тут хранится ХЕШ, не пароль
    val role: Role
)
object UsersTable : Table("users") {
    val id = long("id").autoIncrement()
    val email = varchar("email", 100).uniqueIndex()
    val password = varchar("password", 100)
    val role = varchar("role", 20)

    override val primaryKey = PrimaryKey(id)
}