/**
 * Kitchen, store catalog and inventory types shared across features. Field names mirror the backend
 * (KitchenResponseDTO, store.model.ItemType, store.units.UnitType).
 */

/** A user's kitchen; every user gets one on signup. */
export interface Kitchen {
  id: number
  name: string
  ownerId: number
}

/** What a catalog, inventory, cart or order line refers to. */
export type ItemType = 'INGREDIENT' | 'EQUIPMENT'

/** The units the store, inventory and orders measure quantities in. */
export type UnitType = 'KG' | 'GRAM' | 'LITER' | 'ML' | 'COUNT'
