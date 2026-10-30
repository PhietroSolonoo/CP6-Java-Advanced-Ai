package com.cp6.ai.spring;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/springai")
@Tag(name = "Basic - Spring AI")
public class SpringAIChatController {

    private static final Logger log = LoggerFactory.getLogger(SpringAIChatController.class);

    private final SpringAIChatService chatService;
    private final long chatTimeoutSeconds;
    private final String ollamaBaseUrl;
    private final String ollamaModel;
    private final String embeddingModel;

    public SpringAIChatController(
            SpringAIChatService chatService,
            @Value("${app.chat.timeout-seconds:120}") long chatTimeoutSeconds,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${spring.ai.ollama.chat.model:}") String ollamaModel,
            @Value("${spring.ai.ollama.embedding.model:}") String embeddingModel
    ) {
        this.chatService = chatService;
        this.chatTimeoutSeconds = chatTimeoutSeconds;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.ollamaModel = ollamaModel;
        this.embeddingModel = embeddingModel;
    }

    @GetMapping("/generate")
    public ResponseEntity<Map<String, String>> generate(
            @RequestParam(value = "message",
                    defaultValue = "Java é a melhor linguagem de programação? Responda apenas sim ou não.") String message) {

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Informe uma pergunta no parâmetro message."));
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("framework", "spring-ai");
        body.put("model", ollamaModel);
        body.put("ollama", chatService.run(message));
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("baseUrl", ollamaBaseUrl);
        body.put("model", ollamaModel);
        body.put("embeddingModel", embeddingModel);

        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(ollamaBaseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            body.put("reachable", response.statusCode() >= 200 && response.statusCode() < 300);
            body.put("statusCode", response.statusCode());
            return ResponseEntity.ok(body);
        } catch (Exception ex) {
            body.put("reachable", false);
            body.put("error", rootMessage(ex));
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatMessageResponse> chat(@RequestBody ChatMessageRequest request) {
        String message = (request == null || request.message() == null) ? "" : request.message().trim();
        if (message.isBlank()) {
            return ResponseEntity.badRequest().body(new ChatMessageResponse("", "A mensagem não pode ser vazia."));
        }

        CompletableFuture<SpringAIChatService.ChatResponse> future =
                CompletableFuture.supplyAsync(() -> chatService.runWithMemory(request.sessionId(), message));

        try {
            SpringAIChatService.ChatResponse response = future.get(chatTimeoutSeconds, TimeUnit.SECONDS);
            return ResponseEntity.ok(new ChatMessageResponse(response.sessionId(), response.response()));
        } catch (TimeoutException ex) {
            future.cancel(true);
            log.warn("Timeout de {}s esperando o modelo {}", chatTimeoutSeconds, ollamaModel);
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(new ChatMessageResponse("", "O modelo '" + ollamaModel + "' demorou demais para responder."));
        } catch (Exception ex) {
            log.warn("Erro no chat: {}", rootMessage(ex));
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ChatMessageResponse("", "Modelo indisponível em " + ollamaBaseUrl
                            + ". Causa: " + rootMessage(ex)));
        }
    }

    @PostMapping("/chat/reset")
    public ResponseEntity<Void> reset(@RequestBody ResetChatRequest request) {
        chatService.resetConversation(request.sessionId());
        return ResponseEntity.noContent().build();
    }

    public record ChatMessageRequest(String sessionId, String message) {
    }

    public record ChatMessageResponse(String sessionId, String response) {
    }

    public record ResetChatRequest(String sessionId) {
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}
