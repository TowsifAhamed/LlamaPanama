package io.llamapanama.core;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

final class NativeBindings {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP;

    static final MemoryLayout INT = ValueLayout.JAVA_INT;
    static final MemoryLayout ADDRESS = ValueLayout.ADDRESS;

    private static final MethodHandle BACKEND_INIT;
    private static final MethodHandle MODEL_LOAD;
    private static final MethodHandle CONTEXT_CREATE;
    private static final MethodHandle TOKENIZE;
    private static final MethodHandle EVAL;
    private static final MethodHandle SAMPLE;
    private static final MethodHandle SAMPLE_EX;
    private static final MethodHandle TOKEN_TO_PIECE;
    private static final MethodHandle EMBEDDINGS_DIM;
    private static final MethodHandle GET_EMBEDDINGS;
    private static final MethodHandle GET_LAST_STATS;
    private static final MethodHandle FREE_MODEL;
    private static final MethodHandle FREE_CONTEXT;
    private static final MethodHandle LAST_ERROR;
    private static final MemoryLayout STATS_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_DOUBLE.withName("first_token_ms"),
            ValueLayout.JAVA_DOUBLE.withName("tokens_per_sec"),
            ValueLayout.JAVA_DOUBLE.withName("total_ms"),
            ValueLayout.JAVA_INT.withName("tokens_emitted"),
            MemoryLayout.paddingLayout(4)
    );
    private static final long OFFSET_FIRST = STATS_LAYOUT.byteOffset(PathElement.groupElement("first_token_ms"));
    private static final long OFFSET_TPS = STATS_LAYOUT.byteOffset(PathElement.groupElement("tokens_per_sec"));
    private static final long OFFSET_TOTAL = STATS_LAYOUT.byteOffset(PathElement.groupElement("total_ms"));
    private static final long OFFSET_EMITTED = STATS_LAYOUT.byteOffset(PathElement.groupElement("tokens_emitted"));

    static {
        Path path = NativeLibraryLoader.ensureLoaded();
        LOOKUP = SymbolLookup.libraryLookup(path, Arena.global());
        BACKEND_INIT = downcall("lp_backend_init", FunctionDescriptor.of(ValueLayout.JAVA_INT));
        MODEL_LOAD = downcall("lp_model_load", FunctionDescriptor.of(ADDRESS, ADDRESS, ValueLayout.JAVA_INT, ADDRESS));
        CONTEXT_CREATE = downcall("lp_context_create", FunctionDescriptor.of(ADDRESS, ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ADDRESS));
        TOKENIZE = downcall("lp_tokenize", FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ADDRESS, ValueLayout.JAVA_INT, ADDRESS, ValueLayout.JAVA_INT, ADDRESS));
        EVAL = downcall("lp_eval", FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ADDRESS, ValueLayout.JAVA_INT, ADDRESS));
        SAMPLE = downcall("lp_sample", FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT, ADDRESS));
        SAMPLE_EX = downcall("lp_sample_ex", FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        TOKEN_TO_PIECE = downcall("lp_token_to_piece", FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ValueLayout.JAVA_INT, ADDRESS, ValueLayout.JAVA_INT, ADDRESS));
        EMBEDDINGS_DIM = downcall("lp_embeddings_dim", FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ADDRESS));
        GET_EMBEDDINGS = downcall("lp_get_embeddings", FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ValueLayout.JAVA_INT, ADDRESS));
        FREE_MODEL = downcall("lp_free_model", FunctionDescriptor.ofVoid(ADDRESS));
        FREE_CONTEXT = downcall("lp_free_context", FunctionDescriptor.ofVoid(ADDRESS));
        LAST_ERROR = downcall("lp_last_error", FunctionDescriptor.of(ADDRESS));
        GET_LAST_STATS = downcall("lp_get_last_stats", FunctionDescriptor.of(ValueLayout.JAVA_INT, ADDRESS, STATS_LAYOUT, ADDRESS));
    }

    private static MethodHandle downcall(String symbol, FunctionDescriptor descriptor) {
        try {
            MemorySegment addr = LOOKUP.find(symbol).orElseThrow();
            return LINKER.downcallHandle(addr, descriptor);
        } catch (Throwable t) {
            throw new ExceptionInInitializerError("Unable to link symbol " + symbol + ": " + t.getMessage());
        }
    }

    static void checkError(MemorySegment errOut) {
        int code = errOut.get(ValueLayout.JAVA_INT, 0);
        if (code != 0) {
            MemorySegment errPtr;
            try {
                errPtr = (MemorySegment) LAST_ERROR.invoke();
            } catch (Throwable t) {
                throw new IllegalStateException("Native error but could not fetch message", t);
            }
            String message = errPtr != null ? errPtr.reinterpret(Long.MAX_VALUE).getUtf8String(0) : "unknown";
            throw new IllegalStateException("Native error (" + code + "): " + message);
        }
    }

    static void backendInit() {
        try {
            BACKEND_INIT.invoke();
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to init backend", t);
        }
    }

    static MemorySegment loadModel(String path, SegmentAllocator allocator) {
        MemorySegment errOut = allocator.allocate(ValueLayout.JAVA_INT);
        MemorySegment cPath = allocator.allocateUtf8String(path);
        MemorySegment result;
        try {
            result = (MemorySegment) MODEL_LOAD.invoke(cPath, 0, errOut);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to load model", t);
        }
        checkError(errOut);
        return result;
    }

    static MemorySegment createContext(MemorySegment model, int ctx, int threads, SegmentAllocator allocator) {
        MemorySegment errOut = allocator.allocate(ValueLayout.JAVA_INT);
        MemorySegment result;
        try {
            result = (MemorySegment) CONTEXT_CREATE.invoke(model, ctx, threads, errOut);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to create context", t);
        }
        checkError(errOut);
        return result;
    }

    static int tokenize(MemorySegment model, String text, boolean addBos, MemorySegment outTokens, int maxTokens, SegmentAllocator allocator) {
        MemorySegment errOut = allocator.allocate(ValueLayout.JAVA_INT);
        MemorySegment cText = allocator.allocateUtf8String(text);
        int count;
        try {
            count = (int) TOKENIZE.invoke(model, cText, addBos ? 1 : 0, outTokens, maxTokens, errOut);
        } catch (Throwable t) {
            throw new IllegalStateException("Tokenize failed", t);
        }
        checkError(errOut);
        return count;
    }

    static void eval(MemorySegment context, MemorySegment tokens, int nTokens, SegmentAllocator allocator) {
        MemorySegment errOut = allocator.allocate(ValueLayout.JAVA_INT);
        try {
            EVAL.invoke(context, tokens, nTokens, errOut);
        } catch (Throwable t) {
            throw new IllegalStateException("Eval failed", t);
        }
        checkError(errOut);
    }

    static int sample(MemorySegment context, SamplerParams sampler, SegmentAllocator allocator) {
        MemorySegment errOut = allocator.allocate(ValueLayout.JAVA_INT);
        int token;
        try {
            token = (int) SAMPLE.invoke(context, sampler.temperature(), sampler.topP(), sampler.topK(), sampler.repeatPenalty(), sampler.seed(), errOut);
        } catch (Throwable t) {
            throw new IllegalStateException("Sample failed", t);
        }
        checkError(errOut);
        return token;
    }

    static int sample(MemorySegment context, SamplerParams sampler, SamplerState state, SegmentAllocator allocator) {
        MemorySegment errOut = allocator.allocate(ValueLayout.JAVA_INT);
        MemorySegment grammar = sampler.grammar() == null ? MemorySegment.NULL : allocator.allocateUtf8String(sampler.grammar());
        MemorySegment pos = allocator.allocate(ValueLayout.JAVA_INT);
        pos.set(ValueLayout.JAVA_INT, 0, state.nextPosition());
        int token;
        try {
            token = (int) SAMPLE_EX.invoke(context, sampler.temperature(), sampler.topP(), sampler.topK(), sampler.repeatPenalty(), sampler.seed(), grammar, pos, errOut);
        } catch (Throwable t) {
            throw new IllegalStateException("Sample failed", t);
        }
        checkError(errOut);
        state.updatePosition(pos.get(ValueLayout.JAVA_INT, 0));
        return token;
    }

    static int tokenToPieceBytes(MemorySegment model, int token, MemorySegment buffer, int bufferLen, SegmentAllocator allocator) {
        MemorySegment errOut = allocator.allocate(ValueLayout.JAVA_INT);
        try {
            TOKEN_TO_PIECE.invoke(model, token, buffer, bufferLen, errOut);
        } catch (Throwable t) {
            throw new IllegalStateException("token_to_piece failed", t);
        }
        checkError(errOut);
        int len = 0;
        while (len < bufferLen && buffer.get(ValueLayout.JAVA_BYTE, len) != 0) {
            len++;
        }
        return len;
    }

    static String tokenToPiece(MemorySegment model, int token, SegmentAllocator allocator) {
        MemorySegment buffer = allocator.allocate(256);
        int len = tokenToPieceBytes(model, token, buffer, (int) buffer.byteSize(), allocator);
        if (len == 0) {
            return "";
        }
        return buffer.asSlice(0, len).reinterpret(Long.MAX_VALUE).getUtf8String(0);
    }

    static int embeddingsDim(MemorySegment model, SegmentAllocator allocator) {
        MemorySegment errOut = allocator.allocate(ValueLayout.JAVA_INT);
        int dim;
        try {
            dim = (int) EMBEDDINGS_DIM.invoke(model, errOut);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to fetch embeddings dim", t);
        }
        checkError(errOut);
        return dim;
    }

    static int computeEmbeddings(MemorySegment context, String text, MemorySegment out, int maxLen, SegmentAllocator allocator) {
        MemorySegment errOut = allocator.allocate(ValueLayout.JAVA_INT);
        MemorySegment cText = allocator.allocateUtf8String(text);
        int written;
        try {
            written = (int) GET_EMBEDDINGS.invoke(context, cText, out, maxLen, errOut);
        } catch (Throwable t) {
            throw new IllegalStateException("Embeddings failed", t);
        }
        checkError(errOut);
        return written;
    }

    static InferenceStats fetchStats(MemorySegment context, SegmentAllocator allocator) {
        MemorySegment errOut = allocator.allocate(ValueLayout.JAVA_INT);
        MemorySegment stats = allocator.allocate(STATS_LAYOUT);
        try {
            GET_LAST_STATS.invoke(context, stats, errOut);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to load stats", t);
        }
        checkError(errOut);
        double first = stats.get(ValueLayout.JAVA_DOUBLE, OFFSET_FIRST);
        double tps = stats.get(ValueLayout.JAVA_DOUBLE, OFFSET_TPS);
        double total = stats.get(ValueLayout.JAVA_DOUBLE, OFFSET_TOTAL);
        int emitted = stats.get(ValueLayout.JAVA_INT, OFFSET_EMITTED);
        return new InferenceStats(first, tps, total, emitted);
    }

    static void freeModel(MemorySegment model) {
        try {
            FREE_MODEL.invoke(model);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to free model", t);
        }
    }

    static void freeContext(MemorySegment ctx) {
        try {
            FREE_CONTEXT.invoke(ctx);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to free context", t);
        }
    }
}
