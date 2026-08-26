package io.github.vicx074.geosapiens.ingestion.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RabbitIngestionTopologyConfiguration {

  @Bean
  DirectExchange ingestionExchange(IngestionMessagingProperties properties) {
    return new DirectExchange(properties.ingestionExchange(), true, false);
  }

  @Bean
  DirectExchange ingestionDeadLetterExchange(IngestionMessagingProperties properties) {
    return new DirectExchange(properties.ingestionDeadLetterExchange(), true, false);
  }

  @Bean
  Queue ingestionQueue(IngestionMessagingProperties properties) {
    return QueueBuilder.durable(properties.ingestionQueue())
        .deadLetterExchange(properties.ingestionDeadLetterExchange())
        .deadLetterRoutingKey(properties.ingestionDeadLetterRoutingKey())
        .build();
  }

  @Bean
  Queue ingestionDeadLetterQueue(IngestionMessagingProperties properties) {
    return QueueBuilder.durable(properties.ingestionDeadLetterQueue()).build();
  }

  @Bean
  Binding ingestionBinding(
      @Qualifier("ingestionQueue") Queue ingestionQueue,
      @Qualifier("ingestionExchange") DirectExchange ingestionExchange,
      IngestionMessagingProperties properties) {
    return BindingBuilder.bind(ingestionQueue)
        .to(ingestionExchange)
        .with(properties.ingestionRoutingKey());
  }

  @Bean
  Binding ingestionDeadLetterBinding(
      @Qualifier("ingestionDeadLetterQueue") Queue ingestionDeadLetterQueue,
      @Qualifier("ingestionDeadLetterExchange") DirectExchange ingestionDeadLetterExchange,
      IngestionMessagingProperties properties) {
    return BindingBuilder.bind(ingestionDeadLetterQueue)
        .to(ingestionDeadLetterExchange)
        .with(properties.ingestionDeadLetterRoutingKey());
  }
}
