# jCalculator specifications

This directory contains the specification and its executable evidence.

- `requirements.md` — the specification. Each requirement has a stable id (`R-001` … `R-022`) and
  states one observable behaviour of the application.
- `codewitness.yaml` — the CodeWitness configuration. Every requirement has at least one declared
  scenario. A scenario sets up the public API, performs the calls and checks an invariant; it is
  written in Java because the project is Java.

## Running the verification

From the `jcalculator` directory:

```bash
# static analysis (T0–T3 under jcheck's strict profile)
jcheck check --root .

# tests
./mvnw test

# specifications, built and executed by CodeWitness
codewitness verify . \
  --requirements specs/requirements.md \
  --config specs/codewitness.yaml
```

Or from the repository root, where `jcalculator` is the project:

```bash
codewitness verify jcalculator \
  --requirements jcalculator/specs/requirements.md \
  --config jcalculator/specs/codewitness.yaml
```

CodeWitness copies the project into an isolated workspace, builds it with the project's own Maven
wrapper, runs the JUnit suite, then compiles and runs the scenario tests. The scenario tests are
removed from the project after the run and kept in the run directory.

## Latest result

Run `CW-2026-10-23-7QHQX` (copy mode, not in place):

| Measure | Result |
| --- | --- |
| Overall | `VERIFIED` |
| Requirements | 22 `VERIFIED`, 0 `PARTIALLY_VERIFIED`, 0 `FAILED`, 0 `UNVERIFIED`, 0 `BLOCKED` |
| Existing tests | 78 executed, 78 passed |
| Scenario tests | 56 generated from `codewitness.yaml`, all passed |
| Counterexamples | none |
| Static analysis | 0 compiler findings; 11 medium heuristic findings |
| Coverage | 58.2% line, 48.7% branch |

The JSON and HTML reports are in
`.codewitness/runs/CW-2026-10-23-7QHQX/report.json` and `report.html`. The run directory is
git-ignored because it is generated evidence, not source.

## The heuristic findings

CodeWitness reports eleven medium `heuristics` findings. None of them is a failing requirement, and
all were reviewed:

- Eight are the same `CONCURRENCY` heuristic: a field is read in a condition and written in the same
  class. `CalcEngine`, `CalcInput`, `ExpressionBuilder`, `CalculatorManager`, `UnitConverter` and
  the CLI are single-threaded model objects. A Swing application calls them from the event dispatch
  thread, and the tests construct one instance per test. They are not shared between threads, so
  there is no check-then-act race. The fields are deliberately not `volatile`: making them
  thread-safe would suggest a sharing contract the model does not offer.
- Two `CORRECTNESS` findings match the phrase "calendar" in `DateCalculationEngine` and warn about a
  rolling-window limit. That rule comes from another domain; this engine's contract is explicitly
  calendar-aware difference and clamping (R-015 and R-016), and the scenarios check those
  properties.
- One `MAINTAINABILITY` finding claims a test has no assertion. It points at
  `CalculatorManagerTest.historyIsCappedAtTwentyEntries`; the assertion is after the loop that
  builds the history (`assertEquals(MAX_HISTORY_SIZE, history().size())`).

`jcheck` reports zero findings in its strict profile for the same sources. CodeWitness's heuristics
are intentionally noisy and advisory; the verdicts above are decided by executed scenarios, not by
those findings.
