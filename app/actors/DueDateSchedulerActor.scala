package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import actors.EmailActor.SendDueDateReminderEmail
import repositories.TodoRepository

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime}
import javax.inject.{Inject, Named}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

// ─── DueDateSchedulerActor ────────────────────────────────────────────────
object DueDateSchedulerActor {
  def props(todoRepository: TodoRepository, emailActor: ActorRef)
           (implicit ec: ExecutionContext): Props =
    Props(new DueDateSchedulerActor(todoRepository, emailActor))

  // Scheduler tetikleyici mesaj
  case object CheckDueDates
}

class DueDateSchedulerActor(
  todoRepository: TodoRepository,
  emailActor: ActorRef
)(implicit ec: ExecutionContext) extends Actor with ActorLogging {

  import DueDateSchedulerActor._

  // ── Başlarken scheduler'ı kur ─────────────────────────────────────────
  override def preStart(): Unit = {
    val initialDelay = computeInitialDelay()
    val interval     = 24.hours

    log.info(s"[DueDateScheduler] İlk kontrol ${initialDelay.toSeconds} saniye sonra, her 24 saatte bir çalışacak.")

    context.system.scheduler.scheduleWithFixedDelay(
      initialDelay = initialDelay,
      delay        = interval,
      receiver     = self,
      message      = CheckDueDates
    )
  }

  override def receive: Receive = {
    case CheckDueDates =>
      log.info("[DueDateScheduler] Due date kontrolü başlatıldı...")
      checkAndSendReminders()
  }

  // ── Yarın due date'i olan todoları bul ve email gönder ───────────────
  private def checkAndSendReminders(): Unit = {
    todoRepository.findDueTomorrow().foreach { items =>
      log.info(s"[DueDateScheduler] ${items.size} adet yarın-bitiş tarihli görev bulundu.")
      items.foreach { case (todo, userEmail, username) =>
        todo.dueDate.foreach { dueDate =>
          emailActor ! SendDueDateReminderEmail(
            to       = userEmail,
            username = username,
            title    = todo.title,
            dueDate  = dueDate
          )
        }
      }
    }
  }

  // ── Sabah 09:00'a kaç saniye kaldığını hesapla ───────────────────────
  private def computeInitialDelay(): FiniteDuration = {
    val now        = LocalDateTime.now()
    val targetTime = LocalTime.of(9, 0)
    val todayAt9   = now.toLocalDate.atTime(targetTime)

    val nextRun =
      if (now.toLocalTime.isBefore(targetTime)) todayAt9
      else todayAt9.plusDays(1)

    val secondsUntil = Duration.between(now, nextRun).getSeconds
    secondsUntil.seconds
  }
}
