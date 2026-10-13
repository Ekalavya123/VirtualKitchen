# Recipe Tool styling structure

## Purpose

This area uses a small shared style layer for reusable tokens and component-oriented patterns, with Tailwind used for layout and spacing.

## How to read the styles

- Colour tokens (`--flow-*`, light and dark) live in [frontEnd/src/styles/theme-tokens.css](frontEnd/src/styles/theme-tokens.css); the shared `flow-*` component classes (also used by the recipe library and recipe orders) live in [frontEnd/src/styles/flow-ui.css](frontEnd/src/styles/flow-ui.css).
- Tailwind handles layout, spacing, flex/grid positioning, and responsive structure.
- Component-specific styles remain local to the relevant component when needed.

## Conventions

- Prefer semantic class names such as `header`, `card`, `title`, `actionButton`, and `sectionHeading`.
- Keep layout in Tailwind utilities and keep visual treatment in shared CSS classes.
- Reuse shared classes instead of duplicating similar styles.
- Avoid deep CSS nesting and keep selectors shallow.

## Files to know

- [frontEnd/src/styles/flow-ui.css](frontEnd/src/styles/flow-ui.css): shared `flow-*` component classes (buttons, inputs, cards, recipe library).
- [frontEnd/src/features/recipe-tool/styles/modals.css](frontEnd/src/features/recipe-tool/styles/modals.css): the recipe tool's dialogs — shared overlay/panel (export, AI edit, AI Recipe Creation, recovery, walkthrough), AI progress and task rows.
- [frontEnd/src/features/recipe-tool/styles/RecipePropertiesPanel.css](frontEnd/src/features/recipe-tool/styles/RecipePropertiesPanel.css): step/condition panel controls, also used by the nutrition edit form.
- [frontEnd/src/features/recipe-tool/editor/styles/RecipeProcessCanvas.css](frontEnd/src/features/recipe-tool/editor/styles/RecipeProcessCanvas.css): Recipe Editor shell and canvas.
- [frontEnd/src/features/recipe-tool/editor/styles/RecipeEditorToolbar.css](frontEnd/src/features/recipe-tool/editor/styles/RecipeEditorToolbar.css): the editor's top bar, menus and process sidebar.
- [frontEnd/src/features/recipe-tool/view/styles/recipe-view.css](frontEnd/src/features/recipe-tool/view/styles/recipe-view.css): the Recipe Process view (`rp-*` classes, with a dark-theme counterpart for every colour); loaded by the recipe tool's `index.ts`, since recipe orders reuse the view.
