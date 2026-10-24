# jCalculator requirements

The specification of the Java port of the Windows Calculator that lives in this repository. Each of
the thirty-eight requirements is one observable behaviour, and every one is attached to at least one
executable scenario in `specs/codewitness.yaml`. `specs/README.md` is the handbook: how the two files
fit together, how to add a requirement, and the latest verification result.

## How to read a requirement

A requirement is a heading with a stable id (`R-001` … `R-038`) followed by prose that says what must
be true in the terms a caller can observe. The prose names a class or a method only when that name is
part of the contract, so a refactor that keeps the behaviour does not invalidate the requirement.

An id never moves and is never reused. Verification evidence is compared across runs by id, so adding
a requirement renumbers nothing: new ids go at the end of this file.

A requirement is *VERIFIED* when CodeWitness has built the project, run the whole JUnit suite and
executed every scenario attached to it without a counterexample. It is not verified because someone
read the code — the evidence is the executed scenario.

## Scope

Everything a calculation depends on: exact rational arithmetic, the three calculator modes, memory,
history, date calculation, unit and currency conversion, the display rules, the engine's reporting
protocol and error taxonomy, and the surfaces that drive them — the JSON harness, the MCP server, the
text interface and the desktop window with its pages.

The surfaces are specified because they are what a caller can observe, not duplicated per surface:
the window, the text interface and the protocol are thin shells over one `CalculatorManager`, so a
rule stated once holds for all of them, and a scenario may exercise it through whichever shell is
clearest.

## Non-goals

Three things the original application has are deliberately not reproduced, and no requirement claims
them:

- **Live exchange rates.** The original fetches rates from Bing; that data is not licensed for a
  third-party port. The port converts between fictional currencies with fixed mock rates (R-019).
- **Translation.** The original ships sixty locales. The port is written in English only; the words
  it shows are part of the requirements rather than a resource bundle.
- **Telemetry.** The original traces usage. The port records only the problems its own issue journal
  keeps (R-027), locally and never over the network.

Two implementation differences are also deliberate rather than missing: the desktop window is Swing
instead of XAML, so the original's visual states and animations do not exist; and transcendental
functions are computed with `BigDecimal` at the displayed precision instead of the original's RatPack
algorithms, so a last displayed digit can differ on arbitrary inputs while R-007's accuracy still
holds.

## Index

| Area | Requirements | Count |
| --- | --- | --- |
| Arithmetic, the engine and its reports | `R-001`…`R-011`, `R-020`…`R-022`, `R-030`…`R-032` | 17 |
| Memory and history | `R-012`…`R-014`, `R-033` | 4 |
| Dates | `R-015`, `R-016` | 2 |
| Converter | `R-017`…`R-019`, `R-036` | 4 |
| Surfaces: harness, protocol, MCP, journal | `R-023`…`R-025`, `R-027`…`R-029` | 6 |
| Window, pages and input | `R-026`, `R-034`, `R-035`, `R-037`, `R-038` | 5 |

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

The entry also keeps the buttons that produced it, so the history can re-run a calculation rather
than only describe it: choosing an entry presses the same keys again and reaches the same result. An
entry that was recorded without its buttons says so and re-runs to nothing instead of guessing.

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

The display has room for the mode's precision — sixteen digits on the standard page, thirty-two on the
scientific one — and spends them the way the original does:

- a value with more digits to the left of the decimal point than the display has room for switches to
  exponent form, so `20!` reads `2.43290200817664e+18` at precision sixteen;
- a value below one spends part of the field on its leading zeros, so `sin(1°)` shows fifteen
  significant digits at precision sixteen rather than sixteen (`0.0174524064372835`), and switches to
  exponent form only when the zeros and the digits together would not fit (`1.428571428571429e-7`);
- a value that needs less precision keeps fixed notation however small it is, so `1/10000` reads
  `0.0001`.

A computed value is kept at the working precision the display needs plus guard digits, as the
original keeps them — its sine of thirty degrees carries about twenty-one correct digits at
precision sixteen — so a chain of functions is as close to the original as a single call. The display
then rounds that value once, at its own precision; it must not round to an intermediate precision
first, because a tail like `…849` would carry twice and show `…85`.

Digits are rounded half up and trailing zeros are trimmed, so `1.5` is shown as `1.5`, never
`1.500000`. Decimal output is grouped in threes with commas; hexadecimal and binary in fours and octal
in threes. The forced scientific format always uses an exponent. Error messages are shown instead of
numbers while the calculator is in its error state.

A logarithm whose result is below the working precision of its input snaps to zero, as the original's
logarithm does: `ln` of a value a hair away from one reads `0` rather than a rounding artefact.

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

# R-034 The application moves between pages and remembers its settings

The application is a set of pages over one calculator model: the three keypad pages, the date page,
the converter page and the graphing page. The keypad pages select the session's mode; the other pages
are separate workspaces. Which page is open, and the preferences the user chose, survive a restart.

The preferences are the ones the original's settings page and title bar offer: a light, dark or
follow-the-system colour scheme, and an always-on-top window. They are stored as a plain properties
file beside the issue journal, at the path given by `jcalculator.settings.path` or under
`.jcalculator/`. A settings file is user data, so a corrupted or hand-edited value, an unreadable
file or a failed write never stops the application: an unreadable value falls back to its default,
and a write that cannot be made is reported to the caller rather than thrown.

# R-035 Pasted text is validated for the page before it is entered

A paste is not a sequence of button presses to discover: it is text the user believes is a number or
an expression. The page decides whether it may be pasted, as the original's copy/paste manager does.
The standard page accepts digits, the decimal point, signs, exponent notation, multiplication and
division; the scientific page adds parentheses, the power sign and percent; the programmer page adds
parentheses, percent and the hexadecimal letters, bounded by its current base, so `FF` pasted into a
decimal word and `9` pasted into a binary word are both refused. Whitespace is ignored. An accepted
text becomes the button tokens that enter it — a letter on the programmer page is its hexadecimal
digit, and `e` outside the programmer page is exponent entry — and a refused text is refused whole,
with the caller told what failed, rather than entered in part.

# R-036 The converter suggests the amount in the other units of its category

Under the converter's answer, the same amount is shown in every other unit of the category, as the
original's supplementary results do. The units already in use — the one the value is in and the one
the answer is shown in — are left out. The list is ordered by how close each converted amount is to
one, in orders of magnitude, so the units that read naturally next to the amount come first, and ties
prefer the larger value. Each entry is rounded to two significant digits below one hundred, one
significant digit below one thousand, and to a whole number above that. A unit whose rounded amount
is zero is left out unless the category supports negative values, where zero is a meaningful reading.

# R-037 The programmer page exposes the individual bits of its word

The word is not only a number to be typed: each of its bits can be flipped directly, as clicking a bit
in the original's bit panel does. Flipping a bit changes the current value, keeps the word width and
base, and shows the new value on the display. A bit that does not exist in the current width is
refused with an error naming the width, and flipping a bit outside programmer mode does nothing,
because there is no word on the other pages.

# R-038 The graphing page plots a function of x

A function of `x` can be sampled and drawn. The expression is read with the calculator's own
vocabulary — `x^2`, `sin(x)`, `1/x`, parentheses for grouping — and each sample is evaluated by the
same engine the keypad drives, so the plot obeys the page's arithmetic, precedence and angle unit
rather than a second, private parser. A function call is rewritten into keypad order, because a
function is pressed after its operand. A sample the engine refuses, such as the point where `1/x`
divides by zero or `ln(x)` leaves its domain, is omitted from the plot so the graph shows the gap
instead of a false zero. An unusable range, fewer than two samples, an unbalanced parenthesis or text
that is not part of a function is refused with an explanation.
