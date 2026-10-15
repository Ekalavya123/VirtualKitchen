import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { evaluateRecipeReadiness, type ReadinessIssue } from '../src/features/recipe-tool/model/recipeReadiness.ts'
import type { Process } from '../src/types/process.ts'

/** Shared with the backend's RecipeReadinessEvaluatorTest, so the two implementations can't drift apart. */
type ReadinessCase = {
  name: string
  processes: Array<Omit<Process, 'recipeId'>>
  expected: Array<Pick<ReadinessIssue, 'code' | 'severity' | 'processId' | 'nodeId'>>
}

const cases: ReadinessCase[] = JSON.parse(
  readFileSync(new URL('../../backend/src/test/resources/readiness/readiness-cases.json', import.meta.url), 'utf8'))

const catalog = JSON.parse(readFileSync(new URL('../src/shared/catalog/stepCatalogs.data.json', import.meta.url), 'utf8')) as { actions: { id: string }[] }
const actionIds = new Set(catalog.actions.map((action) => action.id))

const key = (issue: Pick<ReadinessIssue, 'code' | 'severity' | 'processId' | 'nodeId'>) =>
  `${issue.code}|${issue.severity}|${issue.processId}|${issue.nodeId}`

describe('evaluateRecipeReadiness (shared cases)', () => {
  for (const testCase of cases) {
    it(testCase.name, () => {
      const processes = testCase.processes.map((process) => ({ ...process, recipeId: 7 })) as Process[]
      const readiness = evaluateRecipeReadiness(processes, { isKnownAction: (id) => actionIds.has(id) })

      assert.deepEqual(readiness.issues.map(key).sort(), testCase.expected.map(key).sort())
      assert.equal(readiness.ready, !testCase.expected.some((issue) => issue.severity === 'BLOCKER'))
      readiness.issues.forEach((issue) => assert.ok(issue.message.length > 0, `${issue.code} needs a message`))
    })
  }
})

describe('evaluateRecipeReadiness', () => {
  it('accepts any non-blank action when no catalog check is given', () => {
    const main: Process = {
      id: 1, type: 'MAIN', recipeId: 7, name: 'Main', edges: [],
      nodes: [{ id: 'a', kind: 'STEP', data: { step: { action: 'teleport', actionDescription: 'Teleport' } } }],
    }
    assert.equal(evaluateRecipeReadiness([main]).ready, true)
  })

  it('splits blockers from warnings', () => {
    const main: Process = { id: 1, type: 'MAIN', recipeId: 7, name: 'Main', nodes: [], edges: [] }
    const prep: Process = { id: 2, type: 'SUBPROCESS', recipeId: 7, name: 'Preparation', nodes: [], edges: [] }
    const readiness = evaluateRecipeReadiness([prep, main])

    assert.deepEqual(readiness.blockers.map((issue) => issue.code), ['MAIN_EMPTY'])
    assert.deepEqual(readiness.warnings.map((issue) => issue.code), ['PREP_EMPTY'])
    assert.equal(readiness.ready, false)
  })
})
