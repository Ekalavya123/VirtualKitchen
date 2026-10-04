import { useEffect, useState, useSyncExternalStore } from 'react'
import { httpStatusOf } from '../../../../api/client'
import { StepNarrationApi } from '../../../../api/narrationApi'
import { createHtmlNarrationAudio } from './narrationAudio'
import { NarrationPlayer, PLAYBACK_RATES, type NarrationPlayerState, type NarrationSource, type PlayerStep } from './narrationPlayer'

const PREFS_KEY = 'recipeTool.narration.prefs.v1'

type NarrationPrefs = Pick<NarrationPlayerState, 'narrationEnabled' | 'muted' | 'playbackRate'>

/** Per-viewer playback preferences; storage can be unavailable (private mode), so every access is guarded. */
const loadPrefs = (): Partial<NarrationPrefs> => {
  try {
    const raw = window.localStorage.getItem(PREFS_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw) as Partial<NarrationPrefs>
    return {
      narrationEnabled: typeof parsed.narrationEnabled === 'boolean' ? parsed.narrationEnabled : undefined,
      muted: typeof parsed.muted === 'boolean' ? parsed.muted : undefined,
      playbackRate: PLAYBACK_RATES.includes(parsed.playbackRate as (typeof PLAYBACK_RATES)[number])
        ? parsed.playbackRate
        : undefined,
    }
  } catch {
    return {}
  }
}

const savePrefs = (prefs: NarrationPrefs) => {
  try {
    window.localStorage.setItem(PREFS_KEY, JSON.stringify(prefs))
  } catch {
    // Preferences are a convenience only.
  }
}

/** A failed narration request as the viewer should read it (the raw HTTP text means nothing to them). */
const describeRequestError = (error: unknown): Error => {
  const status = httpStatusOf(error)
  const message =
    status === 401 || status === 403 ? 'narration is not available for your account on this recipe'
      : status === 404 ? 'this server does not provide narration'
        : status === 429 ? 'narration is busy right now, try again shortly'
          : status === 503 ? 'no speech provider is available'
            : status != null && status >= 500 ? 'the speech service failed, try again later'
              : 'could not reach the narration service'
  return new Error(message, { cause: error })
}

export type UseNarrationPlayerOptions = {
  recipeId: number
  processId: number
  steps: PlayerStep[]
  /**
   * Resolves once the editor's pending changes are saved (true) or could not be (false). Narration
   * is generated from the *saved* step text, so nothing is requested from the backend before this
   * settles — otherwise a just-edited step would be narrated from its previous text.
   */
  ready?: Promise<boolean> | null
}

export type UseNarrationPlayerResult = {
  player: NarrationPlayer
  state: NarrationPlayerState
  /** Narration cannot be fetched at all (e.g. the process has never been saved). */
  unavailable: boolean
  /** The editor's latest changes could not be saved, so narration may lag what is on screen. */
  syncWarning: boolean
}

/**
 * One NarrationPlayer for the lifetime of the calling component (the slideshow mounts per opening,
 * and the canvas around it remounts per process, so the ids never change underneath it).
 */
export function useNarrationPlayer({ recipeId, processId, steps, ready }: UseNarrationPlayerOptions): UseNarrationPlayerResult {
  const unavailable = !(recipeId > 0 && processId > 0)
  const [{ player, gate }] = useState(() => {
    const gate = (ready ?? Promise.resolve(true)).catch(() => false)
    const source: NarrationSource | null = unavailable
      ? null
      : {
          list: async () => {
            await gate
            return StepNarrationApi.list(recipeId, processId)
          },
          ensure: async (stepId, force) => {
            await gate
            try {
              return await StepNarrationApi.ensure(recipeId, processId, stepId, force)
            } catch (error) {
              throw describeRequestError(error)
            }
          },
        }
    return {
      gate,
      player: new NarrationPlayer({ steps, source, audio: createHtmlNarrationAudio(), initial: loadPrefs() }),
    }
  })
  const [syncWarning, setSyncWarning] = useState(false)
  const stepsKey = steps.map((step) => `${step.id}\u0000${step.text ?? ''}`).join('\u0001')

  useEffect(() => {
    let active = true
    void gate.then((ok) => {
      if (active) setSyncWarning(!ok)
    })
    void player.init()
    const unsubscribe = player.subscribe(() => {
      const { narrationEnabled, muted, playbackRate } = player.getState()
      savePrefs({ narrationEnabled, muted, playbackRate })
    })
    return () => {
      active = false
      unsubscribe()
      player.halt()
    }
  }, [player, gate])

  useEffect(() => {
    player.setSteps(steps)
    // Keyed on content, not array identity: the parent rebuilds the array on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [player, stepsKey])

  const state = useSyncExternalStore(player.subscribe, player.getState)

  return { player, state, unavailable, syncWarning }
}
