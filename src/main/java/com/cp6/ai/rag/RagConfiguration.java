package com.cp6.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Cria/carrega o SimpleVectorStore (arquivo JSON) usado pelo RAG.
 * <p>
 * Os vetores só fazem sentido para o modelo de embeddings que os gerou. Por isso, junto do
 * vectorstore.json é gravado um arquivo ".model" com o nome do modelo. Se o modelo configurado
 * mudar, o arquivo antigo vira ".bak" e um novo é gerado automaticamente.
 */
@Component
public class RagConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RagConfiguration.class);

    @Value("${app.rag.vector-store-file:vectorstore.json}")
    private String vectorStoreName;

    @Value("${spring.ai.ollama.embedding.model:unknown}")
    private String embeddingModelName;

    @Value("classpath:/rfc7231.txt")
    private Resource documentFromTXT;

    private final EmbeddingModel embeddingModel;

    private SimpleVectorStore vectorStore; // criado sob demanda e reaproveitado nas próximas chamadas

    public RagConfiguration(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public synchronized SimpleVectorStore getSimpleVectorStore() {
        if (vectorStore == null) {
            vectorStore = loadOrBuildVectorStore();
        }
        return vectorStore;
    }

    private SimpleVectorStore loadOrBuildVectorStore() {
        File vectorStoreFile = getVectorStoreFile();
        File modelFile = new File(vectorStoreFile.getPath() + ".model");

        if (vectorStoreFile.exists()) {
            if (isSameEmbeddingModel(modelFile)) {
                try {
                    var store = newStore();
                    store.load(vectorStoreFile);
                    log.info("Vector Store carregado de {} (embeddings: {})", vectorStoreFile, embeddingModelName);
                    return store;
                } catch (RuntimeException ex) {
                    log.warn("Falha ao carregar o Vector Store existente. Recriando. Causa: {}", ex.getMessage());
                }
            } else {
                log.warn("O Vector Store existente foi gerado por outro modelo de embeddings. Recriando para '{}'.",
                        embeddingModelName);
            }
            backupOldVectorStore(vectorStoreFile);
        }

        var store = newStore();
        rebuildVectorStore(store, vectorStoreFile);
        writeEmbeddingModel(modelFile);
        return store;
    }

    private SimpleVectorStore newStore() {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    private void rebuildVectorStore(SimpleVectorStore store, File vectorStoreFile) {
        long start = System.currentTimeMillis();
        log.info("Criando Vector Store com o modelo '{}'... (pode demorar alguns minutos)", embeddingModelName);

        var textReader = new TextReader(documentFromTXT);
        textReader.getCustomMetadata().put("filename", "rfc7231.txt");
        List<Document> documents = textReader.get();
        log.info("Total de documentos lidos: {}", documents.size());

        List<Document> chunks = new TokenTextSplitter().apply(documents);
        log.info("Total de trechos (chunks): {}", chunks.size());

        store.add(chunks);
        store.save(vectorStoreFile);

        log.info("Vector Store salvo em {} ({} ms)", vectorStoreFile, System.currentTimeMillis() - start);
    }

    private boolean isSameEmbeddingModel(File modelFile) {
        try {
            return modelFile.exists()
                    && embeddingModelName.equals(Files.readString(modelFile.toPath(), StandardCharsets.UTF_8).trim());
        } catch (IOException ex) {
            return false;
        }
    }

    private void writeEmbeddingModel(File modelFile) {
        try {
            Files.writeString(modelFile.toPath(), embeddingModelName, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("Não foi possível gravar {}: {}", modelFile, ex.getMessage());
        }
    }

    private void backupOldVectorStore(File vectorStoreFile) {
        Path source = vectorStoreFile.toPath();
        Path backup = source.resolveSibling(source.getFileName() + ".bak");
        try {
            Files.move(source, backup, StandardCopyOption.REPLACE_EXISTING);
            log.info("Arquivo antigo movido para {}", backup);
        } catch (IOException ex) {
            log.warn("Não foi possível criar o backup. O arquivo atual será sobrescrito.");
        }
    }

    private File getVectorStoreFile() {
        return Paths.get(vectorStoreName).toFile().getAbsoluteFile();
    }
}
