$ErrorActionPreference = "Stop"

Write-Host "Starting Play app with Kafka-enabled local resource config..."
sbt "-Dconfig.resource=kafka-local.conf.example" run
