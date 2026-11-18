package com.aaronmaxlab.maxplayer.database;

import android.content.Context;
import android.content.ContextWrapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Test-only context that redirects every SQLiteOpenHelper database path into a fresh
 * temporary directory. Production databases are never opened by tests using this context.
 */
final class TestDatabaseContext extends ContextWrapper {

    private final Path root;

    TestDatabaseContext(Context base, String testName) throws IOException {
        super(base);
        String safeName = testName.replaceAll("[^A-Za-z0-9_-]", "_");
        this.root = Files.createTempDirectory("maxplayer-db-" + safeName + "-");
    }

    @Override
    public File getDatabasePath(String name) {
        return root.resolve(name).toFile();
    }

    Path root() {
        return root;
    }

    void cleanup() throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup after the helper has been closed.
                }
            });
        }
    }
}
