package com.alper.todo.notificationconsumer.model

sealed trait NotificationDispatchMode {
  def value: String
}

object NotificationDispatchMode {
  case object Disabled extends NotificationDispatchMode {
    override val value: String = "disabled"
  }

  case object Sandbox extends NotificationDispatchMode {
    override val value: String = "sandbox"
  }

  case object Live extends NotificationDispatchMode {
    override val value: String = "live"
  }
}
