package io.llamapanama.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationTest {

    @Test
    void generateTokensWhenModelAvailable() {
        String modelPath = System.getenv("MODEL_PATH");
        if (modelPath == null || modelPath.isBlank()) {
            return; // skipped
        }
        try (Model model = new Model(modelPath); ChatSession session = new ChatSession(model, SamplerParams.defaults(), 128, Runtime.getRuntime().availableProcessors())) {
            String text = session.generate("Hello");
            assertNotNull(text);
            assertFalse(text.isEmpty());
            assertTrue(text.length() <= 256);
            assertNotNull(session.getLastStats());
        }
    }

    @Test
    void embeddingsAreAvailableWhenModelAvailable() {
        String modelPath = System.getenv("MODEL_PATH");
        if (modelPath == null || modelPath.isBlank()) {
            return; // skipped
        }
        try (Model model = new Model(modelPath); Context context = new Context(model, 128, Runtime.getRuntime().availableProcessors())) {
            Embeddings embeddings = context.createEmbeddings();
            float[] vector = embeddings.embed("Hello world");
            assertNotNull(vector);
            assertTrue(vector.length > 0);
        }
    }

    @Test
    void cancellationStopsStreaming() {
        try (Model model = new Model("dummy"); ChatSession session = new ChatSession(model, SamplerParams.defaults(), 64, 1)) {
            StringBuilder builder = new StringBuilder();
            CancellationToken token = new CancellationToken();
            session.stream("Hello", chunk -> {
                builder.append(chunk);
                token.cancel();
            }, token);
            assertFalse(builder.toString().isEmpty());
        }
    }
}
