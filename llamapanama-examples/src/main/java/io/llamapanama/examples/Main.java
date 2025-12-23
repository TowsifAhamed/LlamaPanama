package io.llamapanama.examples;

import io.llamapanama.core.ChatSession;
import io.llamapanama.core.Context;
import io.llamapanama.core.Embeddings;
import io.llamapanama.core.InferenceStats;
import io.llamapanama.core.CancellationToken;
import io.llamapanama.core.Model;
import io.llamapanama.core.SamplerParams;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class Main {
    public static void main(String[] args) {
        boolean embedMode = args.length > 0 && "embed".equalsIgnoreCase(args[0]);
        String[] trimmedArgs = embedMode ? Arrays.copyOfRange(args, 1, args.length) : args;
        Map<String, String> opts = parseArgs(trimmedArgs);
        String modelPath = opts.get("model");
        if (modelPath == null) {
            System.err.println("--model is required");
            System.exit(1);
        }
        if (embedMode && trimmedArgs.length == 0) {
            System.err.println("Usage: java -jar ... embed --model <path> " +
                    "--prompt <text>");
            System.exit(1);
        }
        String prompt = opts.getOrDefault("prompt", embedMode ? "" : "Hello");
        int ctx = Integer.parseInt(opts.getOrDefault("ctx", "512"));
        int threads = Integer.parseInt(opts.getOrDefault("threads", String.valueOf(Runtime.getRuntime().availableProcessors())));
        int maxTokens = Integer.parseInt(opts.getOrDefault("maxTokens", "32"));
        float temp = Float.parseFloat(opts.getOrDefault("temp", "0.8"));
        float topP = Float.parseFloat(opts.getOrDefault("topP", "0.95"));
        int topK = Integer.parseInt(opts.getOrDefault("topK", "40"));
        int seed = Integer.parseInt(opts.getOrDefault("seed", "42"));
        String grammar = opts.get("grammar");

        SamplerParams params = new SamplerParams(temp, topP, topK, 1.1f, seed, maxTokens, grammar);
        try (Model model = new Model(modelPath)) {
            if (embedMode) {
                try (Context context = new Context(model, ctx, threads)) {
                    Embeddings embeddings = context.createEmbeddings();
                    float[] vector = embeddings.embed(prompt);
                    System.out.println("Embeddings (dim=" + vector.length + "):" );
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < vector.length; i++) {
                        builder.append(String.format("%.4f", vector[i]));
                        if (i < vector.length - 1) {
                            builder.append(", ");
                        }
                    }
                    System.out.println(builder);
                }
            } else {
                try (ChatSession session = new ChatSession(model, params, ctx, threads)) {
                    System.out.println("Prompt: " + prompt);
                    System.out.print("Response: ");
                    CancellationToken token = new CancellationToken();
                    session.stream(prompt, text -> {
                        System.out.print(text);
                        System.out.flush();
                    }, token);
                    System.out.println();
                    InferenceStats stats = session.getLastStats();
                    if (stats != null) {
                        System.out.printf("first_token=%.2fms tokens_per_sec=%.2f total=%.2fms emitted=%d%n",
                                stats.firstTokenMs(), stats.tokensPerSecond(), stats.totalMs(), stats.tokensEmitted());
                    }
                }
            }
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                String key = arg.substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    opts.put(key, args[i + 1]);
                    i++;
                }
            }
        }
        return opts;
    }
}
