/**
 * The bench run file (bench.json): which recipes to run, with which model combinations, as which
 * account. Credentials are never stored here: the file only names the environment variables that
 * hold them.
 */

import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'

export type Stage = 'text' | 'image' | 'speech'
export const ALL_STAGES: Stage[] = ['text', 'image', 'speech']

/** bench.json modality name -> backend AiCapability. */
export const CAPABILITY: Record<Stage, string> = {
  text: 'TEXT_TO_TEXT',
  image: 'TEXT_TO_IMAGE',
  speech: 'TEXT_TO_SPEECH',
}

export type AccountRef = {
  /** Env var holding the account's email. */
  emailEnv?: string
  /** Env var holding the account's password. */
  passwordEnv?: string
  /** Env var holding a ready JWT (skips login). */
  tokenEnv?: string
}

export type RecipeInput = { id: string; text: string; source: string }

export type Combo = {
  name: string
  text?: string
  image?: string
  speech?: string
  /**
   * Admin runtime config to apply while this combo runs, by section then key, e.g.
   * {"images": {"sceneAnchor.enabled": "false"}}. Reverted before the next combo and at the end.
   */
  adminConfig?: Record<string, Record<string, string>>
}

export type BenchConfig = {
  baseUrl: string
  auth: { user: AccountRef; admin?: AccountRef }
  recipes: RecipeInput[]
  combos: Combo[]
  repeats: number
  /** Parallel runs inside one combo (combos always run one after another: they share the user's model preferences). */
  concurrency: number
  /** Credits the admin grants the user before the run (0 = none). */
  grantCredits: number
  /** Let the admin temporarily enable/make user-selectable any model a combo names; restored afterwards. */
  allowAdminOverrides: boolean
  failOnFallback: boolean
  cleanup: 'keep' | 'delete'
  downloadAssets: boolean
  /** Per-stage polling timeouts in seconds. */
  timeouts: Record<Stage, number>
  configDir: string
}

const fail = (message: string): never => {
  throw new Error(`bench config: ${message}`)
}

const asString = (value: unknown, field: string): string => {
  if (typeof value !== 'string' || value.trim() === '') fail(`"${field}" must be a non-empty string`)
  return (value as string).trim()
}

const optionalString = (value: unknown, field: string): string | undefined =>
  value === undefined || value === null || value === '' ? undefined : asString(value, field)

const account = (raw: unknown, field: string): AccountRef => {
  if (!raw || typeof raw !== 'object') fail(`"${field}" must be an object`)
  const value = raw as Record<string, unknown>
  for (const key of Object.keys(value)) {
    if (!['emailEnv', 'passwordEnv', 'tokenEnv'].includes(key)) {
      fail(`"${field}.${key}" is not allowed: put credentials in environment variables and name them with emailEnv/passwordEnv/tokenEnv`)
    }
  }
  const ref: AccountRef = {
    emailEnv: optionalString(value.emailEnv, `${field}.emailEnv`),
    passwordEnv: optionalString(value.passwordEnv, `${field}.passwordEnv`),
    tokenEnv: optionalString(value.tokenEnv, `${field}.tokenEnv`),
  }
  if (!ref.tokenEnv && !(ref.emailEnv && ref.passwordEnv)) fail(`"${field}" needs tokenEnv, or emailEnv and passwordEnv`)
  return ref
}

/** {section: {key: value}}; booleans and numbers are accepted and sent as strings (the API's format). */
const adminConfig = (raw: unknown, field: string): Combo['adminConfig'] => {
  if (raw === undefined || raw === null) return undefined
  if (typeof raw !== 'object' || Array.isArray(raw)) return fail(`"${field}" must be an object of sections`)
  const result: Record<string, Record<string, string>> = {}
  for (const [section, values] of Object.entries(raw as Record<string, unknown>)) {
    if (!values || typeof values !== 'object' || Array.isArray(values)) fail(`"${field}.${section}" must be an object of settings`)
    result[section] = {}
    for (const [key, value] of Object.entries(values as Record<string, unknown>)) {
      if (!['string', 'boolean', 'number'].includes(typeof value)) fail(`"${field}.${section}.${key}" must be a string, number or boolean`)
      result[section][key] = String(value)
    }
  }
  return Object.keys(result).length > 0 ? result : undefined
}

export function loadConfig(path: string): BenchConfig {
  const file = resolve(path)
  const configDir = dirname(file)
  let raw: Record<string, unknown>
  try {
    raw = JSON.parse(readFileSync(file, 'utf8'))
  } catch (error) {
    return fail(`cannot read ${file}: ${(error as Error).message}`)
  }

  const auth = (raw.auth ?? fail('"auth" is required')) as Record<string, unknown>
  const recipesRaw = raw.recipes
  if (!Array.isArray(recipesRaw) || recipesRaw.length === 0) fail('"recipes" must be a non-empty array')
  const recipes = (recipesRaw as Record<string, unknown>[]).map((entry, index): RecipeInput => {
    const id = asString(entry.id, `recipes[${index}].id`)
    if (entry.file) {
      const source = resolve(configDir, asString(entry.file, `recipes[${index}].file`))
      const text = readFileSync(source, 'utf8').trim()
      if (!text) fail(`recipes[${index}] file ${source} is empty`)
      return { id, text, source }
    }
    return { id, text: asString(entry.text, `recipes[${index}].text`), source: 'inline' }
  })
  const recipeIds = new Set<string>()
  for (const recipe of recipes) {
    if (recipeIds.has(recipe.id)) fail(`duplicate recipe id "${recipe.id}"`)
    recipeIds.add(recipe.id)
  }

  const combosRaw = raw.combos
  if (!Array.isArray(combosRaw) || combosRaw.length === 0) fail('"combos" must be a non-empty array')
  const combos = (combosRaw as Record<string, unknown>[]).map((entry, index): Combo => {
    const combo: Combo = {
      name: asString(entry.name, `combos[${index}].name`),
      text: optionalString(entry.text, `combos[${index}].text`),
      image: optionalString(entry.image, `combos[${index}].image`),
      speech: optionalString(entry.speech, `combos[${index}].speech`),
      adminConfig: adminConfig(entry.adminConfig, `combos[${index}].adminConfig`),
    }
    if (!combo.text) fail(`combos[${index}] ("${combo.name}") needs a "text" model: images and narration are made from the generated process`)
    return combo
  })
  const comboNames = new Set<string>()
  for (const combo of combos) {
    if (comboNames.has(combo.name)) fail(`duplicate combo name "${combo.name}"`)
    comboNames.add(combo.name)
  }

  const int = (value: unknown, field: string, fallback: number, min: number) => {
    if (value === undefined) return fallback
    if (!Number.isInteger(value) || (value as number) < min) fail(`"${field}" must be an integer >= ${min}`)
    return value as number
  }
  const timeoutsRaw = (raw.timeouts ?? {}) as Record<string, unknown>

  const cleanup = raw.cleanup ?? 'keep'
  if (cleanup !== 'keep' && cleanup !== 'delete') fail('"cleanup" must be "keep" or "delete"')

  return {
    baseUrl: asString(raw.baseUrl ?? 'http://localhost:8080', 'baseUrl').replace(/\/+$/, ''),
    auth: {
      user: account(auth.user, 'auth.user'),
      admin: auth.admin ? account(auth.admin, 'auth.admin') : undefined,
    },
    recipes,
    combos,
    repeats: int(raw.repeats, 'repeats', 1, 1),
    concurrency: int(raw.concurrency, 'concurrency', 1, 1),
    grantCredits: int(raw.grantCredits, 'grantCredits', 0, 0),
    allowAdminOverrides: raw.allowAdminOverrides === true,
    failOnFallback: raw.failOnFallback === true,
    cleanup: cleanup as 'keep' | 'delete',
    downloadAssets: raw.downloadAssets !== false,
    timeouts: {
      text: int(timeoutsRaw.text, 'timeouts.text', 600, 10),
      image: int(timeoutsRaw.image, 'timeouts.image', 1200, 10),
      speech: int(timeoutsRaw.speech, 'timeouts.speech', 600, 10),
    },
    configDir,
  }
}

/** The stages a combo runs, limited by --stages. */
export const comboStages = (combo: Combo, only: Stage[]): Stage[] =>
  ALL_STAGES.filter((stage) => only.includes(stage) && combo[stage])
