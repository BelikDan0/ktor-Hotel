package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.models.BookingRequest
import com.example.models.BookingResponse
import com.example.models.BookingsTable
import com.example.models.Room
import com.example.models.RoomsTable
import com.example.security.jwt.JwtService
import com.example.service.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.static
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

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
                val token = JwtService.generate(user)

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
        get("/rooms") {
            try {
                val rooms = transaction {
                    RoomsTable.selectAll()
                        .where { RoomsTable.isAvailable eq true }
                        .map {
                            Room(
                                id = it[RoomsTable.id], // Добавь .value если используешь EntityID
                                number = it[RoomsTable.number],
                                category = it[RoomsTable.category],
                                description = it[RoomsTable.description],
                                price = it[RoomsTable.price],
                                isAvailable = it[RoomsTable.isAvailable],
                                squareMeters = it[RoomsTable.squareMeters],
                                capacity = it[RoomsTable.capacity],

                                // Берем байты из колонки image и кодируем в Base64 строку
                                imageBytes = it[RoomsTable.image]?.bytes?.let { bytes ->
                                    java.util.Base64.getEncoder().encodeToString(bytes)
                                }
                            )
                        }
                }
                call.respond(rooms)
            } catch (e: Exception) {
                // Логируем ошибку в консоль, чтобы ты видел, если что-то упадет
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        // 1. Создать бронирование
        // В configureRouting():
        route("/bookings") {

            // 🔥 Добавляем аутентификацию для этих маршрутов
            authenticate("auth-jwt") {

                post("/create") {
                    val userPrincipal = call.principal<JWTPrincipal>()
                    val userId = userPrincipal?.payload?.getClaim("userId")?.asLong()
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))

                    val request = call.receive<BookingRequest>()

                    // 🔥 Сначала проверяем в transaction и получаем результат
                    val bookingExists = transaction {
                        BookingsTable
                            .selectAll()
                            .where {
                                (BookingsTable.userId eq userId) and
                                        (BookingsTable.roomId eq request.roomId) and
                                        (BookingsTable.status eq "PENDING")
                            }
                            .firstOrNull() != null
                    }

                    // 🔥 Потом уже вне transaction делаем respond
                    if (bookingExists) {
                        return@post call.respond(
                            HttpStatusCode.Conflict,
                            mapOf("error" to "У вас уже есть бронь на этот номер")
                        )
                    }

                    // Создаём бронирование
                    transaction {
                        BookingsTable.insert {
                            it[BookingsTable.userId] = userId
                            it[BookingsTable.roomId] = request.roomId
                            it[status] = "PENDING"
                        }
                    }

                    call.respond(HttpStatusCode.Created, mapOf("message" to "Заявка отправлена"))
                }

                get("/my") {
                    val userPrincipal = call.principal<JWTPrincipal>()
                    val userId = userPrincipal?.payload?.getClaim("userId")?.asLong()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))

                    val myBookings = transaction {
                        (BookingsTable innerJoin RoomsTable)
                            .selectAll()
                            .where(BookingsTable.userId eq userId)
                            .map {
                                BookingResponse(
                                    id = it[BookingsTable.id],
                                    roomNumber = it[RoomsTable.number],
                                    price = it[RoomsTable.price],
                                    status = it[BookingsTable.status],
                                    rejectionReason = it[BookingsTable.rejectionReason], // Добавлено поле
                                    createdAt = it[BookingsTable.createdAt].toString()
                                )
                            }
                    }
                    call.respond<List<BookingResponse>>(myBookings)
                }
                post("/pay/{id}") {
                    val bookingId = call.parameters["id"]?.toLong() ?: return@post call.respond(HttpStatusCode.BadRequest)
                    transaction {
                        // 1. Ставим статус брони PAID
                        val roomId = BookingsTable
                            .selectAll()
                            .where{BookingsTable.id eq bookingId}
                            .single()[BookingsTable.roomId]

                        BookingsTable.update({ BookingsTable.id eq bookingId }) {
                            it[status] = "PAID"
                        }

                        // 2. Делаем комнату недоступной для других
                        RoomsTable.update({ RoomsTable.id eq roomId }) {
                            it[isAvailable] = false
                        }
                    }
                    call.respond(HttpStatusCode.OK)
                }
                delete("/delete/{id}") {
                    val bookingId = call.parameters["id"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)

                    try {
                        transaction {
                            val booking = BookingsTable
                                .selectAll()
                                .where { BookingsTable.id eq bookingId }
                                .singleOrNull() ?: throw Exception("Бронирование не найдено")

                            val roomId = booking[BookingsTable.roomId]
                            val currentStatus = booking[BookingsTable.status]

                            // Если была оплата или подтверждение — возвращаем номер в продажу
                            if (currentStatus == "PAID" || currentStatus == "CONFIRMED") {
                                RoomsTable.update({ RoomsTable.id eq roomId }) {
                                    it[isAvailable] = true
                                }
                            }

                            BookingsTable.deleteWhere { BookingsTable.id eq bookingId }
                        }
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Удалено"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                    }
                }
            }
        }
        route("/admin") {
            authenticate("auth-jwt") {
                // 1. Страница админ-панели (твоя заглушка)
                get("/panel") {
                    val role = call.principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString()
                    if (role == "ADMIN" || role == "Role.ADMIN") {
                        // Просто редиректим на статический файл
                        call.respondRedirect("/static/admin_panel.html")
                    } else {
                        call.respond(HttpStatusCode.Forbidden, "Нет доступа")
                    }
                }

                // Группа эндпоинтов для управления номерами
                route("/rooms") {
                    // Добавить новый номер
                    post("/add") {
                        val room = call.receive<Room>()
                        transaction {
                            RoomsTable.insert {
                                it[number] = room.number
                                it[category] = room.category
                                it[description] = room.description
                                it[price] = room.price
                                it[squareMeters] = room.squareMeters
                                it[capacity] = room.capacity
                                it[isAvailable] = room.isAvailable

                                // Обработка картинки из Base64
                                if (!room.imageBytes.isNullOrBlank()) {
                                    // Отрезаем заголовок "data:image/...,base64," если он прилетел
                                    val pureBase64 = room.imageBytes.substringAfter(",")
                                    val decodedBytes = java.util.Base64.getDecoder().decode(pureBase64)
                                    it[image] = ExposedBlob(decodedBytes)
                                }
                            }
                        }
                        call.respond(HttpStatusCode.Created)
                    }

                    // Изменить номер
                    put("/{id}") {
                        val id = call.parameters["id"]?.toLong() ?: return@put call.respond(HttpStatusCode.BadRequest)
                        val room = call.receive<Room>()
                        transaction {
                            RoomsTable.update({ RoomsTable.id eq id }) {
                                it[number] = room.number
                                it[category] = room.category
                                it[description] = room.description
                                it[price] = room.price
                                it[squareMeters] = room.squareMeters
                                it[capacity] = room.capacity
                                it[isAvailable] = room.isAvailable

                                if (!room.imageBytes.isNullOrBlank()) {
                                    val pureBase64 = room.imageBytes.substringAfter(",")
                                    val decodedBytes = java.util.Base64.getDecoder().decode(pureBase64)
                                    it[image] = ExposedBlob(decodedBytes)
                                }
                            }
                        }
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Данные номера обновлены"))
                    }

                    // Удалить номер
                    delete("/delete/{id}") {
                        val id = call.parameters["id"]?.toLong() ?: return@delete call.respond(HttpStatusCode.BadRequest)
                        transaction {
                            RoomsTable.deleteWhere { RoomsTable.id eq id }
                        }
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Номер удален"))
                    }
                }
                // Внутри route("/admin") { authenticate("auth-jwt") { ... } }

                route("/bookings") {
                    // Получить ВСЕ заявки (для админа)
                    get("/all") {
                        try {
                            val bookings = transaction {
                                (BookingsTable innerJoin RoomsTable)
                                    .selectAll()
                                    .map { row ->
                                        // Ключевой момент: вызываем .toString() на ВСЕХ значениях
                                        mapOf(
                                            "id" to row[BookingsTable.id].toString(),
                                            "roomNumber" to row[RoomsTable.number],
                                            "status" to row[BookingsTable.status],
                                            "userId" to row[BookingsTable.userId].toString(),
                                            "createdAt" to row[BookingsTable.createdAt].toString()
                                        )
                                    }
                            }
                            call.respond(bookings)
                        } catch (e: Exception) {
                            // Если что-то пойдет не так, мы увидим нормальный текст ошибки
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown Error")))
                        }
                    }

                    // Изменить статус (Одобрить/Отклонить)
                    post("/update-status") {
                        val request = call.receive<Map<String, String>>()
                        val bookingId = request["id"]?.toLong() ?: return@post call.respond(HttpStatusCode.BadRequest)
                        val newStatus = request["status"] ?: "" // "CONFIRMED" или "REJECTED"
                        val reason = request["reason"] ?: ""

                        transaction {
                            BookingsTable.update({ BookingsTable.id eq bookingId }) {
                                it[status] = newStatus
                                it[rejectionReason] = reason
                            }
                        }
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Статус обновлен"))
                    }
                }
            }
        }
    }
}
