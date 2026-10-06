import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { readingRevealMs, splitWords, visibleWordCount } from '../src/features/recipe-tool/process/narration/wordReveal.ts'

describe('splitWords', () => {
  it('splits on any whitespace and ignores empty text', () => {
    assert.deepEqual(splitWords('  Add the\n chopped   onions. '), ['Add', 'the', 'chopped', 'onions.'])
    assert.deepEqual(splitWords('   '), [])
  })
})

describe('visibleWordCount', () => {
  it('follows the playback position, rounding up to the word being spoken', () => {
    assert.equal(visibleWordCount(0, 8, 16), 0)
    assert.equal(visibleWordCount(0.1, 8, 16), 1)
    assert.equal(visibleWordCount(4, 8, 16), 8)
    assert.equal(visibleWordCount(4.1, 8, 16), 9)
  })

  it('shows everything at or past the end', () => {
    assert.equal(visibleWordCount(8, 8, 16), 16)
    assert.equal(visibleWordCount(9, 8, 16), 16)
  })

  it('jumps straight to the new position after seeking either way', () => {
    assert.equal(visibleWordCount(6, 8, 16), 12)
    assert.equal(visibleWordCount(2, 8, 16), 4)
  })

  it('handles empty and one-word text', () => {
    assert.equal(visibleWordCount(3, 8, 0), 0)
    assert.equal(visibleWordCount(0, 8, 1), 0)
    assert.equal(visibleWordCount(0.01, 8, 1), 1)
  })

  it('falls back to the known narration length while the audio duration is unknown', () => {
    assert.equal(visibleWordCount(2, Number.NaN, 10, 4), 5)
    assert.equal(visibleWordCount(2, Number.POSITIVE_INFINITY, 10, 4), 5)
    assert.equal(visibleWordCount(2, Number.NaN, 10), 0, 'no length known: wait')
    assert.equal(visibleWordCount(2, Number.NaN, 10, null), 0)
    assert.equal(visibleWordCount(Number.NaN, 8, 10), 0)
  })
})

describe('readingRevealMs', () => {
  it('scales with length within sensible bounds', () => {
    assert.equal(readingRevealMs(0), 0)
    assert.equal(readingRevealMs(1), 900)
    assert.equal(readingRevealMs(10), 1800)
    assert.equal(readingRevealMs(200), 5000)
  })
})
