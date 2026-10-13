/**
 * Date display for backend timestamps (ISO local date-times such as "2026-10-10T14:03:22.123").
 * Both helpers return `missing` when the value is absent or unparseable.
 */

const toDate = (value: string | null | undefined): Date | null => {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

/** "Oct 10, 2026" in the viewer's locale. */
export const formatDate = (value: string | null | undefined, missing = ''): string => {
  const date = toDate(value)
  return date ? new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' }).format(date) : missing
}

/** "Oct 10, 2026, 2:03 PM" in the viewer's locale. */
export const formatDateTime = (value: string | null | undefined, missing = ''): string => {
  const date = toDate(value)
  return date ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date) : missing
}
