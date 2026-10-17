import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  annotationsOf,
  ingredientAnnotationId,
  parseDescription,
  parsePreparedAnnotationId,
  preparedAnnotationId,
  refreshSnapshotLabels,
  remapAnnotationIds,
  serializeSegments,
  toPlainText,
  unbackedAnnotations,
  unwrapAnnotations,
  type DescriptionSegment,
} from '../src/features/recipe-tool/model/stepAnnotations.ts'

/** Shared with the backend's StepAnnotationsTest, so the two parsers can't drift apart. */
type AnnotationCase = {
  name: string
  text: string
  segments: Array<{ text: string } | { kind: string; id: string; label: string }>
  plain: string
}

const cases: AnnotationCase[] = JSON.parse(
  readFileSync(new URL('../../backend/src/test/resources/annotations/annotation-cases.json', import.meta.url), 'utf8'))

const simplify = (segments: DescriptionSegment[]) =>
  segments.map((segment) => (segment.type === 'text' ? { text: segment.text } : { kind: segment.kind, id: segment.id, label: segment.label }))

describe('parseDescription (shared cases)', () => {
  for (const testCase of cases) {
    it(testCase.name, () => {
      const segments = parseDescription(testCase.text)
      assert.deepEqual(simplify(segments), testCase.segments)
      assert.equal(toPlainText(testCase.text), testCase.plain)
      assert.deepEqual(simplify(parseDescription(serializeSegments(segments))), testCase.segments)
    })
  }
})

describe('step annotations', () => {
  const text = 'Add @[onions](ingredient:107) and @[sauce](prepared:12:a), then @[base](step:s1).'

  it('reads live labels when they resolve, the snapshot otherwise', () => {
    const plain = toPlainText(text, (annotation) => (annotation.kind === 'prepared' ? 'Tomato Sauce' : null))
    assert.equal(plain, 'Add onions and Tomato Sauce, then base.')
  })

  it('refreshes snapshot labels without touching ids or text', () => {
    const refreshed = refreshSnapshotLabels(text, (annotation) => (annotation.kind === 'ingredient' ? 'Red Onions' : ''))
    assert.equal(refreshed, 'Add @[Red Onions](ingredient:107) and @[sauce](prepared:12:a), then @[base](step:s1).')
  })

  it('remaps ids, and unwraps the ones mapped to null', () => {
    const remapped = remapAnnotationIds(text, (annotation) => {
      if (annotation.kind === 'prepared') return '40:b'
      if (annotation.kind === 'step') return null
      return undefined
    })
    assert.equal(remapped, 'Add @[onions](ingredient:107) and @[sauce](prepared:40:b), then base.')
  })

  it('unwraps matching annotations into their labels', () => {
    assert.equal(unwrapAnnotations(text, (annotation) => annotation.kind === 'ingredient'),
      'Add onions and @[sauce](prepared:12:a), then @[base](step:s1).')
  })

  it('returns text without annotations unchanged', () => {
    assert.equal(remapAnnotationIds('Plain (text) [here]', () => 'x'), 'Plain (text) [here]')
    assert.equal(toPlainText(null), '')
  })

  it('finds annotations whose targets the step no longer uses', () => {
    const description = 'Add @[onions](ingredient:107), @[paste](ingredient:custom:chilli paste) and @[sauce](prepared:12:a)'
    const unbacked = unbackedAnnotations(description, {
      ingredient: [ingredientAnnotationId({ ingredientId: 'custom', customIngredientName: 'Chilli Paste' })],
      prepared: [preparedAnnotationId({ processId: 12, stepId: 'a' })],
      step: [],
    })
    assert.deepEqual(unbacked.map((annotation) => annotation.label), ['onions'])
  })

  it('builds and parses target ids', () => {
    assert.equal(ingredientAnnotationId({ ingredientId: '107' }), '107')
    assert.equal(ingredientAnnotationId({ ingredientId: 'custom', customIngredientName: ' Ghee ' }), 'custom:Ghee')
    assert.equal(ingredientAnnotationId({ ingredientId: 'custom' }), '')
    assert.equal(preparedAnnotationId({ processId: 3 }), '3')
    assert.deepEqual(parsePreparedAnnotationId('3:abc'), { processId: 3, stepId: 'abc' })
    assert.deepEqual(parsePreparedAnnotationId('3'), { processId: 3 })
    assert.equal(parsePreparedAnnotationId('x:abc'), null)
    assert.deepEqual(annotationsOf(text).map((annotation) => annotation.kind), ['ingredient', 'prepared', 'step'])
  })
})
