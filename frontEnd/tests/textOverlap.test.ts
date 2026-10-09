import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { descriptionTail, restatesText } from '../src/features/recipe-tool/presentation/model/textOverlap.ts'

describe('Recipe Process: saying each thing once', () => {
  it('treats an expected result that repeats the instruction as a restatement', () => {
    assert.equal(restatesText('finely chopped onion', ['Chop the onion finely.']), true)
    assert.equal(restatesText('Chopped onions', ['Chop the onion.', 'Chop the onion']), true)
    assert.equal(restatesText('boiled eggs', ['Boil the eggs for 10 minutes.']), true)
  })

  it('keeps an expected result that adds a doneness cue', () => {
    assert.equal(restatesText('edges turn deep golden', ['Fry the onions in oil.']), false)
    assert.equal(restatesText('oil separates from the masala', ['Stir in the paste and cook.']), false)
  })

  it('hides a step name that the description already says, keeps one that adds to it', () => {
    assert.equal(restatesText('Chop the onion', ['Chop the onion finely.']), true)
    assert.equal(restatesText('Fry the browned onions with cooking oil', ['Fry until golden.']), false)
  })

  it('counts empty or filler-only text as saying nothing new', () => {
    assert.equal(restatesText('', ['Anything']), true)
    assert.equal(restatesText('the and with', ['Anything']), true)
  })

  it('is conservative: a mostly new sentence is kept even if a word repeats', () => {
    assert.equal(restatesText('onion is soft, translucent and sweet smelling', ['Chop the onion.']), false)
  })
})

describe('Recipe Process: merging a description into the built sentence', () => {
  it('keeps only what the description adds', () => {
    assert.deepEqual(descriptionTail('Cut the chicken into medium pieces', 'Cut the cleaned chicken.'), { tail: 'into medium pieces', rest: '' })
  })

  it('drops phrases the sentence already says, keeps the rest', () => {
    assert.deepEqual(
      descriptionTail('Fry the chicken on medium heat for 10 minutes until golden.', 'Fry the marinated chicken with 2 tbsp cooking oil over medium heat for 10 min.'),
      { tail: 'until golden', rest: '' },
    )
  })

  it('returns later sentences separately', () => {
    assert.deepEqual(descriptionTail('Cut the chicken into cubes. Discard the skin.', 'Cut the cleaned chicken.'), { tail: 'into cubes', rest: 'Discard the skin.' })
  })

  it('leaves a description that says something else from the start alone', () => {
    assert.equal(descriptionTail('Mix well so every piece is coated, then rest.', 'Marinate the chicken pieces with 200 g yogurt.'), null)
  })

  it('has nothing to add when the description only restates the sentence', () => {
    assert.deepEqual(descriptionTail('Fry the chicken in oil.', 'Fry the marinated chicken with 2 tbsp cooking oil.'), { tail: '', rest: '' })
  })
})
