# jcalculator

A Java implementation of the Windows Calculator application: the standard, scientific and
programmer calculators, date calculation, unit conversion and currency conversion, with memory and
history, exact rational arithmetic, a Swing desktop window and a text interface.

**Version 1.1.0** — the first verified release plus harness integration; see `CHANGELOG.md`.

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
./mvnw package              # build target/jcalculator-1.1.0.jar

java -cp target/classes dev.jcalculator.app.CalculatorWindow     # desktop window
java -cp target/classes dev.jcalculator.app.CalculatorCli        # interactive text mode
java -cp target/classes dev.jcalculator.harness.HarnessCli 2 + 3 # machine-readable one-shot
java -cp target/classes dev.jcalculator.mcp.McpServer            # MCP server on stdio
java -cp target/classes dev.jcalculator.app.CalculatorCli --version
java -jar target/jcalculator-1.1.0.jar --version
bin/jcalculator-harness 2 + 3 =                  # the same harness CLI via its launcher
bin/jcalculator-mcp                              # the MCP server via its launcher
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

## Harness integration

The calculator can be driven by an automated harness without a terminal or a screen. Four surfaces
share one model, so they cannot disagree about what a key press does:

| Surface | Entry point | Needs |
| --- | --- | --- |
| Programmatic session | `dev.jcalculator.harness.CalculatorSession` | nothing |
| JSON line protocol | `dev.jcalculator.harness.HarnessCli` | nothing |
| MCP server | `dev.jcalculator.mcp.McpServer` | nothing |
| Desktop window | `dev.jcalculator.app.UiDriver` | a display |

All four accept the same button tokens as the text interface (`7`, `+`, `sqrt`, `hex`, `qword`,
`deg`, `ms`, `=`), and all four report the same state. The one token whose meaning depends on the
page is `c`: it clears on the standard and scientific pages, where the key is labelled `C`, and
enters hexadecimal `C` on the programmer page, which has a separate `CLR`. The random key is seeded,
so a run can be replayed exactly.

### 1. Programmatic session

`CalculatorSession` never prints, never blocks and never exits; it reports failures as exceptions and
state as an immutable snapshot.

```java
CalculatorSession session = new CalculatorSession(CalculatorMode.SCIENTIFIC, 7L);
session.pressAll(List.of("2", "+", "3", "*", "4", "="));
session.state().display();      // "14"
session.state().expression();   // "2 + 3 × 4="
session.state().error();        // false
session.state().history();      // [HistoryEntry[expression=2 + 3 × 4=, result=14]]
```

`press(String)` is the token form; `press(Command)` is the typed form. An unknown token throws
`UnknownTokenException`, which carries the offending token, rather than being ignored.

### 2. JSON line protocol

`HarnessCli` prints one JSON document per request and nothing else, and its process exit code is
real:

```bash
$ java -cp target/classes dev.jcalculator.harness.HarnessCli 2 + 3 "*" 4 =
{"id":null,"op":"press","ok":true,"errorCode":null,"error":false,"errorMessage":null,
 "display":"20","rawDisplay":"20","expression":"5 × 4=","mode":"STANDARD","radix":10,
 "width":"QWORD","angle":"DEGREES","result":null,"history":[...],"memory":[]}
```

With `--batch`, each input line is either a bare token sequence or a JSON request, and each line
produces one JSON response:

```bash
printf '2 + 3 =\n{"id":2,"op":"convert","category":"length","from":"m","to":"ft","value":"2.5"}\n' \
  | java -cp target/classes dev.jcalculator.harness.HarnessCli --batch
```

Requests (`op`):

| `op` | Arguments | Returns |
| --- | --- | --- |
| `press` | `tokens` (array) or `expression` (string) | display and state |
| `evaluate` | `expression` (string) | display and state |
| `state` | — | the full state |
| `reset` | — | cleared state |
| `mode` | `mode`: `standard`, `scientific`, `programmer` | the new page |
| `convert` | `category`, `from`, `to`, `value` | `result` |
| `date_diff` | `from`, `to` (ISO dates) | `result` |
| `date_add`, `date_subtract` | `from`, `years`, `months`, `days`, `subtract` | `result` |
| `history`, `memory` | — | the list |
| `capabilities` | — | version, seed, modes, categories, operations, tokens |

Every response has the same shape. `ok` says whether the operation produced a defined result,
`error` says whether the calculator is in its error state, and `errorCode` is a stable reason when
`ok` is false: `calculator_error`, `out_of_range`, `unknown_token`, `unknown_op`, `bad_request`,
`parse_error`.

Exit codes: `0` success, `1` calculation error, `2` malformed request, `64` bad command line.
Options: `--seed N`, `--mode MODE`, `--batch`, `--text`, `--json`, `--version`, `--help`.

### 3. MCP server

`McpServer` speaks newline-delimited JSON-RPC 2.0 on standard input and output, as the MCP stdio
transport specifies. Register it with a harness like any other stdio server:

```json
{
  "mcpServers": {
    "jcalculator": {
      "command": "/absolute/path/to/jcalculator/bin/jcalculator-mcp"
    }
  }
}
```

`bin/jcalculator-mcp` runs the packaged jar (or `target/classes` before packaging) and forwards
`--seed` and `--mode`; build once with `./mvnw package` first.

It implements `initialize`, `ping`, `tools/list` and `tools/call`, and advertises the `tools`
capability. The tools are `calculator_press`, `calculator_evaluate`, `calculator_state`,
`calculator_reset`, `calculator_mode`, `calculator_convert`, `calculator_date_diff`,
`calculator_date_add`, `calculator_history`, `calculator_memory` and `calculator_capabilities`.
State persists between calls, and `--seed N` / `--mode MODE` set the initial session.

### 4. Window automation

`AutomationSurface` is the window's controller: each page publishes its key layout by label, and a
click resolves the label against the active page.

```java
AutomationSurface surface = new AutomationSurface(CalculatorMode.SCIENTIFIC);
surface.click("2");
surface.click("+");
surface.click("3");
surface.click("×");   // the label shown on the button
```

It needs no screen, which makes it the right surface for headless tests. `UiDriver` drives the real
Swing window when a display exists: every action runs on the event thread and returns after the
widgets have repainted.

```java
try (UiDriver driver = UiDriver.launch()) {
    driver.click("2");
    driver.click("+");
    driver.click("3");
    driver.click("=");
    driver.display();   // "5"
}
```

Every button carries its label as both its component name and its accessible name, so an
accessibility-based tool can find controls by name too.

## Versioning

jCalculator follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). The current version
is **1.1.0**; the release history is in `CHANGELOG.md`. A version change updates three places,
which the build keeps in step:

- `pom.xml` — the Maven project version, and therefore the jar name and manifest
  `Implementation-Version`;
- `dev.jcalculator.app.AppInfo.VERSION` — what the window title and `--version` print (a test fails
  if it disagrees with the POM);
- `CHANGELOG.md` — the entry describing the release.

Git releases are tagged `v<version>` (for example `v1.1.0`).

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
  app/       CalculatorCli, CalculatorWindow, AutomationSurface, UiDriver
  harness/   CalculatorSession, HarnessProtocol, HarnessCli, Tokens, Json
  mcp/       McpServer, CalculatorTools
bin/                             jcalculator-harness and jcalculator-mcp launchers
src/test/java/dev/jcalculator/   JUnit 5 tests for every layer
specs/                            requirements.md and codewitness.yaml
```
