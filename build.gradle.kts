plugins {
    java
}

group = "dev.cadu"
// Overridden in CI from the release tag: ./gradlew build -PpluginVersion=0.1.0
version = providers.gradleProperty("pluginVersion").getOrElse("dev-SNAPSHOT")

tasks.jar {
    archiveFileName.set("minecraft-villagerdiscount-v${project.version}.jar")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
}

tasks.processResources {
    val props = mapOf("version" to version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release.set(25)
}
