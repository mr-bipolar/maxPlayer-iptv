# Testing MaxPlayer

All local tests are JUnit tests under [`app/src/test/java`](../app/src/test/java). Database tests use Robolectric; the playlist parser test is a host JVM test. Instrumented tests live under [`app/src/androidTest/java`](../app/src/androidTest/java) and need a device or emulator.

Use the Gradle wrapper from the repository root. On Windows, replace `./gradlew` with `gradlew.bat`.

## 1. Database tests

```bash
./gradlew :app:testDebugUnitTest --tests 'com.aaronmaxlab.maxplayer.database.*'
```

This runs every test class in the `com.aaronmaxlab.maxplayer.database` package:

- `SqlDbHelperSchemaTest` — table names, columns, primary keys, unique indexes, defaults, and seeded categories.
- `SqlDbHelperPlaylistChannelTest` — playlist/channel CRUD, duplicate handling, URL normalization, category counts, and propagation to favorites/history.
- `SqlDbHelperMediaListsTest` — favorites, history, and recent-view behavior.
- `SqlDbHelperSearchPersistenceTest` — search filtering and limits, persistence after reopening the helper, and database path redirection.

The database tests use Robolectric with `@Config(sdk = 28)`. That is the test simulation configuration, not a claim that Android 9 is the minimum supported platform.

**Verification:** this command was executed successfully on 2026-11-19. The XML reports recorded 36 tests, 0 failures, 0 errors, and 0 skipped tests.

## 2. Playlist parser and parser security tests

The repository currently has one playlist parser test class, `PlaylistParserSecurityTest`; it covers parsing behavior as well as security-focused validation. Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.aaronmaxlab.maxplayer.playlist.PlaylistParserSecurityTest'
```

`PlaylistParserSecurityTest` verifies:

- HTTP/HTTPS canonicalization and IDN handling.
- Rejection of unsafe URLs and special-purpose IP ranges.
- DNS pinning and prohibited DNS answers.
- Byte and line limits, maximum playlist entry count, and request-header validation.
- M3U/HLS parsing behavior and redirect resolution.
- Absence of the old unvalidated remote metadata helper.

There is no separate non-security parser test class in this repository at the time of writing.

**Verification:** this command was executed successfully on 2026-11-19. The XML report recorded 23 tests, 0 failures, 0 errors, and 0 skipped tests.

## 3. Complete local unit test suite

```bash
./gradlew :app:test
```

This runs all local unit tests for all configured test variants. In this project, the available unit-test task is `testDebugUnitTest`, and `test` delegates to it.

**Verification:** this command was executed successfully on 2026-11-19. The reports recorded 60 tests in total, with 0 failures, 0 errors, and 0 skipped tests.

If you only need the debug variant explicitly, this is equivalent for the current configuration:

```bash
./gradlew :app:testDebugUnitTest
```

## 4. Instrumented tests

```bash
./gradlew :app:connectedDebugAndroidTest
```

This requires a connected device or running emulator. The current instrumented test is:

- `ExampleInstrumentedTest.mpvEngineInitializesAndReleases` — creates, initializes, and releases `MpvPlayer` from the bundled engine.

This command was **not** executed during documentation verification because no device/emulator session was started. Do not report it as passing unless you run it yourself.

## 5. Test reports

After a test run:

- HTML report: `app/build/reports/tests/testDebugUnitTest/index.html`
- XML results: `app/build/test-results/testDebugUnitTest/`
- Problems report: `build/reports/problems/problems-report.html`

## 6. Recommended pre-pull-request checks

```bash
./gradlew :app:test
./gradlew :app:assembleDebug
```

Add the relevant filtered command when iterating on database or playlist parser changes. See [CONTRIBUTING.md](../CONTRIBUTING.md) for the contribution workflow.
