package com.cp6.ai.langchain4j;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class Lang4jChatService {

    private final ChatModel model;
    private final String modelName;

    // Usa as mesmas propriedades do Spring AI: o modelo é trocado em um único lugar (application.properties)
    public Lang4jChatService(
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${spring.ai.ollama.chat.model}") String modelName,
            @Value("${app.chat.timeout-seconds:120}") long timeoutSeconds
    ) {
        this.modelName = modelName;
        this.model = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    public String run(String userPrompt) {
        String answer = model.chat(userPrompt);
        return answer == null ? "" : answer.trim();
    }

    public String getModelName() {
        return modelName;
    }
}
