# Changelog

All notable changes to jCalculator are recorded here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
