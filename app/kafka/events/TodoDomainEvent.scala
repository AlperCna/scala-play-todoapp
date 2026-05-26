package kafka.events

import models.Todo

case class TodoCreated(todo: Todo, correlationId: Option[String] = None)
case class TodoCompleted(todo: Todo, correlationId: Option[String] = None)
case class TodoUpdated(todo: Todo, correlationId: Option[String] = None)
case class TodoDeleted(todo: Todo, correlationId: Option[String] = None)
