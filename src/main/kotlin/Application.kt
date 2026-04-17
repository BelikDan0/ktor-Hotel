package com.example

import com.example.models.BookingsTable
import com.example.models.RoomsTable
import com.example.models.UsersTable
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

import io.ktor.server.plugins.cors.routing.*
import io.ktor.http.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
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
                    it[imageUrl] = "https://images.unsplash.com/photo-1590490360182-c33d57733427?w=500"
                }
                RoomsTable.insert {
                    it[number] = "204"
                    it[category] = "Standard"
                    it[description] = "Уютный номер для двоих, всё включено."
                    it[price] = 5000.0
                    it[imageUrl] = "https://images.unsplash.com/photo-1566665797739-1674de7a421a?w=500"
                }
            }
        }
    }
    // 🔥 CORS (чтобы фронт работал)
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization) // 🔥 ОБЯЗАТЕЛЬНО ДОБАВЬ ЭТО
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Options)
    }

    // 🔥 JSON (иначе receive не будет работать)
    install(ContentNegotiation) {
        json()
    }

    // 🔥 Подключаем твои роуты
    configureRouting()
}