package security

import com.google.inject.{AbstractModule, Inject, Provider}
import org.pac4j.core.context.session.SessionStore
import org.pac4j.play.store.{PlayCookieSessionStore, ShiroAesDataEncrypter}
import play.api.Configuration

class Pac4jModule extends AbstractModule {
  override def configure(): Unit = {
    // AES şifreli cookie session store
    bind(classOf[SessionStore]).toProvider(classOf[SessionStoreProvider]).asEagerSingleton()
    // ADMIN yetkilendirme — SecureAction'a inject edilir
    bind(classOf[AdminAuthorizer]).asEagerSingleton()
  }
}

class SessionStoreProvider @Inject()(configuration: Configuration) extends Provider[SessionStore] {
  override def get(): SessionStore = {
    // 16-byte (128-bit) AES-GCM anahtarı — server restart'ta session'lar geçerli kalır
    val keyBytes = "TodoApp_SecKey16".getBytes("UTF-8")
    new PlayCookieSessionStore(new ShiroAesDataEncrypter(keyBytes))
  }
}
