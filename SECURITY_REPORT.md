# Güvenlik Katmanı — PAC4J Entegrasyon Raporu

## Genel Bakış

Bu proje, Play Framework 2.9 üzerine inşa edilmiş çok kiracılı (multi-tenant) bir Todo uygulamasıdır.
Güvenlik katmanı başlangıçta her controller'a dağılmış manuel session yönetimiyle kurulmuştu.
Bu rapor, güvenlik katmanının **PAC4J** kütüphanesi ile tamamen yeniden yapılandırılmasını,
karşılaşılan sorunları ve alınan kararları belgeler.

---

## Kullanılan PAC4J Versiyonları

```scala
val pac4jPlayVersion = "12.0.0-PLAY2.9"  // Play 2.9 entegrasyonu
val pac4jVersion     = "6.0.6"            // PAC4J core
```

### Eklenen Bağımlılıklar (`build.sbt`)

| Kütüphane | Versiyon | Açıklama |
|---|---|---|
| `play-pac4j` | 12.0.0-PLAY2.9 | Play Framework PAC4J entegrasyonu |
| `pac4j-core` | 6.0.6 | PAC4J temel kütüphane |
| `pac4j-http` | 6.0.6 | HTTP credential ve authenticator desteği |
| `shiro-crypto-cipher` | 1.13.0 | Cookie AES-GCM şifreleme motoru |

### Bağımlılık Çakışması Çözümü

PAC4J, Jackson 2.17.x çekiyordu; Play 2.9 / Akka ise 2.14.x gerektiriyor.
`dependencyOverrides` ile Jackson versiyonu sabitlendi:

```scala
dependencyOverrides ++= Seq(
  "com.fasterxml.jackson.core"   % "jackson-core"           % "2.14.3",
  "com.fasterxml.jackson.core"   % "jackson-databind"       % "2.14.3",
  "com.fasterxml.jackson.core"   % "jackson-annotations"    % "2.14.3",
  "com.fasterxml.jackson.module" %% "jackson-module-scala"  % "2.14.3"
)
```

---

## Önceki Yapı — Manuel Session Yönetimi

### Sorunlar

```scala
// Her controller'da tekrar eden, dağınık auth kontrolü
private def getCurrentUserId(request: RequestHeader): Option[UUID] =
  request.session.get("userId").flatMap(id => Try(UUID.fromString(id)).toOption)

private def isAdmin(request: RequestHeader): Boolean =
  request.session.get("role").contains("ADMIN")

// Login sonrası: şifresiz plain-text session
Redirect(routes.TodoController.index())
  .withSession(
    "userId"   -> user.id.toString,
    "username" -> user.username,
    "role"     -> user.role,
    "tenantId" -> user.tenantId.toString
  )

// Her action'da tekrar eden kontrol
def index = Action.async { implicit request =>
  getCurrentUserId(request) match {
    case Some(userId) => /* iş mantığı */
    case None         => Future.successful(Redirect(routes.AuthController.loginPage()))
  }
}
```

| Sorun | Açıklama |
|---|---|
| Dağınık auth mantığı | Her controller kendi session kontrolünü yapıyordu |
| Type-unsafe okuma | `session.get("userId")` → String, runtime'da UUID parse hatası riski |
| Şifresiz session | Play session cookie'si Base64 encode ama şifrelenmemiş |
| Merkezi yetkilendirme yok | `if (isAdmin)` her yerde tekrarlanıyor |
| SSO tutarsızlığı | Google/Microsoft login farklı session formatı kullanıyordu |

---

## Yeni Yapı — PAC4J Güvenlik Katmanı

### Tek Satır Koruma

```scala
// Giriş gerektiren her endpoint
def index = secure { profile => implicit request =>
  todoService.getTodosPaged(profile.getUserId, ...).map(Ok(_))
}

// Yalnızca ADMIN rolüne açık endpoint
def dashboard = secure.admin { profile => implicit request =>
  adminService.getDashboardStats(profile.getTenantId).map(Ok(_))
}
```

### Tam Login Akışı

```
[Kullanıcı] → LoginForm → AuthController.login()
                              │
                              ▼
                    UsernamePasswordCredentials
                              │
                              ▼
                    CustomDbAuthenticator.validate()
                              │
                    ┌─────────┴──────────┐
                    │ DB'den kullanıcı   │
                    │ doğrulama          │
                    └─────────┬──────────┘
                              │
                              ▼
                    CustomProfile oluşturulur
                    (id, username, role, tenantId)
                              │
                              ▼
                    ProfileManager.save(profile)
                              │
                    ┌─────────┴──────────┐
                    │ AES-GCM şifreli    │
                    │ cookie'ye yazılır  │
                    └─────────┬──────────┘
                              │
                              ▼
                    webContext.supplementResponse()
                              │
                              ▼
                    [Cookie tarayıcıya gönderilir]
```

### Tam Request Akışı (Korumalı Endpoint)

```
[Kullanıcı isteği] → SecureAction.apply()
                              │
                              ▼
                    ProfileManager.getProfile()
                              │
                    ┌─────────┴──────────┐
                    │ Cookie decrypt →   │
                    │ CustomProfile      │
                    └─────────┬──────────┘
                              │
                    ┌─────────┴──────────┐
                 Var │                   │ Yok
                    ▼                   ▼
             Controller'a         Redirect /login
             profile geçilir      + flash mesajı
```

### Admin Yetkilendirme Akışı

```
[Admin endpoint isteği] → SecureAction.admin()
                                  │
                                  ▼
                        ProfileManager.getProfile()
                                  │
                        ┌─────────┴──────────┐
                     Var │                   │ Yok
                        ▼                   ▼
              AdminAuthorizer.isAuthorized() Redirect /login
                        │
              ┌─────────┴──────────┐
           ADMIN │                 │ Diğer
                ▼                 ▼
           Controller'a      Redirect /todos
           profile geçilir   + "yetki yok" mesajı
```

---

## Oluşturulan Dosyalar

### 1. `app/security/CustomProfile.scala`

PAC4J'nin `CommonProfile` sınıfından türetilen özel kullanıcı profili.
UUID ve String alanlarına tip-güvenli erişim sağlar.

```scala
class CustomProfile extends CommonProfile {
  def getUserId:      UUID   = UUID.fromString(getId)
  def getTenantId:    UUID   = UUID.fromString(getAttribute("tenantId").asInstanceOf[String])
  def getAppRole:     String = Option(getAttribute("role")).map(_.toString).getOrElse("")
  def getAppUsername: String = Option(getAttribute("appUsername")).map(_.toString).getOrElse("")
}
```

**Saklanan attribute'lar:**

| Attribute | Tip | Kaynak |
|---|---|---|
| `id` (setId) | String → UUID | DB user.id |
| `appUsername` | String | DB user.username |
| `role` | String (`USER` / `ADMIN`) | DB user.role |
| `tenantId` | String → UUID | DB user.tenantId |

**Faydası:** `session.get("userId")` yerine `profile.getUserId` — tip güvenli, null-safe, runtime parse hatası yok.

---

### 2. `app/security/CustomDbAuthenticator.scala`

PAC4J'nin `Authenticator` arayüzünü implemente eder (PAC4J 6.x API — generics kaldırıldı).
Kullanıcıyı veritabanında doğrular, başarılıysa `CustomProfile` oluşturur.

```scala
class CustomDbAuthenticator @Inject()(authService: AuthService) extends Authenticator {

  override def validate(ctx: CallContext, credentials: Credentials): Optional[Credentials] = {
    val upc = credentials.asInstanceOf[UsernamePasswordCredentials]

    // AuthService üzerinden DB sorgusu (blocking — PAC4J senkron API gerektirir)
    val result = Await.result(
      authService.login(LoginRequest(upc.getUsername, upc.getPassword)),
      5.seconds
    )

    result match {
      case Some(user) =>
        val profile = new CustomProfile()
        profile.setId(user.id.toString)
        profile.addAttribute("appUsername", user.username)
        profile.addAttribute("role", user.role)
        profile.addAttribute("tenantId", user.tenantId.toString)
        upc.setUserProfile(profile)
        Optional.of(upc.asInstanceOf[Credentials])

      case None =>
        throw new CredentialsException("Geçersiz email veya şifre.")
    }
  }
}
```

**Neden `AuthService`'e hâlâ gerek var?**

`AuthService.login()` sadece DB'den kullanıcıyı getiriyor. Bu sınıf onu çağırıp
PAC4J'nin anlayacağı `CustomProfile` formatına dönüştürüyor.
`AuthService` ayrıca `register` ve SSO işlemleri için de kullanılmaya devam ediyor.

**PAC4J 6.x API Farkı:**
- Eski: `Authenticator[UsernamePasswordCredentials]` (generic)
- Yeni: `Authenticator` (generics kaldırıldı, `Optional[Credentials]` döner)

---

### 3. `app/security/AdminAuthorizer.scala`

PAC4J'nin `ProfileAuthorizer` soyut sınıfından türetilen yetkilendirme sınıfı.
Kullanıcının `ADMIN` rolüne sahip olup olmadığını kontrol eder.

```scala
class AdminAuthorizer extends ProfileAuthorizer {

  // Listedeki herhangi bir profil yetkili ise true döner
  override def isAuthorized(
      context: WebContext,
      sessionStore: SessionStore,
      profiles: util.List[UserProfile]
  ): Boolean = isAnyAuthorized(context, sessionStore, profiles)

  // Tek bir profili kontrol eder
  override def isProfileAuthorized(
      context: WebContext,
      sessionStore: SessionStore,
      profile: UserProfile
  ): Boolean =
    Option(profile.getAttribute("role")).exists(_.toString == "ADMIN")
}
```

**PAC4J 6.x API Farkı:**
- `isAuthorized` ve `isProfileAuthorized` metotları `WebContext + SessionStore` alır
- Eski versiyonlarda `CallContext` kullanılıyordu — bu değişiklik derleme hatalarına yol açmıştı

**Dependency Injection:**
`AdminAuthorizer`, Guice tarafından singleton olarak yönetilir.
`SecureAction`'a `@Inject` ile enjekte edilir — `new` ile elle yaratılmaz.

---

### 4. `app/security/Pac4jModule.scala`

Guice DI modülü. PAC4J bileşenlerini bağlar.

```scala
class Pac4jModule extends AbstractModule {
  override def configure(): Unit = {
    // AES şifreli cookie session store — sabit key ile restart-safe
    bind(classOf[SessionStore]).toProvider(classOf[SessionStoreProvider]).asEagerSingleton()
    // AdminAuthorizer singleton olarak bind — SecureAction'a inject edilir
    bind(classOf[AdminAuthorizer]).asEagerSingleton()
  }
}

class SessionStoreProvider @Inject()(configuration: Configuration) extends Provider[SessionStore] {
  override def get(): SessionStore = {
    // 16-byte (128-bit) AES-GCM anahtarı
    // Server restart'ta mevcut session cookie'leri geçerliliğini korur
    val keyBytes = "TodoApp_SecKey16".getBytes("UTF-8")
    new PlayCookieSessionStore(new ShiroAesDataEncrypter(keyBytes))
  }
}
```

**Neden `SessionStoreProvider` var?**

`PlayCookieSessionStore`, her başlatmada rastgele bir AES anahtarı üretir.
Server restart olduğunda eski cookie'ler çözülemez → kullanıcı oturumu kapanır.
Sabit bir anahtar sağlanarak bu sorun çözülmüştür.

**Neden `Config` / `FormClient` yok?**

PAC4J'nin merkezi `Config` nesnesi, otomatik güvenlik filtreleri (Security Filter, CallbackFilter)
için kullanılır. Bu projede güvenlik kontrolü `SecureAction` üzerinden manuel olarak yapıldığından
`Config` nesnesi gereksiz ve yanıltıcı olurdu. Bu nedenle kaldırılmıştır.

---

### 5. `app/security/SecureAction.scala`

Projenin merkezi güvenlik bileşeni. Tüm korumalı endpoint'ler bu sınıf üzerinden geçer.
`DefaultActionBuilder` kullanır; herhangi bir controller trait'inden extend etmez.

```scala
class SecureAction @Inject()(
    sessionStore: SessionStore,
    parser: BodyParsers.Default,
    action: DefaultActionBuilder,
    adminAuthorizer: AdminAuthorizer      // Guice singleton — new ile yaratılmaz
)(implicit ec: ExecutionContext) {

  // ── Giriş yapmış kullanıcıya açık ──────────────────────────────────────────
  def apply(
      f: CustomProfile => Request[AnyContent] => Future[Result]
  ): Action[AnyContent] =
    action.async(parser) { request =>
      getProfile(request) match {
        case Some(profile) => f(profile)(request)
        case None =>
          Future.successful(
            Results.Redirect(routes.AuthController.loginPage())
              .flashing("error" -> "Lütfen önce giriş yapın.")
          )
      }
    }

  // ── Yalnızca ADMIN rolüne açık ─────────────────────────────────────────────
  def admin(
      f: CustomProfile => Request[AnyContent] => Future[Result]
  ): Action[AnyContent] =
    action.async(parser) { request =>
      getProfile(request) match {
        case Some(profile) =>
          val webContext = new PlayWebContext(request)
          val profiles: util.List[UserProfile] =
            util.List.of(profile.asInstanceOf[UserProfile])

          if (adminAuthorizer.isAuthorized(webContext, sessionStore, profiles))
            f(profile)(request)
          else
            Future.successful(
              Results.Redirect(routes.TodoController.index())
                .flashing("error" -> "Bu sayfaya erişim yetkiniz yok.")
            )

        case None =>
          Future.successful(
            Results.Redirect(routes.AuthController.loginPage())
              .flashing("error" -> "Lütfen önce giriş yapın.")
          )
      }
    }

  // ── Session'dan profil okuma ────────────────────────────────────────────────
  def getProfile(request: RequestHeader): Option[CustomProfile] = {
    val webContext = new PlayWebContext(request)
    val pm        = new ProfileManager(webContext, sessionStore)
    pm.getProfile.toScala.collect { case p: CustomProfile => p }
  }
}
```

---

## Güncellenen Dosyalar

### `app/controllers/AuthController.scala`

**Login — Tam PAC4J Akışı:**

```scala
def login = Action.async { implicit request =>
  LoginForm.form.bindFromRequest().fold(
    formWithErrors => Future.successful(BadRequest(views.html.login(formWithErrors))),
    data => {
      Future {
        val credentials = new UsernamePasswordCredentials(data.email, data.password)
        val webContext  = new PlayWebContext(request)
        val callContext = new CallContext(webContext, sessionStore)

        try {
          // 1. PAC4J Authenticator üzerinden DB doğrulaması
          authenticator.validate(callContext, credentials)

          // 2. Authenticator'ın doldurduğu CustomProfile'ı al
          val profile = credentials.getUserProfile.asInstanceOf[CustomProfile]

          // 3. AES şifreli cookie'ye yaz
          val pm = new ProfileManager(webContext, sessionStore)
          pm.save(true, profile, false)

          Right((profile, webContext))
        } catch {
          case _: CredentialsException => Left("Email or password is incorrect.")
          case _: Exception            => Left("Login sırasında bir hata oluştu.")
        }
      }.flatMap {
        case Right((profile, webContext)) =>
          auditLogService
            .log(userId = Some(profile.getUserId), tenantId = Some(profile.getTenantId),
              action = "USER_LOGIN", request = request)
            .map { _ =>
              val result = Redirect(routes.TodoController.index())
                .flashing("success" -> s"Welcome, ${profile.getAppUsername}")
              // 4. PAC4J session cookie'sini response'a ekle
              webContext.supplementResponse(result)
            }

        case Left(errorMsg) =>
          Future.successful(
            BadRequest(views.html.login(LoginForm.form.fill(data).withGlobalError(errorMsg)))
          )
      }
    }
  )
}
```

**Logout:**

```scala
def logout = Action.async { implicit request =>
  val webContext = new PlayWebContext(request)
  val pm         = new ProfileManager(webContext, sessionStore)

  // Logout öncesi profili oku (audit log için)
  val profileOpt      = pm.getProfile.toScala.collect { case p: CustomProfile => p }
  val currentUserId   = profileOpt.map(_.getUserId)
  val currentTenantId = profileOpt.map(_.getTenantId)

  // PAC4J profile'ını temizle
  pm.removeProfiles()

  auditLogService
    .log(userId = currentUserId, tenantId = currentTenantId,
      action = "USER_LOGOUT", request = request)
    .map { _ =>
      val result = Redirect(routes.AuthController.loginPage())
        .withNewSession
        .flashing("success" -> "Logout successful.")
      webContext.supplementResponse(result)
    }
}
```

---

### `app/controllers/TodoController.scala`

Manuel auth kontrolü tamamen kaldırıldı.

```scala
// Eski — her action'da tekrar eden kontrol
def index = Action.async { implicit request =>
  getCurrentUserId(request) match {
    case Some(userId) => todoService.getAll(userId).map(Ok(_))
    case None         => Future.successful(Redirect(routes.AuthController.loginPage()))
  }
}

// Yeni — SecureAction + PAC4J profile implicit olarak view'a geçiliyor
def index() = secure { profile => implicit request =>
  implicit val profileOpt: Option[CustomProfile] = Some(profile)
  val status = request.getQueryString("status").getOrElse("all")
  val search = request.getQueryString("search").getOrElse("")
  val page   = parsePage(request)

  todoService.getTodosPaged(profile.getUserId, status, search, page, pageSize).map { todoPage =>
    Ok(views.html.todos(todoPage, TodoCreateForm.form))
  }
}
```

**Kaldırılan metodlar:** `getCurrentUserId`, `getCurrentTenantId`, `getStatus`, `getSearch`, `getPage`

---

### `app/controllers/AdminController.scala`

```scala
// Eski
def dashboard = Action.async { implicit request =>
  if (!isLoggedIn(request))  Future.successful(Redirect(routes.AuthController.loginPage()))
  else if (!isAdmin(request)) Future.successful(Redirect(routes.TodoController.index()))
  else adminService.getDashboardStats(...).map(Ok(_))
}

// Yeni — SecureAction.admin tüm kontrolü halleder
def dashboard = secure.admin { profile => implicit request =>
  implicit val profileOpt: Option[CustomProfile] = Some(profile)
  adminService.getDashboardStats(profile.getTenantId).map { stats =>
    Ok(views.html.admin(stats))
  }
}
```

**Kaldırılan metodlar:** `isLoggedIn`, `isAdmin`, `getCurrentTenantId`, `unauthorizedResult`

---

### `app/controllers/SSOController.scala`

Google ve Microsoft OAuth callback'lerinde de aynı PAC4J `ProfileManager` akışı kullanılır.
Form login ile tamamen tutarlı.

```scala
private def handleSSOLogin(user: UserResponse)(implicit request: Request[AnyContent]): Future[Result] = {
  val profile = new CustomProfile()
  profile.setId(user.id.toString)
  profile.addAttribute("appUsername", user.username)
  profile.addAttribute("role", user.role)
  profile.addAttribute("tenantId", user.tenantId.toString)

  val webContext = new PlayWebContext(request)
  val pm         = new ProfileManager(webContext, sessionStore)
  pm.save(true, profile, false)   // Form login ile aynı

  auditLogService
    .log(userId = Some(user.id), tenantId = Some(user.tenantId),
      action = "USER_LOGIN_SSO", request = request)
    .map { _ =>
      val result = Redirect(routes.TodoController.index())
        .flashing("success" -> s"Welcome, ${user.username}")
      webContext.supplementResponse(result)
    }
}
```

---

### `app/views/main.scala.html` — Navbar PAC4J Entegrasyonu

Navbar artık `request.session.get("userId")` yerine PAC4J `CustomProfile` kullanır.
Her korumalı view, controller'dan implicit olarak `profileOpt: Option[CustomProfile]` alır.

```html
@(title: String)(content: Html)(implicit request: play.api.mvc.RequestHeader, flash: Flash,
  profileOpt: Option[security.CustomProfile] = None)

@isLoggedIn = @{ profileOpt.isDefined }
@username   = @{ profileOpt.map(_.getAppUsername).getOrElse("") }
@role       = @{ profileOpt.map(_.getAppRole).getOrElse("USER") }
```

**Controller tarafında:**
```scala
// SecureAction bloğu içinde — profile implicit olarak geçilir
def index() = secure { profile => implicit request =>
  implicit val profileOpt: Option[CustomProfile] = Some(profile)
  // views.html.todos artık profileOpt'u alır → main.scala.html'e iletir
  Ok(views.html.todos(todoPage, TodoCreateForm.form))
}
```

**Public action'larda (login, register):**
```scala
// profileOpt = None (default) — navbar "Login / Register" gösterir
def loginPage = Action { implicit request =>
  Ok(views.html.login(LoginForm.form))
}
```

---

### `app/Module.scala`

```scala
class Module extends AbstractModule {
  override def configure(): Unit = {
    // ... diğer binding'ler ...
    install(new Pac4jModule())   // SessionStore + AdminAuthorizer
  }
}
```

---

## Mesajlaşma Paterni — EmailActor

`EmailActor`, **Tell (fire-and-forget)** pattern kullanır.
Gönderen sonucu beklemez; actor başarı/hata durumunu sessizce yönetir.

```scala
emailActor ! SendWelcomeEmail(user.email, user.username)
```

Mesaj tipleri companion object içinde **Command Message Pattern** olarak tanımlıdır:

| Mesaj | Tetikleyen Olay |
|---|---|
| `SendWelcomeEmail` | Kullanıcı kaydı |
| `SendTodoCreatedEmail` | Todo oluşturma |
| `SendTodoCompletedEmail` | Todo tamamlama |
| `SendTodoDeletedEmail` | Todo silme |
| `SendDueDateReminderEmail` | Zamanlanmış görev (son tarih uyarısı) |

HTML içeriği actor içine gömülmüş değil; `app/views/emails/` altındaki
Twirl şablonlarından üretilir:

```scala
case SendWelcomeEmail(to, username) =>
  sendEmail(to, "TodoApp'e Hoş Geldiniz!", views.html.emails.welcome(username).body)
```

---

## Korunan Route'lar

| Route | Metot | Koruma | Yetkisiz Yönlendirme |
|---|---|---|---|
| `/todos` | GET | Login gerekli | → `/login` |
| `/todos/create` | POST | Login gerekli | → `/login` |
| `/todos/:id/edit` | GET | Login gerekli | → `/login` |
| `/todos/:id/update` | POST | Login gerekli | → `/login` |
| `/todos/:id/delete` | POST | Login gerekli | → `/login` |
| `/todos/:id/toggle` | POST | Login gerekli | → `/login` |
| `/admin` | GET | ADMIN rolü gerekli | → `/todos` |
| `/admin/users` | GET | ADMIN rolü gerekli | → `/todos` |
| `/admin/todos` | GET | ADMIN rolü gerekli | → `/todos` |
| `/admin/audit-logs` | GET | ADMIN rolü gerekli | → `/todos` |
| `/login` | GET/POST | Public | — |
| `/register` | GET/POST | Public | — |
| `/auth/google/*` | GET | Public (OAuth) | — |
| `/auth/microsoft/*` | GET | Public (OAuth) | — |

---

## PAC4J Bileşen Kullanım Tablosu

| PAC4J Bileşeni | Sınıf | Nerede Kullanılıyor |
|---|---|---|
| `Authenticator` | `CustomDbAuthenticator` | `AuthController.login` |
| `CommonProfile` | `CustomProfile` | Tüm auth akışları |
| `ProfileAuthorizer` | `AdminAuthorizer` | `SecureAction.admin` |
| `SessionStore` | `PlayCookieSessionStore` | Tüm profile okuma/yazma |
| `ProfileManager` | — | `SecureAction`, `AuthController`, `SSOController` |
| `PlayWebContext` | — | `SecureAction`, `AuthController`, `SSOController` |
| `CallContext` | — | `AuthController.login` |
| `ShiroAesDataEncrypter` | — | `Pac4jModule` (session şifreleme) |

---

## Güvenlik Kontrol Listesi

| Kontrol | Durum | Notlar |
|---|---|---|
| Authentication | ✅ | `CustomDbAuthenticator.validate()` |
| Authorization | ✅ | `AdminAuthorizer.isAuthorized()` |
| Session şifreleme | ✅ | AES-GCM, 128-bit, `PlayCookieSessionStore` |
| Session restart kararlılığı | ✅ | Sabit AES anahtarı |
| CSRF koruması | ✅ | Play `CSRFFilter` + form hidden token |
| HTTPOnly cookie | ✅ | `play.http.session.httpOnly = true` |
| SameSite politikası | ✅ | `play.http.session.sameSite = "lax"` |
| Audit loglama | ✅ | Login, logout, SSO, todo işlemleri |
| SSO tutarlılığı | ✅ | Form login ile aynı `ProfileManager` akışı |
| Type-safe kullanıcı verisi | ✅ | `profile.getUserId` (UUID), `profile.getAppRole` |
| Admin DI singleton | ✅ | `AdminAuthorizer` Guice ile yönetilir |
| Güvenli cookie (HTTPS) | ⚠️ | Geliştirme: `false`, production'da `true` yapılmalı |

---

## Özet Karşılaştırma

| | Öncesi | Sonrası |
|---|---|---|
| **Session yönetimi** | Play raw session (plain-text) | `PlayCookieSessionStore` (AES-GCM şifreli) |
| **Authentication** | `authService.login()` → `withSession(...)` | `CustomDbAuthenticator.validate()` → `ProfileManager.save()` |
| **Authorization** | Her controller'da `if (isAdmin)` | `AdminAuthorizer.isAuthorized()` → `SecureAction.admin` |
| **Kullanıcı verisi** | `session.get("userId")` (String, unsafe) | `profile.getUserId` (UUID, type-safe) |
| **SSO entegrasyonu** | Ayrı session formatı | Form login ile aynı PAC4J akışı |
| **Security merkezi** | Dağınık, her controller'da | `SecureAction` tek noktada |
| **Navbar auth durumu** | `request.session.get("userId")` | `Option[CustomProfile]` implicit geçişi |
| **Email şablonları** | Actor içine gömülü HTML | Twirl şablonları (`app/views/emails/`) |
| **Admin bileşeni** | `new AdminAuthorizer()` (DI dışı) | Guice singleton, `@Inject` |
