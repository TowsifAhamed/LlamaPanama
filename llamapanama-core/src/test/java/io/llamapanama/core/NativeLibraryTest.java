package io.llamapanama.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeLibraryTest {

    @Test
    void loadsLibrary() {
        assertDoesNotThrow(NativeBindings::backendInit);
    }
}
