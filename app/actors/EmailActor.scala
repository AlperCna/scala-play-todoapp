package actors

import akka.actor.{Actor, ActorLogging, Props}
import org.apache.commons.mail.{DefaultAuthenticator, HtmlEmail}
import play.api.Configuration

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ─── Mesaj Protokolü ───────────────────────────────────────────────────────
object EmailActor {
  def props(config: Configuration): Props = Props(new EmailActor(config))

  // Mesaj tipleri
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

  private val formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm")

  // ── Actor mesaj işleyici ─────────────────────────────────────────────────
  override def receive: Receive = {

    case SendWelcomeEmail(to, uname) =>
      log.info(s"[EmailActor] Welcome email gönderiliyor: $to")
      sendEmail(
        to      = to,
        subject = "TodoApp'e Hoş Geldiniz! 🎉",
        body    = welcomeEmailBody(uname)
      )

    case SendTodoCreatedEmail(to, uname, title, dueDate) =>
      log.info(s"[EmailActor] Todo created email gönderiliyor: $to")
      sendEmail(
        to      = to,
        subject = s"Yeni Görev Oluşturuldu ✅",
        body    = todoCreatedEmailBody(uname, title, dueDate)
      )

    case SendTodoCompletedEmail(to, uname, title) =>
      log.info(s"[EmailActor] Todo completed email gönderiliyor: $to")
      sendEmail(
        to      = to,
        subject = s"Tebrikler! Görev Tamamlandı 🏆",
        body    = todoCompletedEmailBody(uname, title)
      )

    case SendTodoDeletedEmail(to, uname, title) =>
      log.info(s"[EmailActor] Todo deleted email gönderiliyor: $to")
      sendEmail(
        to      = to,
        subject = s"Görev Silindi 🗑️",
        body    = todoDeletedEmailBody(uname, title)
      )

    case SendDueDateReminderEmail(to, uname, title, dueDate) =>
      log.info(s"[EmailActor] Due date reminder email gönderiliyor: $to")
      sendEmail(
        to      = to,
        subject = s"⏰ Görevinizin Süresi Yarın Doluyor!",
        body    = dueDateReminderEmailBody(uname, title, dueDate)
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

  // ── Email Şablonları ─────────────────────────────────────────────────────
  private def emailWrapper(content: String): String =
    s"""
      |<!DOCTYPE html>
      |<html>
      |<head>
      |  <meta charset="UTF-8">
      |  <style>
      |    body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f4f8; margin: 0; padding: 20px; }
      |    .container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px;
      |                 box-shadow: 0 4px 20px rgba(0,0,0,0.08); overflow: hidden; }
      |    .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      |              padding: 32px; text-align: center; }
      |    .header h1 { color: white; margin: 0; font-size: 24px; font-weight: 700; }
      |    .header p  { color: rgba(255,255,255,0.85); margin: 8px 0 0; font-size: 14px; }
      |    .body { padding: 32px; }
      |    .body p { color: #374151; line-height: 1.6; font-size: 15px; margin: 0 0 16px; }
      |    .card { background: #f8fafc; border-left: 4px solid #667eea; border-radius: 8px;
      |            padding: 16px 20px; margin: 20px 0; }
      |    .card .label { font-size: 12px; color: #6b7280; text-transform: uppercase;
      |                   letter-spacing: 0.05em; margin-bottom: 4px; }
      |    .card .value { font-size: 16px; font-weight: 600; color: #1f2937; }
      |    .badge { display: inline-block; padding: 4px 12px; border-radius: 999px;
      |             font-size: 13px; font-weight: 600; }
      |    .badge-green  { background: #d1fae5; color: #065f46; }
      |    .badge-red    { background: #fee2e2; color: #991b1b; }
      |    .badge-purple { background: #ede9fe; color: #5b21b6; }
      |    .footer { background: #f8fafc; padding: 20px 32px; text-align: center;
      |              border-top: 1px solid #e5e7eb; }
      |    .footer p { color: #9ca3af; font-size: 13px; margin: 0; }
      |  </style>
      |</head>
      |<body>
      |  <div class="container">
      |    $content
      |    <div class="footer">
      |      <p>Bu email <strong>Todo App</strong> tarafından otomatik olarak gönderilmiştir.</p>
      |      <p>© 2025 Todo App — Tüm hakları saklıdır.</p>
      |    </div>
      |  </div>
      |</body>
      |</html>
    """.stripMargin

  private def welcomeEmailBody(username: String): String = emailWrapper(
    s"""
      |<div class="header">
      |  <h1>🎉 Hoş Geldiniz!</h1>
      |  <p>Todo App ailesine katıldınız</p>
      |</div>
      |<div class="body">
      |  <p>Merhaba <strong>$username</strong>,</p>
      |  <p>Todo App'e başarıyla kayıt oldunuz! Artık görevlerinizi kolayca yönetebilirsiniz.</p>
      |  <div class="card">
      |    <div class="label">Hesabınız</div>
      |    <div class="value">✅ Aktif ve kullanıma hazır</div>
      |  </div>
      |  <p>Hemen giriş yaparak ilk görevinizi oluşturabilirsiniz. Başarılar dileriz!</p>
      |</div>
    """.stripMargin
  )

  private def todoCreatedEmailBody(username: String, title: String, dueDate: Option[LocalDateTime]): String = {
    val dueDateStr = dueDate
      .map(d => s"""<div class="card"><div class="label">Son Tarih</div><div class="value">📅 ${d.format(formatter)}</div></div>""")
      .getOrElse("")

    emailWrapper(
      s"""
        |<div class="header">
        |  <h1>✅ Yeni Görev Oluşturuldu</h1>
        |  <p>Göreviniz başarıyla kaydedildi</p>
        |</div>
        |<div class="body">
        |  <p>Merhaba <strong>$username</strong>,</p>
        |  <p>Yeni bir görev başarıyla oluşturuldu.</p>
        |  <div class="card">
        |    <div class="label">Görev Adı</div>
        |    <div class="value">$title</div>
        |  </div>
        |  $dueDateStr
        |  <p>Görevinizi tamamladığınızda sizi bilgilendireceğiz. Başarılar!</p>
        |</div>
      """.stripMargin
    )
  }

  private def todoCompletedEmailBody(username: String, title: String): String = emailWrapper(
    s"""
      |<div class="header">
      |  <h1>🏆 Tebrikler!</h1>
      |  <p>Görevi başarıyla tamamladınız</p>
      |</div>
      |<div class="body">
      |  <p>Merhaba <strong>$username</strong>,</p>
      |  <p>Harika iş! Bir görevi daha tamamladınız.</p>
      |  <div class="card">
      |    <div class="label">Tamamlanan Görev</div>
      |    <div class="value">$title</div>
      |  </div>
      |  <p><span class="badge badge-green">✓ Tamamlandı</span></p>
      |  <p>Bu başarıyı kutlayın ve bir sonraki göreve hazırlanın!</p>
      |</div>
    """.stripMargin
  )

  private def todoDeletedEmailBody(username: String, title: String): String = emailWrapper(
    s"""
      |<div class="header">
      |  <h1>🗑️ Görev Silindi</h1>
      |  <p>Bir görev listenizden kaldırıldı</p>
      |</div>
      |<div class="body">
      |  <p>Merhaba <strong>$username</strong>,</p>
      |  <p>Aşağıdaki görev listenizden silindi.</p>
      |  <div class="card">
      |    <div class="label">Silinen Görev</div>
      |    <div class="value">$title</div>
      |  </div>
      |  <p><span class="badge badge-red">✗ Silindi</span></p>
      |  <p>Bu işlemi siz gerçekleştirmediyseniz lütfen hesabınızı kontrol edin.</p>
      |</div>
    """.stripMargin
  )

  private def dueDateReminderEmailBody(username: String, title: String, dueDate: LocalDateTime): String = emailWrapper(
    s"""
      |<div class="header">
      |  <h1>⏰ Son Tarih Yaklaşıyor!</h1>
      |  <p>Görevinizin süresi yarın doluyor</p>
      |</div>
      |<div class="body">
      |  <p>Merhaba <strong>$username</strong>,</p>
      |  <p>Bir görevinizin son tarihi <strong>yarın</strong>!</p>
      |  <div class="card">
      |    <div class="label">Görev Adı</div>
      |    <div class="value">$title</div>
      |  </div>
      |  <div class="card">
      |    <div class="label">Son Tarih</div>
      |    <div class="value">📅 ${dueDate.format(formatter)}</div>
      |  </div>
      |  <p><span class="badge badge-purple">⏰ Yarın son gün</span></p>
      |  <p>Görevi zamanında tamamlamak için harekete geçin!</p>
      |</div>
    """.stripMargin
  )
}
