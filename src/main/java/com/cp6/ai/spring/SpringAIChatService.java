package com.cp6.ai.spring;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SpringAIChatService {

    private static final int MAX_TURNS_PER_SESSION = 10;
    private static final String SYSTEM_PROMPT =
            "Você é um assistente prestativo. Responda sempre em português do Brasil, de forma clara e objetiva.";

    private final ChatClient chatClient;
    private final Map<String, Deque<ChatTurn>> conversations = new ConcurrentHashMap<>();

    public SpringAIChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
    }

    /** Pergunta simples, sem memória. */
    public String run(String userPrompt) {
        String content = chatClient.prompt().user(userPrompt).call().content();
        return content == null ? "" : content.trim();
    }

    /** Chat com memória curta (últimas mensagens) guardada em memória, por sessionId. */
    public ChatResponse runWithMemory(String sessionId, String userPrompt) {
        String id = (sessionId == null || sessionId.isBlank())
                ? UUID.randomUUID().toString()
                : sessionId.trim();

        Deque<ChatTurn> turns = conversations.computeIfAbsent(id, ignored -> new ArrayDeque<>());
        String answer;

        synchronized (turns) {
            String content = chatClient.prompt()
                    .user(buildPromptWithMemory(turns, userPrompt))
                    .call()
                    .content();
            answer = content == null ? "" : content.trim();

            turns.addLast(new ChatTurn("user", userPrompt));
            turns.addLast(new ChatTurn("assistant", answer));
            trimHistory(turns);
        }

        return new ChatResponse(id, answer);
    }

    public void resetConversation(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            conversations.remove(sessionId.trim());
        }
    }

    private static String buildPromptWithMemory(Deque<ChatTurn> turns, String userPrompt) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Considere o histórico da conversa para manter o contexto.\n\n");
        prompt.append("Histórico:\n");

        if (turns.isEmpty()) {
            prompt.append("(sem mensagens anteriores)\n");
        } else {
            for (ChatTurn turn : turns) {
                prompt.append(turn.role()).append(": ").append(turn.content()).append('\n');
            }
        }

        prompt.append("\nNova mensagem do usuário:\n").append(userPrompt);
        return prompt.toString();
    }

    private static void trimHistory(Deque<ChatTurn> turns) {
        int maxEntries = MAX_TURNS_PER_SESSION * 2;
        while (turns.size() > maxEntries) {
            turns.removeFirst();
        }
    }

    public record ChatResponse(String sessionId, String response) {
    }

    private record ChatTurn(String role, String content) {
    }
}
