$ErrorActionPreference = "Stop"

$consumerDir = "C:\Users\Alper\todo-play-app\consumers\todo-notification-consumer"

Write-Host "Starting todo-notification-consumer..."
Push-Location $consumerDir
try {
  sbt "runMain com.alper.todo.notificationconsumer.NotificationConsumerApp"
} finally {
  Pop-Location
}
