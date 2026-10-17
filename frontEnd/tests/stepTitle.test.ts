import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { actionVerbOf, deriveStepTitle, joinSubjects, UNTITLED_STEP } from '../src/features/recipe-tool/model/stepTitle.ts'

describe('deriveStepTitle', () => {
  it('names what the instruction annotates, in its order', () => {
    assert.equal(deriveStepTitle({ actionName: 'Chop', description: 'Cut the @[Onion](ingredient:107) into small pieces.' }), 'Chop onion')
    assert.equal(deriveStepTitle({
      actionName: 'Mix / Combine',
      description: 'Combine the @[Sauce](prepared:2:s) with the @[spice blend](ingredient:custom:spice blend).',
    }), 'Mix Sauce and spice blend')
  })

  it('uses live names when they resolve', () => {
    const title = deriveStepTitle({
      actionName: 'Heat',
      description: 'Add the @[chicken](prepared:2:c) and cook for 5 minutes.',
      resolveLabel: () => 'Marinated Chicken',
    })
    assert.equal(title, 'Heat Marinated Chicken')
  })

  it('falls back to what the step works on when the instruction names nothing', () => {
    assert.equal(deriveStepTitle({ actionName: 'Fry', description: 'Fry until golden.', subjects: ['onion', 'Onion', 'garlic'] }), 'Fry onion and garlic')
    assert.equal(deriveStepTitle({ actionName: 'Simmer', description: 'Simmer gently.' }), 'Simmer')
  })

  it('shortens a long list', () => {
    assert.equal(deriveStepTitle({ actionName: 'Add', description: '', subjects: ['cumin', 'turmeric', 'coriander', 'chili'] }), 'Add cumin, turmeric +2')
  })

  it('without an action, uses the first clause of the instruction', () => {
    assert.equal(deriveStepTitle({ actionName: '', description: 'Rest the @[dough](ingredient:custom:dough), covered, for 10 minutes.' }), 'Rest the dough')
    assert.equal(deriveStepTitle({ actionName: '', description: '' }), UNTITLED_STEP)
  })

  it('helpers', () => {
    assert.equal(actionVerbOf('Mix / Combine'), 'Mix')
    assert.equal(joinSubjects([]), '')
    assert.equal(joinSubjects(['a']), 'a')
  })
})
