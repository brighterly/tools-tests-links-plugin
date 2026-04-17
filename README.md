# Tests Links

PhpStorm plugin that shows a gutter indicator next to **Service** and **Controller** classes, links them to their PHPUnit test files, and scaffolds new tests on demand.

## Features

- **Gutter icon with test status** — green when the class has a test file, yellow when the test file exists but has no detected test methods, grey **+** when no test exists.
- **Click to navigate** — single click opens the test file; a popup lets you pick when multiple test files match (e.g. both `tests/Services/...` and `tests/Feature/Services/...`).
- **Test method count in tooltip** — counts methods using `function test*`, `#[Test]` attribute, and `@test` docblock patterns.
- **Click to create test** — grey **+** icon scaffolds `{ClassName}Test.php` at the mirrored path with project conventions (`declare(strict_types=1)`, `extends TestCase`, `DatabaseTransactions`, AAA layout) and opens it.
- **Reverse navigation** — gutter icon on a test class jumps back to the subject Service/Controller.
- **Status bar coverage widget** — shows `Tests 48% (72/150)` for Service + Controller coverage; click for breakdown, rebuild action, and first 30 uncovered classes.
- **Zero-overhead indexing** — test files are scanned once at project open, incrementally refreshed on VFS changes. Gutter rendering is a `ConcurrentHashMap` lookup with no disk I/O.

## Scope

Activates for PHP classes whose file path contains:

- `app/Services/**`
- `app/Http/Controllers/**`

Test files are discovered by the convention `{ClassName}Test.php` anywhere under `{projectRoot}/tests/`.

## How test methods are counted

Regex over the raw file text, unioned across three patterns:

- `function test*(` — classic naming convention
- `#[Test]` — PHPUnit 10+ attribute
- `@test` — docblock annotation

Known limitations:

- `#[DataProvider]` expansion is not applied — one method is counted once regardless of dataset size.
- Inherited test methods from abstract base classes are not resolved.

## Stub layout for generated tests

Given a Service at `app/Services/Customer/Activation/ActivationService.php`, the plugin creates:

- **Path**: `tests/Services/Customer/Activation/ActivationServiceTest.php`
- **Namespace**: `Tests\Services\Customer\Activation`
- **Body**: `extends TestCase`, `use DatabaseTransactions`, one placeholder `testExample()` method resolving the subject via `app(ActivationService::class)`.

Controllers are mapped with the `Http/` segment dropped: `app/Http/Controllers/X/Y/Bar.php` → `tests/Controllers/X/Y/BarTest.php`.

## Requirements

- PhpStorm 2025.3 or later (sandbox tested through 2026.1 / build 261)
- PHP plugin (bundled with PhpStorm)

## Build locally

```
./gradlew buildPlugin
```

Artifact at `build/distributions/tools-tests-links-plugin-*.zip`. Install via **Settings → Plugins → ⚙ → Install Plugin from Disk…**

## Run sandbox IDE

```
./gradlew runIde
```

## Release process

Bump `pluginVersion` in `gradle.properties` and push to `main`. The `Publish Plugin` workflow detects the version change, builds, signs, publishes to JetBrains Marketplace, and creates a GitHub release with the zip attached.

## Note on IDE restart

Updating the plugin requires an IDE restart. This is enforced by IntelliJ's extension point system and cannot be avoided.
