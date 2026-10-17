# Frontend Code Structure

How the Virtual Kitchen frontend is organised: what each folder owns, which way folders may import each other,
and the conventions that keep it that way.

## Tech stack

- React 19 + TypeScript, built with Vite 8
- React Router 7 (`BrowserRouter`, nested routes)
- `@xyflow/react` (React Flow) for the recipe process editor
- Tailwind 4 for layout/spacing, plain CSS files for component styling, `--flow-*` CSS variables for light/dark themes
- `node --test` unit tests in `tests/` (pure modules only; runtime imports there carry a `.ts` extension)

## Folder map

```text
src/
├── main.tsx          mounts the providers (theme, notifications, confirm, AI job tracker) and <App />
├── app/              the app shell
│   ├── App.tsx            the route table + signed-in / admin guards
│   ├── routes/            route components that read URL params and wire features together
│   └── session/           useAppSession (restore / login / logout / 401) and the SignedIn context
├── api/              HTTP client (auth header, 401 → sign-out, envelope unwrapping) + one module per backend area
├── types/            domain types shared across features (kitchen, recipe, process, recipe order, …)
├── shared/           feature-independent building blocks
│   ├── catalog/           step & ingredient catalogs (stepCatalogs.data.json is also read by the backend build)
│   ├── components/        confirm dialog, notifications, payment gateway, ActionMenu, ListEnd, SearchableSelect, …
│   ├── format/            money, date, number formatting
│   ├── hooks/             useApiData (load/reload/mutate), useIncrementalReveal
│   ├── cart/  drafts/  theme/
│   └── errors.ts          errorMessage(error, fallback)
├── styles/           global.css (Tailwind), theme-tokens.css, flow-ui.css (shared flow-* component classes)
└── features/
    ├── home/              landing page
    ├── auth/              login / signup / OTP / password reset
    ├── kitchen/           layout/ (navbar), inventory/, shop/ (catalog, cart, checkout), orders/ (order history)
    ├── recipes/           recipe library page, its components and useRecipeLibrary
    ├── recipe-order/      order wizard: confirm → ingredients → payment → tracking → receipt   (public API: index.ts)
    ├── recipe-tool/       the recipe tool                                                       (public API: index.ts)
    └── admin/ai-analytics/ admin AI cost & usage dashboards
```

### Inside `features/recipe-tool`

```text
recipe-tool/
├── index.ts          everything other features and the app may use
├── recipeToolRoutes.ts
├── page/             RecipeToolPage (editor ⇄ view tabs), its navbar, summary and edit-details dialog
├── model/            the step/condition data model and graph logic, shared by every folder below
│                     (incl. preparationFlows / mergePreparations: the one Preparation and its flows,
│                     recipeReadiness: whole-recipe Preparation + Main checks)
├── session/          RecipeSessionProvider: the one in-memory editing session (autosave, undo/redo,
│                     crash recovery in useRecoveryMirror, generated images in useVisualsApplier,
│                     live readiness + the gate for ordering/cook mode in useRecipeReadiness)
├── jobs/             JobTracker: app-wide polling of AI generation, visuals and workflow jobs
├── editor/           the React Flow editor: components/, hooks/, adapters/ (process ⇄ canvas, AI edits),
│                     model/ (layout, slides, deletion wording), nodes/, context/, styles/
├── view/             the read-only "Recipe Process" view (recipeView.ts builds it from the graphs)
├── walkthrough/      step slideshow + narration player (used by the editor's Preview and the view's cook mode)
├── workflow/         AI Recipe Creation (generate → approve → visuals → narration)
├── process-list/     the standalone list of a recipe's processes
└── styles/           modals.css, RecipePropertiesPanel.css
```

## Dependency rules

Imports only go downward; there are no cycles between folders.

```text
app ──► features ──► shared / api / types
kitchen, recipes ──► recipe-order ──► recipe-tool          (other features: no feature imports)
recipe-tool: page ──► editor / view / workflow ──► session / jobs / walkthrough ──► model
```

- Code outside `recipe-tool` or `recipe-order` imports only from that feature's `index.ts`.
- `recipe-tool` never imports another feature: actions it needs from elsewhere (e.g. "Order recipe") are passed in
  as props by the app's route components.
- `shared` never imports a feature; `api` only imports `types`.

## Conventions

- **Data loading:** `useApiData(key, load)` for fetch-on-mount/reload (it handles cancellation, stale responses
  and derived `loading`); don't hand-write `loading`/`error` state with a fetch inside `useEffect`.
- **Errors:** show `errorMessage(error, 'fallback')`; `ApiError` carries the backend's message and status
  (`httpStatusOf`). A 401 on a signed-in request signs the user out (`api/authToken.ts`).
- **Contexts:** keep the context object and its `useX` hook in a `.ts` file and the provider component in a
  `.tsx` file (React Fast Refresh needs component-only files).
- **Types:** shared domain types go in `types/`; a type used by one feature stays in that feature.
- **Styles:** Tailwind for layout; a component's own CSS file for its look; `--flow-*` tokens for colours.

## Request flow

```text
Route (app/routes) → feature page → hook (useApiData / feature hook) → api/<area>.ts → api/client → backend
```

Slow AI work runs as backend jobs; `recipe-tool/jobs/JobTracker` polls them app-wide so progress survives
navigation and reloads.
