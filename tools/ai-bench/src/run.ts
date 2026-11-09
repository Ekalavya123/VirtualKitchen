/** One bench run: a fresh recipe, run through one model combination, measured end to end. */

import { mkdirSync, writeFileSync } from 'node:fs'
import { join, relative } from 'node:path'
import type { ApiClient } from './api'
import type { Combo, RecipeInput, Stage } from './config'
import type { Process } from '../../../frontEnd/src/types/process'
import type { RunRecord } from './types'
import { runGeneration, saveGeneratedProcesses } from './stages/generate'
import { runVisualization } from './stages/visualize'
import { runNarration } from './stages/narrate'
import { summarizeSteps } from './stages/steps'
import { collectServerMetrics } from './stages/metrics'
import { assessQuality } from './quality'

export type RunContext = {
  runId: string
  outDir: string
  user: ApiClient
  admin: ApiClient | null
  stages: Stage[]
  timeouts: Record<Stage, number>
  downloadAssets: boolean
  onRecipeCreated: (recipeId: number) => void
  log: (line: string) => void
}

const safe = (value: string) => value.replace(/[^A-Za-z0-9._-]+/g, '_').slice(0, 80)

const EXTENSIONS: Record<string, string> = {
  'image/png': 'png', 'image/jpeg': 'jpg', 'image/webp': 'webp',
  'audio/mpeg': 'mp3', 'audio/mp3': 'mp3', 'audio/wav': 'wav', 'audio/x-wav': 'wav', 'audio/ogg': 'ogg', 'audio/webm': 'webm',
}

async function download(api: ApiClient, url: string, dir: string, name: string, outDir: string): Promise<string | undefined> {
  try {
    const { bytes, contentType } = await api.download(url)
    const fromUrl = /\.([a-z0-9]{2,4})(?:\?|$)/i.exec(url)?.[1]
    const extension = EXTENSIONS[(contentType ?? '').split(';')[0].trim()] ?? fromUrl ?? 'bin'
    const file = join(dir, `${safe(name)}.${extension}`)
    writeFileSync(file, bytes)
    return relative(outDir, file).replaceAll('\\', '/')
  } catch {
    return undefined
  }
}

export async function runOne(ctx: RunContext, recipe: RecipeInput, combo: Combo, repeat: number): Promise<RunRecord> {
  const api = ctx.user.fork()
  const started = Date.now()
  const record: RunRecord = {
    runId: ctx.runId, recipe: recipe.id, combo: combo.name, repeat, startedAt: new Date(started).toISOString(),
    totalMs: 0, status: 'failed', fallback: false, stages: {}, steps: [], apiStats: api.stats,
  }
  const artifactDir = join(ctx.outDir, 'artifacts', safe(recipe.id), safe(combo.name), String(repeat))
  mkdirSync(artifactDir, { recursive: true })
  const tag = `[${recipe.id} × ${combo.name} #${repeat}]`

  try {
    const created = await api.post<{ id: number }>('/api/v1/process-templates', {
      name: `bench ${ctx.runId} ${recipe.id} ${combo.name} #${repeat}`.slice(0, 120),
      description: 'Created by tools/ai-bench',
    })
    record.recipeId = created.id
    ctx.onRecipeCreated(created.id)
    ctx.log(`${tag} recipe ${created.id}: generating process with ${combo.text}`)

    // Text → text (always: images and narration are made from its saved steps).
    const generation = await runGeneration(api, created.id, recipe.text, combo.text!, ctx.timeouts.text)
    record.stages.text = generation.stage
    if (!generation.result || !generation.stage.jobId) throw new Error(`generation: ${generation.stage.error ?? 'no result'}`)
    writeFileSync(join(artifactDir, 'generation.json'), JSON.stringify(generation.result, null, 2))

    const { processes, problems } = await saveGeneratedProcesses(api, created.id, generation.stage.jobId, generation.result, generation.stage)
    if (problems.length > 0) {
      generation.stage.status = 'partial'
      generation.stage.error = `converter problems: ${problems.join('; ')}`
      record.quality = assessQuality(record, [], [], problems)
      throw new Error(generation.stage.error)
    }
    writeFileSync(join(artifactDir, 'processes.json'), JSON.stringify(processes, null, 2))
    record.steps = summarizeSteps(processes)
    ctx.log(`${tag} ${record.steps.length} nodes saved in ${generation.stage.clientMs}ms (${generation.stage.resolvedModels.join(',')})`)

    // Image then speech, one after the other, so each stage's latency isn't skewed by the other.
    if (ctx.stages.includes('image') && combo.image) {
      ctx.log(`${tag} visualizing with ${combo.image}`)
      const image = await runVisualization(api, created.id, processes, combo.image, ctx.timeouts.image)
      record.stages.image = image
      if (ctx.downloadAssets) {
        await Promise.all(image.steps.filter((step) => step.imageUrl).map(async (step) => {
          step.file = await download(api, step.imageUrl!, artifactDir, `img-${step.stepId}`, ctx.outDir)
        }))
      }
      ctx.log(`${tag} images ${image.succeeded}/${image.totalSteps} in ${image.clientMs}ms`)
    }
    if (ctx.stages.includes('speech') && combo.speech) {
      ctx.log(`${tag} narrating with ${combo.speech}`)
      const speech = await runNarration(api, created.id, processes, combo.speech, ctx.timeouts.speech)
      record.stages.speech = speech
      if (ctx.downloadAssets) {
        await Promise.all(speech.steps.filter((step) => step.audioUrl).map(async (step) => {
          step.file = await download(api, step.audioUrl!, artifactDir, `audio-${step.stepId}`, ctx.outDir)
        }))
      }
      ctx.log(`${tag} narration ${speech.ready}/${speech.narratable} in ${speech.clientMs}ms`)
    }

    record.quality = assessQuality(record, processes as Process[], record.steps, [])
    const stageList = Object.values(record.stages)
    record.status = stageList.every((stage) => stage.status === 'ok' || stage.status === 'skipped') ? 'ok' : 'failed'
    if (record.status === 'failed') {
      record.error = stageList.filter((stage) => stage.error).map((stage) => stage.error).join(' | ') || 'a stage did not fully succeed'
    }
  } catch (error) {
    record.error = (error as Error).message
    ctx.log(`${tag} FAILED: ${record.error}`)
  }

  record.fallback = Object.values(record.stages).some((stage) => stage.usedFallback)
  if (ctx.admin && record.recipeId) {
    try {
      record.server = await collectServerMetrics(ctx.admin, record.recipeId)
    } catch (error) {
      ctx.log(`${tag} could not read server analytics: ${(error as Error).message}`)
    }
  }
  record.totalMs = Date.now() - started
  return record
}
