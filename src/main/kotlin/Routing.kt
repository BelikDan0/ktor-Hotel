package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.models.BookingRequest
import com.example.models.BookingResponse
import com.example.models.BookingsTable
import com.example.models.Room
import com.example.models.RoomsTable
import com.example.service.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.static
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureRouting()
{
    val userService = UserService()
    routing {
        get("/") {
            call.respondRedirect("/static/index.html")
        }

        // Настройка раздачи файлов из папки resources/static
        static("/static") {
            resources("static")
        }
        post("/register") {
            try {
                val request = call.receive<Map<String, String>>()
                val email = request["email"] ?: ""
                val password = request["password"] ?: ""

                val user = userService.register(email, password) // Возвращает объект User

                // Передаем объект 'user', который мы только что получили
                val token = com.example.security.jwt.JwtService.generate(user)

                call.respond(mapOf("token" to token, "message" to "Success"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Unknown error")))
            }
        }
        post("/login"){
            val request = call.receive<Map<String, String>>()
            val email=request["email"] ?:""
            val password=request["password"] ?:""
            val token=userService.login(email,password)
            call.respond(mapOf("token" to token))
        }
        get("/auth/verify") {
            val authHeader = call.request.headers["Authorization"]
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return@get call.respond(HttpStatusCode.Unauthorized)
            }

            val token = authHeader.removePrefix("Bearer ")
            try {
                val algorithm = Algorithm.HMAC256("super_secret_key")
                val verifier = JWT.require(algorithm)
                    .withIssuer("http://localhost:8080")
                    .build()

                // 🔥 ВОТ ТУТ МАГИЯ: если время вышло, упадет Exception
                verifier.verify(token)

                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                // Если время вышло или токен подделан, летим сюда
                println("Token verification failed: ${e.message}")
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
        get("/api/rooms") {
            try {
                val rooms = transaction {
                    RoomsTable.selectAll().map {
                        Room( // Используем наш data class
                            id = it[RoomsTable.id],
                            number = it[RoomsTable.number],
                            category = it[RoomsTable.category],
                            description = it[RoomsTable.description],
                            price = it[RoomsTable.price],
                            imageUrl = it[RoomsTable.imageUrl]
                        )
                    }
                }
                call.respond(rooms) // Теперь Ktor точно знает, как это сериализовать
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        // 1. Создать бронирование
        route("/api/bookings") {

            // СОЗДАНИЕ БРОНИ
            post("/create") {
                val userPrincipal = call.principal<JWTPrincipal>()
                val userId = userPrincipal?.payload?.getClaim("id")?.asLong() ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val request = call.receive<BookingRequest>()

                transaction {
                    BookingsTable.insert {
                        it[BookingsTable.userId] = userId
                        it[BookingsTable.roomId] = request.roomId
                        it[status] = "PENDING"
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("message" to "Заявка отправлена"))
            }

            // ПОЛУЧЕНИЕ СПИСКА (Исправляет ошибку типов)
            get("/my") {
                val userPrincipal = call.principal<JWTPrincipal>()
                val userId = userPrincipal?.payload?.getClaim("id")?.asLong() ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val myBookings = transaction {
                    // Соединяем таблицы Bookings и Rooms по roomId
                    (BookingsTable innerJoin RoomsTable)
                        .selectAll()
                        .where(BookingsTable.userId eq userId)
                        .map {
                            BookingResponse(
                                id = it[BookingsTable.id],
                                roomNumber = it[RoomsTable.number].toString(),
                                price = it[RoomsTable.price],
                                status = it[BookingsTable.status],
                                createdAt = it[BookingsTable.createdAt].toString()
                            )
                        }
                }
                // Отправляем чистый List<BookingResponse>
                call.respond(myBookings)
            }
        }
    }
}
