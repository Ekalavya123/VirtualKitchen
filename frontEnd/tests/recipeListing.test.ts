import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { isRecipeSort, searchRecipes, sortRecipes } from '../src/features/recipes/recipeListing.ts'

const recipes = [
  { id: 1, name: 'banana bread', description: 'Sweet loaf', createdAt: '2026-02-01T00:00:00Z' },
  { id: 2, name: 'Aglio e olio', description: 'Garlic pasta', createdAt: '2026-03-01T00:00:00Z' },
  { id: 3, name: 'Chai', description: null },
  { id: 4, name: 'Dal tadka', description: 'Lentils with garlic tempering', createdAt: '2026-01-01T00:00:00Z' },
]

describe('searchRecipes', () => {
  it('matches every word in the name or description, ignoring case', () => {
    assert.deepEqual(searchRecipes(recipes, 'GARLIC').map((r) => r.id), [2, 4])
    assert.deepEqual(searchRecipes(recipes, 'garlic lentils').map((r) => r.id), [4])
    assert.deepEqual(searchRecipes(recipes, '   ').map((r) => r.id), [1, 2, 3, 4])
  })
})

describe('sortRecipes', () => {
  it('sorts by date with undated recipes last', () => {
    assert.deepEqual(sortRecipes(recipes, 'newest').map((r) => r.id), [2, 1, 4, 3])
    assert.deepEqual(sortRecipes(recipes, 'oldest').map((r) => r.id), [4, 1, 2, 3])
  })

  it('sorts by name regardless of case', () => {
    assert.deepEqual(sortRecipes(recipes, 'name').map((r) => r.id), [2, 1, 3, 4])
  })

  it('does not change the input', () => {
    const copy = [...recipes]
    sortRecipes(recipes, 'name')
    assert.deepEqual(recipes, copy)
  })
})

describe('isRecipeSort', () => {
  it('accepts only known sorts', () => {
    assert.equal(isRecipeSort('name'), true)
    assert.equal(isRecipeSort('rating'), false)
    assert.equal(isRecipeSort(null), false)
  })
})
