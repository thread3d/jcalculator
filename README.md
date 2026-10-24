# jcalculator

A Java implementation of the Windows Calculator application: the standard, scientific and
programmer calculators, date calculation, unit conversion and currency conversion, with memory and
history, exact rational arithmetic, a Swing desktop window and a text interface.

**Version 2.1.0** — the verified calculator, with its engine internals restructured to mirror the
original's own decomposition; see `CHANGELOG.md`.

The original application in `../calculator-main` is a C#/C++ Universal Windows Platform app. Its
model is split across three layers: `CalcManager` (history, memory, engines), `CalcEngine` (the
command-driven state machine) and `RatPack` (arbitrary-precision rational arithmetic). This port
keeps that structure in Java:

| Original | Java |
| --- | --- |
| `CalcManager/Ratpack/*` | `dev.jcalculator.core.Rational`, `RationalMath` |
| `CalcManager/Ratpack/CalcErr.h` | `dev.jcalculator.engine.EngineError` |
| `CalcManager/CEngine/calc.cpp`, `scicomm.cpp` | `dev.jcalculator.engine.CalcEngine` |
| `CalcManager/CEngine/scioper.cpp` | `dev.jcalculator.engine.OperatorPrecedence` |
| `CalcManager/CEngine/scifunc.cpp` | `dev.jcalculator.engine.UnaryFunctions` |
| `CalcManager/CEngine/scidisp.cpp` | `dev.jcalculator.engine.DisplayFormatter`, `format.NumberFormatter` |
| `CalcManager/CEngine/basex.cpp`, `logic.cpp` (bit helpers) | `dev.jcalculator.engine.BitMath` |
| `CalcManager/CEngine/CalcInput.cpp` | `dev.jcalculator.engine.CalcInput` |
| `CalcManager/CEngine/History.cpp`, `ExpressionCommand.cpp` | `ExpressionBuilder`, `ExpressionToken` |
| `CalcManager/Header Files/ICalcDisplay.h` | `CalcEngine.EngineListener` |
| `CalcManager/CalculatorManager.cpp` | `dev.jcalculator.manager.CalculatorManager` |
| `CalcManager/UnitConverter.cpp` + `UnitConverterDataLoader.cs` | `dev.jcalculator.convert.UnitConverter` |
| `CalcManager/NumberFormattingUtils.cpp` | `dev.jcalculator.format.NumberFormatter` |
| `Calculator.ViewModels/Common/DateCalculator.cs` | `dev.jcalculator.date.DateCalculationEngine` |
| `Calculator.ViewModels/StandardCalculatorViewModel.cs` | `CalculatorManager` + `CalculatorWindow` |
| `Calculator/Views/*.xaml` | `dev.jcalculator.app.CalculatorWindow` (Swing) |

The engine reports through the original's display interface rather than touching a view: the primary
display, the expression as tokens that each keep their command, the parenthesis depth, and the
non-error signals a presentation layer may act on (a refused digit, a right parenthesis with nothing
to close, a binary operator arriving). Failures carry a named code as well as the text the display
shows, so nothing has to match on display text.

The XAML view cannot exist in Java, so the window is a Swing front end over the same manager the
text interface and the tests use. Everything a calculation depends on — arithmetic, operator
precedence, integer width, base conversion, date rules, conversion factors and display formatting —
is in the model classes.

## Build and run

The build is self-contained: `mvnw` uses the Maven distribution vendored in `.tools/` and keeps the
local repository in `.m2repo/`, so it works offline and inside a sandbox.

```bash
./mvnw test                 # compile and run the test suite
./mvnw package              # build target/jcalculator-2.1.0.jar

java -cp target/classes dev.jcalculator.app.CalculatorWindow     # desktop window
java -cp target/classes dev.jcalculator.app.CalculatorCli        # interactive text mode
java -cp target/classes dev.jcalculator.harness.HarnessCli 2 + 3 # machine-readable one-shot
java -cp target/classes dev.jcalculator.mcp.McpServer            # MCP server on stdio
java -cp target/classes dev.jcalculator.app.CalculatorCli --version
java -jar target/jcalculator-2.1.0.jar --version
bin/jcalculator                                  # the desktop window via its launcher
bin/jcalculator-harness 2 + 3 =                  # the same harness CLI via its launcher
bin/jcalculator-cli 2 + 3 =                      # the text interface via its launcher
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
`deg`, `ms`, `=`), and all four report the same state. A token means what the active page's keypad
shows: `c` clears on the standard and scientific pages, where the key is labelled `C`, and is the
hexadecimal digit `C` on the programmer page, which has a separate `CLR`; `e` is Euler's constant on
the standard and scientific pages and the hexadecimal digit `E` on the programmer page. The letters
`a`, `b`, `d` and `f` are buttons only on the programmer page, so pressing one elsewhere is refused
with an explanation rather than accepted with no effect. The random key is seeded, so a run can be
replayed exactly.

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
 "display":"20","rawDisplay":"20","expression":"5 × 4=","parenthesis":0,"signal":null,
 "mode":"STANDARD","radix":10,"width":"QWORD","angle":"DEGREES","result":null,
 "history":[...],"memory":[]}
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
| `date_add`, `date_subtract` | `from`, `years`, `months`, `days` (whole numbers), `subtract` | `result` |
| `history`, `memory` | — | the list |
| `memory_edit` | `action` (`add`, `subtract`, `load`, `clear`), `index` (default 0) | the list |
| `capabilities` | — | version, seed, modes, categories, operations, tokens, journal |
| `report_issue` | `summary`, `detail`, `severity` | the recorded issue |
| `issues` | `status` (`open`, `resolved`, `all`), `limit` | the page and its counts |
| `resolve_issue` | `id`, `disposition` (`fixed`, `wontfix`), `note` | the resolved issue |

Every response has the same shape. `ok` says whether the operation produced a defined result,
`error` says whether the calculator is in its error state, and `errorCode` is a stable reason when
`ok` is false: `calculator_error`, `out_of_range`, `unknown_token`, `unknown_op`, `bad_request`,
`parse_error`. `parenthesis` is how many brackets the expression still has open, and `signal` is the
last non-error signal the engine raised (`no_right_paren`, `binary_operator`, `max_digits`,
`input_changed`, `memory_changed`) or `null`.

A field of the wrong type is a `bad_request` that says what the field expects, never an
`internal_error`: `tokens` must be an array of button names (a whitespace-separated string goes in
`expression`), and a duration field must be a whole number in 32-bit range.

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
capability. The fifteen tools are `calculator_press`, `calculator_evaluate`, `calculator_state`,
`calculator_reset`, `calculator_mode`, `calculator_convert`, `calculator_date_diff`,
`calculator_date_add`, `calculator_history`, `calculator_memory`, `calculator_memory_edit`,
`calculator_capabilities`,
`calculator_report_issue`, `calculator_issues` and `calculator_resolve_issue`.
State persists between calls, and `--seed N` / `--mode MODE` set the initial session. The
`initialize` instructions mention how many issues the journal currently holds open.

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

## Problems met in use are logged

jCalculator keeps a journal of the problems met while it is being used, so nobody has to remember
them until a later session can fix them. It needs no configuration:

- **Automatic.** An unknown token, an unknown operation and an unexpected internal failure are
  recorded with the surface (`cli`, `harness`, `mcp`, `swing`), the input and the state that
  produced them. A malformed request is not recorded: that is the caller's mistake, and the response
  already says so.
- **Explicit.** A person or an agent can report a problem in its own words:

  ```bash
  bin/jcalculator-cli --report "2 + 2 displayed 5" --detail "expected 4"
  bin/jcalculator-cli --issues                # open problems, newest first
  bin/jcalculator-cli --issues --all          # include the resolved ones
  bin/jcalculator-cli --resolve JC-1A2B3C4D --note "fixed in the engine"
  ```

  Over MCP the same operations are `calculator_report_issue`, `calculator_issues` and
  `calculator_resolve_issue`; the JSON protocol calls them `report_issue`, `issues` and
  `resolve_issue`. `calculator_capabilities` reports the journal's path and open count.

The journal is append-only JSON Lines at `.jcalculator/issues.jsonl` under the project. The `bin/`
launchers pin it there; `JCALC_ISSUES` or `-Djcalculator.issues.path` overrides the path, and `off`
disables recording. Each line is an event and an issue is the fold of its events, so the same
problem seen twice is one entry with a count, and a resolved problem that happens again reopens. A
damaged line is ignored and counted, and a journal that cannot be written never changes a
calculation or a response. The file is git-ignored: it is local, durable state, not source.

Entries are named `JC-XXXXXXXX` — a hash of the kind, the summary and a stable context signature —
so a session can cite one without transcribing its text.

### Fixing what the journal recorded

Ask a session to "fix the open issues", or work through them directly:

1. `bin/jcalculator-cli --issues` (or `calculator_issues` over MCP) reads the open entries. Each one
   carries its surface, occurrence count and context: the token, operation, mode and request.
2. Reproduce with `bin/jcalculator-harness` or the MCP tools; the entry's context is enough to replay
   the failure.
3. Fix it, with a requirement, a scenario and a test as usual.
4. `--resolve <id> --note "..."` (or `calculator_resolve_issue`) so the next session does not
   investigate it again.

## Versioning

jCalculator follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). The current version
is **2.1.0**; the release history is in `CHANGELOG.md`. A version change updates three places,
which the build keeps in step:

- `pom.xml` — the Maven project version, and therefore the jar name and manifest
  `Implementation-Version`;
- `dev.jcalculator.app.AppInfo.VERSION` — what the window title and `--version` print (a test fails
  if it disagrees with the POM);
- `CHANGELOG.md` — the entry describing the release.

Git releases are tagged `v<version>` (for example `v1.2.0`).

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
jcheck's `strict` profile (T0–T3) with no suppressed diagnostics. The only `@SuppressWarnings` in the
tree are two `"unchecked"` annotations on the generic casts in `harness/Json.java`, the JSON codec;
they silence a javac warning, not a jcheck finding.

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
  diagnostics/ IssueJournal, IssueDraft, Issue, Faults — the self-logged issue journal
  mcp/       McpServer, CalculatorTools
bin/                             jcalculator-cli, jcalculator-harness and jcalculator-mcp launchers
src/test/java/dev/jcalculator/   JUnit 5 tests for every layer
specs/                            requirements.md and codewitness.yaml
```
