package com.mediflow.prescription.ai;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBean(ConnectionFactory.class)
public class AiRabbitConfig {

  public static final String EXCHANGE = "ai.rx";

  @Bean
  DirectExchange aiRxExchange() {
    return new DirectExchange(EXCHANGE, true, false);
  }

  @Bean
  Queue aiRxRequestsQueue() {
    return QueueBuilder.durable(AiJobMessages.REQUESTS)
        .withArgument("x-dead-letter-exchange", "")
        .withArgument("x-dead-letter-routing-key", AiJobMessages.DLQ)
        .build();
  }

  @Bean
  Queue aiRxResultsQueue() {
    return QueueBuilder.durable(AiJobMessages.RESULTS).build();
  }

  @Bean
  Queue aiRxDlq() {
    return QueueBuilder.durable(AiJobMessages.DLQ).build();
  }

  @Bean
  Binding aiRxRequestsBinding(Queue aiRxRequestsQueue, DirectExchange aiRxExchange) {
    return BindingBuilder.bind(aiRxRequestsQueue).to(aiRxExchange).with(AiJobMessages.REQUESTS);
  }

  @Bean
  Binding aiRxResultsBinding(Queue aiRxResultsQueue, DirectExchange aiRxExchange) {
    return BindingBuilder.bind(aiRxResultsQueue).to(aiRxExchange).with(AiJobMessages.RESULTS);
  }

  @Bean
  MessageConverter aiJacksonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }
}
