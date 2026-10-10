import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  CUSTOM_INGREDIENT_ID,
  findIngredientDefinition,
  getCatalogIngredients,
  getIngredientCatalogVersion,
  isIngredientCatalogLoaded,
  resolveIngredientAlias,
  setIngredientCatalog,
  subscribeIngredientCatalog,
  toIngredientDefinition,
} from '../src/features/recipe-tool/catalog/ingredientCatalogStore.ts'
import type { GlobalIngredient } from '../src/api/recipeApi.ts'

// Fixture entries shaped like GET /api/v1/ingredients (the database catalog).
const ONION: GlobalIngredient = {
  id: 42,
  name: 'Onion',
  defaultUnit: 'COUNT',
  catalogSlug: 'onion',
  category: 'aromatics',
  icon: '🧅',
  aliases: ['pyaz', 'red onion'],
  recipeUnits: ['piece', 'g', 'cup'],
  preparationStyleSets: null,
}

const MILK: GlobalIngredient = {
  id: 7,
  name: 'Milk',
  defaultUnit: 'LITER',
  catalogSlug: null,
  category: null,
  icon: null,
  aliases: null,
  recipeUnits: null,
  preparationStyleSets: [],
  imageUrl: 'https://example.test/milk.png',
}

describe('ingredient catalog store', () => {
  it('maps an API entry onto a step-editor definition', () => {
    assert.deepEqual(toIngredientDefinition(ONION), {
      id: '42',
      name: 'Onion',
      category: 'aromatics',
      icon: '🧅',
      imageUrl: undefined,
      defaultUnit: 'piece',
      units: ['piece', 'g', 'cup'],
      aliases: ['onion', 'pyaz', 'red onion'],
      preparationStyleSets: undefined,
    })
  })

  it('falls back for entries without catalog fields', () => {
    const milk = toIngredientDefinition(MILK, '🥛')
    assert.equal(milk.category, 'other')
    assert.equal(milk.icon, '🥛')
    // No recipe units: the shop unit stands in as the default.
    assert.equal(milk.defaultUnit, 'l')
    assert.deepEqual(milk.units, [])
    assert.deepEqual(milk.aliases, [])
    // An explicit empty override stays empty (null would mean "use the category's").
    assert.deepEqual(milk.preparationStyleSets, [])
    assert.equal(milk.imageUrl, 'https://example.test/milk.png')
    assert.equal(toIngredientDefinition({ ...MILK, defaultUnit: null }).defaultUnit, 'piece')
  })

  it('replaces the catalog, keeps the custom entry and notifies subscribers', () => {
    const before = getIngredientCatalogVersion()
    let notified = 0
    const unsubscribe = subscribeIngredientCatalog(() => notified++)

    setIngredientCatalog([ONION, MILK])
    unsubscribe()
    setIngredientCatalog([ONION, MILK])

    assert.equal(isIngredientCatalogLoaded(), true)
    assert.equal(getIngredientCatalogVersion(), before + 2)
    assert.equal(notified, 1)
    assert.deepEqual(getCatalogIngredients().map((ingredient) => ingredient.id), ['42', '7', CUSTOM_INGREDIENT_ID])
    assert.equal(findIngredientDefinition('42')?.name, 'Onion')
    assert.equal(findIngredientDefinition('onion'), undefined)
  })

  it('resolves ids, names, aliases and legacy slugs to the database id', () => {
    setIngredientCatalog([ONION, MILK])
    assert.equal(resolveIngredientAlias('42'), '42')
    assert.equal(resolveIngredientAlias('onion'), '42')
    assert.equal(resolveIngredientAlias('  Red   Onion '), '42')
    assert.equal(resolveIngredientAlias('milk'), '7')
    assert.equal(resolveIngredientAlias('custom'), CUSTOM_INGREDIENT_ID)
    assert.equal(resolveIngredientAlias('saffron'), '')
    assert.equal(resolveIngredientAlias(42), '')
  })
})
