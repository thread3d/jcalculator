# Changelog

All notable changes to jCalculator are recorded here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

The display now spends the mode's precision the way the original does, and the maths snap the way the
original's logarithms do. Both were derived by compiling RatPack — the original's own algorithms —
from the sources in `calculator-main` and comparing 660 displayed values at each precision.

### Changed

- `NumberFormatter` follows the original's `NumberToString` field rules: a value with more whole
  digits than the field holds switches to exponent form (`20!` reads `2.43290200817664e+18` at
  precision 16); a value below one spends part of the field on its leading zeros (`sin(1°)` shows
  fifteen significant digits, not sixteen); a value with few enough digits stays in fixed notation
  however small (`1/10000` reads `0.0001`); digits are rounded half up and trailing zeros dropped
  (`R-020`). The memory list, in the protocol and in the window, follows the same rule.
- `ln` and `log10` snap a result below the working precision of its input to zero, as the original's
  `lograt` does, so a logarithm of a value a hair away from one reads `0` rather than an artefact of
  the last guard digit (`R-020`).

### Tests

- Five `NumberFormatterTest` cases pin the field rules, and two scenarios carry them.

Measured against the compiled original: at precision 16 the display now agrees on **658 of 660**
values (99.7%, from 590), and at precision 32 on **659 of 660** (99.8%, from 596). The remaining
three differ by one unit in the last displayed digit, from the port's `exp`/`ln` round-trip carried
at 16 digits rather than from the display rule.

## [2.2.0] - 2026-10-24

A minor bump: the interfaces a caller uses — the JSON protocol, the MCP tools, the CLI exit codes
and the window's automation accessors — are unchanged or additive. The one public shape that moved is
`HistoryEntry`, which gained the button sequence and kept a two-argument constructor, so building an
entry still compiles.

The gaps between this port and the original's application shell are closed. The window is now the
whole application — pages, menus, settings, clipboard, bit panel and graphing — rather than a single
keypad, and the specification grew five requirements (`R-034` … `R-038`) with their scenarios.

### Added

- An application shell: a menu that moves between the three keypad pages and the date, converter and
  graphing workspaces; a view menu with the theme (light, dark, follow-the-system), always-on-top, the
  history list and the memory list; file copy/paste/clear; a help menu with the about box and the
  shortcut list; and `Ctrl+1`…`Ctrl+6`, `Ctrl+C`, `Ctrl+V`, `Ctrl+H` and `Ctrl+M` (`R-034`).
- `Settings` and `SettingsStore`: the theme and always-on-top choices are kept in
  `.jcalculator/settings.properties` and survive a restart, with a damaged or unreadable file falling
  back to the defaults (`R-034`).
- The date page and the converter page, both thin shells over the models the JSON protocol already
  exposes; the converter lists the same amount in the other units of its category, ordered by
  magnitude and rounded the way the original rounds its supplementary results (`R-036`).
- The programmer bit panel: each bit of the word can be flipped by clicking it, bounded by the current
  width and ignored on the other pages (`R-037`).
- Clipboard support: a paste is validated against the active page's alphabet and base before anything
  is entered, and an accepted text becomes the keypad presses that type it (`R-035`).
- A graphing page over a new `graph` package: `GraphExpression` reads a function of x and rewrites a
  prefix call into keypad order, and `FunctionPlotter` samples it through the calculator engine, so a
  plot obeys the page's arithmetic, precedence and angle unit. Points the engine refuses are omitted
  so the curve shows the gap (`R-038`).
- Interactive history and memory dialogs: a history entry keeps the buttons that produced it and can
  be re-run, copied or cleared (`R-013`), and the memory list offers the per-slot `M+`, `M−` and `MC`
  the original's memory panel has.

### Changed

- `CalculatorWindow` is the application shell; the keypad moved into `app.CalculatorPage`, and
  `DatePage`, `ConverterPage` and `GraphPage` are new pages beside it. The window's automation
  accessors (`surface()`, `displayText()`, `findButton()`, …) are unchanged.
- `specs/requirements.md` gained a preamble and an index, and `specs/README.md` is a handbook: how the
  two files fit together, how a requirement is verified, a traceability table, coverage and the
  reviewed heuristics.

### Tests

- `SettingsStoreTest`, `ClipboardTextTest`, `FunctionPlotterTest` and `CalculatorWindowShellTest`, plus
  cases in `ProgrammerCalculatorTest`, `UnitConverterTest` and `CalculatorSessionTest`: **385 tests**
  in 32 classes.

## [2.1.0] - 2026-10-24

The test suite now covers the parts of the application it had never exercised. CodeWitness coverage
rises from 74.4% line / 57.4% branch to **85.6% line / 72.3% branch**, and the suite from 212 to 341
tests. Nothing the calculator does has changed, apart from one error-classification fix noted below.

### Added

- A command-line suite for the text interface (53 tests): every flag, every usage error, the issue
  journal commands, the one-token interactive commands, and the interactive loop driven from a
  scripted input stream.
- Unit suites for the unary-function dispatch (16 tests), the mathematical core (23), the word
  arithmetic behind programmer mode (5), the command table (5), operator precedence (3) and the
  number formatter (9), plus the MCP transport's error paths (13) and the two record predicates
  (`Unit.hasFactor`, `DateDifference.isDaysOnly`).
- `CalculatorCli.run(List<String>)` returns the process exit code, and the interface takes the
  streams it writes to and the input it reads, so its whole command line is exercisable in the
  process that runs it. `main` is now a wrapper that exits with that code; the exit codes are
  unchanged.

### Fixed

- A malformed date on the text interface's command line (`--date-diff not-a-date 2024-01-01`) is a
  usage error answering `64`, not an internal failure answering `70`, and is no longer recorded in
  the issue journal as a problem with the calculator.

## [2.0.0] - 2026-10-24

A major bump because the restructuring changes shapes outside the model: `CalcEngine.EngineListener`
takes expression tokens instead of a rendered string, `CalculatorState` gains two components, and the
JSON protocol gains two fields and an operation. A caller that implemented the listener or read the
state record has to be updated, so this is not a compatible minor release. The calculator's
behaviour is unchanged, and no requirement was renumbered.

The internals now mirror the original's own decomposition. The single 1,338-line `CalcEngine` was
split along the seams the original uses, the engine reports through the original's display interface
instead of handing a view a pre-rendered string, failures carry named codes, and the memory keys act
on individual slots. Four requirements describe the result: R-030 (display protocol), R-031 (error
codes), R-032 (the expression line as a token stream) and R-033 (per-slot memory).

### Added

- `EngineError` and `CalculationException`: the failures the engine can raise are named
  (`divide_by_zero`, `undefined`, `overflow`, `domain`, `invalid_range`) and mapped to the display
  text the calculator has always shown, mirroring `CalcErr.h`. `CalcEngine.error()` and
  `errorCode()` expose them; `CalcEngine.ERROR_*` remain as aliases.
- `ExpressionToken` and a token-based `ExpressionBuilder`: every part of the expression line keeps the
  command that produced it, and the completed equation stays on the line until the next key.
- The engine listener now matches the original's `ICalcDisplay`: `onExpression(tokens)`,
  `onParenthesisCount`, `onNoRightParen`, `onBinaryOperatorReceived`, `onMaxDigitsReached`,
  `onHistoryEntry` and `onDisplay`. `CalculatorManager` implements them and exposes
  `parenthesisCount()` and `lastSignal()`.
- `CalculatorState.parenthesisCount` and `CalculatorState.signal`, and the JSON protocol's
  `parenthesis` and `signal` fields, so an automation surface can read the bracket depth and tell
  whether the calculator complained without treating it as an error.
- Requirement R-033 and per-slot memory: `CalculatorManager.memorizedNumberAdd/Subtract/Load/Clear`
  follow the original's memory functions (select the slot into the engine, run the command, copy the
  result back), including the empty-list cases where `M+` stores the displayed value and `M-` stores
  its negation. Every change to the list raises the `memory_changed` signal, and a recall raises
  `input_changed`. The protocol gains `memory_edit` and the MCP server gains `calculator_memory_edit`
  (fifteen tools).

### Changed

- `CalcEngine` (1,338 lines) is split along the original's seams: `OperatorPrecedence`
  (`scioper.cpp`), `UnaryFunctions` (`scifunc.cpp`), `DisplayFormatter` (`scidisp.cpp`) and
  `BitMath` (`basex.cpp`/`logic.cpp`). The engine keeps the state machine and the operations that
  need pending-operation state (`%`, `NOT`, the rotations).
- `ExpressionBuilder` builds tokens rather than a pre-rendered string, so `ExpressionToken.render` is
  the one place that decides spacing.

### Fixed

- Replacing a pending operator with one that binds more tightly no longer leaves the replaced
  operator inside the brackets: `1 × 2 +` followed by `^` now reads `(1 × 2) ^` instead of
  `(1 × 2 +) ^`. The partial result the engine computed is what the brackets enclose.
- Clearing after an error now clears the engine's error code as well as its text.

### Tests

- `EngineProtocolTest` (10 tests), 5 new `CalculatorManagerTest` cases, 3 new
  `HarnessProtocolTest` cases and a `ProgrammerCalculatorTest` regression case: **212 tests** in 20
  classes, all passing. jcheck `strict` reports 0 errors and 0 warnings, and CodeWitness verifies
  33/33 requirements from 80 scenarios with no counterexamples.

## [1.3.0] - 2026-10-23

The journal pays off. Every problem the 1.2.0 journal recorded (four entries, JC-C53044D9,
JC-80E21685, JC-2320648F and JC-BE96C466) is fixed, and the two defects behind them are now
requirements: a token means what the active page's keypad shows (R-028), and a malformed request
field is refused rather than reported as an internal failure (R-029).

### Added

- Requirement R-028 and its scenarios: token resolution is page-aware. Euler's constant has its own
  token, `e` (and `euler`), which is Euler on the standard and scientific pages and the hexadecimal
  digit `E` on the programmer page. The letters `a`, `b`, `d` and `f` are buttons only on the
  programmer page; pressing one elsewhere is refused with an `UnknownTokenException` whose message
  names the token and says where the hexadecimal digits live, instead of reporting success with an
  unchanged display. `Tokens.refusalMessage` produces that sentence once, so the text interface, the
  JSON protocol, the MCP tools, `AutomationSurface` and `UiDriver` all explain a refusal the same way.
- Requirement R-029 and its scenarios: `Json.integer` refuses a non-integral or oversized number as
  a `JsonException` naming the field, so `date_add`/`date_subtract` with `1.5` or `1e10` days is a
  `bad_request` (exit 2) rather than an `internal_error`. `HarnessProtocol.tokensOf` names the
  expected shape when `tokens` is not an array, so `{"tokens":"2 + 3 ="}` says to use an array or
  `expression` instead of "missing tokens or expression". Neither case is journalled: malformed
  caller input is not a problem with the calculator.
- `TokensTest` (8 tests) and new cases in `JsonTest`, `HarnessProtocolTest`,
  `HarnessProtocolIssueTest`, `CalculatorCliTest` and `AutomationSurfaceTest`: 193 tests in total.

### Changed

- `Tokens.commandFor(String)` now reads a token the way the standard page does, because a
  hexadecimal letter has no page-independent meaning; every front end that knows the page uses the
  page-aware overload. `AutomationSurface.type`, `UiDriver.type` and `CalculatorCli.execute` were
  mode-blind before and now resolve against the active page, so typing `ff` in standard mode is
  refused instead of pressing digit commands the engine ignores.
- `UnknownTokenException` can carry an explanation (`UnknownTokenException(token, message)`); the
  one-argument constructor is unchanged.
- `HarnessProtocol.execute`'s unexpected-failure safety net is the named method
  `HarnessProtocol.unexpectedFailure`, so the journalling of an internal failure is tested directly
  now that no validated request can produce one.

### Verification

- CodeWitness run `CW-2026-10-23-V7DHP` (copy mode): overall `VERIFIED`, 29/29 requirements
  verified, 193/193 tests passing, 71 generated scenarios, no counterexamples.
- jcheck strict profile (T0–T3): 0 errors, 0 warnings.
- Coverage: 73.2% line, 56.5% branch.

## [1.2.0] - 2026-10-23

Self-logged issues. The calculator now keeps its own journal of the problems met while it is in
use, so a later session can find and fix them instead of depending on someone remembering.

### Added

- `dev.jcalculator.diagnostics.IssueJournal`: an append-only JSON Lines journal at
  `.jcalculator/issues.jsonl` (git-ignored). An issue is folded from its events, so a repeat is one
  entry with an occurrence count, a resolved problem that happens again reopens, and a damaged line
  is ignored without losing the rest. The location comes from `JCALC_ISSUES` or
  `jcalculator.issues.path`; `off` disables it; the `bin/` launchers pin it inside the project.
- `dev.jcalculator.diagnostics.IssueDraft`, `Issue` and `Faults`: the draft a surface records, the
  folded issue, and the automatic classifications (unknown token, unknown operation, unexpected
  failure) with the context needed to reproduce them.
- Automatic recording in the JSON protocol, the MCP server and the text interface: an unknown
  token, an unknown operation and an unexpected internal failure are journalled with the surface,
  input and calculator state. Malformed requests are not, because the response already explains
  them. Recording is best effort and never changes a result.
- `report_issue`, `issues` and `resolve_issue` operations in the JSON protocol, mirrored as the MCP
  tools `calculator_report_issue`, `calculator_issues` and `calculator_resolve_issue`, and as
  `--report`, `--issues` and `--resolve` in the text interface. `capabilities` now reports the
  journal path and open count, and the MCP `initialize` instructions mention open issues.
- `bin/jcalculator-cli`: a launcher for the text interface that keeps the journal in the project.
- Requirement R-027 and two CodeWitness scenarios covering folding, resolution, automatic recording
  and the protocol operations; 27 new JUnit tests.

### Changed

- `HarnessProtocol` takes an `IssueJournal` and the `IssueSurface` it serves, so an issue names the
  front end that met it. The existing constructors keep working and use the configured journal.
- The version surfaces (`pom.xml`, `AppInfo.VERSION`, this file) and the README move to 1.2.0.

### Verification

- CodeWitness run `CW-2026-10-23-RX128`: overall `VERIFIED`, 27/27 requirements verified,
  175/175 tests passing, 66 generated scenarios, no counterexamples.
- jcheck strict profile (T0–T3): 0 errors, 0 warnings.
- Coverage: 73.1% line, 56.1% branch.

## [1.1.0] - 2026-10-23

Harness integration. The calculator is now drivable by an automated harness on four levels: a
programmatic session, a line-oriented JSON protocol, a scriptable desktop window, and an MCP server.

### Added

- `dev.jcalculator.harness.CalculatorSession`: a silent, deterministic facade over the manager.
  It accepts the same button tokens as the text interface, reports an immutable state snapshot,
  refuses unknown tokens, and seeds the random key so a run can be replayed.
- `dev.jcalculator.harness.HarnessProtocol`: one request line in, one JSON response out. A bare
  token line is a press sequence; a JSON request selects an operation. Responses have a fixed shape
  with `ok`, `error`, `errorCode`, the display, the expression and the full state.
- `dev.jcalculator.harness.HarnessCli`: a headless entry point with `--seed`, `--mode`, `--batch`,
  `--text` and `--json`, and real exit codes (0 success, 1 calculation error, 2 malformed request,
  64 usage).
- `dev.jcalculator.harness.Json`: a small dependency-free JSON codec, so the offline build gains no
  runtime dependency.
- `dev.jcalculator.mcp.McpServer`: an MCP server over newline-delimited JSON-RPC 2.0 on stdio.
  It implements `initialize`, `ping`, `tools/list` and `tools/call`, and exposes eleven calculator
  tools covering pressing keys, evaluating tokens, state, reset, mode, conversion, date
  difference and addition, history, memory and capability discovery.
- `dev.jcalculator.app.AutomationSurface`: the window's scriptable controller. Each page publishes
  its key layout by label, clicks resolve labels against the active page, and the window renders
  this surface.
- `dev.jcalculator.app.UiDriver`: deterministic driving of the real Swing window. Actions run on the
  event thread and return after the widgets have repainted, and every control carries its label as
  both its component name and its accessible name.
- `bin/jcalculator-harness` and `bin/jcalculator-mcp`: launchers that resolve the packaged jar (or
  `target/classes`) so a harness can register either entry point without assembling a classpath.
- 70 new tests, including a process-level check that the harness exit codes are real, and four new
  requirements (R-023 to R-026) with executable CodeWitness scenarios.

### Changed

- Token-to-command mapping is shared through `dev.jcalculator.harness.Tokens`; the text interface and
  the harness can no longer drift apart.
- `CalcEngine` and `CalculatorManager` accept a caller-supplied random source, which is what makes
  the random key reproducible.
- Token resolution is page-aware for `c`, the one ambiguous key: it clears on the standard and
  scientific pages and enters hexadecimal `C` on the programmer page, matching each keypad.

### Fixed

- The desktop window abandoned the number being typed whenever it refreshed: `JComboBox`
  `setSelectedIndex` fires even when the index is unchanged, so a refresh echoed a RADIX or ANGLE
  command. Selector echoes are now suppressed while the window writes them.

### Verification

- CodeWitness run `CW-2026-10-23-NR6XP`: overall `VERIFIED`, 26/26 requirements verified,
  148/148 tests passing, no counterexamples.
- jcheck strict profile (T0–T3): 0 errors, 0 warnings.
- Coverage: 73.3% line, 56.4% branch.

## [1.0.0] - 2026-10-23

The first complete, independently verified release of the Java port.

### Added

- Exact rational arithmetic for addition, subtraction, multiplication and division.
- Standard calculator mode with immediate execution and a 16-digit display.
- Scientific calculator mode with operator precedence, parentheses, a 32-digit display,
  trigonometric, hyperbolic, logarithmic and exponential functions, and DEG/RAD/GRAD angle units.
- Programmer calculator mode with HEX/DEC/OCT/BIN bases, QWORD/DWORD/WORD/BYTE widths,
  two's-complement arithmetic, bitwise operations, shifts and rotates.
- Memory with up to 100 slots and per-mode calculation history capped at 20 entries.
- Date calculation: calendar-aware differences and add/subtract with leap-year and month-end rules.
- Unit conversion for length, area, volume, weight, speed, time, energy, power, data, pressure,
  angle and temperature, and currency conversion using the original mock rates.
- Display formatting with digit grouping, precision rounding and trailing-zero trimming.
- A Swing desktop window and a text interface over the same calculator model.
- Executable specifications: 22 requirements and 56 CodeWitness scenarios in `specs/`.
- MIT license, upstream notice and third-party attribution notices.

### Verification

- CodeWitness run `CW-2026-10-23-7QHQX`: overall `VERIFIED`, 22/22 requirements verified,
  78/78 tests passing, no counterexamples.
- jcheck strict profile (T0–T3): 0 errors, 0 warnings.
- Coverage: 58.2% line, 48.7% branch.

### Known limitations

- The user interface is Swing, not the original XAML; visual states, navigation and accessibility
  features of the UWP application are not reproduced.
- Graphing, the settings page, telemetry and live exchange rates are out of scope.
- Transcendental functions use arbitrary-precision decimal algorithms rather than RatPack; results
  agree to the displayed precision, but a last displayed digit can differ for arbitrary inputs.
