package io.llamapanama.core;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Model implements AutoCloseable {
    private static final Cleaner CLEANER = Cleaner.create();
    private final MemorySegment handle;
    private final Cleaner.Cleanable cleanable;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public Model(String path) {
        Objects.requireNonNull(path, "path");
        NativeBindings.backendInit();
        Arena arena = Arena.ofShared();
        this.handle = NativeBindings.loadModel(path, arena);
        this.cleanable = CLEANER.register(this, () -> NativeBindings.freeModel(handle));
    }

    MemorySegment handle() {
        ensureOpen();
        return handle;
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Model already closed");
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            cleanable.clean();
        }
    }
}
