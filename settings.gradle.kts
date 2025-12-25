plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "LlamaPanama"
include("llamapanama-core")
include("llamapanama-native")
include("llamapanama-examples")
