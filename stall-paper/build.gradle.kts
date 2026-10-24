plugins {
    java
}

val javaVersion = providers.gradleProperty("java_version").get().toInt()
val spigotApiVersion = providers.gradleProperty("spigot_api_version").get()
val coreProject = project(":stall-core")

base {
    archivesName.set("Stall-Paper")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    // Compiling against Spigot prevents accidental calls to Paper-only APIs.
    compileOnly("org.spigotmc:spigot-api:$spigotApiVersion")
    implementation(project(":stall-core"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}

tasks.jar {
    // Keep the existing artifact name; this one plugin jar works on Spigot and Paper.
    // The core uses the Gson both servers provide.
    val coreJar = coreProject.tasks.named<Jar>("jar")
    dependsOn(coreJar)
    from(coreJar.map { zipTree(it.archiveFile) }) {
        exclude("META-INF/MANIFEST.MF")
    }
    manifest {
        // The native item codec uses Minecraft's unobfuscated 26.3 names.
        attributes("paperweight-mappings-namespace" to "mojang")
    }
}

// Optional checks that need a running server. These artifacts are never release artifacts.
val serverTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
    runtimeClasspath += output + compileClasspath
}

val serverTestJar = tasks.register<Jar>("serverTestJar") {
    group = "verification"
    description = "Builds Stall with checks callable by the isolated server-test probe."
    dependsOn(tasks.jar, tasks.named(serverTest.classesTaskName))
    archiveClassifier.set("server-tests")
    from(tasks.jar.map { zipTree(it.archiveFile) })
    from(serverTest.output) {
        exclude("**/ServerTestProbe.class", "probe-plugin.yml")
    }
    manifest { attributes("paperweight-mappings-namespace" to "mojang") }
}

val serverTestProbeJar = tasks.register<Jar>("serverTestProbeJar") {
    group = "verification"
    dependsOn(tasks.named(serverTest.classesTaskName))
    archiveBaseName.set("Stall-Server-Test-Probe")
    from(serverTest.output) {
        include("**/ServerTestProbe.class", "probe-plugin.yml")
        rename("probe-plugin.yml", "plugin.yml")
    }
}

tasks.register("serverTestArtifacts") {
    group = "verification"
    dependsOn(serverTestJar, serverTestProbeJar)
}
