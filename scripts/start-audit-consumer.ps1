$ErrorActionPreference = "Stop"

$consumerDir = "C:\Users\Alper\todo-play-app\consumers\todo-audit-consumer"

Write-Host "Starting todo-audit-consumer..."
Push-Location $consumerDir
try {
  sbt "runMain com.alper.todo.auditconsumer.AuditConsumerApp"
} finally {
  Pop-Location
}
