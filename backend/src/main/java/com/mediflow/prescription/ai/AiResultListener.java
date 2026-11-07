package com.mediflow.prescription.ai;

import com.mediflow.prescription.ai.AiJobMessages.Result;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(ConnectionFactory.class)
public class AiResultListener {

  private final AiGatewayService gateway;

  public AiResultListener(AiGatewayService gateway) {
    this.gateway = gateway;
  }

  @RabbitListener(queues = AiJobMessages.RESULTS)
  public void onResult(Result result) {
    gateway.handleResult(result);
  }
}
