# AI Bench

Runs a list of recipes through every listed AI model combination (text→text, text→image, text→speech), as a real account, and reports latency, cost, fallbacks and output quality side by side.

Each run is **(recipe × combo × repeat)** and does what a user would do in the app:

1. Create a fresh recipe. Images are cached per recipe step, so recipes are never reused between combos.
2. **Text→text:** start a process generation job and poll it.
3. Load the result with the frontend's own converter (`recipeProcessGenerationConverter.ts`) and save it through the editor's own save path (`snapshotPersistence.ts`).
4. **Text→image:** run one visualization job per saved process.
5. **Text→speech:** narrate every step.
6. If an admin account is configured, read the server-side numbers for the recipe from AI analytics: queue wait, execution time, tokens and cost.

Models are chosen by setting the user's saved AI preferences before each combo. The preferences are restored afterwards, as are any admin overrides.

## Setup

The bench has no dependencies of its own. It builds with the frontend's toolchain, so run `npm install` in `frontEnd/` first.

```bash
cd tools/ai-bench
npm run build          # -> dist/bench.mjs
npm run typecheck      # optional
```

1. Copy `bench.example.json` to `bench.json` and edit the recipes and combos. List the model keys with `GET /api/v1/ai/models` or in Admin → Models.
2. Put credentials in environment variables, never in the JSON. The config only names the variables. Use `tokenEnv` instead of `emailEnv`/`passwordEnv` to pass a ready JWT.
   - The **user** account runs the recipes. It must be email-verified.
   - The **admin** account is optional. It is needed for:
     - `grantCredits`, so paid models don't silently fall back when credits run out,
     - `allowAdminOverrides`, to temporarily enable non-selectable models such as `gemini-tts`, `ollama-qwen`, `drawthings-sdxl` or `local-tts`,
     - server metrics.

```bash
export BENCH_USER_EMAIL=... BENCH_USER_PASSWORD=...
export BENCH_ADMIN_EMAIL=... BENCH_ADMIN_PASSWORD=...
```

## Run

```bash
node dist/bench.mjs run bench.json --dry-run      # checks accounts, models, overrides; changes nothing
node dist/bench.mjs run bench.json
node dist/bench.mjs run bench.json --only-combo gemini-all --only-recipe pancakes --stages text,image
```

Output goes to `out/<runId>/`:

| File | What |
|---|---|
| `results.jsonl` | One JSON row per run with per-stage status, requested vs. resolved models, fallback, client latency, backend stage timings, warnings/repairs, per-step image/audio results, quality checks and server metrics |
| `summary.csv` | Per combo × stage: p50/p95/max latency, success and fallback rate, server queue/execution time, tokens, cost, average steps/warnings/repairs, image failure rate, audio seconds per step |
| `report.html` | Recipes × combos grid with each run's steps, image thumbnails, audio players and badges |
| `artifacts/` | Raw generation JSON, saved processes, downloaded images and audio |
| `restore.json` | What the run changed and has to put back |

To re-render the report from the results:

```bash
node dist/bench.mjs report out/<runId>
```

If a run is killed hard, Ctrl+C restores state automatically. If restoring didn't happen:

```bash
node dist/bench.mjs restore out/<runId> --config bench.json
```

## Config reference

| Key | Default | Meaning |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | Backend |
| `recipes[]` | — | `{id, file}` (relative to the config) or `{id, text}` |
| `combos[]` | — | `{name, text, image?, speech?, adminConfig?}`. `text` is required; a missing modality skips that stage |
| `combos[].adminConfig` | — | Admin runtime settings to use while this combo runs, by section then key, e.g. `{"images": {"sceneAnchor.enabled": false}}`. Needs an admin account. Checked in the dry run, reverted before the next combo, and restored at the end. Keys are the ones in Admin → Config (`GET /api/v1/admin/config/{section}`) |
| `repeats` | 1 | Runs per recipe × combo |
| `concurrency` | 1 | Parallel runs within a combo. Combos always run one after another because they share the user's preferences. Use 1 for clean latency numbers |
| `grantCredits` | 0 | Credits the admin adds before the run. Not taken back |
| `allowAdminOverrides` | false | Temporarily set `model.<key>.enabled` / `userSelectable` to true for the combos' models |
| `failOnFallback` | false | Exit code 1 if any stage ran on a different model than requested |
| `cleanup` | `keep` | `delete` removes the bench recipes afterwards |
| `downloadAssets` | true | Save images and audio under `artifacts/` |
| `timeouts` | text 600, image 1200, speech 600 | Per-stage polling timeout in seconds |

## Comparing a setting, e.g. the scene anchor

To test the same models with a setting on and off, give two combos the same models and different `adminConfig`:

```json
"combos": [
  { "name": "gemini-anchor-on",  "text": "gemini-flash", "image": "gemini-image" },
  { "name": "gemini-anchor-off", "text": "gemini-flash", "image": "gemini-image",
    "adminConfig": { "images": { "sceneAnchor.enabled": false } } }
]
```

A combo without `adminConfig` runs with the settings as they were before the run. The setting is shown under the combo name in `report.html` and in the `adminConfig` column of `summary.csv`. With the anchor off, expect one image call fewer per recipe and less consistency between step images.

## Notes

- Bench recipes are named `bench <runId> <recipe> <combo> #n`, so they're easy to find in My Recipes. Server metrics join to them by `recipeId`.
- The image row in `summary.csv` includes the text calls the backend makes to write image prompts.
- Exit codes: `0` all ok, `1` a run failed (or fell back, with `failOnFallback`), `2` config or model errors.
