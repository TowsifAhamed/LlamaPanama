package io.llamapanama.core;

import java.util.concurrent.atomic.AtomicInteger;

public final class SamplerState {
    private final AtomicInteger position;
    private final int seed;

    public SamplerState(int seed) {
        this.seed = seed;
        this.position = new AtomicInteger(0);
    }

    int nextPosition() {
        return position.get();
    }

    void updatePosition(int value) {
        position.set(value);
    }

    public void reset() {
        position.set(0);
    }

    public int seed() {
        return seed;
    }
}
