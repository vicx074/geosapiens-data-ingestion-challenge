package io.github.vicx074.geosapiens.ingestion.infrastructure.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IngestionWorkerConfiguration {

  @Bean
  SimpleRabbitListenerContainerFactory ingestionWorkerContainerFactory(
      SimpleRabbitListenerContainerFactoryConfigurer configurer,
      ConnectionFactory connectionFactory,
      WorkerProperties properties) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    configurer.configure(factory, connectionFactory);
    factory.setConcurrentConsumers(properties.concurrency());
    factory.setMaxConcurrentConsumers(properties.concurrency());
    factory.setPrefetchCount(properties.prefetch());
    factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
    factory.setDefaultRequeueRejected(false);
    return factory;
  }
}
