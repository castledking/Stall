// Stall - physical player shops for Paper and Fabric servers, by CASTLED CODEX.
//
// Module layout:
//   :stall-core    platform-neutral shop model, trade planner and menu layouts. No Paper, no Fabric,
//                  no Minecraft classes; checkCoreBoundary fails the build if any creep in.
//   :stall-paper   the Paper plugin.
//   :stall-fabric  the Fabric mod, for dedicated servers. Vanilla clients need nothing installed.
pluginManagement {
    val loomVersion = providers.gradleProperty("loom_version").get()
    plugins {
        // 26.x is unobfuscated: the non-remapping Loom plugin builds against Mojang names as-is.
        id("net.fabricmc.fabric-loom") version loomVersion
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven {
                    name = "Fabric"
                    url = uri("https://maven.fabricmc.net")
                }
            }
            filter { includeGroupAndSubgroups("net.fabricmc") }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Stall"

include("stall-core")
include("stall-paper")
include("stall-fabric")
