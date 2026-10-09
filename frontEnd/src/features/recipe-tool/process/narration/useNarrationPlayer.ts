import { useEffect, useState, useSyncExternalStore } from 'react'
import { httpStatusOf } from '../../../../api/client'
import { StepNarrationApi } from '../../../../api/narrationApi'
import { createHtmlNarrationAudio } from './narrationAudio'
import { NarrationPlayer, PLAYBACK_RATES, type NarrationPlayerState, type NarrationSource, type PlayerStep } from './narrationPlayer'
import type { StepNarration } from '../../../../types/narration'

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

/** A recipe-wide step id: the step's process and its node id, as `${processId}:${nodeId}`. */
export const recipeStepId = (processId: number, nodeId: string) => `${processId}:${nodeId}`

const splitRecipeStepId = (id: string) => {
  const separator = id.indexOf(':')
  return { processId: Number(id.slice(0, separator)), stepId: id.slice(separator + 1) }
}

const silentNarration = (stepId: string): StepNarration => ({ stepId, status: 'NOT_GENERATED', narratable: false })

/**
 * Narration across every process of a recipe (Cook mode): step ids are recipe-wide
 * ({@link recipeStepId}), and each request goes to that step's own process. Steps in `silent`
 * (e.g. checks) are never narrated; a process that isn't saved yet (a temporary negative id) has
 * no narration to fetch. The processes are the ones the walkthrough opened with (it mounts per opening).
 */
const recipeWideSource = (
  recipeId: number,
  gate: Promise<boolean>,
  steps: PlayerStep[],
  silent: ReadonlySet<string>,
): NarrationSource => {
  const processIds = [...new Set(steps.map((step) => splitRecipeStepId(step.id).processId))].filter((id) => id > 0)
  return {
    list: async () => {
      await gate
      const perProcess = await Promise.all(processIds.map(async (processId) =>
        (await StepNarrationApi.list(recipeId, processId)).map((narration) => ({ ...narration, stepId: recipeStepId(processId, narration.stepId) }))))
      return [...perProcess.flat().filter((narration) => !silent.has(narration.stepId)), ...[...silent].map(silentNarration)]
    },
    ensure: async (id, force) => {
      if (silent.has(id)) return silentNarration(id)
      await gate
      const { processId, stepId } = splitRecipeStepId(id)
      if (!(processId > 0)) throw new Error('this part of the recipe is not saved yet')
      try {
        return { ...(await StepNarrationApi.ensure(recipeId, processId, stepId, force)), stepId: id }
      } catch (error) {
        throw describeRequestError(error)
      }
    },
  }
}

export type UseNarrationPlayerOptions = {
  recipeId: number
  /**
   * The process whose steps are narrated, or null for a recipe-wide walkthrough (Cook mode), whose
   * step ids are then {@link recipeStepId}s spanning several processes.
   */
  processId: number | null
  steps: PlayerStep[]
  /** Steps that are never narrated (recipe-wide mode only), e.g. checks. */
  silentStepIds?: readonly string[]
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
export function useNarrationPlayer({ recipeId, processId, steps, silentStepIds, ready }: UseNarrationPlayerOptions): UseNarrationPlayerResult {
  const unavailable = !(recipeId > 0 && (processId == null || processId > 0))
  const [{ player, gate }] = useState(() => {
    const gate = (ready ?? Promise.resolve(true)).catch(() => false)
    const source: NarrationSource | null = unavailable
      ? null
      : processId == null
        ? recipeWideSource(recipeId, gate, steps, new Set(silentStepIds))
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
