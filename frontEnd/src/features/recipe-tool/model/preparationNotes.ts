/**
 * Default preparation guidance: routine handling every preparation assumes, so neither the AI nor the user has to
 * write a step for it. A recipe can replace it with its own text (RecipeDetail.preparationNotes). It is guidance
 * only — it never changes a step, and a recipe that needs something specific (soaking, deveining) still says so in
 * a step.
 */
export const DEFAULT_PREPARATION_NOTES = [
  'Rinse fresh produce, herbs, rice and lentils before use.',
  'Peel vegetables and fruit whose skin isn\'t eaten (onions, garlic, ginger) unless a step says otherwise.',
  'Pat meat, poultry and fish dry before seasoning or searing.',
  'Trim stems, roots and any damaged parts.',
].join('\n')

/** The guidance a recipe uses: its own text when it has any, else the default. */
export const effectivePreparationNotes = (notes: string | null | undefined) =>
  notes && notes.trim() ? notes : DEFAULT_PREPARATION_NOTES
