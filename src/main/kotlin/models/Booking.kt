package com.example.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

@Serializable
data class BookingResponse(
    val id: Long,
    val roomNumber: String,
    val price: Double,
    val status: String,
    val rejectionReason: String?, // Добавил ?, так как может быть null
    val createdAt: String
)

object BookingsTable : Table("bookings") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(UsersTable.id)
    val roomId = long("room_id").references(RoomsTable.id)
    val status = varchar("status", 20).default("PENDING")
    val rejectionReason = varchar("rejection_reason", 255).nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class BookingRequest(val roomId: Long)