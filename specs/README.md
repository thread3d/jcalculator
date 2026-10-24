# jCalculator specifications

This directory is the specification, and the evidence that the code meets it. Two files:

- **`requirements.md`** — thirty-eight requirements, `R-001` … `R-038`. Each one is a single
  observable behaviour, written so that it can be checked against the public API rather than read
  and agreed with. The identifiers never move.
- **`codewitness.yaml`** — the CodeWitness configuration: eighty-eight executable scenarios, at least
  one attached to every requirement. A scenario is Java source embedded in YAML; it sets up the
  public API, performs one interaction, and asserts an invariant.

## How the two files fit together

A requirement is a claim. A scenario is the check. The link is the `requirement:` field on the
scenario, and it is what CodeWitness follows when it decides whether a requirement is verified:

```
requirement R-036  ──declared on──▶  SC-CONVERTER-SUPPLEMENTS  ──executes──▶  observation / invariant
```

A requirement is **VERIFIED** when CodeWitness has built the project, run the whole JUnit suite and
executed every scenario attached to it without a counterexample. Nothing is verified by inspection.
The verdict is per requirement, so a broken behaviour shows up as one failed requirement with a
counterexample rather than as a vague "tests failed".

The JUnit suite and the scenarios are different evidence and both count. The suite is the fast,
detailed check a developer runs on every change; the scenarios are a small number of black-box claims
that must hold through the public API. A behaviour with a test but no scenario is under-specified; a
scenario with no test is unusual and usually means the behaviour is thin.

## What a scenario is

```yaml
- id: SC-CONVERTER-SUPPLEMENTS          # stable, unique
  requirement: R-036                    # the claim it checks
  description: ...                      # what a reader should understand
  mode: single
  setup: "... Java ..."                 # declarations, run once
  steps:
    - call: "... Java ..."              # the interaction
  observation: "... an expression ..."  # what to look at
  invariant: "... a boolean expression ..."  # what must be true
```

Two rules cause most of the lost time when writing one:

1. **The invariant is checked after every step, not once at the end.** The steps and the check are
   emitted inside one loop, so the observation must compile and hold after step 1 as well. Putting
   the whole interaction in a single `call` is the reliable way to satisfy that, and every scenario
   in this file does.
2. **The bodies are Java inside YAML double-quoted strings**, so `"` and `\` must be escaped. A
   mis-escaped body fails as a Java compile error inside the isolated workspace, which is slow to
   read; round-trip the file through a YAML parser after editing it.

A third rule is about budgets rather than syntax: `verification.max_generated_tests` is a budget for
the whole run, not a per-scenario limit. Add scenarios past it and the extras are silently *not
executed*, and their requirements come back `UNVERIFIED` with no counterexample to say why. Raise the
cap with the scenario count.

## Running the checks

Three checks, in this order; all three must be green before a release.

```bash
# 1. the suite
cd jcalculator
./mvnw -o test

# 2. static analysis, from the jcalculator directory (its Maven home is relative to the cwd)
/Users/threaded/projects/JCheck/jcheck/jcheck check --root . --message-format human

# 3. the specification, in copy mode so the tree stays clean
cd ..
JAVA_HOME=/Users/threaded/projects/CodeWitness/.tools/jdk-25 \
  /Users/threaded/projects/CodeWitness/codewitness/codewitness verify jcalculator \
  --requirements jcalculator/specs/requirements.md \
  --config jcalculator/specs/codewitness.yaml --max-duration 30m
```

CodeWitness copies the project into an isolated workspace, builds it with the project's own Maven
wrapper, runs the JUnit suite, then compiles and runs the scenario tests. The generated tests are
removed from the project afterwards and kept in the run directory.

## Latest result

Run `CW-2026-10-24-FXN02` (copy mode, not in place):

| Measure | Result |
| --- | --- |
| Overall | `VERIFIED` |
| Requirements | 38 `VERIFIED`, 0 `PARTIALLY_VERIFIED`, 0 `FAILED`, 0 `UNVERIFIED`, 0 `BLOCKED` |
| Existing tests | 393 executed, 393 passed |
| Scenario tests | 90 generated from `codewitness.yaml`, all passed |
| Counterexamples | none |
| Static analysis | 0 compiler findings; 14 medium heuristic findings |
| Coverage | 85.1% line, 73.2% branch |

The JSON and HTML reports are in `.codewitness/runs/CW-2026-10-24-FXN02/report.json` and
`report.html`. The run directory is git-ignored because it is generated evidence, not source.

## Traceability

Which requirements each area covers, how many scenarios carry them, and where the behaviour is
exercised in the suite. The scenario ids are in `codewitness.yaml`; the test classes are under
`src/test/java`.

| Area | Requirements | Scenarios | Exercised by |
| --- | --- | --- | --- |
| Exact arithmetic, precedence, entry and display | `R-001`…`R-004`, `R-020`…`R-022` | 14 | `RationalTest`, `StandardCalculatorTest`, `ScientificCalculatorTest`, `NumberFormatterTest`, `CommandTest`, `OperatorPrecedenceTest` |
| Unary, transcendental and trigonometric functions | `R-005`…`R-008` | 15 | `RationalMathTest`, `UnaryFunctionsTest`, `EngineProtocolTest`, `ScientificCalculatorTest` |
| Programmer mode and its word | `R-009`…`R-011`, `R-037` | 10 | `ProgrammerCalculatorTest`, `BitMathTest`, `CalculatorWindowShellTest` |
| Memory and history | `R-012`…`R-014`, `R-033` | 7 | `CalculatorManagerTest`, `CalculatorSessionTest` |
| Dates | `R-015`, `R-016` | 5 | `DateCalculationEngineTest`, `CalculatorWindowShellTest` |
| Converter | `R-017`…`R-019`, `R-036` | 9 | `UnitConverterTest`, `CalculatorWindowShellTest` |
| Harness session, protocol and MCP | `R-023`…`R-025`, `R-029` | 8 | `CalculatorSessionTest`, `HarnessProtocolTest`, `HarnessCliTest`, `McpServerTest`, `McpServerTransportTest`, `JsonTest` |
| Tokens and journal | `R-027`, `R-028` | 5 | `TokensTest`, `IssueJournalTest`, `HarnessProtocolIssueTest`, `CalculatorCliTest` |
| Engine reporting, errors, expression, bits | `R-030`…`R-032` | 7 | `EngineProtocolTest`, `CalculatorManagerTest`, `HarnessProtocolTest` |
| Pages, settings, paste and graphing | `R-026`, `R-034`, `R-035`, `R-038` | 8 | `SettingsStoreTest`, `ClipboardTextTest`, `FunctionPlotterTest`, `AutomationSurfaceTest`, `UiDriverTest`, `CalculatorWindowShellTest` |

## Coverage

CodeWitness measures line and branch coverage of the production sources with JaCoCo while the suite
runs. The number is a signal about which behaviour is cheap to break unnoticed, not the verdict — the
verdict comes from executed scenarios.

What is deliberately not covered, and why it is acceptable:

- **The Swing window's listeners** are exercised through the window tests where they can be, but the
  dialogs and menu actions a person clicks are only partly reachable without a screen.
- **The `main` methods** of the launcher-style front ends exit the process, which cannot be measured
  from inside it. Their behaviour is covered below `main` instead: `CalculatorCli.run` answers an
  exit code, and that is what the tests drive.
- **One branch of `RationalMath.pow`** refuses an exponent above 100 000; reaching it would mean
  building a number with a hundred thousand digits, which the test suite has no reason to do.

## The heuristic findings

CodeWitness reports fourteen medium `heuristics` findings. None of them is a failing requirement, and
all were reviewed:

- Nine `CW-CONC-001` findings: a field is read in a condition and written in the same class.
  `CalcEngine`, `CalcInput`, `ExpressionBuilder` (`lastCompletedExpression`), `CalculatorManager`,
  `UnitConverter`, the CLI's running flag, the calculator page's selector guard and the converter
  page's unit-filling guard are single-threaded objects, and `IssueJournal`'s accumulator is created
  and used entirely inside one read. A Swing application calls them from the event dispatch thread,
  and the tests construct one instance per test. They are not shared between threads, so there is no
  check-then-act race. The fields are deliberately not `volatile`: making them thread-safe would
  suggest a sharing contract the model does not offer.
- Four `CW-DATE-001` findings match the phrase "calendar" in `DateCalculationEngine` and warn about a
  rolling-window limit. That rule comes from another domain; this engine's contract is explicitly
  calendar-aware difference and clamping (`R-015` and `R-016`), and the scenarios check those
  properties.
- One `CW-NUM-001` finding says "the result of `BigDecimal.add(...)` is discarded" in `UnitConverter`.
  The two calls it points at are `scored.add(...)` and `result.add(...)` on `ArrayList`s: the
  heuristic matches the method name without the receiver's type. The results are kept, not discarded.

The 1.3.0 run also reported three `MAINTAINABILITY` findings claiming a test had no assertion (the
assertion followed the loop that built the state). They did not recur.

`jcheck` reports zero findings in its strict profile for the same sources. CodeWitness's heuristics
are intentionally noisy and advisory.

## Adding a requirement

1. **Append** `# R-0NN <one sentence>` and its prose to `requirements.md`. Never renumber; new ids go
   last.
2. **Add a scenario** to `codewitness.yaml` for the new id, following the two rules above.
3. **Add a JUnit test** for the behaviour, in the package that owns it.
4. **Run the three checks** and update the table above with the new run id and counts.
5. If the behaviour changes a public shape (a record's components, a listener's methods, a protocol
   field), say so in `CHANGELOG.md` under *Changed* — that is what decides whether the next release
   is a minor or a major one.

## Non-goals

Three things the original has are deliberately absent, and no requirement claims them: live exchange
rates (the data is not licensed for a third-party port; the port uses fixed mock rates, `R-019`),
translation into the original's sixty locales, and telemetry. Two differences are implementation
choices rather than gaps: the window is Swing instead of XAML, and transcendental functions are
computed with `BigDecimal` at the displayed precision rather than with the original's algorithms, so
a last displayed digit can differ while `R-007` still holds.

## Glossary

| Term | Meaning here |
| --- | --- |
| **Requirement** | One observable behaviour, identified by a stable `R-0NN` id. |
| **Scenario** | An executable check of one requirement, declared in `codewitness.yaml`. |
| **Invariant** | The boolean expression a scenario asserts; it must hold after every step. |
| **Counterexample** | A scenario that executed and broke its invariant. It fails the requirement. |
| **VERIFIED** | Every scenario of a requirement executed on a fresh build without a counterexample. |
| **Engine** | `CalcEngine` and the units it delegates to: the command-driven state machine. |
| **Surface** | A front end over one `CalculatorManager`: the window, the text interface, the JSON harness, the MCP server. |
| **Journal** | The local, git-ignored record of problems met in use (`R-027`). |
