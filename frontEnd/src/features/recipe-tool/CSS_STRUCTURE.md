# Recipe Tool styling structure

## Purpose

This area uses a small shared style layer for reusable tokens and component-oriented patterns, with Tailwind used for layout and spacing.

## How to read the styles

- Shared tokens live in [frontEnd/src/features/recipe-tool/styles/recipe-tool.css](frontEnd/src/features/recipe-tool/styles/recipe-tool.css). Its `--flow-*` variables and `flow-*` classes keep their original names.
- Tailwind handles layout, spacing, flex/grid positioning, and responsive structure.
- Component-specific styles remain local to the relevant component when needed.

## Conventions

- Prefer semantic class names such as `header`, `card`, `title`, `actionButton`, and `sectionHeading`.
- Keep layout in Tailwind utilities and keep visual treatment in shared CSS classes.
- Reuse shared classes instead of duplicating similar styles.
- Avoid deep CSS nesting and keep selectors shallow.

## Files to know

- [frontEnd/src/features/recipe-tool/styles/recipe-tool.css](frontEnd/src/features/recipe-tool/styles/recipe-tool.css): shared tokens and reusable component styles.
- [frontEnd/src/features/recipe-tool/process/styles/RecipeProcessCanvas.css](frontEnd/src/features/recipe-tool/process/styles/RecipeProcessCanvas.css): Recipe Process editor shell, canvas and modals.
- [frontEnd/src/features/recipe-tool/process/styles/RecipeProcessSidebar.css](frontEnd/src/features/recipe-tool/process/styles/RecipeProcessSidebar.css): Recipe Process sidebar styles.
- [frontEnd/src/features/recipe-tool/process/styles/RecipePropertiesPanel.css](frontEnd/src/features/recipe-tool/process/styles/RecipePropertiesPanel.css): step/condition panel controls, also used by the Ingredients and Nutrition tabs.
- [frontEnd/src/features/recipes/RecipeHomePage.tsx](frontEnd/src/features/recipes/RecipeHomePage.tsx): recipe landing page.
