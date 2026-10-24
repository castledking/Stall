// Root build file. Every module configures itself; this only shares the project coordinates.
subprojects {
    group = rootProject.property("group") as String
    version = rootProject.property("version") as String
}
