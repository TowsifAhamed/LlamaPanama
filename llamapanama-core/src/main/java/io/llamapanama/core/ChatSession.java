package io.llamapanama.core;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChatSession implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ChatSession.class);
    private static final int STREAM_BATCH_CHARS = 32;
    private static final int TOKEN_PIECE_BYTES = 512;
    private static final int UTF8_BUFFER_BYTES = 4096;
    private final Context context;
    private final SamplerParams sampler;
    private final SamplerState samplerState;
    private volatile InferenceStats lastStats;

    public ChatSession(Model model, SamplerParams sampler, int ctxTokens, int threads) {
        Objects.requireNonNull(model, "model");
        this.sampler = sampler == null ? SamplerParams.defaults() : sampler;
        this.context = new Context(model, ctxTokens, threads);
        this.samplerState = context.newSamplerState(this.sampler);
    }

    public String generate(String prompt) {
        StringBuilder builder = new StringBuilder();
        stream(prompt, builder::append, CancellationToken.none());
        return builder.toString();
    }

    public void stream(String prompt, TokenListener listener) {
        stream(prompt, listener, CancellationToken.none());
    }

    public void stream(String prompt, TokenListener listener, CancellationToken token) {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(token, "token");
        samplerState.reset();
        long start = System.nanoTime();
        int[] tokens = context.tokenize(prompt, true);
        context.eval(tokens);
        int produced = 0;
        StringBuilder batch = new StringBuilder();
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        byte[] pieceBuffer = new byte[TOKEN_PIECE_BYTES];
        byte[] pendingBytes = new byte[UTF8_BUFFER_BYTES];
        int[] pendingLenHolder = new int[]{0};
        while (produced < sampler.maxTokens() && !token.isCancelled()) {
            int tokenId = context.sample(sampler, samplerState);
            if (tokenId == 0) {
                break;
            }
            int pieceLen = context.tokenToPieceBytes(tokenId, pieceBuffer);
            if (pieceLen > 0) {
                pendingBytes = appendBytes(pendingBytes, pendingLenHolder[0], pieceBuffer, pieceLen);
                pendingLenHolder[0] += pieceLen;
                String decoded = decodeAvailable(decoder, pendingBytes, pendingLenHolder);
                if (!decoded.isEmpty()) {
                    batch.append(decoded);
                    if (shouldFlushBatch(batch, decoded, produced)) {
                        listener.onToken(batch.toString());
                        batch.setLength(0);
                    }
                }
            }
            produced++;
        }
        String tail = flushDecoder(decoder, pendingBytes, pendingLenHolder[0]);
        if (!tail.isEmpty()) {
            batch.append(tail);
        }
        if (batch.length() > 0) {
            listener.onToken(batch.toString());
        }
        long end = System.nanoTime();
        lastStats = context.getLastStats();
        LOG.info("first_token_ms={} tokens_per_sec={} total_ms={} emitted={} wall_ms={}",
                lastStats.firstTokenMs(), lastStats.tokensPerSecond(), lastStats.totalMs(),
                lastStats.tokensEmitted(), (end - start) / 1_000_000.0);
    }

    public InferenceStats getLastStats() {
        return lastStats;
    }

    private boolean shouldFlushBatch(StringBuilder batch, String lastPiece, int produced) {
        if (batch.length() >= STREAM_BATCH_CHARS) {
            return true;
        }
        if (produced == 0) {
            return false;
        }
        if (!lastPiece.isEmpty()) {
            char last = lastPiece.charAt(lastPiece.length() - 1);
            return last == '.' || last == '!' || last == '?' || last == '\n';
        }
        return false;
    }

    private static byte[] appendBytes(byte[] target, int targetLen, byte[] source, int sourceLen) {
        int needed = targetLen + sourceLen;
        if (needed > target.length) {
            int newSize = Math.max(needed, target.length * 2);
            byte[] resized = new byte[newSize];
            System.arraycopy(target, 0, resized, 0, targetLen);
            target = resized;
        }
        System.arraycopy(source, 0, target, targetLen, sourceLen);
        return target;
    }

    private static String decodeAvailable(CharsetDecoder decoder, byte[] pending, int[] pendingLenHolder) {
        if (pendingLenHolder[0] == 0) {
            return "";
        }
        ByteBuffer byteBuffer = ByteBuffer.wrap(pending, 0, pendingLenHolder[0]);
        CharBuffer charBuffer = CharBuffer.allocate(1024);
        StringBuilder decoded = new StringBuilder();
        while (true) {
            CoderResult result = decoder.decode(byteBuffer, charBuffer, false);
            charBuffer.flip();
            if (charBuffer.hasRemaining()) {
                decoded.append(charBuffer);
            }
            charBuffer.clear();
            if (result.isOverflow()) {
                continue;
            }
            break;
        }
        int remaining = byteBuffer.remaining();
        if (remaining > 0) {
            System.arraycopy(pending, pendingLenHolder[0] - remaining, pending, 0, remaining);
        }
        pendingLenHolder[0] = remaining;
        return decoded.toString();
    }

    private static String flushDecoder(CharsetDecoder decoder, byte[] pending, int pendingLen) {
        if (pendingLen == 0) {
            return "";
        }
        ByteBuffer byteBuffer = ByteBuffer.wrap(pending, 0, pendingLen);
        CharBuffer charBuffer = CharBuffer.allocate(1024);
        StringBuilder decoded = new StringBuilder();
        CoderResult result = decoder.decode(byteBuffer, charBuffer, true);
        while (result.isOverflow()) {
            charBuffer.flip();
            decoded.append(charBuffer);
            charBuffer.clear();
            result = decoder.decode(byteBuffer, charBuffer, true);
        }
        decoder.flush(charBuffer);
        charBuffer.flip();
        if (charBuffer.hasRemaining()) {
            decoded.append(charBuffer);
        }
        return decoded.toString();
    }

    @Override
    public void close() {
        context.close();
    }
}
