/**
 * What a run changes outside its own recipes — the user's saved model choices and any admin
 * model-config overrides — recorded to out/<runId>/restore.json *before* changing it, so that
 * `bench restore` can put it back even if the run was killed.
 */

import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import type { ApiClient } from './api'

export type RestoreState = {
  baseUrl: string
  userId: number
  /** The user's own saved choices (not the effective ones) before the run. */
  preferences: Record<string, string | null> | null
  /** Admin `models` config values before the run, for every key the run changed. */
  adminModelConfig: Record<string, string>
  /** Recipes the run created (for `cleanup: delete`). */
  recipeIds: number[]
  restored: boolean
}

export type PreferencesView = {
  modelKeys: Record<string, string | null> | null
  effective: Record<string, string | null>
  staleChoice: boolean
}
type ConfigField = { key: string; current: string; options: string[]; type: string }
type SectionView = { id: string; fields: ConfigField[] }

export const PREFERENCES = '/api/v1/users/me/ai-preferences'
const MODELS_CONFIG = '/api/v1/admin/config/models'

export class RunState {
  private constructor(private readonly file: string, readonly data: RestoreState) {}

  static create(outDir: string, baseUrl: string, userId: number): RunState {
    const state = new RunState(join(outDir, 'restore.json'), {
      baseUrl, userId, preferences: null, adminModelConfig: {}, recipeIds: [], restored: false,
    })
    state.save()
    return state
  }

  static load(outDir: string): RunState {
    const file = join(outDir, 'restore.json')
    if (!existsSync(file)) throw new Error(`${file} not found`)
    return new RunState(file, JSON.parse(readFileSync(file, 'utf8')))
  }

  save() {
    writeFileSync(this.file, JSON.stringify(this.data, null, 2))
  }

  async snapshotPreferences(user: ApiClient) {
    const view = await user.get<PreferencesView>(PREFERENCES)
    this.data.preferences = { ...(view.modelKeys ?? {}) }
    this.save()
  }

  addRecipe(recipeId: number) {
    this.data.recipeIds.push(recipeId)
    this.save()
  }

  /**
   * Makes sure every model in `modelKeys` is enabled and user-selectable, changing (and recording)
   * only what isn't. Returns the keys it changed.
   */
  async ensureSelectable(admin: ApiClient, modelKeys: string[]): Promise<string[]> {
    const section = await admin.get<SectionView>(MODELS_CONFIG)
    const fields = new Map(section.fields.map((field) => [field.key, field]))
    const changes: Record<string, string> = {}
    for (const key of modelKeys) {
      for (const setting of ['enabled', 'userSelectable']) {
        const fieldKey = `model.${key}.${setting}`
        const field = fields.get(fieldKey)
        if (!field) throw new Error(`admin config has no "${fieldKey}" (is "${key}" a registered model?)`)
        if (field.current === 'true') continue
        if (!(fieldKey in this.data.adminModelConfig)) this.data.adminModelConfig[fieldKey] = field.current
        changes[fieldKey] = 'true'
      }
    }
    if (Object.keys(changes).length === 0) return []
    this.save() // record the old values before changing anything
    await admin.put(MODELS_CONFIG, { values: changes })
    return Object.keys(changes)
  }

  /** Puts back the user's choices and the admin config. Safe to call twice. */
  async restore(user: ApiClient, admin: ApiClient | null, log: (line: string) => void) {
    const errors: string[] = []
    if (this.data.preferences) {
      try {
        await user.put(PREFERENCES, { modelKeys: this.data.preferences })
        log(`restored model preferences: ${JSON.stringify(this.data.preferences)}`)
      } catch (error) {
        errors.push(`preferences: ${(error as Error).message}`)
      }
    }
    const adminValues = this.data.adminModelConfig
    if (Object.keys(adminValues).length > 0) {
      if (!admin) {
        errors.push(`admin model config needs an admin session to restore: ${JSON.stringify(adminValues)}`)
      } else {
        try {
          // Writing a field's baseline value removes the override (RuntimeConfigService.update).
          await admin.put(MODELS_CONFIG, { values: adminValues })
          log(`restored admin model config: ${JSON.stringify(adminValues)}`)
        } catch (error) {
          errors.push(`admin model config: ${(error as Error).message}`)
        }
      }
    }
    this.data.restored = errors.length === 0
    this.save()
    if (errors.length > 0) throw new Error(`restore incomplete:\n  ${errors.join('\n  ')}`)
  }
}
