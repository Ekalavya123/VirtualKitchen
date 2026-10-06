/**
 * Spoken narration of a recipe step, as returned by the backend
 * (recipe/dto/StepNarrationResponseDTO). Provider-independent: `provider`/`modelKey` are metadata
 * only, and `audioUrl` is set only while `status` is READY — stale audio is never handed out.
 */
export type StepNarrationStatus = 'NOT_GENERATED' | 'GENERATING' | 'READY' | 'STALE' | 'FAILED'

export interface StepNarration {
  stepId: string
  status: StepNarrationStatus
  /** False when the step has no text to speak; such a step never gets narration. */
  narratable: boolean
  /** The exact script spoken for the step's current saved content; when READY it matches the audio word for word. */
  text?: string | null
  audioUrl?: string | null
  mimeType?: string | null
  durationSeconds?: number | null
  provider?: string | null
  modelKey?: string | null
  voice?: string | null
  languageCode?: string | null
  generatedAt?: string | null
  failureReason?: string | null
  /** For FAILED: when an automatic retry is allowed again. */
  retryAfter?: string | null
}
