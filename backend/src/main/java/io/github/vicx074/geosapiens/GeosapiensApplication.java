package io.github.vicx074.geosapiens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GeosapiensApplication {

  public static void main(String[] args) {
    SpringApplication.run(GeosapiensApplication.class, args);
  }
}
