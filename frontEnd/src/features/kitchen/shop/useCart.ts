import { useEffect, useMemo, useState } from 'react'
import { useConfirm } from '../../../shared/components/confirm/confirmContext'
import { clearCart, loadCart, mergeCartLine, saveCart, type CartItem } from '../../../shared/cart/cartStorage'
import type { ItemType, UnitType } from '../../../types/kitchen'
import type { ShopCatalogItem } from './shopCatalog'

const sameLine = (a: { itemType: ItemType; itemId: number }, b: { itemType: ItemType; itemId: number }) =>
  a.itemType === b.itemType && a.itemId === b.itemId

/** The signed-in user's shopping cart, kept in this browser (see cartStorage) between visits. */
export function useCart(userId: number) {
  const confirm = useConfirm()
  // Read from storage on mount (not in an effect), so the persist effect below can never write an
  // empty cart over the stored one before it has been read.
  const [items, setItems] = useState<CartItem[]>(() => loadCart(userId))

  useEffect(() => {
    saveCart(userId, items)
  }, [items, userId])

  const total = useMemo(() => items.reduce((sum, item) => sum + item.price * item.quantity, 0), [items])
  const count = useMemo(() => items.reduce((sum, item) => sum + item.quantity, 0), [items])

  /** The cart line for a catalog item, if it's in the cart. */
  const lineFor = (item: ShopCatalogItem) => items.find((line) => sameLine(line, { itemType: item.itemType, itemId: item.id }))

  /** One more of an item; an item already in the cart keeps that line's unit. */
  const add = (item: ShopCatalogItem) =>
    setItems((current) => mergeCartLine(current, {
      itemId: item.id,
      itemType: item.itemType,
      itemName: item.name,
      quantity: 1,
      unit: item.itemType === 'EQUIPMENT' ? 'COUNT' : (item.defaultUnit ?? 'GRAM'),
      price: item.basePrice,
    }))

  const remove = async (line: CartItem) => {
    const confirmed = await confirm({
      eyebrow: 'Remove from cart',
      title: `Remove ${line.itemName}?`,
      message: `${line.itemName} (${line.quantity} ${line.unit}) will be taken out of your cart.`,
      confirmLabel: 'Remove',
    })
    if (!confirmed) return
    setItems((current) => current.filter((item) => !sameLine(item, line)))
  }

  /** A quantity of zero or less asks to remove the line instead. */
  const setQuantity = (line: CartItem, quantity: number) => {
    const safeQuantity = Number.isFinite(quantity) ? quantity : 0
    if (safeQuantity <= 0) {
      void remove(line)
      return
    }
    setItems((current) => current.map((item) => (sameLine(item, line) ? { ...item, quantity: safeQuantity } : item)))
  }

  const setUnit = (line: CartItem, unit: UnitType) =>
    setItems((current) => current.map((item) => (sameLine(item, line) ? { ...item, unit } : item)))

  const empty = () => {
    setItems([])
    clearCart(userId)
  }

  return { items, total, count, lineFor, add, remove, setQuantity, setUnit, empty }
}
