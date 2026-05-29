package com.alper.todo.auditconsumer

import com.alper.todo.auditconsumer.config.{AuditConsumerSettings, AuditConsumerSettingsLoader}
import com.alper.todo.auditconsumer.infrastructure.{JdbcAuditLogWriter, KafkaDeadLetterPublisher}
import com.alper.todo.auditconsumer.model.AuditConsumerRecordResult.{DuplicateIgnored, MalformedPayloadIgnored, Processed, UnsupportedEventIgnored, UnsupportedVersionIgnored}
import com.alper.todo.auditconsumer.service.{AuditCommandFactory, AuditEventProcessor, AuditKafkaRecordHandler}
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord, KafkaConsumer, OffsetAndMetadata}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer

import java.time.Duration
import java.util.{Collections, Properties}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.jdk.CollectionConverters._

object AuditConsumerApp extends App {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  private val settings = AuditConsumerSettingsLoader.load()
  private val consumer = new KafkaConsumer[String, String](consumerProperties(settings))
  private val dlqPublisher = new KafkaDeadLetterPublisher(settings.bootstrapServers, settings.dlqTopic, settings.consumerName)
  private val handler = new AuditKafkaRecordHandler(
    new AuditEventProcessor(
      settings = settings,
      auditLogWriter = new JdbcAuditLogWriter(settings.database, settings.consumerName),
      commandFactory = new AuditCommandFactory()
    )
  )

  Runtime.getRuntime.addShutdownHook(new Thread(() => {
    println("[INFO] audit-consumer shutdown requested")
    consumer.wakeup()
    dlqPublisher.close()
  }))

  println(
    s"[INFO] audit-consumer starting topic=${settings.topic} groupId=${settings.groupId} consumerName=${settings.consumerName} dlqTopic=${settings.dlqTopic}"
  )

  consumer.subscribe(Collections.singletonList(settings.topic))

  try {
    while (true) {
      val records = consumer.poll(Duration.ofSeconds(1))
      for (record <- records.iterator().asScala) {
        try {
          processRecord(record)
        } catch {
          case ex: Throwable =>
            println(
              s"[ERROR] audit-consumer record loop swallowed error offset=${record.offset()} partition=${record.partition()} reason=${ex.getMessage}"
            )
        }
      }
    }
  } catch {
    case _: WakeupException =>
      println("[INFO] audit-consumer stopped")
  } finally {
    consumer.close()
    dlqPublisher.close()
  }

  private def processRecord(record: ConsumerRecord[String, String]): Unit = {
    val result = retry(record) {
      Await.result(handler.handle(record.value()), 10.seconds)
    }
    println(
      s"[INFO] audit-consumer handled offset=${record.offset()} partition=${record.partition()} eventKey=${record.key()} result=$result"
    )

    result match {
      case MalformedPayloadIgnored =>
        publishToDlq(record, "MALFORMED_PAYLOAD", Some("JSON payload could not be parsed"))
      case UnsupportedVersionIgnored =>
        publishToDlq(record, "UNSUPPORTED_VERSION", Some(s"Supported version=${settings.supportedEventVersion}"))
      case UnsupportedEventIgnored =>
        publishToDlq(record, "UNSUPPORTED_EVENT", Some("Audit consumer does not support this event type"))
      case _ =>
    }

    val nextOffset = new OffsetAndMetadata(record.offset() + 1)
    val partition = new TopicPartition(record.topic(), record.partition())
    consumer.commitSync(Map(partition -> nextOffset).asJava)
  }

  private def retry(
    record: ConsumerRecord[String, String]
  )(thunk: => com.alper.todo.auditconsumer.model.AuditConsumerRecordResult): com.alper.todo.auditconsumer.model.AuditConsumerRecordResult = {
    var attempt = 0
    var lastError: Option[Throwable] = None

    while (attempt < settings.maxRetries) {
      try {
        return thunk
      } catch {
        case ex: Throwable =>
          attempt += 1
          lastError = Some(ex)
          println(
            s"[WARN] audit-consumer attempt=$attempt/${settings.maxRetries} failed offset=${record.offset()} partition=${record.partition()} reason=${ex.getMessage}"
          )
          Thread.sleep(settings.retryBackoffMillis)
      }
    }

    publishToDlq(record, "PROCESSING_FAILED", lastError.map(_.getMessage))
    com.alper.todo.auditconsumer.model.AuditConsumerRecordResult.Processed
  }

  private def publishToDlq(record: ConsumerRecord[String, String], reason: String, errorMessage: Option[String]): Unit = {
    dlqPublisher.publish(record, reason, errorMessage)
    println(
      s"[WARN] audit-consumer published offset=${record.offset()} partition=${record.partition()} to DLQ reason=$reason error=${errorMessage.getOrElse("-")}"
    )
  }

  private def consumerProperties(settings: AuditConsumerSettings): Properties = {
    val props = new Properties()
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, settings.bootstrapServers)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, settings.groupId)
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10")
    props
  }
}
