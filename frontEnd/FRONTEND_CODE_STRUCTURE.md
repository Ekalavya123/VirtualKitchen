# Frontend Code Structure

This document explains the frontend folder structure of the Virtual Kitchen application and the responsibility of each main directory.

## Overall architecture

The frontend is built with React, TypeScript, and Vite. The code is organized by feature and responsibility so the UI remains modular and easy to extend.

## Main folder structure

```text
frontEnd/
├── api/
├── app/
├── assets/
├── features/
│   ├── auth/
│   ├── kitchen/
│   ├── recipe-tool/
│   └── recipes/
├── shared/
├── styles/
├── types/
├── App.css
├── index.css
├── main.tsx
├── package.json
├── tsconfig.json
└── vite.config.ts
```

## Folder responsibilities

### `api`

- Contains API client and integration code.
- Example files:
  - `client.ts`
  - `endpoints.ts`
  - `inventoryApi.ts`
  - `recipeApi.ts`
  - `authApi.ts`
- Responsibilities:
  - Configure HTTP client behavior.
  - Define server endpoints and payload contracts.
  - Provide reusable API methods for feature code.

### `app`

- Contains the main application shell.
- Example: `App.tsx`
- Responsibilities:
  - Renders the root application layout.
  - Connects page routes and feature modules.
  - Hosts global providers and layout structure.

### `features`

- Contains feature-based modules grouped by business capability.
- Current features include:
  - `auth` for authentication and login flows.
  - `recipes` for the recipe list (create, publish, copy).
  - `recipe-tool` for a single recipe: ingredients, nutrition and its Recipe Process editor.
  - `kitchen` for inventory and kitchen management.
- Responsibilities:
  - Keep related UI, state, and business logic together.
  - Isolate feature-specific code from shared infrastructure.

### `features/auth`

- Handles user authentication UI.
- Includes login and auth-related views.
- Responsibilities:
  - Render sign-in screens.
  - Manage authentication forms and validation.
  - Connect with the `api/authApi.ts` client.

### `features/recipes`

- `RecipeHomePage.tsx`: My Recipes / Global Recipes, create, publish/unpublish, copy and delete. Opening a recipe goes to the Recipe Tool.

### `features/recipe-tool`

- The Recipe Tool for one recipe (`RecipeToolPage.tsx`): summary, Ingredients and Nutrition tabs, and the Recipe Process tab.
- `context/RecipeSessionContext.tsx` holds the unified in-memory recipe snapshot (MAIN + every SUBPROCESS); the recipe-level Save persists all of it at once.
- `catalog/`: the recipe step catalogs (actions, ingredients, units, preparation styles, flame levels) built from `stepCatalogs.data.json`.
- `styles/recipe-tool.css`: shared tokens and component styles.

#### `features/recipe-tool/process` (Recipe Process)

The recipe-specific editor built on the generic Process graph (`types/process.ts`):

- `components/`: `RecipeProcessCanvas` (the React Flow editor), `RecipeProcessEditor`, `RecipeProcessTopBar`, `RecipeProcessSidebar`, `RecipeProcessBreadcrumb`, `RecipeStepPanel`, `RecipeConditionPanel`, `RecipeProcessView`, `RecipeProcessListPage`, `RecipeProcessGenerationModal` and `RecipeVisualizationSlideshow`.
- `nodes/`: `RecipeStepNode` and `RecipeConditionNode`.
- `model/`: recipe step data (Action / Action On / cooking properties), condition data, node type constants and the canvas payload types.
- `adapters/`: Process graph ↔ canvas conversion, and AI generation result → processes.
- `context/`: per-canvas graph context for node components.

### `features/kitchen`

- Contains inventory and kitchen page components.
- Responsibilities:
  - Render inventory views and shop interfaces.
  - Display kitchen dashboard and navigation.
  - Manage kitchen-related user interactions.

### `shared`

- Contains reusable UI components used across the app.
- Example: `shared/components/Button.tsx`
- Responsibilities:
  - Avoid duplication.
  - Provide consistent shared building blocks.

### `assets`

- Stores static assets such as images, icons, and other resources.
- Responsibilities:
  - Keep non-code resources organized.
  - Make assets easy to import into components.

### `styles`

- Contains global and shared styling files.
- Example: `global.css`
- Responsibilities:
  - Define application-wide visual styles.
  - Keep styling separate from component logic.

### `types`

- Contains TypeScript type definitions.
- Responsibilities:
  - Define shared domain types and interfaces.
  - Ensure type safety across the frontend.

### Root files

- `main.tsx`
  - Application entry point.
  - Mounts React into the DOM.
- `App.css`, `index.css`
  - Define global and app-level styles.
- `package.json`
  - Manages dependencies and scripts.
- `vite.config.ts`
  - Configures the Vite build and dev server.
- `tsconfig.json`
  - Configures TypeScript compiler options.

## Typical frontend flow

A typical UI flow usually looks like this:

```text
User action -> React component -> feature/api logic -> state update -> UI render
```

For the editor feature, the flow is:

```text
User edits node/edge -> component updates store -> canvas rerenders -> state persists via API
```

## Summary

The frontend is organized around:

- `api` for backend integration and shared service calls
- `app` for the root application shell
- `features` for business-oriented UI modules
- `shared` for reusable components
- `styles` and `assets` for styling and static resources
- `types` for TypeScript definitions

This layout keeps the application modular, maintainable, and ready to grow with new functionality.
