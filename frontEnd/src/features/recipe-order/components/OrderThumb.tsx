import RecipeImage from '../../recipe-tool/presentation/components/RecipeImage'
import '../../recipe-tool/presentation/styles/recipe-process.css'
import '../recipeOrder.css'

type OrderThumbProps = {
  src?: string | null
  alt: string
  /** Emoji for the fallback tile (no image, or it failed to load). */
  icon?: string | null
  size?: 'sm' | 'md' | 'lg'
}

/** A recipe's thumbnail in a fixed rounded frame, falling back to its icon. */
export default function OrderThumb({ src, alt, icon, size = 'md' }: OrderThumbProps) {
  return (
    <div className={`ro-thumb ro-thumb-${size}`}>
      <RecipeImage src={src ?? undefined} alt={alt} fallbackIcon={icon || '🍳'} />
    </div>
  )
}
