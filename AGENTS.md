# Repository Guidelines

A dorm-repair system with three independent Vue frontends, a Java backend and a Python AI service. Production code is in `app/`; `prototype/` is a frozen visual and behavioral reference.

## Project Structure & Module Organization

- `app/server/`: Spring Boot 4 / Java 17 backend. Code is under `src/main/java/com/zxp/dormrepair/`; JUnit tests are under `src/test/java/`.
- `app/student-web/`, `app/worker-web/`, `app/admin-web/`: independent Vue 3 + Vite applications for students (5173), workers (5174), and admins (5175). Shared display contracts live in `app/web/packages/contract/`; the rest of `app/web/` is a legacy reference app.
- `app/ai/`: FastAPI classifier service. Keep pytest files beside their modules as `test_*.py`.
- `prototype/`: offline reference only; do not add production features.
- `docs/`: source docs and generators. Edit `docs/tools/` rather than generated files in `docs/05_交付文档/`.

## Build, Test, and Development Commands

```powershell
# Backend
cd app\server
mvn -q test
mvn -q -DskipTests package
java -jar target\dorm-repair-server-1.0.0.jar

# Each frontend (student-web / worker-web / admin-web)
cd app\student-web
npm install
npm run dev
npm run build

# Legacy shared frontend unit tests (reference only)
cd app\web
npm run test:unit

# AI service
cd app\ai
python -m uvicorn app:app --host 127.0.0.1 --port 8001
python -m pytest -q
```

`start.bat` starts AI and backend only; start each of the three Vite applications separately. Shared code must not reintroduce role pages or authentication state across the three applications. With MySQL and services running, execute the live checks in `app\server\tests\` (`m1-chain.ps1` through `m5-auth.ps1`).

## Coding Style & Naming Conventions

Use four spaces in Java/Python and two spaces in JavaScript/Vue; follow the existing no-semicolon JavaScript style. Use PascalCase for classes and Vue components, camelCase for Java methods/fields and JavaScript identifiers, and snake_case for Python functions. No formatter or linter is configured; match nearby code and keep diffs focused.

Change state only through `OrderStateService.transit()` and define legal edges only in `TransitionTable.java`. Route HTTP calls through each application request module, reuse shared status constants, and do not invent API fields or hard-code thresholds.

## Testing Guidelines

Current verified baselines: Java 132 tests (2 external-service tests skipped), AI pytest 31, legacy frontend Vitest 8, prototype selftest 34. Regenerate delivery artifacts with `docs/tools/build_docs.py`, `build_extras.py`, `build_cg08.py`, then run `check_delivery.py`.

Use JUnit 5/MockMvc (`*Test.java`), Vitest (`*.test.js` or `*.spec.js`), and pytest (`test_*.py`). No coverage threshold is enforced. Test state transitions, optimistic locking, authorization, AI fallback, and rejected paths; rejected transitions must leave state unchanged. Never weaken assertions to make tests pass.

## Commit & Pull Request Guidelines

Use Conventional Commits: `type(scope): concise Chinese summary` with `feat`, `fix`, `refactor`, `test`, `docs`, `style`, or `chore`. Explain the reason and behavior change in the body. Pull requests should state scope, list verification commands/results, note API/schema/configuration changes, and include screenshots for UI changes. Link related issues when available.

## Configuration & Security

Copy `app/ai/.env.example` to `app/ai/.env.local`; never commit keys or local environment files. Configure with `DB_*`, `APP_STORE`, and `AI_*` variables. Keep `APP_AUTH_ALLOW_HEADER=false` outside tests/demos. Do not modify `docs/99_存档/` or `prototype/reference/`.