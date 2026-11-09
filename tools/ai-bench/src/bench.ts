/**
 * AI Bench — runs a set of recipes through every listed AI model combination, as a given account,
 * and reports latency, cost, fallbacks and output quality side by side.
 *
 *   node dist/bench.mjs run bench.json [--only-combo a,b] [--only-recipe x] [--stages text,image,speech] [--dry-run]
 *   node dist/bench.mjs report out/<runId>
 *   node dist/bench.mjs restore out/<runId> [--config bench.json]
 *
 * See README.md.
 */

import { appendFileSync, existsSync, mkdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { openSession, type ApiClient, type Session } from './api'
import { ALL_STAGES, CAPABILITY, comboStages, loadConfig, type BenchConfig, type Combo, type Stage } from './config'
import { PREFERENCES, RunState, type PreferencesView } from './state'
import { runOne } from './run'
import { writeReports } from './report'
import type { RunRecord } from './types'

type Args = { command: string; positional: string[]; flags: Map<string, string | true> }

function parseArgs(argv: string[]): Args {
  const [command = 'help', ...rest] = argv
  const positional: string[] = []
  const flags = new Map<string, string | true>()
  for (let i = 0; i < rest.length; i++) {
    const arg = rest[i]
    if (!arg.startsWith('--')) { positional.push(arg); continue }
    const [name, inline] = arg.slice(2).split('=', 2)
    if (inline !== undefined) flags.set(name, inline)
    else if (rest[i + 1] && !rest[i + 1].startsWith('--') && name !== 'dry-run') flags.set(name, rest[++i])
    else flags.set(name, true)
  }
  return { command, positional, flags }
}

const list = (value: string | true | undefined) => (typeof value === 'string' ? value.split(',').map((s) => s.trim()).filter(Boolean) : null)
const log = (line: string) => console.log(`${new Date().toISOString().slice(11, 19)} ${line}`)

type ModelDTO = { key: string; capability: string; enabled: boolean; userSelectable: boolean; tier: string; creditCost: number }

async function openSessions(config: BenchConfig, args: Args): Promise<{ user: Session; admin: Session | null }> {
  const userToken = typeof args.flags.get('token') === 'string' ? args.flags.get('token') as string : undefined
  const adminToken = typeof args.flags.get('admin-token') === 'string' ? args.flags.get('admin-token') as string : undefined
  const user = await openSession(config.baseUrl, config.auth.user, userToken)
  let admin: Session | null = null
  if (config.auth.admin || adminToken) {
    admin = await openSession(config.baseUrl, config.auth.admin ?? {}, adminToken)
    if (admin.user.userType !== 'ADMIN') throw new Error(`admin account (user ${admin.user.id}) is not an ADMIN`)
  }
  return { user, admin }
}

/** Checks every combo's models and works out which need an admin override to be usable. */
function planModels(config: BenchConfig, models: ModelDTO[], stages: Stage[]) {
  const byKey = new Map(models.map((model) => [model.key, model]))
  const needOverride = new Set<string>()
  const errors: string[] = []
  for (const combo of config.combos) {
    for (const stage of comboStages(combo, stages)) {
      const key = combo[stage]!
      const model = byKey.get(key)
      if (!model) { errors.push(`${combo.name}.${stage}: unknown model "${key}"`); continue }
      if (model.capability !== CAPABILITY[stage]) {
        errors.push(`${combo.name}.${stage}: "${key}" is a ${model.capability} model, not ${CAPABILITY[stage]}`)
        continue
      }
      if (!model.enabled || !model.userSelectable) needOverride.add(key)
    }
  }
  return { byKey, needOverride: [...needOverride], errors }
}

async function setPreferences(user: ApiClient, combo: Combo, stages: Stage[], baseline: Record<string, string | null>) {
  const modelKeys: Record<string, string | null> = { ...baseline }
  for (const stage of comboStages(combo, stages)) modelKeys[CAPABILITY[stage]] = combo[stage]!
  const view = await user.put<PreferencesView>(PREFERENCES, { modelKeys })
  const mismatched = comboStages(combo, stages)
    .filter((stage) => view.effective[CAPABILITY[stage]] !== combo[stage])
    .map((stage) => `${stage}: wanted ${combo[stage]}, effective ${view.effective[CAPABILITY[stage]]}`)
  if (mismatched.length > 0) throw new Error(`model preferences did not take effect (${mismatched.join('; ')})`)
}

/** Runs `tasks` with at most `limit` in flight. */
async function pool<T>(tasks: (() => Promise<T>)[], limit: number): Promise<T[]> {
  const results: T[] = []
  let next = 0
  await Promise.all(Array.from({ length: Math.min(limit, tasks.length) }, async () => {
    while (next < tasks.length) {
      const index = next++
      results[index] = await tasks[index]()
    }
  }))
  return results
}

async function commandRun(args: Args): Promise<number> {
  const configPath = args.positional[0] ?? 'bench.json'
  const config = loadConfig(configPath)
  const onlyCombos = list(args.flags.get('only-combo'))
  const onlyRecipes = list(args.flags.get('only-recipe'))
  const stages = (list(args.flags.get('stages')) ?? ALL_STAGES) as Stage[]
  for (const stage of stages) if (!ALL_STAGES.includes(stage)) throw new Error(`unknown stage "${stage}"`)
  if (onlyCombos) config.combos = config.combos.filter((combo) => onlyCombos.includes(combo.name))
  if (onlyRecipes) config.recipes = config.recipes.filter((recipe) => onlyRecipes.includes(recipe.id))
  if (!config.combos.length || !config.recipes.length) throw new Error('nothing to run after --only-* filters')

  const { user, admin } = await openSessions(config, args)
  log(`user ${user.user.id} (${user.user.userType ?? 'USER'})${admin ? `, admin ${admin.user.id}` : ', no admin account: no credit grant, overrides or server metrics'}`)

  const models = await user.api.get<ModelDTO[]>('/api/v1/ai/models')
  const plan = planModels(config, models, stages)
  if (plan.needOverride.length > 0 && !(config.allowAdminOverrides && admin)) {
    plan.errors.push(`not enabled/user-selectable: ${plan.needOverride.join(', ')} (set "allowAdminOverrides": true and an admin account)`)
  }
  const total = config.recipes.length * config.combos.length * config.repeats

  console.log('\nPlan:')
  for (const combo of config.combos) {
    const parts = comboStages(combo, stages).map((stage) => {
      const model = plan.byKey.get(combo[stage]!)
      return `${stage}=${combo[stage]}${model ? ` (${model.tier}, ${model.creditCost} cr${plan.needOverride.includes(model.key) ? ', needs override' : ''})` : ''}`
    })
    console.log(`  ${combo.name}: ${parts.join('  ')}`)
  }
  console.log(`  ${config.recipes.length} recipes × ${config.combos.length} combos × ${config.repeats} repeats = ${total} runs, concurrency ${config.concurrency}\n`)
  if (plan.errors.length > 0) {
    for (const error of plan.errors) console.error(`  ✗ ${error}`)
    return 2
  }
  if (args.flags.has('dry-run')) {
    console.log('Dry run: config, accounts and models are OK. Nothing was changed.')
    return 0
  }

  const runId = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
  const outRoot = typeof args.flags.get('out') === 'string' ? resolve(args.flags.get('out') as string) : join(config.configDir, 'out')
  const outDir = join(outRoot, runId)
  mkdirSync(outDir, { recursive: true })
  const resultsFile = join(outDir, 'results.jsonl')
  const state = RunState.create(outDir, config.baseUrl, user.user.id)
  log(`output: ${outDir}`)

  let interrupted = false
  const restore = () => state.restore(user.api, admin?.api ?? null, log)
  process.once('SIGINT', () => {
    interrupted = true
    log('interrupted: restoring preferences/config, then exiting (Ctrl+C again to force)')
    restore().catch((error) => console.error(error.message)).finally(() => process.exit(130))
  })

  const records: RunRecord[] = []
  try {
    await state.snapshotPreferences(user.api)
    if (plan.needOverride.length > 0 && admin) {
      const changed = await state.ensureSelectable(admin.api, plan.needOverride)
      log(`admin overrides (temporary): ${changed.join(', ')}`)
    }
    if (config.grantCredits > 0 && admin) {
      const balance = await admin.api.post<{ availableBalance: number }>(
        `/api/v1/admin/users/${user.user.id}/ai-credits/grant`, { amount: config.grantCredits })
      log(`granted ${config.grantCredits} credits, balance ${balance.availableBalance}`)
    }

    let done = 0
    for (const combo of config.combos) {
      if (interrupted) break
      await setPreferences(user.api, combo, stages, state.data.preferences ?? {})
      log(`combo ${combo.name}: preferences set`)
      const tasks = config.recipes.flatMap((recipe) => Array.from({ length: config.repeats }, (_, index) => async () => {
        const record = await runOne({
          runId, outDir, user: user.api, admin: admin?.api ?? null, stages, timeouts: config.timeouts,
          downloadAssets: config.downloadAssets, onRecipeCreated: (id) => state.addRecipe(id), log,
        }, recipe, combo, index + 1)
        appendFileSync(resultsFile, JSON.stringify(record) + '\n')
        log(`(${++done}/${total}) ${recipe.id} × ${combo.name} #${index + 1}: ${record.status}${record.fallback ? ' (fallback)' : ''} in ${(record.totalMs / 1000).toFixed(1)}s`)
        return record
      }))
      records.push(...await pool(tasks, config.concurrency))
    }
  } finally {
    await restore().catch((error) => console.error(error.message))
    if (config.cleanup === 'delete') {
      for (const id of state.data.recipeIds) {
        await user.api.delete(`/api/v1/process-templates/${id}`).catch((error) => console.error(`delete recipe ${id}: ${error.message}`))
      }
      log(`deleted ${state.data.recipeIds.length} bench recipes`)
    }
    if (records.length > 0) {
      writeReports(outDir, records, config.baseUrl)
      log(`report: ${join(outDir, 'report.html')}`)
    }
  }

  const failed = records.filter((record) => record.status !== 'ok').length
  const fallbacks = records.filter((record) => record.fallback).length
  log(`done: ${records.length - failed} ok, ${failed} failed, ${fallbacks} with fallback`)
  return failed > 0 || (config.failOnFallback && fallbacks > 0) ? 1 : 0
}

function readRecords(outDir: string): RunRecord[] {
  const file = join(outDir, 'results.jsonl')
  if (!existsSync(file)) throw new Error(`${file} not found`)
  return readFileSync(file, 'utf8').split('\n').filter(Boolean).map((line) => JSON.parse(line))
}

async function commandReport(args: Args): Promise<number> {
  const outDir = resolve(args.positional[0] ?? '')
  const records = readRecords(outDir)
  const baseUrl = existsSync(join(outDir, 'restore.json')) ? RunState.load(outDir).data.baseUrl : ''
  writeReports(outDir, records, baseUrl)
  log(`wrote ${join(outDir, 'summary.csv')} and report.html (${records.length} runs)`)
  return 0
}

async function commandRestore(args: Args): Promise<number> {
  const outDir = resolve(args.positional[0] ?? '')
  const state = RunState.load(outDir)
  const configFlag = args.flags.get('config')
  const config = loadConfig(typeof configFlag === 'string' ? configFlag : 'bench.json')
  if (config.baseUrl !== state.data.baseUrl) throw new Error(`run was against ${state.data.baseUrl}, config points to ${config.baseUrl}`)
  const { user, admin } = await openSessions(config, args)
  if (user.user.id !== state.data.userId) throw new Error(`run was for user ${state.data.userId}, logged in as ${user.user.id}`)
  await state.restore(user.api, admin?.api ?? null, log)
  return 0
}

const HELP = `AI Bench
  run <bench.json> [--only-combo a,b] [--only-recipe x,y] [--stages text,image,speech] [--dry-run] [--out dir] [--token JWT] [--admin-token JWT]
  report <outDir>                       re-render summary.csv and report.html from results.jsonl
  restore <outDir> [--config bench.json] put back model preferences / admin overrides after a killed run`

async function main() {
  const args = parseArgs(process.argv.slice(2))
  const commands: Record<string, (args: Args) => Promise<number>> = { run: commandRun, report: commandReport, restore: commandRestore }
  const command = commands[args.command]
  if (!command) {
    console.log(HELP)
    return args.command === 'help' || args.command === '--help' ? 0 : 2
  }
  return command(args)
}

main().then((code) => process.exit(code), (error) => {
  console.error(`error: ${error instanceof Error ? error.message : String(error)}`)
  process.exit(1)
})
