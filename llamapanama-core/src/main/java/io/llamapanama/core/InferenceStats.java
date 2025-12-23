package io.llamapanama.core;

public final class InferenceStats {
    private final double firstTokenMs;
    private final double tokensPerSecond;
    private final double totalMs;
    private final int tokensEmitted;

    public InferenceStats(double firstTokenMs, double tokensPerSecond, double totalMs, int tokensEmitted) {
        this.firstTokenMs = firstTokenMs;
        this.tokensPerSecond = tokensPerSecond;
        this.totalMs = totalMs;
        this.tokensEmitted = tokensEmitted;
    }

    public double firstTokenMs() {
        return firstTokenMs;
    }

    public double tokensPerSecond() {
        return tokensPerSecond;
    }

    public double totalMs() {
        return totalMs;
    }

    public int tokensEmitted() {
        return tokensEmitted;
    }
}
