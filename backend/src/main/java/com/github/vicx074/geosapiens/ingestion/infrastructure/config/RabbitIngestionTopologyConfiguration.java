package com.github.vicx074.geosapiens.ingestion.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "true")
public class RabbitIngestionTopologyConfiguration {

  @Bean
  DirectExchange ingestionExchange(IngestionMessagingProperties properties) {
    return new DirectExchange(properties.ingestionExchange(), true, false);
  }

  @Bean
  Queue ingestionQueue(IngestionMessagingProperties properties) {
    return new Queue(properties.ingestionQueue(), true, false, false);
  }

  @Bean
  Binding ingestionBinding(
      Queue ingestionQueue,
      DirectExchange ingestionExchange,
      IngestionMessagingProperties properties) {
    return BindingBuilder.bind(ingestionQueue)
        .to(ingestionExchange)
        .with(properties.ingestionRoutingKey());
  }
}
