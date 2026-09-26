# Virtual Kitchen — Branch Cut `virtual-kitchen-v.01(NP)`

| | |
|---|---|
| **Branch** | `virtual-kitchen-v.01(NP)` — *NP = Not Production-level* |
| **Cut from** | `master` @ `fe52889` (Merge PR #36 — `feature/recipeCanvasProcessViewModelTool`) |
| **Cut date** | 2026-09-26 |
| **History** | 130 commits, 36 merged PRs, 2026-04-30 → 2026-09-26 |
| **Status** | Feature-complete prototype for internal demo / testing. **Not for production deployment** — see [§9 Known Gaps](#9-known-gaps--why-this-cut-is-np). |

---

## Table of Contents

1. [What is Virtual Kitchen](#1-what-is-virtual-kitchen)
2. [Architecture & Tech Stack](#2-architecture--tech-stack)
3. [Running Locally](#3-running-locally)
4. [Navigation Map](#4-navigation-map)
5. [Features — Screen by Screen](#5-features--screen-by-screen)
6. [AI Subsystem (Models, Credits, Jobs, Artifacts)](#6-ai-subsystem)
7. [API Reference (Input / Output / Usage)](#7-api-reference)
8. [Data Model](#8-data-model)
9. [Known Gaps — Why This Cut Is NP](#9-known-gaps--why-this-cut-is-np)
10. [Milestone History (PRs in this cut)](#10-milestone-history)

---

## 1. What is Virtual Kitchen

Virtual Kitchen is a recipe **process-visualisation** platform. A user:

1. Signs up and automatically gets their own **virtual kitchen**.
2. Stocks the kitchen by **shopping** ingredients and equipment (dummy checkout) — purchases land in **inventory**.
3. Builds **recipes** as visual flow graphs — manually on a canvas, or by pasting a recipe in plain English and letting **AI generate** the flow.
4. Breaks a recipe into a **main process** plus reusable **subprocesses** (e.g. "Make masala" used by the main "Cook curry" process).
5. **Generates AI images** for every step and plays them back as a **step-by-step slideshow**.
6. Shares recipes **publicly** so others can browse them under **Global Recipes** and copy them into their own collection.

AI usage is metered through a monthly **AI credit** balance; when credits run out, the system transparently falls back to free open-source models.

---

## 2. Architecture & Tech Stack

```
┌─────────────────────┐      REST/JSON + JWT      ┌──────────────────────────┐
│ frontEnd (React/TS) │ ────────────────────────▶ │ backend (Spring Boot 4)  │
│ Vite · :5173        │ ◀──────────────────────── │ :8080 · Swagger at /ui   │
└─────────────────────┘                           └────────────┬─────────────┘
                                                               │
          ┌──────────────┬────────────────┬──────────────┬─────┴────────┬───────────────┐
          ▼              ▼                ▼              ▼              ▼               ▼
      MongoDB        Gemini API        Ollama        Supabase        SMTP          Google OAuth
   (data + GridFS)  (text + image)  (local qwen3)  (image storage)  (OTP mail)    (tokeninfo)
```

| Layer | Stack |
|---|---|
| **Frontend** (`frontEnd/`) | React 19.2, TypeScript 6, Vite 8, react-router-dom 7, **@xyflow/react 12** (flow canvases), Tailwind CSS 4 |
| **Backend** (`backend/`) | Java 17, Spring Boot 4.0.6 (webmvc, security, data-mongodb, mail, validation, actuator), jjwt 0.12.6, springdoc-openapi 3, Lombok |
| **Database** | MongoDB (sequential `Long` ids via `database_sequences`), GridFS bucket `ai_artifacts` |
| **AI providers** | Gemini (paid text + image), Ollama `qwen3:4b` (free text fallback), DrawThings SDXL (free image fallback — configured but disabled), OpenAI (client present, disabled) |
| **Storage** | Supabase Storage for generated step images |
| **Python** (`Services/pythonServices/`) | OpenCV/NumPy green-screen + video-merge **prototype library** — *not a service, not wired to the backend* |

### Backend module layout (`backend/src/main/java/com/processVisualisation/virtualKitchen/`)

| Package | Responsibility |
|---|---|
| `auth` | Signup, login (password / OTP / Google), JWT, users |
| `kitchen` | Kitchens, inventory, orders (checkout) |
| `store` | Global catalog: ingredients, equipment, item cost |
| `recipe` | Recipes (templates), processes (MAIN/SUBPROCESS graphs), legacy flows, visualization, legacy execution tracking |
| `ai` | Model registry & routing, credits, request queue, generation services, artifact store |
| `restclient` | HTTP clients for Gemini, OpenAI, Ollama, DrawThings, Supabase |
| `common` | Security config, thread pools, exception handling, mappers, `ApiResponse` envelope, seed tool |

### Frontend feature layout (`frontEnd/src/`)

| Folder | Responsibility |
|---|---|
| `app/App.tsx` | Router, session restore, route guards |
| `features/HomePage.tsx` | Public marketing landing page |
| `features/auth` | Auth modal / page, Google button |
| `features/kitchen` | Navbar, Inventory, Shop + Cart, Order History |
| `features/flow-editor` | Recipe list, **legacy flow editor**, **Process Builder** canvas, node catalog |
| `features/recipe-tool` | **Recipe Tool** (Process / Ingredients / Nutrition tabs) |
| `shared` | Toasts, theme (light/dark), searchable select, session token |
| `api` | Typed API clients + endpoint constants |

---

## 3. Running Locally

| Component | Command | Notes |
|---|---|---|
| Backend | `cd backend && ./mvnw spring-boot:run` | Port **8080**. Needs `src/main/resources/application.properties` (gitignored — copy from `exampleProps` and fill values). Build reads `../frontEnd/src/features/flow-editor/catalog/stepCatalogs.data.json`, so the frontend folder must be present. |
| Frontend | `cd frontEnd && npm install && npm run dev` | Port **5173**. Env: `VITE_API_BASE_URL` (default `http://localhost:8080`), `VITE_GOOGLE_CLIENT_ID` (Google button hidden when empty). |
| Seed data | `PresetUp` (`common/tools/PresetUp.java`) | See `backend/presetup/README.md` (**note**: README uses the old package path `...tools.PresetUp`; actual is `...common.tools.PresetUp`). |
| Migrations | `backend/tools/migration/*.js` (mongosh) | `migrate_recipe_process_model.js` (legacy flow → MAIN process, re-runnable); `migrate_set_kitchenId_from_kitchen_inventory.js`. |
| Swagger | `http://localhost:8080/ui` | HTTP Basic protected. |
| Local Ollama | `localhost:11434` with `qwen3:4b` | Required for the free text fallback. |

**Key config groups** (names only): `spring.data.mongodb.*`, `app.jwt.*`, `app.otp.*`, `spring.mail.*`, `google.oauth.client-id`, `supabase.*`, `ai.gemini.*`, `ai.ollama.*`, `ai.models[n].*`, `ai.default-model.*`, `ai.credits.*`, `ai.request.*`, `ai.artifact.*`, `app.taskpool.*`, `app.queue.*`.

---

## 4. Navigation Map

```
/  (Home — public)
 ├─ "Log in" / "Try it" / "Create your recipe →"  ──▶  Auth modal
 │                                                     └─ success ──▶ /kitchen/inventory
/auth  (full-page auth; redirects to /kitchen/inventory if logged in)

/kitchen  (requires login — else redirect to /)
 ├─ Navbar:  [Brand → /]  [My Kitchen]  [Shop]  [My Recipes]   🌙/☀️   (Profile ▾)
 │                                                                   ├─ Profile          (disabled)
 │                                                                   ├─ Order History  → /kitchen/orders
 │                                                                   ├─ Account Settings (disabled)
 │                                                                   └─ Log out        → /
 ├─ /kitchen/inventory       My Kitchen (default landing)
 ├─ /kitchen/shop            Shop + Cart drawer ── checkout ──▶ /kitchen/orders
 ├─ /kitchen/orders          Order History
 └─ /kitchen/recipes         Recipes (My / Global tabs)
      ├─ "Open recipe →" / "+ Create recipe"  ──▶ /kitchen/recipes/:id          (Legacy Flow Editor)
      ├─ "🧰 Recipe Tool"                      ──▶ /kitchen/recipes/:id/tool     (Recipe Tool)
      │                                             tabs: Recipe Process | Ingredients | Nutritions
      ├─ /kitchen/recipes/:id/processes              (Process list — URL only, no UI link)
      └─ /kitchen/recipes/:id/process/:processId     (Standalone Process Editor — URL only)

*  (unknown) ──▶ /kitchen/inventory if logged in, else /auth
```

**Session:** on load, if a token exists the app calls `GET /api/v1/auth/me` then `GET /api/v1/kitchens/owner/{id}`; failure clears the token. "Logged in" = user **and** kitchen loaded. No route is role-gated.

**Browser storage keys:** `virtual-kitchen.session.token`, `virtual-kitchen.theme`, `virtual-kitchen.cart.{userId}`, `recipe-flow-draft:{recipeId}`.

---

## 5. Features — Screen by Screen

### 5.1 Home page (`/`)
Marketing landing page with anchor links **How it works · Features · Our vision**. CTAs (**Try it**, **Create your recipe →**, **Try Virtual Kitchen →**) go to `/kitchen/recipes` when a session exists, otherwise open the Auth modal. *Hero video and AI chat demo are static placeholders.*

### 5.2 Authentication (`features/auth/Auth.tsx`)

| Option | What it does |
|---|---|
| **Sign Up** tab | Name, Email, Password (min 8, server-checked), Confirm → **Create Account**. Backend creates the user **and** a kitchen named "&lt;name&gt;'s Virtual Kitchen", then emails a 6-digit code. |
| Verify screen | Enter code → **Verify & Continue** (logs in). **Resend code**. |
| **Login** tab | Email + Password → **Login**. Requires a verified email. |
| **Sign in with an email code instead** | Passwordless: send code → **Verify & Login**. |
| **Forgot password?** | Send reset code → **Verify code** → new password + confirm → returns to login. |
| **Continue with Google** | Google Identity Services ID token → backend verifies and creates/links the account + kitchen. |

Enter submits every form; backdrop / ✕ closes the modal.

### 5.3 My Kitchen — Inventory (`/kitchen/inventory`)
Read-only view of what the kitchen holds.
- **Search** box (with clear ✕) and filter tabs **Ingredients / Equipment**.
- Item cards: type, name, **Available quantity + unit**, **Last updated**.
- Loading skeletons, error state with **Try again**, empty states.
- Inventory is filled **only** by placing Shop orders (no manual add/edit).

### 5.4 Shop & Cart (`/kitchen/shop`)
- Catalog of global **ingredients** and **equipment**; **search** (name/description), filters **All Items / 🥘 Ingredients / ⚙️ Equipment**.
- Card actions: **+ Add to cart** / **+ Add one more**, "✓ N in cart" chip. Cart button shows a count badge.
- **Cart drawer**: per-line Quantity (0 removes), Unit (ingredients: KG/GRAM/LITER/ML/COUNT; equipment: COUNT), Remove, price & subtotal, Total.
- **Proceed to payment · $X** → 1.2 s simulated payment → `POST /api/v1/orders` → redirected to Order History. *Dummy payment — no gateway.*
- Cart persists in localStorage per user. Missing prices default to $5 (ingredient) / $50 (equipment).

### 5.5 Order History (`/kitchen/orders`)
Read-only list of past orders: Order #, date, **Payment** and **Order** status badges, line items (name, qty, unit, price, subtotal), Total. Newest first.

### 5.6 Recipes (`/kitchen/recipes`)

| Tab | Content & actions |
|---|---|
| **My Recipes** | Your recipes. **+ Create recipe** (name required, description optional → opens legacy editor). Per card: **Open recipe →** (legacy editor), **🧰 Recipe Tool**, **🌐 Public / 🔒 Private** toggle (with confirmation), **🗑️ Delete** (*no confirmation*). |
| **Global Recipes** | Other users' **PUBLIC** recipes. **View recipe →** (read-only name/description), **🧰 Recipe Tool** (read-only), **+ Add to My Recipes** (copies as a private recipe, switches tab and highlights it). |

New recipes are always **PRIVATE**. Search box filters both tabs.

### 5.7 Legacy Flow Editor (`/kitchen/recipes/:id`)
The original free-form recipe canvas (stored in the `flows` collection).

**Top bar:** ← Back · title · ⚡ AI credit badge · node-size zoom (− / % / +, 50–200%, Reset) · ↩ Undo / ↪ Redo · visuals progress · **🎨 Generate Visuals** · **🎬 Visualize** · **📤 Export** · **💾 Save**.

**Left sidebar "Recipe Builder"** (collapsible/resizable):
- **Quick Add**: **+ Step**, **+ Condition**, **+ Parallel Start**, **+ Parallel End**.
- **Flow Overview** counters (Steps, Conditions, Parallel, Connections) and a clickable **Nodes** list.

**AI Recipe Builder panel:** paste a *Recipe brief* → **Generate Flow** (async job with staged progress: Queued → Reading → Asking the AI → Checking → Retrying → Finalizing → Done) → canvas is replaced. **Clear**, **Regenerate**. Info toast if a fallback model was used.

**Node types & connection rules**

| Node | Handles | Purpose |
|---|---|---|
| **Step** | in / out | A cooking action |
| **Condition** | top = in, right (green) = **Yes**, left (red) = **No** | A check, e.g. "Is the oil hot?" |
| **Parallel Start** | 1 in → outputs A/B | Fork concurrent work |
| **Parallel End** | inputs A/B → 1 out | Join |

No self-links or duplicate edges.

**Properties panel** (opens on selection):
- *Step*: **Action** (searchable catalog; Custom → custom name) and action-specific fields — Ingredient, Quantity, Unit, Preparation Style, Temperature, Flame Level, Duration, **Repeat Interval** (e.g. "Stir every 2 minutes"), Notes; catalog details; per-step visual with Generate/Regenerate.
- *Condition*: Question, Expected Result, Success/Failure labels, Notes.
- *Parallel*: type, label, notes.
- All: **📋 Duplicate**, **🗑 Delete**.

**Step catalog** (`catalog/stepCatalogs.data.json`, shared with backend AI prompts):

| Category | Actions |
|---|---|
| Ingredient Operations | Add, Remove, Pour, Season |
| Preparation Operations | Cut, Chop, Slice, Dice |
| Cooking Operations | Heat, Boil, Fry, Bake |
| Mixing Operations | Stir, Mix, Whisk |
| Waiting Operations | Wait, Rest |
| Finish Operations | Serve, Garnish |
| Custom | Custom Action |

Ingredients (18 + Custom): Water, Oil, Salt, Sugar, Rice, Onion, Tomato, Garlic, Ginger, Chili, Potato, Carrot, Capsicum, Egg, Milk, Butter, Chicken, Paneer. Units: ml, L, cup, g, kg, piece, tsp, tbsp, pinch, custom. Prep styles: Fine, Medium, Large, Thin/Thick Slice, Julienne, Rough Chop. Flame: Low, Medium, High.

**Other options:**
- **💾 Save** → server; every change also autosaves a local draft (used if server load fails).
- **🎨 Generate Visuals** → async job; AI writes an image prompt per step, generates the image, uploads it to Supabase; images appear progressively.
- **🎬 Visualize** → slideshow in flow order: ⏮ Previous / ▶ Play (1.8 s/slide, loops) / Next ⏭.
- **📤 Export** → JSON view, **⬇ Download .json**, **📋 Copy**.
- **Keyboard**: Delete/Backspace = delete, Ctrl/Cmd+Z = undo, Ctrl/Cmd+Y = redo.

### 5.8 Recipe Tool (`/kitchen/recipes/:id/tool`) — *new model*
Navbar: ← Back · recipe chip (click → summary popover: visibility, ingredient count, nutrition/main-process status) · tabs **🧩 Recipe Process | 🥕 Ingredients | 📊 Nutritions**. Tabs stay mounted so unsaved work survives tab switches.

#### Tab: Recipe Process (Process Builder)
**Left column "Processes (n)":** **✨ AI** and **+ Subprocess** (owner only) · 👑 **MAIN** process (or **Create Main Process**) · 🧩 subprocesses with step counts and a **NEW** badge for unsaved AI-generated ones. Clicking switches the canvas in place.

**✨ Generate Recipe with AI** modal: paste recipe text → **Generate** → async job builds a MAIN process **plus subprocesses** (warns if an existing MAIN will be replaced). Result loads into the session **unsaved** — review, then Save.

**Process top bar:** breadcrumb (Recipe → parents → current) · MAIN/SUBPROCESS badge · **+ Step** · **+ Condition** · **Select node** dropdown · "● Unsaved changes" · ⚡ credits · ⛶ Fit view · zoom − / % / + · ↩ / ↪ (50-entry history per process) · 🗑 delete selected · **🖼️ Generate Visuals** · **🎬 Visualize** · **📤 Export** · **💾 Save**.

**Step panel:**
- **Action** (+ custom name).
- **Action On** — two tabs:
  - **🥕 Ingredients** — rows of ingredient / quantity / unit (count, g, kg, mL, L) / prep style (for Cut/Chop/etc.) / notes; **+ Add Ingredient**.
  - **🔗 Processes** — reference subprocesses this step uses; **Open →** drills into the subprocess (breadcrumb grows).
- **Action Description**, **Expected Output**.
- **▸ Advanced Options**: Flame Level, Temperature, Duration.
- Visualization image + Generate/Regenerate; **📋 Duplicate**, **🗑 Delete**.

**💾 Save** is recipe-wide: creates any unsaved generated processes, remaps subprocess references, then saves every process in one batch call.

#### Tab: Ingredients
Read-only **bill of materials** computed live from every step's *Action On → Ingredients* across all processes, grouped by ingredient + unit with prep styles listed.

#### Tab: Nutritions
Calories (kcal), Protein, Carbohydrates, Fat, Fiber (g), Sodium (mg), Servings. Owner edits → **Save Nutrition**; others see read-only values.

### 5.9 Shared UI
- **Toasts** (success ✅ / error ⚠️ / info ℹ️, auto-dismiss 4 s).
- **Theme toggle** light/dark (persisted, defaults to OS).
- **⚡ AI credit badge** — "available / monthly"; turns amber at ≤10% and warns AI will fall back to a standard model.

### 5.10 Roles & access
| Rule | Behaviour |
|---|---|
| Roles | `USER`, `ADMIN`. Only admin API is credit grant (`/api/v1/admin/**`). No admin UI; no way to promote a user. |
| Recipe read | PUBLIC → anyone; PRIVATE → owner only (403 otherwise). |
| Recipe write | Owner only (edit, visibility, delete, processes, nutrition, AI generation). |
| Copy | Only PUBLIC recipes; copy is PRIVATE and owned by the caller. |
| Global list | PUBLIC recipes not owned by the caller. |

---

## 6. AI Subsystem

### 6.1 AI features
| Feature | Where in UI | Pipeline |
|---|---|---|
| **Recipe → Flow** (legacy) | Legacy editor · AI Recipe Builder | Prompt with step vocabulary → Gemini (temp 0.1) → validate → 1 retry with errors → graph |
| **Recipe → Process Builder** | Recipe Tool · ✨ AI | Prompt → validate node types / units / vocabulary / subprocess cycles → MAIN + subprocesses (not persisted until Save) |
| **Step visualization** | Generate Visuals (both editors) | Per step: text model writes `{imagePrompt, videoPrompt}` → image model → Supabase upload → `visualization_asset`. Steps ordered topologically. *Video prompt produced, video never generated.* |
| **Chat** | none | Generic chat endpoint (bypasses credits) |

### 6.2 Model registry & routing
| Key | Capability | Tier | Provider | Credits | Fallback | Enabled |
|---|---|---|---|---|---|---|
| `gemini-flash` | Text | PAID | Gemini | 1 | `ollama-qwen` | ✅ (default text) |
| `ollama-qwen` | Text | OPEN_SOURCE | Ollama `qwen3:4b` | 0 | — | ✅ |
| `openai-gpt4o-mini` | Text | PAID | OpenAI | 2 | `ollama-qwen` | ❌ |
| `gemini-image` | Image | PAID | Gemini | 5 | `drawthings-sdxl` | ✅ (default image) |
| `drawthings-sdxl` | Image | OPEN_SOURCE | DrawThings | 0 | — | ✅ (provider not configured) |

Selection: preferred (currently always default) → if PAID, atomically reserve credits → if insufficient, use fallback (`usedFallback=true`, reason `INSUFFICIENT_CREDITS`) → none available → 503.

### 6.3 Credits
- Per-user wallet (`user_ai_credits`), **100 credits/month** default, created lazily.
- Lifecycle: **reserve** → **consume** on success / **release** (refund) on failure — every movement in the `ai_credit_transactions` ledger.
- Monthly reset job (cron, default 02:00 daily for expired cycles). Admin can grant/deduct.
- Cost example: one visualized step = prompt (1) + image (5) = **6 credits** → ~16 steps/month on the default allocation. Generation retries happen inside one reservation (no double charge).

### 6.4 Background jobs
All long AI operations are **async jobs** polled every 2 s by the UI; `clientRequestId` makes submits idempotent.

| Job | Statuses | Stages / progress |
|---|---|---|
| Flow generation | QUEUED · IN_PROGRESS · COMPLETED · FAILED | QUEUED 0 → BUILDING_PROMPT 10 → CALLING_MODEL 35 → VALIDATING_RESPONSE 65 → RETRYING 80 → PERSISTING 95 → COMPLETED 100 |
| Process generation | same | QUEUED 0 → BUILDING_PROMPT 10 → CALLING_MODEL 40 → VALIDATING_RESPONSE 75 → RETRYING 90 → COMPLETED 100 |
| Visualization | QUEUED · IN_PROGRESS · COMPLETED · COMPLETED_WITH_ERRORS · FAILED | `completedSteps / totalSteps` + per-step results |

Thread pools: `visualization` (4), `visualization-orchestrator` (2), `ai-text` (4), `flow-generation-orchestrator` (2). Admission limits: text 20, image 30 in flight → **429** beyond.

### 6.5 AI Artifact Store (durable paid output)
Paid image output is staged in `ai_artifacts` (GridFS for large payloads) **before** upload, so a crash or failed Supabase upload never loses paid work. Identical requests are **reused at 0 credits**. A recovery sweeper (every 5 min) retries orphaned artifacts up to 3× then marks them ABANDONED. Retention: 7 days consumed / 30 days abandoned. Design: `AI_ARTIFACT_STORE_DESIGN.md` (phases 1–5 implemented; phase 6 JSON codec not).

---

## 7. API Reference

**Base URL:** `http://localhost:8080` · **Auth header:** `Authorization: Bearer <JWT>` (HS256, 24 h, subject = userId, claims `email`, `userType`).

**Success envelope** (most endpoints):
```json
{ "success": true, "message": "…", "data": <T>, "timestamp": "2026-09-26T10:00:00" }
```
**Error shape:**
```json
{ "timestamp": "…", "status": 404, "error": "Not Found", "message": "…" }
```

| HTTP | Raised by |
|---|---|
| 400 | Bean validation failure, `UserNotFoundException` |
| 401 / 403 | `AuthException` (bad credentials, unverified email), `RecipeAccessDeniedException` (403), provider auth failure (401) |
| 404 | `NoSuchElementException` |
| 409 | Duplicate key (e.g. email exists, duplicate `clientRequestId`) |
| 422 | Process graph validation, AI output still invalid after retry, unknown job id |
| 429 | AI queue full, OTP throttle |
| 502 / 504 | AI provider bad response / timeout |
| 503 | No AI model available |
| 500 | Unhandled `RuntimeException` / `IllegalArgumentException` (not in error shape) |

**Auth column legend:** **Public** = no token needed · **JWT** = token required · **JWT-opt** = token used for PUBLIC/PRIVATE checks if present · **Owner** = JWT + must own recipe · **Admin** = `ROLE_ADMIN`. `*` = required field.

### 7.1 Authentication — `/api/v1/auth`

| Method & Path | Auth | Input | Output (`data`) | Significance / Usage |
|---|---|---|---|---|
| POST `/signup` | Public | `{name*, email*, password* (≥8), confirmPassword*}` | `UserResponseDTO` · **201** | Sign Up → Create Account. Creates user + kitchen, sends verification OTP. 400 mismatch, 409 exists. |
| POST `/login` | Public | `{email*, password*}` | `{token, user: UserResponseDTO}` | Login tab. 401 bad creds, 403 unverified. |
| POST `/google` | Public | `{idToken*}` | `{token, user}` | Continue with Google. Links/creates user + kitchen. |
| POST `/email/send-otp` | Public | `{email*}` | `null` | Resend verification code. 429 throttled. |
| POST `/email/verify-otp` | Public | `{email*, otp*}` | `{token, user}` | Verify & Continue — verifies email and logs in. |
| POST `/login/otp/send` | Public | `{email*}` | `null` | Passwordless: Send sign-in code. |
| POST `/login/otp/verify` | Public | `{email*, otp*}` | `{token, user}` | Passwordless: Verify & Login. |
| POST `/password/forgot` | Public | `{email*}` | `null` | Forgot password → Send reset code. |
| POST `/password/verify-otp` | Public | `{email*, otp*}` | `null` | Verify reset code (not consumed yet). |
| POST `/password/reset` | Public | `{email*, newPassword* (≥8), confirmNewPassword*}` | `null` | Consumes verified OTP, sets password. |
| GET `/me` | JWT | — | `UserResponseDTO` | Session restore on app load. |

`UserResponseDTO` = `{id, name, email, status (ACTIVE|INACTIVE), emailVerified, authProvider (LOCAL|GOOGLE), userType (ADMIN|USER), createdAt, updatedAt}`

### 7.2 Users — `/api/v1/users` *(admin-style CRUD, not used by UI)*

| Method & Path | Auth | Input | Output | Usage |
|---|---|---|---|---|
| POST `/` | Public | `{name*, email*, password* (≥8)}` | `UserResponseDTO` · 201 | Direct create (skips verification + kitchen). |
| GET `/{id}` | Public | path `id` | `UserResponseDTO` | Lookup. |
| GET `/email/{email}` | Public | path `email` | `UserResponseDTO` | Lookup by email. |
| PUT `/{id}` | Public | `{name*}` | `UserResponseDTO` | Rename. |
| DELETE `/{id}` | Public | — | `null` | Delete user. |

### 7.3 Kitchen, Inventory & Orders

| Method & Path | Auth | Input | Output (`data`) | Significance / Usage |
|---|---|---|---|---|
| POST `/api/v1/kitchens` | Public | `{name, ownerId}` | `KitchenResponseDTO {id, name, ownerId, createdAt, updatedAt}` | Create kitchen (normally auto-created on signup). |
| GET `/api/v1/kitchens/{id}` | Public | path | `KitchenResponseDTO` | Lookup. 404 if missing. |
| GET `/api/v1/kitchens/owner/{ownerId}` | Public | path | `KitchenResponseDTO[]` | **After every login / session restore** — resolves the user's kitchen. |
| PUT `/api/v1/kitchens/{id}` | Public | `{name}` | `KitchenResponseDTO` | Rename. |
| DELETE `/api/v1/kitchens/{id}` | Public | — | `null` | Delete. |
| POST `/api/v1/inventory` | Public | `{userId, kitchenId, itemType (INGREDIENT|EQUIPMENT), itemId, quantity, unit}` | `InventoryResponseDTO` | Upsert — **adds** quantity to existing row. |
| GET `/api/v1/inventory/{userId}` | Public | path | `InventoryResponseDTO[]` | Legacy per-user list. |
| GET `/api/v1/inventory/kitchen/{kitchenId}` | Public | path | `InventoryResponseDTO[]` (with `itemName`) | **My Kitchen** screen. |
| POST / GET / DELETE `/api/v1/kitchen-inventory[/{id}]` | Public | `{kitchenId, inventoryId}` | `KitchenInventoryResponseDTO` | Legacy join table (superseded by `inventory.kitchenId`). |
| POST `/api/v1/orders` | Public | `{userId, items: [{itemId, itemType, itemName, quantity, unit, price}]}` | `OrderResponseDTO` | **Shop → Proceed to payment**. Saves order (CONFIRMED/PAID) and adds items to kitchen inventory. |
| GET `/api/v1/orders/user/{userId}` | Public | path | `OrderResponseDTO[]` (newest first) | **Order History** screen. |

- `InventoryResponseDTO` = `{id, kitchenId, userId, itemType, itemId, itemName, quantity, unit, lastUpdated}`
- `OrderResponseDTO` = `{orderId, userId, items: [{itemId, itemType, itemName, quantity, unit, price, subTotal}], totalAmount, orderStatus (PENDING|CONFIRMED|CANCELLED|FAILED), paymentStatus (PENDING|PAID|FAILED), createdAt}`

### 7.4 Store catalog

| Method & Path | Auth | Input | Output (`data`) | Significance / Usage |
|---|---|---|---|---|
| POST `/api/v1/ingredients` | Public | `{name*, description, defaultUnit* (KG|GRAM|LITER|ML|COUNT), imageUrl}` | `IngredientResponseDTO {id, name, description, defaultUnit, imageUrl, createdAt, updatedAt}` | Catalog admin (no UI). |
| GET `/api/v1/ingredients` | Public | — | `IngredientResponseDTO[]` | **Shop** catalog. |
| GET / PUT / DELETE `/api/v1/ingredients/{id}` | Public | PUT: `{name*, description, defaultUnit*, imageUrl}` | `IngredientResponseDTO` / `null` | Maintain catalog. |
| POST `/api/v1/equipments` | Public | `{name, description}` | `EquipmentResponseDTO {id, name, description, createdAt, updatedAt}` | Catalog admin. |
| GET `/api/v1/equipments` | Public | — | `EquipmentResponseDTO[]` | **Shop** catalog. |
| POST `/api/v1/item-cost` | Public | `{itemType, itemId, unit, costPerUnit, currency, effectiveFrom}` | `ItemCostResponseDTO` | Price history (not yet read anywhere). |

### 7.5 Recipes (templates) — `/api/v1/process-templates`
*`userId` is passed as a query/path parameter, not taken from the JWT.*

| Method & Path | Auth | Input | Output (`data`) | Significance / Usage |
|---|---|---|---|---|
| POST `/` | Public | `{name, description, createdBy}` | `RecipeTemplateResponseDTO` | **+ Create recipe** modal. Always PRIVATE. |
| GET `/{id}` | Public | path | `RecipeTemplateResponseDTO` | Lookup. |
| GET `/user/{userId}` | Public | path | `RecipeTemplateResponseDTO[]` | **My Recipes** tab. |
| GET `/global/{userId}` | Public | path | `RecipeTemplateResponseDTO[]` | **Global Recipes** tab (PUBLIC, not own). |
| PUT `/{id}?userId=` | Owner (param) | `{name, description}` | `RecipeTemplateResponseDTO` | Rename (no UI yet). |
| PUT `/{id}/visibility?userId=&visibility=PUBLIC\|PRIVATE` | Owner (param) | query | `RecipeTemplateResponseDTO` | **🌐/🔒 toggle**. |
| POST `/{id}/copy?userId=` | Public recipe only | query | `RecipeTemplateResponseDTO` (new, PRIVATE) | **+ Add to My Recipes**. |
| DELETE `/{id}?userId=` | Owner (param) | query | `null` | **🗑️ Delete**. |

`RecipeTemplateResponseDTO` = `{id, name, description, createdBy, visibility, createdAt, updatedAt}`

### 7.6 Recipe detail — `/api/v1/recipes/{recipeId}`

| Method & Path | Auth | Input | Output (`data`) | Significance / Usage |
|---|---|---|---|---|
| GET `/` | JWT-opt | path | `RecipeDetailResponseDTO` | **Recipe Tool** load, summary popover. |
| PUT `/ingredients` | Owner | `[{ingredientId*, quantity*, unit*, notes, preparation}]` | `RecipeDetailResponseDTO` | Recipe-level ingredient list (no UI yet). |
| PUT `/nutrition` | Owner | `{calories, proteinGrams, carbohydratesGrams, fatGrams, fiberGrams, sodiumMilligrams, servings}` | `RecipeDetailResponseDTO` | **Nutritions → Save Nutrition**. |
| GET `/main-process` | JWT-opt | — | `ProcessResponseDTO` · 404 if none | Fetch MAIN process. |
| POST `/main-process` | Owner | — | `ProcessResponseDTO` | **Create Main Process** (idempotent). |

`RecipeDetailResponseDTO` = `{id, name, description, createdBy, visibility, ingredients[], nutrition, mainProcessId, createdAt, updatedAt}`

### 7.7 Processes (Process Builder) — `/api/v1/recipes/{recipeId}/processes`

| Method & Path | Auth | Input | Output (`data`) | Significance / Usage |
|---|---|---|---|---|
| GET `/` | JWT-opt | — | `ProcessResponseDTO[]` | Load all processes into the Recipe Tool session / process sidebar. |
| POST `/` | Owner | `{type* (MAIN|SUBPROCESS), name*, description}` | `ProcessResponseDTO` | **+ Subprocess**; saving AI-generated processes. |
| GET `/{processId}` | JWT-opt | — | `ProcessResponseDTO` | Single process. |
| PUT `/{processId}` | Owner | `{name*, description, nodes, edges, viewport}` | `ProcessResponseDTO` · 422 invalid graph | Save one process. |
| PUT `/` | Owner | `{processes*: [{processId*, name*, description, nodes, edges, viewport}]}` | `ProcessResponseDTO[]` | **💾 Save** (recipe-wide batch). |
| DELETE `/{processId}` | Owner | — | `null` · 422 if MAIN | Delete subprocess (no UI yet). |
| POST `/{processId}/copy` | Owner | — | `ProcessResponseDTO` | Duplicate a subprocess (no UI yet). |

- `ProcessResponseDTO` = `{id, type, recipeId, name, description, nodes: ProcessNodeDTO[], edges: ProcessEdgeDTO[], viewport {x, y, zoom}, createdAt, updatedAt}`
- `ProcessNodeDTO` = `{id, kind (STEP|CONDITION), data {…step fields…}, type, position {x,y}, measured, width, height, parentId, extent, draggable, selectable, deletable}`
- `ProcessEdgeDTO` = `{id, source, target, label, sourceHandle, targetHandle, type, animated, style, data}`
- Validation: unique node/edge ids, edges reference existing nodes, STEP/CONDITION data non-empty, one MAIN per recipe.

### 7.8 AI generation jobs

| Method & Path | Auth | Input | Output (`data`) | Significance / Usage |
|---|---|---|---|---|
| POST `/api/v1/recipes/{recipeId}/processes/generate/jobs` | JWT | `{recipeText*, clientRequestId}` | `ProcessGenerationJobResponseDTO` | **Recipe Tool → ✨ Generate with AI**. |
| GET `/api/v1/recipes/{recipeId}/processes/generate/jobs/{jobId}` | JWT | path | `ProcessGenerationJobResponseDTO` | Poll every 2 s. |
| POST `/api/recipe/generate-flow/jobs` | JWT | `{recipe*, clientRequestId}` | `RecipeFlowGenerationJobResponseDTO` | **Legacy editor → Generate Flow**. |
| GET `/api/recipe/generate-flow/jobs/{jobId}` | Public | path | `RecipeFlowGenerationJobResponseDTO` | Poll every 2 s. |
| POST `/api/recipe/generate-flow` | JWT | `{recipe*, clientRequestId}` | `RecipeFlowGenerationResponseDTO` (raw, sync, up to 250 s) | Synchronous variant (unused by UI). |

- **Job DTO** = `{jobId, status, stage, progressPercent, result, errorMessage}`
- **Process result** = `{mainProcess, subprocesses[], modelUsed, modelTier, usedFallback, fallbackReason}`; each process `{ref, name, steps[]}`; each step `{nodeType, action, actionOn {ingredients: [{ingredientId, quantity, unit, preparationStyle, customIngredientName}], processes: [ref]}, temperature, flameLevel, duration, title, expectedResult, actionDescription, expectedOutput}`
- **Flow result** = `{steps: [{id, nodeType, data}], edges: [{from, to, label}], modelUsed, modelTier, usedFallback, fallbackReason}`

### 7.9 Visualization (step images)

| Method & Path | Auth | Input | Output (`data`) | Significance / Usage |
|---|---|---|---|---|
| POST `/api/v1/recipes/{recipeId}/processes/{processId}/visualization/jobs` | JWT | path | `VisualizationJobResponseDTO` | **Process Builder → 🖼️ Generate Visuals**. |
| GET `…/visualization/jobs/{jobId}` | Public | path | `VisualizationJobResponseDTO` | Poll every 2 s. |
| POST `/api/recipes/{flowId}/visualization/jobs` | JWT | path | `VisualizationJobResponseDTO` | **Legacy editor → 🎨 Generate Visuals** / per-node Generate. |
| GET `/api/recipes/visualization/jobs/{jobId}` | Public | path | `VisualizationJobResponseDTO` | Poll. |
| POST `/api/recipes/{flowId}/visualization/generate` | JWT | path | `{recipeId, message, steps: StepDTO[]}` | Synchronous whole-flow (unused by UI). |
| POST `/api/recipes/{flowId}/visualization/steps/{stepId}/generate` | JWT | path | `StepDTO {stepId, visualizationAssetId, imagePrompt, imageUrl, modelKey, modelTier, usedFallback}` | Synchronous single step (unused by UI). |
| POST `/api/v1/visualizations/{flowId}` | Public | path | `{processTemplateId, message, clips[], finalClip}` | **Stub** — placeholder video clip rows, no AI call. |

`VisualizationJobResponseDTO` = `{jobId, recipeId, processId, status, totalSteps, completedSteps, steps: [{stepId, success, visualizationAssetId, imageUrl, errorMessage, modelKey, tier, usedFallback}]}`

### 7.10 AI models & credits

| Method & Path | Auth | Input | Output (`data`) | Significance / Usage |
|---|---|---|---|---|
| GET `/api/v1/ai/models?capability=` | Public | optional `TEXT_TO_TEXT` \| `TEXT_TO_IMAGE` | `[{key, capability, tier, providerModelId, creditCost, enabled}]` | Registry view for a future model picker. |
| GET `/api/v1/users/me/ai-credits` | JWT | — | `{userId, monthlyAllocation, availableBalance, reservedBalance, cycleStart, cycleEnd}` | **⚡ AI credit badge**. |
| GET `/api/v1/users/me/ai-credits/history` | JWT | — | `[{id, requestId, type, amount, balanceAfter, capability, modelKey, tier, createdAt}]` | Credit ledger (no UI yet). |
| POST `/api/v1/admin/users/{userId}/ai-credits/grant` | Admin | `{amount*}` (signed) | `UserAiCreditResponseDTO` | Admin top-up / deduction. |

### 7.11 Legacy flow graph — `/api/v1/flows`

| Method & Path | Auth | Input | Output | Usage |
|---|---|---|---|---|
| GET `/{flowId}` | Public | path | Raw `Recipe` entity `{flowId, nodes, edges, viewport, …}` | **Legacy editor** load. |
| PUT `/{flowId}` | Public | `{flowId, userId, templateId, nodes[], edges[], viewport}` | `{flowId, userId, message}` | **Legacy editor → 💾 Save**. |

### 7.12 AI chat & response log *(not used by UI)*

| Method & Path | Auth | Input | Output | Usage |
|---|---|---|---|---|
| POST `/api/ai/chat` | Public | `{prompt*}` | raw `{content}` | Generic chat. |
| POST `/api/ai/responses` | Public | `{context, input, success, responseData}` | `AIResponseDocument` · 201 | Manual log insert. |
| GET `/api/ai/responses?context=&success=` | Public | query | `AIResponseDocument[]` | Browse AI call log (every generation attempt is logged here). |
| GET / DELETE `/api/ai/responses/{id}` | Public | path | document / 204 | Inspect / delete. |

### 7.13 Legacy execution tracking *(not used by UI)*
Early "cooking run" model, kept for reference. All Public, no validation.

| Path | Purpose |
|---|---|
| `/api/v1/process-template-steps` (POST, GET `/{templateId}`) | Ordered step list for a template |
| `/api/v1/step-definitions` (POST, GET) | Reusable step definitions `{name, description, mediaUrl, estimatedTimeSec}` |
| `/api/v1/process-executions` (POST, PUT `/{id}/{status}`, GET `/user/{userId}`) | A user's cooking run; status NOT_STARTED/IN_PROGRESS/DONE/CANCELLED |
| `/api/v1/step-executions` (POST, PUT `/{id}`, GET `/{executionId}`) | Per-step progress |
| `/api/v1/process-ingredient-usage`, `/api/v1/process-equipment-usage` (POST, GET) | Consumption & cost at time of cooking |

### 7.14 Python services
`Services/pythonServices` exposes **no API**. `merger.py` provides OpenCV functions (`removeGreenScreen`, `changeCameraAngle`, `merge_video`) prototyping future step-video compositing. `app.py` and `requirements.txt` are empty.

---

## 8. Data Model

| Domain | Collection | Represents |
|---|---|---|
| Auth | `users` | Account: email (unique), password hash, `authProvider`, `googleId`, `userType`, `status`, `emailVerified` |
| | `otps` | Hashed 6-digit codes: purpose EMAIL_VERIFICATION / PASSWORD_RESET / LOGIN, expiry, attempts |
| Kitchen | `kitchen` | A user's virtual kitchen (one per user) |
| | `inventory` | Stock row: kitchen, item type/id, quantity, unit |
| | `orders` | Checkout with embedded line items, totals, order & payment status |
| | `kitchen_inventory` | Legacy join (superseded) |
| Store | `ingredients`, `equipment`, `item_cost` | Global catalog + price history |
| Recipe | `process_template` | **The Recipe**: name, description, owner, visibility, ingredients, nutrition, `mainProcessId` |
| | `processes` | **Process Builder graphs**: one MAIN + N SUBPROCESS per recipe (nodes, edges, viewport) |
| | `flows` | **Legacy flow graph** (keyed by `flowId = templateId`) |
| | `process_template_step`, `step_definition`, `process_execution`, `step_execution`, `process_*_usage` | Legacy execution tracking |
| AI | `user_ai_credits`, `ai_credit_transactions` | Wallet + ledger |
| | `ai_request_jobs` | Per-provider-call job (idempotency, status) |
| | `recipe_flow_generation_job`, `process_generation_job`, `visualization_job` | User-facing async jobs |
| | `visualization_asset`, `visualization_clip` | Step images (Supabase URLs) / placeholder clips |
| | `ai_artifacts` (+ GridFS) | Durable paid output |
| | `ai_responses` | Log of every AI call |
| Common | `database_sequences` | Sequential Long id generator |

> **Two recipe graph models coexist.** "Open recipe" uses the legacy `flows` model; "🧰 Recipe Tool" uses the new `processes` model. `migrate_recipe_process_model.js` converts legacy flows into MAIN processes.

---

## 9. Known Gaps — Why This Cut Is NP

### 🔴 Security (blockers for production)
1. `SecurityConfig` permits all of `/api/**` ("for local development"). Users, kitchens, inventory, orders, store, recipe templates, flows, `/api/ai/chat` and `/api/ai/responses` are **unauthenticated** — e.g. anyone can delete any user or spend Gemini quota via chat.
2. Several modules trust a **client-supplied `userId`** (process-templates, inventory, orders) instead of the JWT → ownership can be spoofed.
3. Job-status endpoints have no ownership check.
4. **Credentials are present in tracked files and git history** (`application.properties` in earlier commits, `MongoConfig.java` default value, `PresetUp.java`, `backend/test-output.txt`, migration script comments). `MongoConfig` also prints the DB URI at startup. → **Rotate all secrets and move to environment variables / a secret manager before any shared deployment.**
5. No refresh tokens / logout / revocation; deleted or inactive users keep access until token expiry.
6. User enumeration via auth error messages and public `/users/email/{email}`.

### 🟠 Functional / correctness
7. `@EnableMongoAuditing` missing → all `createdAt/updatedAt` are null, **OTP resend throttling doesn't work**, and "latest OTP" lookup is unreliable.
8. Checkout is simulated: prices come from the client, order is hard-coded CONFIRMED/PAID, no payment gateway; inventory adds quantities without unit conversion.
9. Recipe copy clones only the legacy `flows`, not `processes`; recipe delete doesn't cascade.
10. AI regeneration over an existing MAIN process may not refresh the canvas and doesn't mark changes unsaved.
11. In-memory thread pools: jobs left QUEUED/IN_PROGRESS after a restart are never recovered (UI polls forever); admission control is per-JVM.
12. Credit cycle reset zeroes `reservedBalance` even while requests are in flight; timed-out provider calls aren't cancelled.
13. Several 4xx conditions surface as 500 (no catch-all handler).

### 🟡 UX / completeness
14. Two editors side by side (legacy vs Recipe Tool); "+ Create recipe" still opens the legacy editor.
15. Standalone process routes reachable only by URL.
16. No UI for: rename recipe, delete/copy subprocess, credit history, model picker, admin.
17. Profile / Account Settings disabled; footer links are `#`; home-page media are placeholders.
18. Recipe delete has no confirmation; legacy editor uses browser `alert()`.
19. Recipe ingredients use the hard-coded 18-item catalog, disconnected from the Shop/Inventory ingredient catalog.
20. Video generation not implemented (prompts only); Python service is a prototype.

### ⚪ Hygiene
21. Tracked binaries: Python `venv/pyvenv.cfg`, `__pycache__`, `uploads/` (~5 MB), `outputs/`, five ~1 MB PNGs in `frontEnd/`.
22. `requirements.txt` empty / no lockfile for Python.
23. `System.out.println` debug output; `MongoVerifyRunner` writes a test doc on every startup.
24. No tests for auth, JWT, security config, controllers, kitchen/store services; `SupabaseStorageServiceTest` hits the real bucket. (~150 tests otherwise, strong coverage on process service/validator, AI validator and concurrency.)
25. Docs drift: `frontEnd/README.md` is the Vite template; `FRONTEND_CODE_STRUCTURE.md` / `BACKEND_CODE_STRUCTURE.md` are out of date; `AI_ARTIFACT_STORE_DESIGN.md` contains NUL bytes.

---

## 10. Milestone History

| PRs | Milestone |
|---|---|
| #1–#5 | Codebase structure, section/node UI refactor, naming |
| #6, #10 | Python image/video merging prototype |
| #7 | Code-structure READMEs |
| #8–#9 | Flow chart save |
| #11–#12 | Authentication + refactor |
| #13–#14 | Code structure updates, inventory response fix |
| #15–#17 | Linear flow canvas, agents & flow canvas |
| #18–#20 | Flow canvas refactor to fixed schema |
| #21–#23 | AI integration, drag/prompt fixes |
| #24 | Shop, cart & checkout |
| #25–#26 | UI component refactor, icons & home page |
| #27–#28 | Prompt building & image generation |
| #29 | Recipe access division (public/private/global) |
| #30–#31 | Recipe backend refactor, auth implementation (OTP, Google) |
| #32–#34 | Flow canvas enhancements, Javadocs, canvas UI refactor |
| #35 | AI model management & credits, background jobs, artifact store |
| #36 | **Unified Process Builder** with AI generation & visualization (Recipe Tool) |
