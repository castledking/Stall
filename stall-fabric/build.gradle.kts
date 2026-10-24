// The Fabric mod, for Minecraft 26.3. Everything Minecraft-specific lives here; shop rules, the trade
// planner and the menu layout come from :stall-core.
plugins {
    java
    id("net.fabricmc.fabric-loom")
}

val javaVersion = providers.gradleProperty("java_version").get().toInt()
val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val minecraftDependency = providers.gradleProperty("fabric_minecraft_dependency").get()
val loaderVersion = providers.gradleProperty("fabric_loader_version").get()
val loaderMinimum = providers.gradleProperty("fabric_loader_minimum").get()
val fabricApiVersion = providers.gradleProperty("fabric_api_version").get()

// The Fabric API modules Stall calls, compiled against one by one so no other module creeps in. Keep
// fabric.mod.json's "depends" in step. All of them ship in the regular Fabric API download.
val fabricApiModules = listOf(
    "fabric-api-base",
    "fabric-command-api-v2",
    "fabric-data-attachment-api-v1",
    "fabric-events-interaction-v0",
    "fabric-lifecycle-events-v1",
    "fabric-networking-api-v1",
    "fabric-permission-api-v1"
)

base {
    archivesName.set("Stall-Fabric")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    for (module in fabricApiModules) {
        implementation(fabricApi.module(module, fabricApiVersion))
    }
    implementation(project(":stall-core"))
    // The core jar ships inside the mod (jar-in-jar), so there is one file to install.
    include(project(":stall-core"))

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The Tracy profiler's natives are client-only and not needed to test anything here.
configurations.named("testRuntimeClasspath") {
    exclude(group = "com.mojang", module = "jtracy")
}

tasks.test {
    useJUnitPlatform()
    // Where the item codec test leaves Fabric-encoded items, for checking that Paper reads them too.
    systemProperty("stall.fixtureDir", layout.buildDirectory.dir("test-fixtures").get().asFile.absolutePath)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}

tasks.processResources {
    val properties = mapOf(
        "version" to project.version.toString(),
        "minecraftDependency" to minecraftDependency,
        "loaderMinimum" to loaderMinimum,
        "javaVersion" to javaVersion.toString()
    )
    inputs.properties(properties)
    filesMatching("fabric.mod.json") {
        expand(properties)
    }
}
