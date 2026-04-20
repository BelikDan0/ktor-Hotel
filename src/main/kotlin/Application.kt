package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.enums.Role
import com.example.models.BookingsTable
import com.example.models.RoomsTable
import com.example.models.UsersTable
import com.example.security.PasswordUtil
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

import io.ktor.server.plugins.cors.routing.*
import io.ktor.http.*
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.parseAuthorizationHeader
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun main() {
    embeddedServer(Netty, port = 8080) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "Hotel API"
            verifier(
                JWT.require(Algorithm.HMAC256("super_secret_key"))
                    .withIssuer("http://localhost:8080")
                    .build()
            )
            validate { credential ->
                // Возвращаем твой userId обратно
                if (credential.payload.getClaim("userId").asLong() != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            // Настройка для того, чтобы Ktor видел токен при переходе по ссылке
            authHeader { call ->
                // 1. Сначала проверяем куки (для перехода в админку)
                val cookieToken = call.request.cookies["token"]
                if (cookieToken != null) return@authHeader HttpAuthHeader.Single("Bearer", cookieToken)

                // 2. Затем проверяем стандартный заголовок (для fetch-запросов)
                val authHeader = call.request.parseAuthorizationHeader()
                if (authHeader is HttpAuthHeader.Single && authHeader.authScheme == "Bearer") {
                    return@authHeader authHeader
                }
                null
            }
        }
    }

    Database.connect(
        url = "jdbc:postgresql://localhost:5432/hotel_db",
        driver = "org.postgresql.Driver",
        user = "postgres",
        password = "2324"
    )

    // Создание таблицы, если её нет
    transaction {
        SchemaUtils.create(UsersTable)
        SchemaUtils.create(RoomsTable)
        SchemaUtils.create(BookingsTable)
        transaction {
            SchemaUtils.create(RoomsTable)
            if (RoomsTable.selectAll().empty()) {
                RoomsTable.insert {
                    it[number] = "101"
                    it[category] = "Premium"
                    it[description] = "Панорамный вид на горы Сочи, джакузи."
                    it[price] = 15000.0
                    it[isAvailable] = true

                }
                RoomsTable.insert {
                    it[number] = "204"
                    it[category] = "Standard"
                    it[description] = "Уютный номер для двоих, всё включено."
                    it[price] = 5000.0
                    it[isAvailable] = true

                }
            }
        }
    }
    // 🔥 CORS (чтобы фронт работал)
    install(CORS) {
        // Разрешаем запросы с твоего фронтенда
        allowHost("127.0.0.1:8080")
        allowHost("localhost:8080")

        // Разрешаем нужные методы
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)

        // Разрешаем заголовки
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)

        // Позволяет передавать куки (нужно для auth_token)
        allowCredentials = true

        // Если тестируешь локально, можно временно разрешить любой источник (не для продакшена!)
        // anyHost()
    }

    // 🔥 JSON (иначе receive не будет работать)
    install(ContentNegotiation) {
        json()
    }

    // 🔥 Подключаем твои роуты
    configureRouting()
}