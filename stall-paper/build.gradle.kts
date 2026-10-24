plugins {
    java
}

val javaVersion = providers.gradleProperty("java_version").get().toInt()
val paperApiVersion = providers.gradleProperty("paper_api_version").get()
val coreProject = project(":stall-core")

base {
    archivesName.set("Stall-Paper")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
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
    // The core ships inside the plugin jar. It only uses its own package and the Gson Paper provides.
    val coreJar = coreProject.tasks.named<Jar>("jar")
    dependsOn(coreJar)
    from(coreJar.map { zipTree(it.archiveFile) }) {
        exclude("META-INF/MANIFEST.MF")
    }
    manifest {
        // Stall only uses the Paper API, so Paper has nothing to remap.
        attributes("paperweight-mappings-namespace" to "mojang")
    }
}
