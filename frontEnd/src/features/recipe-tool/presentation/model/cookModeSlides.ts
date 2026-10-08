import type { SlideshowStep } from '../../process/components/RecipeVisualizationSlideshow'
import { describeOutcome, type RecipeTimelineSection } from './recipePresentation'

/**
 * Cook mode: the whole recipe as one walkthrough, in exactly the order the Recipe Process page
 * shows it (every stage, then the main process), built from the same presentation so both say the
 * same thing. Steps carry their one-sentence instruction (ingredients, amounts, heat and time
 * included), plus any extra explanation and what "done" looks like; checks become a question with
 * an answer per outcome that jumps to where it leads (and are never narrated).
 *
 * Slide ids are the presentation keys, `${processId}:${nodeId}` — the recipe-wide step ids the
 * narration player expects (see recipeStepId in useNarrationPlayer).
 */
export const buildCookModeSlides = (sections: RecipeTimelineSection[]): SlideshowStep[] => {
  const totalSteps = sections.reduce((count, section) => count + section.stepCount, 0)
  const staged = sections.length > 1

  return sections.flatMap((section) => section.items.map<SlideshowStep>((item) => {
    const where = staged ? `${section.title} · ` : ''

    if (item.kind === 'check') {
      return {
        id: item.key,
        title: '',
        label: `${where}Check`,
        instruction: item.question,
        description: item.notes ? `How to check: ${item.notes}` : undefined,
        choices: item.outcomes.map((outcome) => ({
          key: outcome.key,
          label: outcome.answer,
          detail: describeOutcome(outcome),
          targetId: outcome.target.key,
        })),
        silent: true,
      }
    }

    const description = [
      item.explanation,
      item.readyWhen ? `Ready when: ${item.readyWhen}` : '',
    ].filter(Boolean).join(' · ')

    return {
      id: item.key,
      title: item.showName ? item.name : '',
      label: `${where}Step ${item.number} of ${totalSteps}`,
      instruction: item.instructionText,
      description: description || undefined,
      imageUrl: item.imageUrl,
      stepNumber: item.number,
    }
  }))
}
