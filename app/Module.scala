import com.google.inject.AbstractModule
import repositories._
import services._

class Module extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[UserRepository]).to(classOf[UserRepositoryImpl])
    bind(classOf[AuthService]).to(classOf[AuthServiceImpl])
    bind(classOf[TodoRepository]).to(classOf[TodoRepositoryImpl])
    bind(classOf[TodoService]).to(classOf[TodoServiceImpl])
    bind(classOf[AdminService]).to(classOf[AdminServiceImpl])
    bind(classOf[AuditLogRepository]).to(classOf[AuditLogRepositoryImpl])
    bind(classOf[AuditLogService]).to(classOf[AuditLogServiceImpl])
    bind(classOf[TenantRepository]).to(classOf[TenantRepositoryImpl])
  }
}