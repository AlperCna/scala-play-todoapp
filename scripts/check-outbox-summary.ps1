param(
  [string]$Server = "127.0.0.1,1433",
  [string]$Database = "ScalaTodoPlayAppDb",
  [string]$Username = "sa",
  [string]$Password = "123456"
)

$ErrorActionPreference = "Stop"

$sqlcmd = "C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\170\Tools\Binn\SQLCMD.EXE"

if (-not (Test-Path $sqlcmd)) {
  throw "sqlcmd not found at expected path: $sqlcmd"
}

& $sqlcmd `
  -S $Server `
  -U $Username `
  -P $Password `
  -d $Database `
  -Q "SET NOCOUNT ON; SELECT status, COUNT(*) AS total FROM todo_event_outbox GROUP BY status ORDER BY status;"

if ($LASTEXITCODE -ne 0) {
  throw "Failed to query outbox summary."
}
