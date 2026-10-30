package com.cp6.ai.rag;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/rag")
@Tag(name = "RAG")
public class RagFileDbController {

    private static final Logger log = LoggerFactory.getLogger(RagFileDbController.class);

    @Value("classpath:/rag-prompt-template.st")
    private Resource ragPromptTemplate;

    @Value("${app.rag.top-k:3}")
    private int topK;

    private final ChatClient chatClient;
    private final RagConfiguration ragConfiguration;

    public RagFileDbController(ChatClient.Builder builder, RagConfiguration ragConfiguration) {
        this.ragConfiguration = ragConfiguration;
        this.chatClient = builder.build();
    }

    @GetMapping("/generate")
    public ResponseEntity<String> generate(
            @RequestParam(value = "message", defaultValue = "Qual o código do status de Bad Request?") String message) {
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body("Informe uma pergunta no parâmetro message.");
        }
        try {
            // 1) busca os trechos do documento mais parecidos com a pergunta
            List<Document> similarDocuments = ragConfiguration.getSimpleVectorStore()
                    .similaritySearch(SearchRequest.builder().query(message).topK(topK).build());
            List<String> contentList = similarDocuments.stream().map(Document::getText).toList();

            // 2) monta o prompt com a pergunta + trechos encontrados
            PromptTemplate promptTemplate =
                    new PromptTemplate(ragPromptTemplate.getContentAsString(StandardCharsets.UTF_8));
            var promptParameters = new HashMap<String, Object>();
            promptParameters.put("input", message);
            promptParameters.put("documents", String.join("\n", contentList));
            var prompt = promptTemplate.create(promptParameters);

            log.info("Prompt enviado ao modelo:\n{}", prompt.getContents());

            // 3) pergunta ao modelo de chat
            return ResponseEntity.ok(chatClient.prompt(prompt).call().content());
        } catch (Exception ex) {
            log.error("Erro no RAG", ex);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    "Falha no RAG: " + ex.getMessage()
                            + "\nConfira se o Ollama está rodando e se os modelos de chat e de embeddings "
                            + "foram baixados (ollama pull <modelo>).");
        }
    }
}
