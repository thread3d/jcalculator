# jcalculator

A Java implementation of the Windows Calculator application: the standard, scientific and
programmer calculators, date calculation, unit conversion and currency conversion, with memory and
history, exact rational arithmetic, a Swing desktop window and a text interface.

**Version 1.0.0** — the first verified release; see `CHANGELOG.md`.

The original application in `../calculator-main` is a C#/C++ Universal Windows Platform app. Its
model is split across three layers: `CalcManager` (history, memory, engines), `CalcEngine` (the
command-driven state machine) and `RatPack` (arbitrary-precision rational arithmetic). This port
keeps that structure in Java:

| Original | Java |
| --- | --- |
| `CalcManager/Ratpack/*` | `dev.jcalculator.core.Rational`, `RationalMath` |
| `CalcManager/CEngine/*` | `dev.jcalculator.engine.CalcEngine`, `CalcInput`, `Command`, `ExpressionBuilder` |
| `CalcManager/CalculatorManager.cpp` | `dev.jcalculator.manager.CalculatorManager` |
| `CalcManager/UnitConverter.cpp` + `UnitConverterDataLoader.cs` | `dev.jcalculator.convert.UnitConverter` |
| `CalcManager/NumberFormattingUtils.cpp` | `dev.jcalculator.format.NumberFormatter` |
| `Calculator.ViewModels/Common/DateCalculator.cs` | `dev.jcalculator.date.DateCalculationEngine` |
| `Calculator.ViewModels/StandardCalculatorViewModel.cs` | `CalculatorManager` + `CalculatorWindow` |
| `Calculator/Views/*.xaml` | `dev.jcalculator.app.CalculatorWindow` (Swing) |

The XAML view cannot exist in Java, so the window is a Swing front end over the same manager the
text interface and the tests use. Everything a calculation depends on — arithmetic, operator
precedence, integer width, base conversion, date rules, conversion factors and display formatting —
is in the model classes.

## Build and run

The build is self-contained: `mvnw` uses the Maven distribution vendored in `.tools/` and keeps the
local repository in `.m2repo/`, so it works offline and inside a sandbox.

```bash
./mvnw test                 # compile and run the test suite
./mvnw package              # build target/jcalculator-1.0.0.jar

java -cp target/classes dev.jcalculator.app.CalculatorWindow   # desktop window
java -cp target/classes dev.jcalculator.app.CalculatorCli      # interactive text mode
java -cp target/classes dev.jcalculator.app.CalculatorCli --version
java -jar target/jcalculator-1.0.0.jar --version
```

One-shot examples:

```bash
java -cp target/classes dev.jcalculator.app.CalculatorCli 2 + 3 * 4 =
java -cp target/classes dev.jcalculator.app.CalculatorCli --mode scientific 2 + 3 * 4 =
java -cp target/classes dev.jcalculator.app.CalculatorCli --mode programmer 5 3 nand 8 3 and
java -cp target/classes dev.jcalculator.app.CalculatorCli --convert length m ft 2.5
java -cp target/classes dev.jcalculator.app.CalculatorCli --date-diff 2024-01-01 2024-12-31
```

Tokens are button presses: digits, operators (`+ - * / ^ mod and or xor nand nor lsh rsh`), functions
(`sqrt ln log sin cos tan ...`), settings (`hex dec oct bin`, `qword dword word byte`, `deg rad grad`,
`standard scientific programmer`) and memory keys (`ms mr m+ m- mc`).

## Versioning

jCalculator follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). The current version
is **1.0.0**; the release history is in `CHANGELOG.md`. A version change updates three places,
which the build keeps in step:

- `pom.xml` — the Maven project version, and therefore the jar name and manifest
  `Implementation-Version`;
- `dev.jcalculator.app.AppInfo.VERSION` — what the window title and `--version` print (a test fails
  if it disagrees with the POM);
- `CHANGELOG.md` — the entry describing the release.

Git releases are tagged `v<version>` (for example `v1.0.0`).

## Specifications

`specs/requirements.md` is the specification. Each requirement has a stable id (`R-001` …) and states
a behaviour in terms a reader can check. `specs/codewitness.yaml` attaches an executable scenario to
every requirement: the scenario constructs the public API, performs the calls and checks an
invariant. The specs are deliberately written for CodeWitness, whose verification model treats a
declared, executed scenario as the only path to `VERIFIED`.

Verify with CodeWitness from this directory:

```bash
codewitness verify . --requirements specs/requirements.md --config specs/codewitness.yaml
```

Or from the repository root:

```bash
codewitness verify jcalculator \
  --requirements jcalculator/specs/requirements.md \
  --config jcalculator/specs/codewitness.yaml
```

CodeWitness builds the project, runs the existing JUnit suite, compiles the scenario tests, runs
them, and reports a verdict per requirement. A failing scenario becomes a counterexample with the
exact inputs and a replay command.

## Static analysis

`jcheck` is configured to analyse the project from its root:

```bash
jcheck check --root .
```

`jcheck.toml` points the embedder at the vendored Maven installation. The code is kept clean under
jcheck's `strict` profile (T0–T3), with no suppressions.

## Deliberate differences from the original

- **No XAML.** The UI is Swing; it exposes the same modes and keys, not the original visual states,
  navigation view or accessibility layer.
- **Transcendental accuracy.** Functions are computed with arbitrary-precision decimal arithmetic to
  the mode's precision (32 significant digits in scientific mode), not with the original RatPack
  algorithms. Results agree with the original to the displayed precision for the cases in the specs;
  the last digit of some trigonometric values can differ.
- **Currency data.** The converter uses the same fictional-planet mock data as the open-source build
  (1 Martian Dollar = 0.5 Moon Bucks, and so on), not live exchange rates.
- **Graphing, the settings page and telemetry** from the original application are out of scope.

## License and attribution

jCalculator is a Java derivative of Microsoft's Windows Calculator, which is MIT-licensed. It is
distributed under the MIT license; see `LICENSE`. The upstream copyright notice and permission
notice, Microsoft's `NOTICE` file, and a precise description of what was derived are in
`THIRD_PARTY_NOTICES.md` and `NOTICE`.

The MIT terms permit use, modification, sublicensing and redistribution, provided the copyright
and permission notice travel with copies or substantial portions of the work. They do not require
publishing source code, but this repository is source-available anyway. No Microsoft asset,
binary, font or XAML is included, and the retail currency data (which the original project says is
not licensed for third-party use) is not used.

JUnit and Maven are build/test-only dependencies and are not redistributed with the jar; their
licenses are listed in `THIRD_PARTY_NOTICES.md`.

## Layout

```
src/main/java/dev/jcalculator/
  core/      Rational, RationalMath, AngleType
  engine/    Command, CalcInput, CalcEngine, ExpressionBuilder, history entries
  manager/   CalculatorManager (modes, memory, history)
  format/    NumberFormatter
  date/      DateCalculationEngine, DateDifference, DateUnit
  convert/   UnitConverter, Unit, UnitCategory
  app/       CalculatorCli, CalculatorWindow
src/test/java/dev/jcalculator/   JUnit 5 tests for every layer
specs/                            requirements.md and codewitness.yaml
```
