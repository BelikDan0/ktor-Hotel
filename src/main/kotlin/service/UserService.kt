package com.example.service

import com.example.enums.Role
import com.example.models.User
import com.example.models.UsersTable
import com.example.security.PasswordUtil
import com.example.security.jwt.JwtService
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction


class UserService {
    private val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\$".toRegex()

    fun register(email: String, password: String): User {
        // 1. Валидация
        if (!email.matches(emailRegex)) throw IllegalArgumentException("Неверный формат почты")
        if (password.length < 6) throw IllegalArgumentException("Пароль должен быть не менее 6 символов")

        return transaction {
            // Исправленный поиск: используем selectAll().where
            val exists = UsersTable.selectAll().where { UsersTable.email eq email }.any()
            if (exists) throw Exception("Пользователь уже существует")

            val hashed = PasswordUtil.hash(password)

            // Вставка данных
            val insertStatement = UsersTable.insert {
                it[UsersTable.email] = email
                it[UsersTable.password] = hashed
                it[UsersTable.role] = Role.USER.name
            }

            // Возвращаем объект User с реальным ID из БД
            User(
                id = insertStatement[UsersTable.id],
                email = email,
                password = hashed,
                role = Role.USER
            )
        }
    }

    fun login(email: String, password: String): String {
        return transaction {
            // Ищем юзера в таблице
            val userRow = UsersTable.selectAll().where { UsersTable.email eq email }.singleOrNull()
                ?: throw Exception("Пользователь не найден")

            val user = User(
                id = userRow[UsersTable.id],
                email = userRow[UsersTable.email],
                password = userRow[UsersTable.password],
                role = Role.valueOf(userRow[UsersTable.role])
            )

            if (!PasswordUtil.verify(password, user.password)) {
                throw Exception("Неверный пароль")
            }

            JwtService.generate(user)
        }
    }
}