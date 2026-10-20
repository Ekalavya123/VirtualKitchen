import type { CSSProperties } from 'react'
import type { AddToMyRecipes } from './useAddToMyRecipes'

type AddToMyRecipesButtonProps = {
  action: AddToMyRecipes
  className?: string
  style?: CSSProperties
  /** Hide the label on narrow screens (the navbar), keeping just the icon. */
  compact?: boolean
}

/** A reader's way out of read mode: "Add to My Recipes", or "Open your copy" once they already have one. */
export default function AddToMyRecipesButton({ action, className, style, compact = false }: AddToMyRecipesButtonProps) {
  const hasCopy = action.existingCopyId != null
  const label = hasCopy ? 'Open your copy' : action.adding ? 'Adding…' : 'Add to My Recipes'
  return (
    <button
      type="button"
      className={className}
      style={style}
      onClick={() => (hasCopy ? action.openCopy() : void action.add())}
      // Wait until we know whether a copy exists, so a quick click never makes a duplicate.
      disabled={action.adding || action.existingCopyId === undefined}
      aria-label={label}
      title={hasCopy
        ? 'You already added this recipe — open your own copy to edit or order it'
        : 'Copy this recipe into your kitchen so you can edit, customise and order it'}
    >
      <span aria-hidden>{hasCopy ? '✓' : '+'}</span>
      <span className={compact ? 'hidden md:inline' : undefined}> {label}{hasCopy ? ' →' : ''}</span>
    </button>
  )
}
