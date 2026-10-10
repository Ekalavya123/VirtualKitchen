import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  cleanAddress,
  formatAddressLines,
  formatNumber,
  formatQuantity,
  getStatusDisplay,
  missingAddressFields,
  sameAddress,
  timeValue,
} from '../src/features/recipe-order/model/orderFormat.ts'

describe('recipe order formatting', () => {
  it('formats quantities in inventory units', () => {
    assert.equal(formatQuantity(250, 'GRAM'), '250 g')
    assert.equal(formatQuantity(1.505, 'KG'), '1.51 kg')
    assert.equal(formatQuantity(3, 'COUNT'), '3 pcs')
    assert.equal(formatQuantity(2, null), '2')
    assert.equal(formatNumber(null), '—')
  })

  it('labels statuses', () => {
    assert.deepEqual(getStatusDisplay('PAYMENT_FAILED'), { label: 'Payment declined', tone: 'danger' })
    assert.deepEqual(getStatusDisplay('COMPLETED'), { label: 'Delivered', tone: 'success' })
  })

  it('sorts by time with missing dates last', () => {
    assert.ok(timeValue('2026-10-10T10:00:00') > timeValue('2026-10-09T10:00:00'))
    assert.equal(timeValue(null), 0)
    assert.equal(timeValue('not a date'), 0)
  })
})

describe('delivery address helpers', () => {
  const address = { recipientName: ' Ana ', line1: '1 Main St', line2: '', city: 'Springfield', postalCode: '12345' }

  it('lists what is still required', () => {
    assert.deepEqual(missingAddressFields(address), [])
    assert.deepEqual(missingAddressFields({ line1: 'x', city: '  ' }), ['recipientName', 'city', 'postalCode'])
    assert.deepEqual(missingAddressFields(null), ['recipientName', 'line1', 'city', 'postalCode'])
  })

  it('trims and drops blank fields, and compares by content', () => {
    assert.deepEqual(cleanAddress(address), { recipientName: 'Ana', line1: '1 Main St', city: 'Springfield', postalCode: '12345' })
    assert.equal(sameAddress(address, { recipientName: 'Ana', line1: '1 Main St', city: 'Springfield', postalCode: '12345' }), true)
    assert.equal(sameAddress(address, { ...address, city: 'Shelbyville' }), false)
    assert.equal(sameAddress(null, {}), true)
  })

  it('formats address lines for display', () => {
    assert.deepEqual(formatAddressLines({ ...address, state: 'IL', phone: '555' }), ['Ana', '1 Main St', 'Springfield, IL, 12345', '555'])
    assert.deepEqual(formatAddressLines(null), [])
  })
})
