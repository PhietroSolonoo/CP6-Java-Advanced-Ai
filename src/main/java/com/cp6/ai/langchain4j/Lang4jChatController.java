package com.cp6.ai.langchain4j;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/langchain4j")
@Tag(name = "Basic - LangChain4j")
public class Lang4jChatController {

    private final Lang4jChatService chatService;

    public Lang4jChatController(Lang4jChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/generate")
    public ResponseEntity<Map<String, String>> generate(
            @RequestParam(value = "message",
                    defaultValue = "Java é a melhor linguagem de programação? Responda apenas sim ou não.") String message) {

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Informe uma pergunta no parâmetro message."));
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("framework", "langchain4j");
        body.put("model", chatService.getModelName());
        body.put("ollama", chatService.run(message));
        return new ResponseEntity<>(body, HttpStatus.OK);
    }
}
