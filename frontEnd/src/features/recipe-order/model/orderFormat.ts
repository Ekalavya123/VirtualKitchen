/**
 * Display helpers for recipe orders: status labels/tones, quantities in inventory units, servings
 * and addresses. Pure (runtime imports carry their .ts extension), so node:test covers it directly.
 */

import { formatNumber } from '../../../shared/format/number.ts'
import type { UnitType } from '../../../types/kitchen'
import type { DeliveryAddress, RecipeOrderStatus } from '../../../types/recipeOrder'

export type StatusTone = 'neutral' | 'info' | 'warning' | 'danger' | 'success' | 'accent'

const STATUS_DISPLAY: Record<RecipeOrderStatus, { label: string; tone: StatusTone }> = {
  DRAFT: { label: 'Draft', tone: 'neutral' },
  AWAITING_INGREDIENTS: { label: 'Checking ingredients', tone: 'info' },
  RESERVING: { label: 'Reserving ingredients', tone: 'info' },
  INVENTORY_CONFLICT: { label: 'Stock changed', tone: 'warning' },
  AWAITING_PAYMENT: { label: 'Awaiting payment', tone: 'warning' },
  PAYMENT_FAILED: { label: 'Payment declined', tone: 'danger' },
  CONFIRMED: { label: 'Confirmed', tone: 'accent' },
  PREPARING: { label: 'Preparing', tone: 'accent' },
  QUALITY_CHECK: { label: 'Quality check', tone: 'accent' },
  OUT_FOR_DELIVERY: { label: 'Out for delivery', tone: 'info' },
  COMPLETING: { label: 'Finishing up', tone: 'info' },
  COMPLETED: { label: 'Delivered', tone: 'success' },
  CANCELLED: { label: 'Cancelled', tone: 'danger' },
}

export const getStatusDisplay = (status: RecipeOrderStatus): { label: string; tone: StatusTone } =>
  STATUS_DISPLAY[status] ?? { label: String(status).toLowerCase().replace(/_/g, ' '), tone: 'neutral' }

const UNIT_LABEL: Record<UnitType, string> = {
  KG: 'kg',
  GRAM: 'g',
  LITER: 'L',
  ML: 'ml',
  COUNT: 'pcs',
}

const formatUnit = (unit: UnitType | string | null | undefined): string =>
  unit ? (UNIT_LABEL[unit as UnitType] ?? unit.toLowerCase()) : ''

/** "250 g", "1.5 kg", "3 pcs". */
export const formatQuantity = (value: number | null | undefined, unit: UnitType | string | null | undefined): string => {
  const label = formatUnit(unit)
  return label ? `${formatNumber(value)} ${label}` : formatNumber(value)
}

/** "4 servings", or "2 batches" when the recipe has no serving count of its own. */
export const formatServings = (servings: number, baseServings: number | null | undefined): string => {
  if (baseServings == null) return `${servings} ${servings === 1 ? 'batch' : 'batches'}`
  return `${servings} ${servings === 1 ? 'serving' : 'servings'}`
}

/** Milliseconds since the epoch for sorting; 0 when missing/invalid. */
export const timeValue = (value: string | null | undefined): number => {
  if (!value) return 0
  const time = new Date(value).getTime()
  return Number.isNaN(time) ? 0 : time
}

/** Address lines for display, blank parts skipped. */
export const formatAddressLines = (address: DeliveryAddress | null | undefined): string[] => {
  if (!address) return []
  const cityLine = [address.city, address.state, address.postalCode].map((part) => part?.trim()).filter(Boolean).join(', ')
  return [address.recipientName, address.line1, address.line2, cityLine, address.phone]
    .map((part) => part?.trim() ?? '')
    .filter(Boolean)
}

/** The fields the backend requires before an order can be confirmed. */
const REQUIRED_ADDRESS_FIELDS = ['recipientName', 'line1', 'city', 'postalCode'] as const

export type RequiredAddressField = (typeof REQUIRED_ADDRESS_FIELDS)[number]

export const missingAddressFields = (address: DeliveryAddress | null | undefined): RequiredAddressField[] =>
  REQUIRED_ADDRESS_FIELDS.filter((field) => !address?.[field]?.trim())

/** Trimmed copy with blank fields dropped (what's sent to the API). */
export const cleanAddress = (address: DeliveryAddress): DeliveryAddress => {
  const cleaned: DeliveryAddress = {}
  for (const [key, value] of Object.entries(address) as [keyof DeliveryAddress, string | undefined][]) {
    const trimmed = value?.trim()
    if (trimmed) cleaned[key] = trimmed
  }
  return cleaned
}

export const sameAddress = (a: DeliveryAddress | null | undefined, b: DeliveryAddress | null | undefined): boolean => {
  const left = cleanAddress(a ?? {})
  const right = cleanAddress(b ?? {})
  const keys = new Set([...Object.keys(left), ...Object.keys(right)] as (keyof DeliveryAddress)[])
  return [...keys].every((key) => left[key] === right[key])
}
