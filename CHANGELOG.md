# Changelog

All notable changes to jCalculator are recorded here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
