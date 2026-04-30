package com.mikepenz.agentbelay.testutil

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension

/**
 * Loader for harness payload fixtures under `resources/harnesses/<harness>/`.
 *
 * Fixtures are real-shape JSON captured from (or modelled on) the harness's
 * actual hook payload. The first property is a `_fixture` metadata object
 * documenting source / version / scenario; adapters tolerate it because they
 * all decode with `ignoreUnknownKeys = true`.
 *
 * Tests that don't need to enumerate fixtures should use [read] with a
 * literal path. Tests that want to apply a check to *every* fixture for a
 * harness (parameterized round-trip) should use [listFor].
 */
object GoldenPayloads {

    /**
     * Loads a fixture by relative path under `resources/harnesses/`. Throws
     * [IllegalStateException] with a clear message if the path is missing —
     * gives loud failures when fixtures are renamed without updating callers.
     */
    fun read(path: String): String {
        val url = checkNotNull(GoldenPayloads::class.java.getResource("/harnesses/$path")) {
            "Missing fixture: harnesses/$path"
        }
        return url.readText()
    }

    /**
     * Lists all fixture paths for a harness, e.g. `listFor("claudecode")` →
     * `["claudecode/permission_request_bash_ls.json", …]`. Returned paths are
     * already in the form [read] accepts.
     *
     * Resolves the resource directory via the URL of an arbitrary classpath
     * marker so it works whether tests run from `build/resources` or a jar.
     */
    fun listFor(harness: String): List<String> {
        val markerPath = "/harnesses/$harness"
        val url = checkNotNull(GoldenPayloads::class.java.getResource(markerPath)) {
            "Missing fixture directory: harnesses/$harness"
        }
        // Only file-protocol URLs are supported. If we ever package fixtures
        // into a jar this needs to switch to a JarFile walk.
        require(url.protocol == "file") {
            "GoldenPayloads.listFor only supports filesystem resources (got ${url.protocol})"
        }
        val dir: Path = Paths.get(url.toURI())
        return Files.list(dir).use { stream ->
            stream
                .filter { it.extension == "json" }
                .map { "$harness/${it.fileName}" }
                .sorted()
                .toList()
        }
    }
}
