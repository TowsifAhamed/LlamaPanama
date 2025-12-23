plugins {
    id("base")
}

subprojects {
    repositories {
        mavenCentral()
    }

    afterEvaluate {
        if (plugins.hasPlugin("java")) {
            configure<JavaPluginExtension> {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(21))
                }
            }
            tasks.withType<JavaCompile>().configureEach {
                options.encoding = "UTF-8"
                options.compilerArgs.add("--enable-preview")
            }
            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
                jvmArgs("--enable-preview")
            }
            tasks.withType<JavaExec>().configureEach {
                jvmArgs("--enable-preview")
            }
        }
    }
}
