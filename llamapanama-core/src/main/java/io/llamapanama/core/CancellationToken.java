package io.llamapanama.core;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CancellationToken {
    private final AtomicBoolean cancelled;

    public CancellationToken() {
        this(false);
    }

    private CancellationToken(boolean initial) {
        this.cancelled = new AtomicBoolean(initial);
    }

    public static CancellationToken none() {
        return new CancellationToken(false);
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
