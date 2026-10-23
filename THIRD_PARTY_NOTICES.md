# Third-party notices and attributions

jCalculator is a Java port of the Windows Calculator application. This file records what was
derived from other work, the licenses that apply, and what is deliberately not included.

## Windows Calculator (Microsoft)

- **Project:** Windows Calculator — https://github.com/microsoft/calculator
- **Copyright:** Copyright (c) Microsoft Corporation. All rights reserved.
- **License:** MIT — https://github.com/microsoft/calculator/blob/main/LICENSE
- **Local copy:** `../calculator-main` in this repository, and `NOTICE` in this directory.

The Java implementation in `src/` is a derivative work of that project: its architecture follows
the original `CalculatorManager` / `CalcEngine` / `RatPack` layering, its command set and operator
semantics follow the original `Command` table and state machine, and the unit conversion factors,
temperature formulas, date-calculation rules and the fictional-planet currency rates are taken from
the original source and resource data (`UnitConverterDataLoader.cs`, `DateCalculator.cs`,
`CurrencyHttpClient.cs`). The requirements in `specs/requirements.md` describe that behaviour.

The MIT license requires that the copyright notice and permission notice be included in all copies
or substantial portions of the software. They are reproduced in `LICENSE`, and the upstream
`NOTICE` is reproduced verbatim in `NOTICE` as distributed with the original project. The
font-shaping component described in that NOTICE (Hebrew OpenType Layout logic) is not part of this
port; no fonts, icons, XAML, binaries or other assets from the original application are included.

No Microsoft trademark or logo is used, and nothing in this project should be read as claiming
Microsoft endorsement or affiliation. "Windows Calculator" is used only to identify the work that
was ported.

## Runtime data that is deliberately not used

The original README states that Microsoft's retail currency data is not licensed for third-party
use. jCalculator does not use it. The currency converter ships only the open-source build's mock
data, which names fictional planets rather than countries.

## Test-only and build-only dependencies

These are not part of the jCalculator jar and are not redistributed with it. They are listed so a
source distribution has a complete inventory.

| Component | Version | License | Use |
| --- | --- | --- | --- |
| JUnit Jupiter (`org.junit.jupiter:junit-jupiter`) | 5.10.2 | Eclipse Public License 2.0 — https://github.com/junit-team/junit5/blob/main/LICENSE.md | Test compilation and execution only |
| Apache Maven | 3.9.16 | Apache License 2.0 — https://maven.apache.org/license.html | Build tool; vendored in the git-ignored `.tools/` directory for an offline build |
| Maven Compiler, Surefire and Jar plugins | 3.13.0 / 3.2.5 / 3.4.1 | Apache License 2.0 | Build only |

The `.m2repo/` directory is the Maven local repository used by `./mvnw`; it contains downloaded
artifacts (including JUnit and Maven plugins). It is git-ignored. If you redistribute a working
directory rather than the git repository, include the licenses of whatever is present in `.tools/`
and `.m2repo/` and retain Maven's own `NOTICE` and `LICENSE` files.

## Scope of this project's own license

New Java code, tests and specification text in this repository are licensed under the MIT license
in `LICENSE`. The upstream-derived portions remain subject to Microsoft's MIT terms; the MIT text
covers both, and the combined work is distributed under MIT. If you relicense the new code under a
different permissive license, keep `LICENSE`, `NOTICE` and this file so the upstream attribution
still travels with the derived material.
