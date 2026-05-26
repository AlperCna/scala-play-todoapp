# scala-play-todoapp

A Todo application built with Scala and Play Framework 2.9. The project now includes a phased Kafka integration with domain events, outbox persistence, a Kafka producer, and an outbox publisher worker.

## Kafka Docs
- [Kafka Phase 1](docs/kafka-phase-1.md)
- [Kafka Phase 2](docs/kafka-phase-2.md)
- [Kafka Phase 3](docs/kafka-phase-3.md)
- [Kafka Phase 4](docs/kafka-phase-4.md)
- [Kafka Phase 5](docs/kafka-phase-5.md)
- [Kafka Phase 6](docs/kafka-phase-6.md)
- [Kafka Local Runbook](docs/kafka-local-runbook.md)

## Local Kafka Quick Start
PowerShell:

```powershell
.\scripts\start-local-kafka.ps1
sbt -Dconfig.file=conf/kafka-local.conf.example run
```

Kafka UI:
- [http://localhost:8085](http://localhost:8085)

Useful helper scripts:
- `.\scripts\create-kafka-topics.ps1`
- `.\scripts\list-kafka-topics.ps1`
- `.\scripts\read-kafka-topic.ps1 -MaxMessages 5`
