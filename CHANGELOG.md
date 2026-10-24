# Changelog

All notable changes to jCalculator are recorded here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

- `EngineProtocolTest` (10 tests), 4 new `CalculatorManagerTest` cases and 3 new
  `HarnessProtocolTest` cases: **210 tests** in 20 classes, all passing. jcheck `strict` reports
  0 errors and 0 warnings.

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
