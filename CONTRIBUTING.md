# Contributing to MaxPlayer

Thanks for helping improve MaxPlayer. This document describes the workflow and standards to use when preparing a change.

## Before you start

- Read the [README](README.md), [building guide](docs/BUILDING.md), [testing guide](docs/TESTING.md), and [platform support notes](docs/PLATFORM_SUPPORT.md).
- Search existing issues and pull requests before starting a large change.
- For significant features or architectural changes, open an issue first so the approach can be discussed.
- MaxPlayer does not bundle or provide TV channels, playlists, or copyrighted media. Do not include such content in a contribution.

## Fork and clone

```bash
git clone https://github.com/<your-account>/maxPlayer-iptv.git
cd maxPlayer-iptv
git remote add upstream https://github.com/mr-bipolar/maxPlayer-iptv.git
```

The default branch in this repository is `main`.

## Create a branch

There is no documented branch-naming convention in the repository. The following is a **recommendation**, not an existing rule:

```bash
git switch -c feature/<short-topic>
git switch -c fix/<short-topic>
git switch -c docs/<short-topic>
```

Keep each branch and pull request focused on one concern. Avoid mixing a bug fix with unrelated formatting, dependency upgrades, AAR replacement, or UI redesigns.

## Coding conventions

No formatter configuration or written style guide is checked in. Follow the conventions of the surrounding code:

- Java source is compiled with source/target compatibility Java 11.
- Use four spaces for indentation in Java and XML; the checked-in text sources do not use tabs.
- Keep package declarations and imports in the existing order, and preserve the existing class/method naming style.
- Prefer existing string, color, dimension, and style resources over hard-coded UI values.
- Use view binding as configured in [`app/build.gradle.kts`](app/build.gradle.kts).
- Keep resource names in the existing `lower_snake_case` style.
- Do not reformat whole files as part of a focused change.
- Avoid adding dependencies unless the change requires them. Dependency versions are centralized in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
- Do not replace `app/libs/mpvengine-release.aar`, `app/libs/mpvengine-debug.aar`, Gradle files, or native artifacts unless the change is specifically about that integration and its provenance/licensing is understood.

## Test your change

See [TESTING.md](docs/TESTING.md) for exact commands. At minimum:

```bash
./gradlew :app:test
./gradlew :app:assembleDebug
```

Run the focused database or playlist parser command when changing those areas. Only report tests as passing if you actually ran them. Include the commands and results in the pull request description.

## Commit messages

The repository does not define a required commit message convention. Recommended format:

```text
Short imperative summary (50-72 characters)

Explain why the change was made and any non-obvious behavior.
Reference the issue number when applicable.
```

Examples:

```text
Fix favorite URL normalization in database helper

Trim stored favorite URLs consistently so duplicate detection matches
other media lists. Refs #123.
```

```text
Document debug build and test commands
```

Keep commits small and self-contained where practical.

## Open a pull request

1. Push the topic branch to your fork.
2. Open a pull request against `mr-bipolar/maxPlayer-iptv:main`.
3. Use a clear title that describes the change.
4. In the description, include:
   - What changed and why.
   - The issue or discussion it addresses.
   - User-visible effects, including screenshots for UI changes.
   - Devices, emulators, API levels, and ABIs used for testing.
   - Commands run and their results.
   - Any licensing, dependency, or native-engine implications.
5. Confirm that only intended files changed and that no generated output, local configuration, credentials, or AARs were added accidentally.

## Report bugs and propose features

Use GitHub Issues. Before opening a new issue, search for an existing report.

For bugs, include:

- MaxPlayer version and source commit/branch.
- Device or emulator, Android version, and ABI.
- Selected playback engine: Media3 or MPV.
- Exact steps to reproduce.
- Expected and actual behavior.
- Logcat excerpts, with personal data and stream credentials removed.
- A minimal, redacted playlist or stream URL when the issue depends on content.

For features, describe the user problem, proposed behavior, and any trade-offs. Add mockups or examples if useful.

## License and notices

This repository does not currently include a top-level `LICENSE` or `NOTICE` file. MaxPlayer incorporates third-party components with separate terms; review the README and upstream third-party notices before redistributing a modified build.
