plugins {
    `java-library`
}

val javaVersion = providers.gradleProperty("java_version").get().toInt()

base {
    archivesName.set("stall-core")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // Gson is compileOnly on purpose: Minecraft 26.3 and Paper 26.3 both ship Gson 2.14.0, so Stall
    // never has to bundle a JSON library. Tests need a real implementation.
    compileOnly("com.google.code.gson:gson:2.14.0")
    testImplementation("com.google.code.gson:gson:2.14.0")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}

tasks.test {
    useJUnitPlatform()
}

val checkCoreBoundary = tasks.register("checkCoreBoundary") {
    group = "verification"
    description = "Fails if stall-core sources import platform APIs."

    val sources = fileTree("src/main/java") { include("**/*.java") }
    inputs.files(sources)

    doLast {
        val forbidden = listOf(
            "org.bukkit",
            "io.papermc",
            "net.kyori",
            "net.minecraft",
            "net.fabricmc",
            "com.mojang"
        )
        val violations = sources.files.flatMap { sourceFile ->
            sourceFile.readLines().mapIndexedNotNull { index, line ->
                val trimmed = line.trim()
                val hit = forbidden.firstOrNull {
                    trimmed.startsWith("import $it.") || trimmed.startsWith("import static $it.")
                }
                if (hit == null) null else "${sourceFile.relativeTo(projectDir)}:${index + 1}: $trimmed"
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "stall-core must stay platform-neutral. Forbidden imports:\n" + violations.joinToString("\n")
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkCoreBoundary)
}
