package com.cp6.ai;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "CP6 - Spring AI + LangChain4j",
        version = "0.0.1",
        description = "Spring AI e LangChain4j conversando com modelos locais via Ollama (Qwen + nomic-embed-text)"))
public class Cp6Application {

    public static void main(String[] args) {
        SpringApplication.run(Cp6Application.class, args);
    }

}
