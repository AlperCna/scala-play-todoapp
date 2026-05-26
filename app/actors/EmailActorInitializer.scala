package actors

import akka.actor.{ActorRef, ActorSystem}
import kafka.outbox.{TodoOutboxPublishService, TodoOutboxWorkerSettingsLoader}
import play.api.Configuration
import repositories.TodoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

// ── EmailActor + DueDateSchedulerActor'ı başlatan ve dışarıya açan sınıf ──
@Singleton
class EmailActorInitializer @Inject()(
  system:         ActorSystem,
  config:         Configuration,
  todoRepository: TodoRepository,
  todoOutboxPublishService: TodoOutboxPublishService,
  todoOutboxWorkerSettingsLoader: TodoOutboxWorkerSettingsLoader
)(implicit ec: ExecutionContext) {

  // EmailActor'ı başlat — diğer servisler bu ref'i kullanır
  val emailActor: ActorRef = system.actorOf(
    EmailActor.props(config),
    name = "email-actor"
  )

  // DueDateSchedulerActor'ı başlat — EmailActor'ı referans olarak ver
  system.actorOf(
    DueDateSchedulerActor.props(todoRepository, emailActor),
    name = "due-date-scheduler"
  )

  system.actorOf(
    TodoOutboxPublisherActor.props(todoOutboxPublishService, todoOutboxWorkerSettingsLoader),
    name = "todo-outbox-publisher"
  )
}
