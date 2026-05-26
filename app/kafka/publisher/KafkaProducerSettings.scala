package kafka.publisher

case class KafkaProducerSettings(
  enabled: Boolean,
  bootstrapServers: String,
  clientId: String,
  todoEventsTopic: String,
  acks: String,
  enableIdempotence: Boolean,
  requestTimeoutMs: Int,
  deliveryTimeoutMs: Int,
  maxInFlightRequestsPerConnection: Int
)
