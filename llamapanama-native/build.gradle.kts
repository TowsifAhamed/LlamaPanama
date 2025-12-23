plugins {
    base
}

val buildType = providers.environmentVariable("CMAKE_BUILD_TYPE").orElse("Release")
val cmakeDir = layout.buildDirectory.dir("cmake")

val cmakeConfigure by tasks.registering(Exec::class) {
    commandLine("cmake", "-S", project.projectDir, "-B", cmakeDir.get().asFile, "-DCMAKE_BUILD_TYPE=${'$'}buildType")
}

val cmakeBuild by tasks.registering(Exec::class) {
    dependsOn(cmakeConfigure)
    commandLine("cmake", "--build", cmakeDir.get().asFile, "--config", buildType.get())
}

tasks.assemble {
    dependsOn(cmakeBuild)
}
