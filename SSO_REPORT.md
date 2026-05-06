# SSO Entegrasyon Raporu — Google & Microsoft OAuth2 + PAC4J

## Genel Bakış

Bu proje, Play Framework 2.9 üzerine inşa edilmiş çok kiracılı (multi-tenant) bir Todo uygulamasıdır.
SSO (Single Sign-On) entegrasyonu, Google ve Microsoft hesaplarıyla giriş imkânı sunar.
Güvenlik katmanı **PAC4J** kütüphanesi ile desteklenmektedir.

---

## Mimari Özet

```
[Kullanıcı] → Login Sayfası → "Google / Microsoft ile Giriş Yap"
                                        │
                    ┌───────────────────┴───────────────────┐
                    │         OAuth Protokol Katmanı         │
                    │         (WSClient — HTTP iletişimi)    │
                    │                                        │
                    │  1. State üret → PAC4J SessionStore    │
                    │  2. Provider'a yönlendir               │
                    │  3. Code al → Token exchange           │
                    │  4. UserInfo endpoint'ten email/name   │
                    └───────────────────┬───────────────────┘
                                        │
                    ┌───────────────────┴───────────────────┐
                    │         PAC4J Güvenlik Katmanı         │
                    │                                        │
                    │  5. CustomProfile oluştur              │
                    │  6. ProfileManager.save()              │
                    │  7. AES şifreli cookie'ye yaz          │
                    │  8. supplementResponse() ile gönder    │
                    └───────────────────────────────────────┘
```

---

## Kullanılan Teknolojiler

| Katman | Teknoloji | Açıklama |
|---|---|---|
| OAuth protokolü | Play `WSClient` | HTTP istekleri, token exchange, userInfo |
| State/CSRF koruması | PAC4J `SessionStore` | AES şifreli cookie'de saklanır |
| Profil yönetimi | PAC4J `CustomProfile` | Type-safe kullanıcı profili |
| Session yazma | PAC4J `ProfileManager` | AES şifreli cookie'ye kaydeder |
| Session okuma | PAC4J `SecureAction` | Her korumalı request'te doğrular |

---

## Desteklenen Sağlayıcılar

| Sağlayıcı | Protokol | Scope | Callback URL |
|---|---|---|---|
| Google | OAuth 2.0 | `email profile` | `/auth/google/callback` |
| Microsoft Azure AD | OAuth 2.0 | `openid email profile User.Read` | `/auth/microsoft/callback` |

---

## Akış Detayı

### 1. Login Başlatma (googleLogin / microsoftLogin)

```scala
def googleLogin = Action { implicit request =>
  val state      = UUID.randomUUID().toString
  val webContext = new PlayWebContext(request)

  // CSRF koruması: state PAC4J SessionStore'a yazılır (AES şifreli)
  sessionStore.set(webContext, "oauth_state", state)

  val authorizationUrl =
    s"$googleAuthUrl?client_id=$googleClientId" +
      s"&redirect_uri=$googleCallbackUrl" +
      s"&response_type=code&scope=email%20profile&state=$state"

  // PAC4J session cookie response'a eklenir
  webContext.supplementResponse(Redirect(authorizationUrl))
}
```

**Adımlar:**
1. Kriptografik olarak rastgele bir `state` değeri üretilir (`UUID.randomUUID()`)
2. State, PAC4J `SessionStore.set()` ile AES şifreli cookie'ye yazılır
3. Provider'ın authorization URL'ine yönlendirme yapılır
4. `supplementResponse()` ile PAC4J session cookie'si response'a eklenir

---

### 2. Callback — State Doğrulama

```scala
def googleCallback = Action.async { implicit request =>
  val webContext   = new PlayWebContext(request)

  // PAC4J SessionStore'dan state okunur
  val sessionState = sessionStore.get(webContext, "oauth_state").toScala.map(_.toString)
  val queryState   = request.getQueryString("state")

  // Kullanıldıktan sonra temizlenir (replay saldırısı önlemi)
  sessionStore.set(webContext, "oauth_state", null)

  if (sessionState.isEmpty || queryState.isEmpty || sessionState != queryState) {
    // CSRF saldırısı tespit edildi
    Future.successful(
      webContext.supplementResponse(
        Redirect(routes.AuthController.loginPage())
          .flashing("error" -> "Güvenlik doğrulaması başarısız.")
      )
    )
  } else {
    // State geçerli → token exchange başlatılır
    ...
  }
}
```

**Güvenlik Kontrolleri:**
- `sessionState.isEmpty` → session bulunamadı / manipüle edildi
- `queryState.isEmpty` → state parametresi eksik
- `sessionState != queryState` → CSRF saldırısı girişimi
- State tek kullanımlık: doğrulamadan hemen sonra `null` ile silinir

---

### 3. Token Exchange (exchangeGoogleCode / exchangeMicrosoftCode)

```scala
private def exchangeGoogleCode(code: String): Future[Either[String, JsValue]] = {
  ws.url(googleTokenUrl)
    .post(Map(
      "client_id"     -> googleClientId,
      "client_secret" -> googleClientSecret,
      "code"          -> code,
      "redirect_uri"  -> googleCallbackUrl,
      "grant_type"    -> "authorization_code"
    ))
    .flatMap { tokenResponse =>
      if (tokenResponse.status == 200) {
        val accessToken = (tokenResponse.json \ "access_token").as[String]
        ws.url(googleUserInfoUrl)
          .addHttpHeaders("Authorization" -> s"Bearer $accessToken")
          .get()
          .map { userResponse =>
            if (userResponse.status == 200) Right(userResponse.json)
            else Left("Kullanıcı bilgileri alınamadı.")
          }
      } else Future.successful(Left("Token alınamadı."))
    }
    .recover { case ex => Left(s"Bağlantı hatası: ${ex.getMessage}") }
}
```

**Adımlar:**
1. Authorization `code`, token endpoint'e POST edilir
2. `access_token` alınır
3. Access token ile userInfo endpoint'ten kullanıcı bilgileri çekilir
4. `Either[String, JsValue]` döner — `Left` hata, `Right` başarı

---

### 4. PAC4J ile Oturum Açma (handleSSOLogin)

```scala
private def handleSSOLogin(
    email: String,
    username: String,
    provider: String,
    request: Request[AnyContent],
    webContext: PlayWebContext
): Future[Result] = {
  authService.loginOrRegisterBySSO(email, username, provider).flatMap { user =>
    auditLogService.log(
      userId   = Some(user.id),
      tenantId = Some(user.tenantId),
      action   = s"SSO_LOGIN_$provider",
      request  = request
    ).map { _ =>
      // Form login ile tamamen aynı PAC4J akışı
      val profile = new CustomProfile()
      profile.setId(user.id.toString)
      profile.addAttribute("appUsername", user.username)
      profile.addAttribute("role", user.role)
      profile.addAttribute("tenantId", user.tenantId.toString)

      val pm = new ProfileManager(webContext, sessionStore)
      pm.save(true, profile, false)   // AES şifreli cookie'ye yazar

      val result = Redirect(routes.TodoController.index())
        .flashing("success" -> s"$provider ile giriş başarılı.")
      webContext.supplementResponse(result)  // Cookie response'a eklenir
    }
  }
}
```

**PAC4J Adımları:**
1. `authService.loginOrRegisterBySSO()` → DB'de kullanıcı bulunur veya oluşturulur
2. `CustomProfile` oluşturulur — form login ile birebir aynı yapı
3. `ProfileManager.save(true, profile, false)` → AES şifreli cookie'ye yazılır
4. `supplementResponse()` → cookie response'a eklenir, tarayıcıya gönderilir

---

## PAC4J Bileşenlerinin SSO'daki Rolü

### `SessionStore` — State Güvenliği

```
Login:    sessionStore.set(webContext, "oauth_state", state)
Callback: sessionStore.get(webContext, "oauth_state")  → doğrula
Temizlik: sessionStore.set(webContext, "oauth_state", null) → sil
```

State, ham Play session'ına yazılmıyor. PAC4J'nin AES şifreli
`PlayCookieSessionStore`'una yazılıyor. Manipüle edilemez.

### `CustomProfile` — Tip Güvenli Profil

```scala
class CustomProfile extends CommonProfile {
  def getUserId:      UUID   = UUID.fromString(getId)
  def getTenantId:    UUID   = UUID.fromString(getAttribute("tenantId").toString)
  def getAppRole:     String = Option(getAttribute("role")).map(_.toString).getOrElse("")
  def getAppUsername: String = Option(getAttribute("appUsername")).map(_.toString).getOrElse("")
}
```

Google ve Microsoft SSO, form login ile **aynı** `CustomProfile` yapısını kullanır.
Hangi yöntemle giriş yapıldığından bağımsız olarak uygulama tek tip profille çalışır.

### `ProfileManager` — Session Yönetimi

```scala
val pm = new ProfileManager(webContext, sessionStore)
pm.save(true, profile, false)
```

| Parametre | Değer | Açıklama |
|---|---|---|
| 1. `renewSession` | `true` | Cookie yenilenir |
| 2. `profile` | `CustomProfile` | Kaydedilecek profil |
| 3. `multiProfile` | `false` | Tek profil modu |

### `webContext.supplementResponse()` — Cookie Gönderimi

Her SSO adımında tek bir `supplementResponse()` çağrısı yapılır:
- State değişikliği (set/clear) +
- Profil kaydı (pm.save) +

Tümü aynı `webContext` üzerinden birleşik olarak tarayıcıya gönderilir.

---

## Form Login ile SSO Karşılaştırması

| Adım | Form Login | Google SSO | Microsoft SSO |
|---|---|---|---|
| Kimlik doğrulama | `CustomDbAuthenticator` | Google OAuth2 + userInfo | Microsoft OAuth2 + Graph API |
| Profil oluşturma | `CustomProfile` ✅ | `CustomProfile` ✅ | `CustomProfile` ✅ |
| Session yazma | `ProfileManager.save()` ✅ | `ProfileManager.save()` ✅ | `ProfileManager.save()` ✅ |
| Cookie şifreleme | AES (PAC4J) ✅ | AES (PAC4J) ✅ | AES (PAC4J) ✅ |
| CSRF koruması | CSRF token (Play) | State (PAC4J SessionStore) ✅ | State (PAC4J SessionStore) ✅ |
| Audit log | ✅ `USER_LOGIN` | ✅ `SSO_LOGIN_GOOGLE` | ✅ `SSO_LOGIN_MICROSOFT` |

---

## Güvenlik Özellikleri

| Özellik | Mekanizma | Açıklama |
|---|---|---|
| **CSRF Koruması** | OAuth state + PAC4J SessionStore | State AES şifreli cookie'de, manipüle edilemez |
| **Replay Saldırısı** | State tek kullanımlık | Callback sonrası `null` ile silinir |
| **Session Şifreleme** | AES-GCM 128-bit | `ShiroAesDataEncrypter` ile `PlayCookieSessionStore` |
| **Tip Güvenliği** | `CustomProfile` | `UUID`, `String` — type-safe erişim |
| **Audit Trail** | `AuditLogService` | Her SSO girişi DB'ye kaydedilir |
| **Multi-tenant** | `tenantId` profile attribute | Her kullanıcı kendi tenant'ına atanır |

---

## Konfigürasyon (application.conf)

```hocon
# Google OAuth2
google.oauth {
  clientId     = "..."
  clientSecret = "..."
  callbackUrl  = "http://localhost:9000/auth/google/callback"
  authUrl      = "https://accounts.google.com/o/oauth2/v2/auth"
  tokenUrl     = "https://oauth2.googleapis.com/token"
  userInfoUrl  = "https://www.googleapis.com/oauth2/v2/userinfo"
}

# Microsoft Azure AD OAuth2
microsoft.oauth {
  clientId     = "..."
  clientSecret = "..."
  callbackUrl  = "http://localhost:9000/auth/microsoft/callback"
  authUrl      = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
  tokenUrl     = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
  userInfoUrl  = "https://graph.microsoft.com/v1.0/me"
}
```

---

## Route Yapısı

```
GET  /auth/google/callback     controllers.SSOController.googleCallback
GET  /auth/google              controllers.SSOController.googleLogin
GET  /auth/microsoft           controllers.SSOController.microsoftLogin
GET  /auth/microsoft/callback  controllers.SSOController.microsoftCallback
```

---

## Tasarım Kararları

### Neden `pac4j-oidc` Kullanılmadı?

PAC4J'nin tam OIDC entegrasyonu (`pac4j-oidc`) denendi ancak:

- **Google**: `OidcClient.getUserProfile()` email claim'ini döndürmedi
- **Microsoft**: Multi-tenant `common` endpoint'i için discovery metadata `{tenantid}` placeholder'ı
  nedeniyle `"Error getting OP metadata"` hatası oluştu

Bu nedenle OAuth protokol katmanı `WSClient` ile bırakıldı.

### Neden Bu Mimari Doğru?

PAC4J'nin temel sorumluluğu **güvenlik katmanı**dır:
- Kim giriş yaptı? → `CustomProfile`
- Oturumu nerede sakla? → `PlayCookieSessionStore`
- Yetkili mi? → `AdminAuthorizer`

OAuth'un HTTP protokol detayları (token exchange, userInfo API çağrısı) PAC4J'nin
kapsamı dışındadır. `WSClient` bu HTTP iletişimini üstlenir. Sorumluluklar
temiz biçimde ayrılmıştır.

---

## Özet

```
OAuth Protokolü  →  WSClient      (HTTP, token exchange, userInfo)
CSRF Koruması   →  PAC4J          (SessionStore ile AES şifreli state)
Profil Yönetimi →  PAC4J          (CustomProfile + ProfileManager)
Session Şifreleme → PAC4J         (PlayCookieSessionStore, AES-GCM)
Yetkilendirme   →  PAC4J          (SecureAction + AdminAuthorizer)
```
