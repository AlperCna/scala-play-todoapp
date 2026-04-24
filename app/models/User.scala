package models

case class User(
                 id: Long,
                 username: String,
                 email: String,
                 passwordHash: String,
                 role: String,
                 createdAt: java.time.LocalDateTime
               )