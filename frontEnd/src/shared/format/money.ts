/** The currency every price in the app is shown in. */
export const APP_CURRENCY = 'INR'

/**
 * A price for display, in Indian rupees by default: `formatMoney(1250)` → "₹1,250.00". An order
 * that recorded another currency code is shown in that code.
 */
export const formatMoney = (amount: number | null | undefined, currency: string | null | undefined = APP_CURRENCY): string => {
  const value = amount != null && Number.isFinite(amount) ? amount : 0
  const code = currency || APP_CURRENCY
  try {
    return new Intl.NumberFormat('en-IN', { style: 'currency', currency: code }).format(value)
  } catch {
    // An unknown currency code: plain amount with the code.
    return `${value.toFixed(2)} ${code}`
  }
}
