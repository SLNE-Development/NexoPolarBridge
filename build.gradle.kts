group = "dev.slne.surf.polarnexobridge"
version = "1.0.0-SNAPSHOT"

plugins {
    kotlin("jvm") version "2.0.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

kotlin { jvmToolchain(21) }

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.nexomc.com/releases")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    compileOnly("com.nexomc:nexo:1.15.0")

    implementation(kotlin("stdlib"))
}


tasks {
    shadowJar { archiveClassifier.set("") }
    build { dependsOn(shadowJar) }
}

tasks.withType<ProcessResources>().configureEach {
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(
            mapOf(
                "version" to project.version.toString(),
            )
        )
    }
}