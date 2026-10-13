/** A caught error's message for display (an ApiError carries the backend's message), else `fallback`. */
export const errorMessage = (error: unknown, fallback: string): string =>
  error instanceof Error && error.message ? error.message : fallback
