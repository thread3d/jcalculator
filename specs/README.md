# jCalculator specifications

This directory contains the specification and its executable evidence.

- `requirements.md` — the specification. Each requirement has a stable id (`R-001` … `R-033`) and
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

Run `CW-2026-10-24-G2KEK` (copy mode, not in place):

| Measure | Result |
| --- | --- |
| Overall | `VERIFIED` |
| Requirements | 33 `VERIFIED`, 0 `PARTIALLY_VERIFIED`, 0 `FAILED`, 0 `UNVERIFIED`, 0 `BLOCKED` |
| Existing tests | 341 executed, 341 passed |
| Scenario tests | 80 generated from `codewitness.yaml`, all passed |
| Counterexamples | none |
| Static analysis | 0 compiler findings; 12 medium heuristic findings |
| Coverage | 85.6% line, 72.3% branch |

The JSON and HTML reports are in
`.codewitness/runs/CW-2026-10-24-G2KEK/report.json` and `report.html`. The run directory is
git-ignored because it is generated evidence, not source.

Coverage is measured over the sources the existing suite already builds; the untested remainder is
mostly the Swing window's listeners, the `main` methods of the two launcher-style fronts (which exit
the process), and the branch of `RationalMath.pow` that refuses an exponent above 100 000.

## The heuristic findings

CodeWitness reports twelve medium `heuristics` findings. None of them is a failing requirement, and
all were reviewed:

- Eight `CW-CONC-001` findings: a field is read in a condition and written in the same class.
  `CalcEngine`, `CalcInput`, `ExpressionBuilder` (`lastCompletedExpression` after 2.0.0),
  `CalculatorManager`, `UnitConverter`, the CLI and the window's refresh guard are single-threaded
  model objects, and `IssueJournal`'s accumulator is created and used entirely inside one read. A
  Swing application calls them from the event dispatch thread, and the tests construct one instance
  per test. They are not shared between threads, so there is no check-then-act race. The fields are
  deliberately not `volatile`: making them thread-safe would suggest a sharing contract the model
  does not offer.
- Four `CW-DATE-001` findings match the phrase "calendar" in `DateCalculationEngine` and warn about a
  rolling-window limit. That rule comes from another domain; this engine's contract is explicitly
  calendar-aware difference and clamping (R-015 and R-016), and the scenarios check those
  properties.

The 1.3.0 run also reported three `MAINTAINABILITY` findings claiming a test had no assertion (the
assertion followed the loop that built the state). They did not recur in this run.

`jcheck` reports zero findings in its strict profile for the same sources. CodeWitness's heuristics
are intentionally noisy and advisory; the verdicts above are decided by executed scenarios, not by
those findings.
