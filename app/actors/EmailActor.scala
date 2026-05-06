package actors

import akka.actor.{Actor, ActorLogging, Props}
import org.apache.commons.mail.{DefaultAuthenticator, HtmlEmail}
import play.api.Configuration

import java.time.LocalDateTime

// ─── Mesaj Protokolü ───────────────────────────────────────────────────────
object EmailActor {
  def props(config: Configuration): Props = Props(new EmailActor(config))

  case class SendWelcomeEmail(to: String, username: String)
  case class SendTodoCreatedEmail(to: String, username: String, title: String, dueDate: Option[LocalDateTime])
  case class SendTodoCompletedEmail(to: String, username: String, title: String)
  case class SendTodoDeletedEmail(to: String, username: String, title: String)
  case class SendDueDateReminderEmail(to: String, username: String, title: String, dueDate: LocalDateTime)
}

// ─── EmailActor ────────────────────────────────────────────────────────────
class EmailActor(config: Configuration) extends Actor with ActorLogging {

  import EmailActor._

  private val smtpHost = config.get[String]("email.smtp.host")
  private val smtpPort = config.get[Int]("email.smtp.port")
  private val fromAddr = config.get[String]("email.from")
  private val username = config.get[String]("email.username")
  private val password = config.get[String]("email.password")

  // ── Actor mesaj işleyici ─────────────────────────────────────────────────
  override def receive: Receive = {

    case SendWelcomeEmail(to, uname) =>
      log.info(s"[EmailActor] Welcome email gönderiliyor: $to")
      sendEmail(
        to      = to,
        subject = "TodoApp'e Hoş Geldiniz!",
        body    = views.html.emails.welcome(uname).body
      )

    case SendTodoCreatedEmail(to, uname, title, dueDate) =>
      log.info(s"[EmailActor] Todo created email gönderiliyor: $to")
      sendEmail(
        to      = to,
        subject = "Yeni Görev Oluşturuldu",
        body    = views.html.emails.todoCreated(uname, title, dueDate).body
      )

    case SendTodoCompletedEmail(to, uname, title) =>
      log.info(s"[EmailActor] Todo completed email gönderiliyor: $to")
      sendEmail(
        to      = to,
        subject = "Tebrikler! Görev Tamamlandı",
        body    = views.html.emails.todoCompleted(uname, title).body
      )

    case SendTodoDeletedEmail(to, uname, title) =>
      log.info(s"[EmailActor] Todo deleted email gönderiliyor: $to")
      sendEmail(
        to      = to,
        subject = "Görev Silindi",
        body    = views.html.emails.todoDeleted(uname, title).body
      )

    case SendDueDateReminderEmail(to, uname, title, dueDate) =>
      log.info(s"[EmailActor] Due date reminder email gönderiliyor: $to")
      sendEmail(
        to      = to,
        subject = "Görevinizin Süresi Yarın Doluyor!",
        body    = views.html.emails.dueDateReminder(uname, title, dueDate).body
      )

    case unknown =>
      log.warning(s"[EmailActor] Bilinmeyen mesaj: $unknown")
  }

  // ── SMTP Gönderici ───────────────────────────────────────────────────────
  private def sendEmail(to: String, subject: String, body: String): Unit = {
    try {
      val email = new HtmlEmail()
      email.setHostName(smtpHost)
      email.setSmtpPort(smtpPort)
      email.setAuthenticator(new DefaultAuthenticator(username, password))
      email.setStartTLSEnabled(true)
      email.setCharset("UTF-8")
      email.setFrom(fromAddr, "Todo App")
      email.setSubject(subject)
      email.setHtmlMsg(body)
      email.addTo(to)
      email.send()
      log.info(s"[EmailActor] Email başarıyla gönderildi: $to | $subject")
    } catch {
      case ex: Exception =>
        log.error(ex, s"[EmailActor] Email gönderilemedi: $to | Hata: ${ex.getMessage}")
    }
  }
}
