plugins {
    alias(baseLibs.plugins.composeMultiplatform) apply false
    alias(baseLibs.plugins.composeCompiler) apply false
    alias(baseLibs.plugins.kotlinMultiplatform) apply false
    alias(baseLibs.plugins.kotlinSerialization) apply false
    alias(baseLibs.plugins.aboutLibraries) apply false
    alias(libs.plugins.nucleus) apply false
}

// Resolves every resolvable configuration across all projects (buildscript + project).
// Used to force Gradle to download every dependency so `--write-verification-metadata`
// captures host-independent checksums. Mirrors the approach used by the JetBrains/kotlin repo.
tasks.register("resolveDependencies") {
    doLast {
        rootProject.allprojects.forEach { p ->
            (p.buildscript.configurations + p.configurations)
                .filter { it.isCanBeResolved }
                .forEach { c ->
                    runCatching {
                        c.incoming.artifactView { isLenient = true }.files.files
                    }
                }
        }
    }
}
