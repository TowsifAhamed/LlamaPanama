package io.llamapanama.core;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Cleaner;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Context implements AutoCloseable {
    private static final Cleaner CLEANER = Cleaner.create();
    private final MemorySegment handle;
    private final Model model;
    private final Cleaner.Cleanable cleanable;
    private final int maxContextTokens;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile InferenceStats lastStats;

    public Context(Model model, int ctx, int threads) {
        this.model = Objects.requireNonNull(model, "model");
        this.maxContextTokens = ctx;
        Arena arena = Arena.ofShared();
        this.handle = NativeBindings.createContext(model.handle(), ctx, threads, arena);
        this.cleanable = CLEANER.register(this, () -> NativeBindings.freeContext(handle));
    }

    int[] tokenize(String text, boolean addBos) {
        ensureOpen();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocateArray(ValueLayout.JAVA_INT, maxContextTokens);
            int count = NativeBindings.tokenize(model.handle(), text, addBos, buffer, maxContextTokens, arena);
            int[] tokens = new int[count];
            for (int i = 0; i < count; i++) {
                tokens[i] = buffer.getAtIndex(ValueLayout.JAVA_INT, i);
            }
            return tokens;
        }
    }

    void eval(int[] tokens) {
        ensureOpen();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeTokens = arena.allocateArray(ValueLayout.JAVA_INT, tokens.length);
            for (int i = 0; i < tokens.length; i++) {
                nativeTokens.setAtIndex(ValueLayout.JAVA_INT, i, tokens[i]);
            }
            NativeBindings.eval(handle, nativeTokens, tokens.length, arena);
            lastStats = null;
        }
    }

    int sample(SamplerParams params, SamplerState state) {
        ensureOpen();
        try (Arena arena = Arena.ofConfined()) {
            return NativeBindings.sample(handle, params, state, arena);
        }
    }

    String tokenToPiece(int token) {
        ensureOpen();
        try (Arena arena = Arena.ofConfined()) {
            return NativeBindings.tokenToPiece(model.handle(), token, arena);
        }
    }

    int tokenToPieceBytes(int token, byte[] buffer) {
        ensureOpen();
        Objects.requireNonNull(buffer, "buffer");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeBuffer = arena.allocate(buffer.length);
            int len = NativeBindings.tokenToPieceBytes(model.handle(), token, nativeBuffer, buffer.length, arena);
            MemorySegment heapBuffer = MemorySegment.ofArray(buffer);
            MemorySegment.copy(nativeBuffer, 0, heapBuffer, 0, len);
            return len;
        }
    }

    public Embeddings createEmbeddings() {
        ensureOpen();
        int dim;
        try (Arena arena = Arena.ofConfined()) {
            dim = NativeBindings.embeddingsDim(model.handle(), arena);
        }
        return new Embeddings() {
            private final ThreadLocal<float[]> buffers = ThreadLocal.withInitial(() -> new float[dim]);
            private final Arena nativeArena = Arena.ofShared();
            private final MemorySegment nativeBuffer = nativeArena.allocateArray(ValueLayout.JAVA_FLOAT, dim);
            private final Cleaner.Cleanable cleanable = CLEANER.register(this, nativeArena::close);

            @Override
            public synchronized float[] embed(String text) {
                Objects.requireNonNull(text, "text");
                ensureOpen();
                try (Arena arena = Arena.ofConfined()) {
                    int written = NativeBindings.computeEmbeddings(handle, text, nativeBuffer, dim, arena);
                    float[] buffer = buffers.get();
                    for (int i = 0; i < written; i++) {
                        buffer[i] = nativeBuffer.getAtIndex(ValueLayout.JAVA_FLOAT, i);
                    }
                    if (written < buffer.length) {
                        for (int i = written; i < buffer.length; i++) {
                            buffer[i] = 0f;
                        }
                    }
                    return buffer;
                }
            }
        };
    }

    public InferenceStats getLastStats() {
        ensureOpen();
        InferenceStats stats = lastStats;
        if (stats != null) {
            return stats;
        }
        try (Arena arena = Arena.ofConfined()) {
            lastStats = NativeBindings.fetchStats(handle, arena);
            return lastStats;
        }
    }

    public SamplerState newSamplerState(SamplerParams params) {
        return new SamplerState(params.seed());
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Context already closed");
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            cleanable.clean();
        }
    }
}
