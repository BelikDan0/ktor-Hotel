package com.example.models


import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Table

@Serializable
data class Room(
    val id: Long,
    val number: String,
    val category: String,
    val description: String,
    val price: Double,
    val imageUrl: String
)
object RoomsTable : Table("rooms") {
    val id = long("id").autoIncrement()
    val number = varchar("number", 10)
    val category = varchar("category", 20) // "Standard", "Good", "Premium"
    val description = text("description")
    val price = double("price")
    val imageUrl = varchar("image_url", 255)
    val rating = double("rating").default(5.0)

    override val primaryKey = PrimaryKey(id)
}