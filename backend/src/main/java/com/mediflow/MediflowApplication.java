package com.mediflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MediflowApplication {

  public static void main(String[] args) {
    SpringApplication.run(MediflowApplication.class, args);
  }
}
