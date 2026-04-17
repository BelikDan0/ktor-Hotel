package models;

data class User(
        val id: Long,
        val email: String,
        val password: String, // ВАЖНО: тут хранится ХЕШ, не пароль
        val role: String = "USER"
)