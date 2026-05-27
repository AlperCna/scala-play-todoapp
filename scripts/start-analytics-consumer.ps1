$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$consumerDir = Join-Path $repoRoot "consumers\\todo-analytics-consumer"

Push-Location $consumerDir
try {
    sbt "runMain com.alper.todo.analyticsconsumer.AnalyticsConsumerApp"
}
finally {
    Pop-Location
}
