package com.example.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

// В файле models/Booking.kt

@Serializable
data class BookingResponse(
    val id: Long,
    val roomNumber: String,
    val price: Double,
    val status: String,
    val rejectionReason: String?,
    val createdAt: String,
    val checkIn: String,  // Добавили
    val checkOut: String  // Добавили
)

object BookingsTable : Table("bookings") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id)
    val roomId = long("room_id").references(RoomsTable.id)
    val status = varchar("status", 20).default("PENDING")
    val rejectionReason = varchar("rejection_reason", 255).nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())

    val checkIn = datetime("check_in")
    val checkOut = datetime("check_out")

    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class BookingRequest(
    val roomId: Long,
    val checkIn: String,  // Формат "YYYY-MM-DDTHH:mm:ss"
    val checkOut: String
)