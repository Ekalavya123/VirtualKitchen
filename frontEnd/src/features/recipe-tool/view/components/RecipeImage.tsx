import { useState } from 'react'

type RecipeImageProps = {
  src?: string
  alt: string
  /** Emoji shown on the decorative fallback when there's no image or it fails to load. */
  fallbackIcon: string
}

/** A cover-fit image that falls back to a decorative tile instead of ever showing a broken image. */
export default function RecipeImage({ src, alt, fallbackIcon }: RecipeImageProps) {
  // Remembers which src failed (not just "failed"), so a new src gets a fresh attempt.
  const [failedSrc, setFailedSrc] = useState<string | null>(null)

  if (!src || failedSrc === src) {
    return (
      <div className="rp-image-fallback" role="img" aria-label={alt}>
        <span aria-hidden>{fallbackIcon}</span>
      </div>
    )
  }

  return <img className="rp-image" src={src} alt={alt} loading="lazy" onError={() => setFailedSrc(src)} />
}
