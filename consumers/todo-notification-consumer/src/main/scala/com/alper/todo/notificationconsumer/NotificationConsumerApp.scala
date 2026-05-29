package com.alper.todo.notificationconsumer

import com.alper.todo.notificationconsumer.config.{NotificationConsumerSettings, NotificationConsumerSettingsLoader}
import com.alper.todo.notificationconsumer.infrastructure.{JdbcProcessedEventStore, KafkaDeadLetterPublisher, LoggingNotificationSender}
import com.alper.todo.notificationconsumer.model.NotificationConsumerRecordResult
import com.alper.todo.notificationconsumer.model.NotificationConsumerRecordResult.{DisabledIgnored, DuplicateIgnored, MalformedPayloadIgnored, Processed, UnsupportedEventIgnored, UnsupportedVersionIgnored}
import com.alper.todo.notificationconsumer.service.{NotificationCommandFactory, NotificationEventProcessor, NotificationKafkaRecordHandler}
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord, KafkaConsumer, OffsetAndMetadata}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer

import java.time.Duration
import java.util.{Collections, Properties}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.jdk.CollectionConverters._

object NotificationConsumerApp extends App {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  private val settings = NotificationConsumerSettingsLoader.load()
  private val consumer = new KafkaConsumer[String, String](consumerProperties(settings))
  private val dlqPublisher = new KafkaDeadLetterPublisher(settings.bootstrapServers, settings.dlqTopic, settings.consumerName)
  private val handler = new NotificationKafkaRecordHandler(
    new NotificationEventProcessor(
      settings = settings,
      processedEventStore = new JdbcProcessedEventStore(settings.database, settings.consumerName),
      notificationSender = new LoggingNotificationSender(),
      commandFactory = new NotificationCommandFactory()
    )
  )

  Runtime.getRuntime.addShutdownHook(new Thread(() => {
    println("[INFO] notification-consumer shutdown requested")
    consumer.wakeup()
    dlqPublisher.close()
  }))

  println(
    s"[INFO] notification-consumer starting topic=${settings.topic} groupId=${settings.groupId} mode=${settings.dispatchMode.value} dlqTopic=${settings.dlqTopic}"
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
              s"[ERROR] notification-consumer record loop swallowed error offset=${record.offset()} partition=${record.partition()} reason=${ex.getMessage}"
            )
        }
      }
    }
  } catch {
    case _: WakeupException =>
      println("[INFO] notification-consumer stopped")
  } finally {
    consumer.close()
    dlqPublisher.close()
  }

  private def processRecord(record: ConsumerRecord[String, String]): Unit = {
    val result = retry(record) {
      Await.result(handler.handle(record.value()), 10.seconds)
    }

    println(
      s"[INFO] notification-consumer handled offset=${record.offset()} partition=${record.partition()} eventKey=${record.key()} result=$result"
    )

    result match {
      case MalformedPayloadIgnored =>
        publishToDlq(record, "MALFORMED_PAYLOAD", Some("JSON payload could not be parsed"))
      case UnsupportedVersionIgnored =>
        publishToDlq(record, "UNSUPPORTED_VERSION", Some(s"Supported version=${settings.supportedEventVersion}"))
      case _ =>
    }

    val nextOffset = new OffsetAndMetadata(record.offset() + 1)
    val partition = new TopicPartition(record.topic(), record.partition())
    consumer.commitSync(Map(partition -> nextOffset).asJava)
  }

  private def retry(record: ConsumerRecord[String, String])(thunk: => NotificationConsumerRecordResult): NotificationConsumerRecordResult = {
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
            s"[WARN] notification-consumer attempt=$attempt/${settings.maxRetries} failed offset=${record.offset()} partition=${record.partition()} reason=${ex.getMessage}"
          )
          Thread.sleep(settings.retryBackoffMillis)
      }
    }

    publishToDlq(record, "PROCESSING_FAILED", lastError.map(_.getMessage))
    Processed
  }

  private def publishToDlq(record: ConsumerRecord[String, String], reason: String, errorMessage: Option[String]): Unit = {
    dlqPublisher.publish(record, reason, errorMessage)
    println(
      s"[WARN] notification-consumer published offset=${record.offset()} partition=${record.partition()} to DLQ reason=$reason error=${errorMessage.getOrElse("-")}"
    )
  }

  private def consumerProperties(settings: NotificationConsumerSettings): Properties = {
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
