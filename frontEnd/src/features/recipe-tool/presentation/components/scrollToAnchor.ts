/**
 * Smooth-scrolls the Recipe Process page to an in-page anchor without touching the router URL
 * (a plain `#hash` link would push a history entry on every click).
 */
export const scrollToAnchor = (anchorId: string) => {
  document.getElementById(anchorId)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

export const stepAnchorId = (stepNumber: number) => `recipe-step-${stepNumber}`
