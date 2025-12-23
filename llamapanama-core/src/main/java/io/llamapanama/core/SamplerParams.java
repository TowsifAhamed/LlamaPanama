package io.llamapanama.core;

public record SamplerParams(
        float temperature,
        float topP,
        int topK,
        float repeatPenalty,
        int seed,
        int maxTokens,
        String grammar
) {
    public static SamplerParams defaults() {
        return new SamplerParams(0.8f, 0.95f, 40, 1.1f, 42, 128, null);
    }

    public SamplerParams {
        grammar = (grammar == null || grammar.isBlank()) ? null : grammar;
    }

    public SamplerParams withGrammar(String grammar) {
        return new SamplerParams(temperature, topP, topK, repeatPenalty, seed, maxTokens, grammar);
    }

    public SamplerParams withSeed(int seed) {
        return new SamplerParams(temperature, topP, topK, repeatPenalty, seed, maxTokens, grammar);
    }
}
