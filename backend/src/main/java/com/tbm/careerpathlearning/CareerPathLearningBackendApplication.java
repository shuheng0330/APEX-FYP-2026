package com.tbm.careerpathlearning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@SpringBootApplication
@EnableAsync
@Controller
@ConfigurationPropertiesScan
public class CareerPathLearningBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareerPathLearningBackendApplication.class, args);
    }

    @GetMapping("/**/{path:[^\\.]*}")
    public String redirectApi() {
        return "forward:/index.html";
    }

}
