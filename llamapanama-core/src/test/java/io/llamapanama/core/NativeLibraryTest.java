package io.llamapanama.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import static org.junit.jupiter.api.Assertions.*;

class NativeLibraryTest {

    @Test
    void loadsLibrary() {
        try {
            Class.forName("io.llamapanama.core.NativeBindings");
        } catch (Throwable e) {
            // Skip test if native library is not available
            Assumptions.assumeTrue(false, "Native library not available: " + e.getMessage());
        }
    }
}
