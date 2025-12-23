plugins {
    `java-library`
}

dependencies {
    api("org.slf4j:slf4j-api:2.0.13")
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

val nativeBuild = tasks.register("nativeBuild") {
    dependsOn(project(":llamapanama-native").tasks.named("cmakeBuild"))
}

sourceSets {
    val main by getting {
        resources.srcDir(project(":llamapanama-native").layout.buildDirectory.dir("cmake/artifacts"))
    }
}

tasks.processResources {
    dependsOn(nativeBuild)
}
