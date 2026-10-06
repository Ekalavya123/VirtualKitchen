/**
 * Word-by-word reveal of a step's instruction, approximated from the narration's playback
 * position (no word timestamps yet). Kept pure so it is unit-testable (tests/wordReveal.test.ts);
 * precise timestamps can later replace {@link visibleWordCount} without touching the player.
 */

export const splitWords = (text: string): string[] => text.trim().split(/\s+/).filter(Boolean)

/**
 * How many of `totalWords` should be visible `currentTime` seconds into narration lasting
 * `duration` seconds. An unknown duration (NaN/Infinity while loading or streaming) falls back to
 * `fallbackDuration` (the backend's measured length), and without either nothing is shown yet.
 */
export function visibleWordCount(
  currentTime: number,
  duration: number,
  totalWords: number,
  fallbackDuration?: number | null,
): number {
  if (totalWords <= 0) return 0
  const length = Number.isFinite(duration) && duration > 0
    ? duration
    : fallbackDuration != null && Number.isFinite(fallbackDuration) && fallbackDuration > 0
      ? fallbackDuration
      : 0
  if (length === 0 || !Number.isFinite(currentTime) || currentTime <= 0) return 0
  if (currentTime >= length) return totalWords
  // Rounded up: a word appears as soon as its share of the narration starts, so the latest
  // visible word is roughly the one being spoken.
  return Math.min(totalWords, Math.max(0, Math.ceil((currentTime / length) * totalWords)))
}

const READING_MS_PER_WORD = 180
const READING_MIN_MS = 900
const READING_MAX_MS = 5000

/**
 * Total time to reveal text that has no narration to follow: scales with its length but stays
 * short (well inside the player's silent dwell of ~350 ms per word) so nobody waits on it.
 */
export const readingRevealMs = (totalWords: number): number =>
  totalWords <= 0 ? 0 : Math.min(READING_MAX_MS, Math.max(READING_MIN_MS, totalWords * READING_MS_PER_WORD))
