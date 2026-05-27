# scala-play-todoapp

A Todo application built with Scala and Play Framework 2.9. The project now includes a phased Kafka integration with domain events, outbox persistence, a Kafka producer, and an outbox publisher worker.

## Kafka Docs
- [Kafka Phase 1](docs/kafka-phase-1.md)
- [Kafka Phase 2](docs/kafka-phase-2.md)
- [Kafka Phase 3](docs/kafka-phase-3.md)
- [Kafka Phase 4](docs/kafka-phase-4.md)
- [Kafka Phase 5](docs/kafka-phase-5.md)
- [Kafka Phase 6](docs/kafka-phase-6.md)
- [Kafka Phase 7](docs/kafka-phase-7.md)
- [Kafka Phase 8](docs/kafka-phase-8.md)
- [Kafka Phase 9](docs/kafka-phase-9.md)
- [Kafka Phase 10](docs/kafka-phase-10.md)
- [Kafka Phase 11](docs/kafka-phase-11.md)
- [Kafka Phase 12](docs/kafka-phase-12.md)
- [Kafka Local Runbook](docs/kafka-local-runbook.md)
- [Kafka Rollout Checklist](docs/kafka-rollout-checklist.md)

## Consumer Skeletons
- [todo-notification-consumer](consumers/todo-notification-consumer/README.md)
- [todo-audit-consumer](consumers/todo-audit-consumer/README.md)
- [todo-analytics-consumer](consumers/todo-analytics-consumer/README.md)

## Local Kafka Quick Start
PowerShell:

```powershell
.\scripts\start-local-kafka.ps1
.\scripts\start-kafka-enabled-app.ps1
```

Kafka UI:
- [http://localhost:8085](http://localhost:8085)

Useful helper scripts:
- `.\scripts\create-kafka-topics.ps1`
- `.\scripts\list-kafka-topics.ps1`
- `.\scripts\read-kafka-topic.ps1 -MaxMessages 5`
- `.\scripts\start-notification-consumer.ps1`
- `.\scripts\start-audit-consumer.ps1`
- `.\scripts\start-analytics-consumer.ps1`
- `.\scripts\check-outbox-summary.ps1`
- `.\scripts\check-kafka-rollout.ps1`
- `.\scripts\stop-local-kafka.ps1`
