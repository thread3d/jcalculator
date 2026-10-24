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

# R-027 Problems met in use are recorded, not lost

Every front end keeps a durable journal of the problems met while the calculator is in use, so a
later session can find them instead of relying on someone's memory. An unknown token, an unknown
operation and an unexpected internal failure are recorded automatically, together with the surface,
the input and the state that produced them, and a caller can also report a problem in its own words.
Repeats of the same problem fold into one entry with an occurrence count and a last-seen time.
Entries can be listed and resolved with a disposition and a note. The journal is append-only JSON
Lines: the location is selected by `JCALC_ISSUES` or `jcalculator.issues.path` (the launchers pin it
inside the project), a damaged line is counted and ignored instead of losing the rest, and a journal
that cannot be written never changes a calculation or a response.

# R-028 A token means what the active page's keypad shows

A token names a button. A digit is a digit on every page, and a named key is a button whose effect
the active page decides: a function the page does not offer is ignored, as the original does. Single
letters that could be hexadecimal digits are page-specific and are never guessed. `e` is Euler's
constant on the standard and scientific pages and the hexadecimal digit `E` on the programmer page;
`c` clears outside the programmer page (where the key is labelled `C`) and is the hexadecimal digit
`C` on it. The letters `a`, `b`, `d` and `f` are buttons only on the programmer page. Pressing one of
those outside the programmer page is refused with an `UnknownTokenException` whose message names the
token and says that the hexadecimal digits belong to the programmer page, rather than reporting
success with an unchanged display. Capability discovery still lists the whole vocabulary, which is the
union across pages.

# R-029 A malformed request field is refused, never reported as an internal failure

A request whose field has the wrong type or a value the calculator cannot accept is the caller's
mistake, not a fault in the calculator. The JSON protocol answers it with `bad_request` and a message
that says what the field must be. `date_add` and `date_subtract` accept only whole years, months and
days in 32-bit range, so a fractional or oversized day count is refused before any calendar
arithmetic. `press` requires `tokens` to be an array of button names and `expression` to carry the
whitespace-separated string form; a `tokens` value that is a string, or an entry that is not a string,
is refused with a message that names the expected shape instead of complaining that both fields are
missing. A malformed request is not recorded in the issue journal, because the response already
explains it; `internal_error` is reserved for a failure the calculator did not expect.


# R-030 The engine reports through the original's display protocol

The engine never touches a view. It reports the primary display and whether it is the error text, the
expression as a stream of tokens, the parenthesis depth, and each completed history entry. A token
keeps the command behind it as well as its text, because the original hands its presentation layer a
pair of text and command for every part of the expression, so a view can treat an operator differently
from an operand. Three further reports are signals rather than failures: a digit refused because the
display is full, a right parenthesis pressed with nothing open to close, and a binary operator
arriving. The manager implements the sink, exposes the parenthesis depth, and remembers the last
signal so an automation surface can tell that the calculator complained without treating the
complaint as an error. A signal never changes the display and never sets the error state.

# R-031 A failure carries a code as well as the text the display shows

Every failure the engine can raise is named, so a caller can tell one from another without matching on
display text. The names follow the original's error codes: `divide_by_zero`, `undefined` (both sides
of a quotient were zero), `overflow`, `domain` (the input is outside the function's domain) and
`invalid_range` (a value the operation cannot accept, such as a shift wider than the word). The code is
readable while the error is in force and clears when the user clears, and the text shown on the display
is the one the calculator has always shown: `Cannot divide by zero`, `Result is undefined`, `Overflow`
and `Invalid input`. The domain and range codes deliberately share that last text, exactly as the
original's domain and invalid-range results map to one resource string.

# R-032 The expression line is a token stream and keeps the completed equation

The expression line is built from tokens, not from a pre-rendered string, so the display layer receives
the same structure the engine used to build it. Pressing equals leaves the completed equation on the
line until the next key. Pressing an operator over a pending one replaces it; when the replacement binds
more tightly than the operator before it, the part that was already computed is enclosed instead of
being left ambiguous, so `1 × 2 +` followed by `^` reads `(1 × 2) ^` and not `1 × 2 +`. The replaced
operator does not survive inside the brackets.

# R-033 Memory slots are edited individually

A memorized value is a slot in a list, newest first, and every memory key acts on the slot it is given
rather than only on the newest one: `MS` stores a new slot, and a slot can be loaded, added to,
subtracted from and removed by index. Adding to or subtracting from an empty memory stores the displayed
value first, which is what the original does when there is no memory to adjust; subtracting from an
empty memory therefore leaves the negation of the displayed value. Removing a slot that does not exist
is harmless, while asking the protocol to edit a slot that does not exist is refused as a
`bad_request` naming the index. The arithmetic stays in the engine: the manager selects the slot into
the engine's immediate memory, runs the memory command there and copies the result back, as the
original's memory functions do. Every change to the list — a stored slot, an adjusted slot, a removed
slot — raises the `memory_changed` signal, so a presentation layer can redraw the memory list without
diffing it, and a recall raises `input_changed` because it changes the display without a keystroke.
