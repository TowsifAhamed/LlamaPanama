package io.llamapanama.core;

@FunctionalInterface
public interface TokenListener {
    void onToken(String token);
}
