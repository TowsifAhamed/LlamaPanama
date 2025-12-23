# LlamaPanama

Run GGUF-style LLMs from Java 21 using Project Panama Foreign Function & Memory API with a lightweight shim over `llama.cpp`. This repository provides a Gradle multi-module build with:

- **llamapanama-native**: minimal C shim and vendoring hook for `llama.cpp`.
- **llamapanama-core**: Java 21 bindings and high-level API similar to LlamaSharp.
- **llamapanama-examples**: runnable CLI using the bindings.

## Quickstart

```bash
./gradlew shadowJar
java -jar llamapanama-examples/build/libs/llamapanama-examples-all.jar \
  --model /path/to/model.gguf \
  --prompt "Hello" \
  --ctx 512 --threads 4 --maxTokens 16 --temp 0.8 --topP 0.95 --topK 40
```

### Streaming example

```bash
java -jar llamapanama-examples/build/libs/llamapanama-examples-all.jar \
  --model /path/to/model.gguf \
  --prompt "Explain Panama bindings" \
  --maxTokens 64
```

### Embeddings example

```bash
java -jar llamapanama-examples/build/libs/llamapanama-examples-all.jar embed \
  --model /path/to/model.gguf \
  --prompt "hello world"
```

The native library is built via CMake and copied into the core module resources automatically during `processResources`.

## Supported platforms

- Linux x86_64 (default CI target)
- macOS x86_64/aarch64
- Windows x86_64

Artifacts are produced as:
- `libllamapanama.so`
- `libllamapanama.dylib`
- `llamapanama.dll`

## Native loading

`NativeLibraryLoader` detects OS/architecture, extracts the correct shared library from packaged resources (built under `llamapanama-native/build/cmake/artifacts/<os>-<arch>`), writes it to a temporary directory, and loads it once per JVM.

## Why Panama?

Project Panama (FFM API) avoids JNI/JNA boilerplate and enables safer, faster native interop in modern Java. This repository intentionally uses only the Java 21 FFM API—no JNI/JNA fallbacks yet—to keep the surface small and type-safe while demonstrating end-to-end LLM inference plumbing.

### Why LlamaPanama vs Jlama vs other wrappers?

- **Panama-first**: zero JNI/JNA glue, leaning on Java 21 FFM for performance and safety.
- **Stable shim**: small C ABI surface instead of binding directly to C++ symbols.
- **Deterministic streaming**: batching, cancellation, and instrumentation for testing.
- **Embeddings + grammar hook**: available from Java with minimal copying and reusable buffers.

## Troubleshooting

- Ensure a C compiler and CMake are available on your build machine.
- On macOS, install Xcode command line tools and `libc++` if missing.
- On Linux, CPUs without AVX may not run optimized llama.cpp builds; rebuild with appropriate flags.
- Windows builds rely on the Visual Studio toolchain provided by GitHub Actions.

## Roadmap

- JNI/JNA fallback for Java 17 runtimes.
- Full llama.cpp vendoring with the pinned commit from `LLAMA_CPP_COMMIT`.
- JSON/grammar guided generation.
- Richer sampling strategies (Mirostat, penalties) with reusable sampler state.
- Extended metrics and profiling helpers.
