package models

case class Todo(
                 id: Long,
                 userId: Long,
                 title: String,
                 isCompleted: Boolean,
                 createdAt: java.time.LocalDateTime
               )