# jCalculator requirements

These requirements are the specification for the Java port of the Windows Calculator
application that lives in this repository. They are written so that each one can be executed
against the public API, and `specs/codewitness.yaml` attaches an executable scenario to every
requirement. The identifiers are stable: evidence from one verification run can be compared with
evidence from another only because the ids do not move.

The scope is the calculator's model and behaviour: rational arithmetic, the three calculator
modes, memory, history, date calculation, unit conversion, and the formatting rules the display
uses. The Swing window and the text interface are thin front ends over the same
`CalculatorManager`; they are not separately specified here.

# R-001 Basic arithmetic is exact

Addition, subtraction, multiplication and division of rational numbers must be exact. No result
may be rounded or lose precision because of a fixed machine word or binary floating point. Results
must be normalised, so equal values compare equal regardless of how the fraction was produced.

# R-002 Standard mode evaluates immediately

In standard mode there is no operator precedence. Each binary operator that follows a completed
operand evaluates the pending operation, left to right. The sequence `2 + 3 × 4 =` therefore shows
20, not 14.

# R-003 Scientific mode respects operator precedence

In scientific mode, multiplication and division bind more tightly than addition and subtraction,
and powers and roots bind more tightly still. The sequence `2 + 3 × 4 =` therefore shows 14.

# R-004 Parentheses change evaluation order

Scientific mode must support parentheses. A closing parenthesis evaluates the enclosed expression
before applying the surrounding operator. `(8)2=` is implicit multiplication and shows 16.
`1 + (0 + 3)` evaluates the parenthesised sum first and shows 3. An equals press automatically
closes any parenthesis the user left open.

# R-005 Division by zero is an error, not a value

Dividing zero by zero must display "Result is undefined". Dividing a non-zero value by zero must
display "Cannot divide by zero". In both cases the calculator must enter its error state; it must
not display infinity, a very large number, or zero.

# R-006 Unary functions apply to the displayed value

Square root, square, cube, cube root, factorial, reciprocal and percent apply to the value currently
on display, replace it with the result, and are recorded in the expression line. Repeated square
roots compose. `256 √ √ √` shows 2. `5!` shows 120. `100 ± 1/x` shows -0.01. Percent is relative to
the pending operation: `50 + 20% =` shows 60 and `6 × 6% =` shows 0.36.

# R-007 Transcendental functions are accurate to the displayed precision

Scientific mode computes logarithms, exponentials, powers, roots and trigonometric functions to its
32-significant-digit precision. `ln(10)` must show
`2.3025850929940456840179914546844`, `√2` must show
`1.4142135623730950488016887242097`, `5 ^ 2` must show 25, `8 yroot 3` must show 2, `log(10)` must
show 1, and `sin(90°)` must show 1.

# R-008 Trigonometric functions honour the angle unit

Angle measurement is selectable between degrees, radians and gradians. With radians selected,
`π sin` must show 0 and `π cos` must show -1. With gradians selected, `400 cos` must show 1. The
unit also changes how inverse trigonometric results are reported.

# R-009 Programmer mode works on fixed-width two's-complement integers

The programmer calculator operates on integers of a selected width: QWORD (64 bits), DWORD (32),
WORD (16) or BYTE (8). Arithmetic wraps within that width. In BYTE mode `127 + 1` shows -128.
Division truncates toward zero, so `5 ÷ 2` shows 2 and `5 ÷ 2 × 2` shows 4. In QWORD mode
`5 - 9` shows -4.

# R-010 Bitwise operations are width-bounded

AND, OR, XOR, NAND, NOR, shifts, rotates and the complement operate on the selected width and honour
two's-complement interpretation. `53 NAND 83 AND` shows -18. `5 Lsh 1` shows 10. Rotating 1 left in
QWORD mode shows 2; rotating 1 right sets the sign bit and shows -9,223,372,036,854,775,808.
A shift count that is negative or at least the word width is an error.

# R-011 Bases convert without changing the value

The programmer calculator displays and accepts values in DEC, HEX, OCT and BIN. Switching base must
not change the value, only its representation. Hex FF shows 255 in decimal. In BYTE mode hex FF
shows -1 in decimal because the high bit is the sign bit. Binary and hexadecimal digits are grouped
in fours and octal and decimal digits in threes.

# R-012 Memory stores and recalls values

The memory keys must store the displayed value, recall it, add the displayed value to the most
recent stored value, subtract it, and clear all memory. Up to 100 values are kept, newest first, and
any stored slot can be recalled. Recalling a value replaces the display without entering it as a
typed operand.

# R-013 Completed calculations are kept in history

Every completed equation is recorded with its expression and formatted result. The list is
newest-first and holds at most 20 entries per calculator mode. An intermediate operation that is
completed by pressing a later operator is recorded as its own entry, and the new calculation
continues from its result.

# R-014 Modes keep independent state and histories

Switching between standard, scientific and programmer modes clears the display and starts a fresh
calculation, but each mode keeps its own history. Switching back must not lose the history produced
before the switch.

# R-015 Date difference is calendar-aware

The difference between two dates is reported in whole years, months and weeks plus the remaining
days. It must respect real month lengths and leap years, and it must be symmetric: the difference
from A to B equals the difference from B to A. A day-only format reports the total number of days.
For 1 January 2024 to 31 December 2024 the breakdown is 0 years, 11 months, 4 weeks and 2 days.

# R-016 Adding and subtracting dates is calendar-aware

Adding a duration applies years, then months, then days. Subtracting applies days, then months, then
years. A month-end date clamps to the last day of the target month: 31 January plus one month is
29 February 2024, and 29 February 2020 plus one year is 28 February 2021. A result outside
1 January 1601 through 31 December 9999 is rejected as out of range rather than wrapped.

# R-017 Unit conversion uses the application's conversion factors

Length, area, volume, weight, speed, time, energy, power, data, pressure and angle convert through
their documented factors. `1 ft` is 12 inches, `1 m` is 100 cm, `1 mile` is 1.609344 km, `1 MB` is
1000 kB and `1 L` is 1000 mL. Results are shown with at most 15 significant digits and trimmed of
trailing zeros. A conversion between different categories is rejected.

# R-018 Temperature conversion uses offsets

Celsius, Fahrenheit and Kelvin convert with the correct offsets, not a plain ratio. 100 °C is
212 °F, 32 °F is 0 °C, 0 °C is 273.15 K, and -40 °C is -40 °F. Temperature is one of the categories
that accepts negative input; length, weight and the other non-negative categories reject it.

# R-019 Currency conversion uses the application's rates

The currency converter uses the fictional planetary rates shipped by the open-source build. One
Martian Dollar is 0.50 Moon Bucks, and one Moon Buck is 2.00 Martian Dollars. Conversion is the
source value multiplied by the target rate divided by the source rate.

# R-020 The display formats numbers predictably

Decimal output is grouped in threes with commas and rounds to the mode's precision. Values are
trimmed of trailing zeros: 1.5 is shown as `1.5`, never `1.500000`. Hexadecimal and binary output is
grouped in fours and octal in threes. Error messages are shown instead of numbers while the
calculator is in its error state.

# R-021 An error freezes input until it is cleared

Once an operation fails, further digits and operations must not change the display. Only CLEAR (or
CLEAR ENTRY, which is treated as CLEAR) leaves the error state, after which the calculator accepts
input again and starts from zero.

# R-022 Number entry can be edited

Backspace removes the last typed character, and backspacing past the end of the number leaves zero.
CLEAR ENTRY abandons the number being typed but keeps the pending operation and the rest of the
expression. `12 + 5 CE 2 +` therefore shows 14.

# R-023 The harness session is silent, deterministic and explicit about failure

A `CalculatorSession` drives the calculator without touching the terminal. It accepts the same
button tokens as the text interface, reports its state as an immutable snapshot, and never writes to
standard output, blocks for input or exits the process. A token that is not a button is refused with
an `UnknownTokenException` carrying the token; it is never ignored, because a harness that silently
drops input produces a wrong answer with a success status. Two sessions created with the same seed
produce the same sequence of values for the random key, so a recorded run can be replayed.

# R-024 The JSON harness protocol is line-oriented and machine-readable

`HarnessProtocol` maps one request to one JSON response. A bare line of tokens is a press sequence;
a JSON request selects an operation. Every response has the same shape: `ok` says whether the
operation produced a defined result, `error` says whether the calculator is in its error state,
`errorCode` is a stable machine-readable reason when `ok` is false, and the display, expression, mode,
base, width, angle unit, `result`, history and memory are always present. The process exit code is 0
on success, 1 for a calculation error and 2 for a malformed request.

# R-025 The MCP server exposes the calculator over standard input and output

`McpServer` speaks newline-delimited JSON-RPC 2.0 on standard input and output and writes nothing
else to standard output. It answers `initialize` with a negotiated protocol version and the `tools`
capability, lists the calculator tools with JSON Schema inputs, and executes `tools/call` against the
same session every other front end uses. Notifications produce no response. An unknown method is a
`-32601` error, an unknown tool is `-32602`, and malformed JSON is `-32700`. A tool that runs but
whose calculation fails reports `isError` in its result rather than a protocol error.

# R-026 The desktop window is drivable without a screen

`AutomationSurface` is the window's controller and the window renders it, so the two cannot disagree.
It publishes each page's key layout by label, resolves a click by label against the active page, and
reports the display, expression, error state, mode, base, width and angle unit. A label that the
active page does not show, and a label that is an application action rather than a calculator key,
are both rejected instead of silently doing nothing. The window gives every button its label as both
its component name and its accessible name, and `UiDriver` marshals every action onto the event
thread so a harness observes the repainted state without sleeping.
