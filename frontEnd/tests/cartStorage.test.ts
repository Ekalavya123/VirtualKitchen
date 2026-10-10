import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  clearCart,
  defaultShopPrice,
  getCartStorageKey,
  loadCart,
  mergeCartLine,
  normalizeCartItems,
  saveCart,
  type CartItem,
  type CartStorage,
} from '../src/shared/cart/cartStorage.ts'

const memoryStorage = (): CartStorage & { data: Map<string, string> } => {
  const data = new Map<string, string>()
  return {
    data,
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => void data.set(key, value),
    removeItem: (key) => void data.delete(key),
  }
}

const onion = (quantity: number, unit: CartItem['unit'] = 'KG'): CartItem => ({
  itemId: 42, itemType: 'INGREDIENT', itemName: 'Onion', quantity, unit, price: 5,
})

describe('cart storage', () => {
  it('round-trips a cart per user', () => {
    const storage = memoryStorage()
    saveCart(3, [onion(2)], storage)
    assert.deepEqual(loadCart(3, storage), [onion(2)])
    assert.deepEqual(loadCart(4, storage), [])
    assert.ok(storage.data.has(getCartStorageKey(3)))
    clearCart(3, storage)
    assert.deepEqual(loadCart(3, storage), [])
  })

  it('drops malformed entries and survives bad JSON', () => {
    assert.deepEqual(
      normalizeCartItems([
        { itemId: '42', itemType: 'INGREDIENT', itemName: 'Onion', quantity: '2', unit: 'kg', price: 5 },
        { itemId: 'x', itemName: 'Broken', quantity: 1, price: 1 },
        { itemId: 9, itemName: 'Zero', quantity: 0, price: 1 },
        null,
      ]),
      [onion(2)],
    )
    const storage = memoryStorage()
    storage.setItem(getCartStorageKey(1), '{not json')
    assert.deepEqual(loadCart(1, storage), [])
    assert.deepEqual(loadCart(1, null), [])
  })

  it('applies the shop price rule', () => {
    assert.equal(defaultShopPrice('INGREDIENT'), 5)
    assert.equal(defaultShopPrice('EQUIPMENT', null), 50)
    assert.equal(defaultShopPrice('INGREDIENT', 2.5), 2.5)
  })
})

describe('cart line merging', () => {
  it('appends a new item', () => {
    assert.deepEqual(mergeCartLine([], onion(1)), [onion(1)])
  })

  it('adds quantities in "add" mode, keeping the existing unit', () => {
    assert.deepEqual(mergeCartLine([onion(2, 'GRAM')], onion(1, 'KG'), 'add'), [onion(3, 'GRAM')])
  })

  it('tops up to at least the needed quantity in "atLeast" mode', () => {
    assert.deepEqual(mergeCartLine([onion(1)], onion(2), 'atLeast'), [onion(2)])
    assert.deepEqual(mergeCartLine([onion(3)], onion(2), 'atLeast'), [onion(3)])
    // A different unit can't be compared: the needed line replaces it.
    assert.deepEqual(mergeCartLine([onion(500, 'GRAM')], onion(1), 'atLeast'), [onion(1)])
  })

  it('leaves other items alone', () => {
    const pan: CartItem = { itemId: 42, itemType: 'EQUIPMENT', itemName: 'Pan', quantity: 1, unit: 'COUNT', price: 50 }
    assert.deepEqual(mergeCartLine([pan], onion(1), 'atLeast'), [pan, onion(1)])
  })
})
