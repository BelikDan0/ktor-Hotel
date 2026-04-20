package com.example.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Table

@Serializable
data class Room(
    val id: Long? = null, // Опционально для новых комнат
    val number: String,
    val category: String,
    val description: String,
    val price: Double,
    val squareMeters: Int,
    val capacity: Int,
    val isAvailable: Boolean = true, // Вернули статус доступности
    val imageBytes: String? = null
)

object RoomsTable : Table("rooms") {
    val id = long("id").autoIncrement()
    val number = varchar("number", 10)
    val category = varchar("category", 20)
    val description = text("description")
    val price = double("price")
    val squareMeters = integer("square_meters").default(20)
    val capacity = integer("capacity").default(2)
    val isAvailable = bool("is_available").default(true) // Поле в БД
    val image = blob("image").nullable()

    override val primaryKey = PrimaryKey(id)
}