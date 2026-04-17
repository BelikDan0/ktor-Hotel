package com.example.security.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.models.User
import java.util.Date

object JwtService {
    private const val secret = "super_secret_key"
    private const val issuer = "http://localhost:8080"

    fun generate(user: User): String {
        return JWT.create()
            .withIssuer(issuer)
            .withClaim("userId", user.id.toLong()) // Передаем значение Long
            .withClaim("role", user.role.name)      // Передаем строку (USER/ADMIN)
            .withExpiresAt(Date(System.currentTimeMillis() + 86400000)) // 1 день (исправил на нормальное время)
            .sign(Algorithm.HMAC256(secret))
    }
}