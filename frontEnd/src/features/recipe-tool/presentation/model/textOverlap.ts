/**
 * Whether one piece of recipe text only restates another — so the Recipe Process can say each
 * thing once (see buildRecipePresentation: a step's name and its expected result are shown only
 * when they add something to its instruction). Pure and dependency-free.
 */

const FILLER_WORDS = new Set([
  'the', 'and', 'with', 'into', 'until', 'for', 'its', 'are', 'some', 'your', 'them', 'then',
  'from', 'over', 'all', 'this', 'that', 'out', 'well', 'little', 'bit',
])

/** A rough word stem, enough to match "chopped" with "chop" and "onions" with "onion". */
const stem = (word: string) => {
  let result = word
  for (const suffix of ['ingly', 'ing', 'edly', 'ed', 'ly', 'es', 's']) {
    if (result.length > suffix.length + 2 && result.endsWith(suffix)) {
      result = result.slice(0, -suffix.length)
      break
    }
  }
  if (/([^aeiou])\1$/.test(result)) result = result.slice(0, -1)
  if (result.length > 3 && result.endsWith('e')) result = result.slice(0, -1)
  return result
}

/** Units written out and abbreviated mean the same: "10 minutes" restates "10 min". */
const SAME_AS: Record<string, string> = { minut: 'min', mins: 'min', hour: 'hr', hrs: 'hr', second: 'sec', secs: 'sec' }

const contentWords = (text: string) =>
  text.toLowerCase().split(/[^a-z0-9]+/).filter((word) => word.length > 2 && !FILLER_WORDS.has(word)).map((word) => {
    const stemmed = stem(word)
    return SAME_AS[stemmed] ?? stemmed
  })

/**
 * Whether `text` only restates what `against` already says: at least 80% of its meaningful words
 * appear there. "Finely chopped onion" restates "Chop the onion finely."; "Edges turn deep golden"
 * doesn't restate "Fry the onions in oil.". Deliberately conservative — when in doubt, show it.
 */
export const restatesText = (text: string, against: string[]) => {
  const words = contentWords(text)
  if (words.length === 0) return true
  const known = new Set(against.flatMap(contentWords))
  return words.filter((word) => known.has(word)).length / words.length >= 0.8
}

/** Words that start a new phrase of an instruction: "Cut the chicken | into medium pieces | until …". */
const PHRASE_STARTS = new Set([
  'into', 'until', 'till', 'in', 'on', 'over', 'at', 'for', 'with', 'without', 'so', 'then', 'while',
  'before', 'after', 'and', 'to', 'as', 'under', 'through', 'onto', 'from',
])

const bareWord = (word: string) => word.toLowerCase().replace(/[^a-z]/g, '')

/**
 * What a written description adds to a step's built sentence, so the two can read as one:
 * "Cut the cleaned chicken." + "Cut the chicken into medium pieces." → tail "into medium pieces".
 *
 * Only when the description's first sentence *opens* by restating the built sentence ("Cut the
 * chicken"); its following phrases are kept unless the built sentence already says them ("on
 * medium heat", "for 10 minutes" are dropped next to "over medium heat for 10 min"). Any further
 * sentences come back as `rest`. Null when the description says something else from the start
 * ("Mix well so every piece is coated") — it then stays a separate line.
 */
export const descriptionTail = (description: string, sentence: string): { tail: string; rest: string } | null => {
  const text = description.trim()
  const end = text.search(/[.!?](\s|$)/)
  const first = (end >= 0 ? text.slice(0, end) : text).trim()
  const rest = end >= 0 ? text.slice(end + 1).trim() : ''

  const words = first.split(/\s+/).filter(Boolean)
  const start = words.findIndex((word, index) => index > 0 && PHRASE_STARTS.has(bareWord(word)))
  if (start <= 0 || !restatesText(words.slice(0, start).join(' '), [sentence])) return null

  const phrases: string[][] = []
  for (const word of words.slice(start)) {
    if (phrases.length === 0 || PHRASE_STARTS.has(bareWord(word))) phrases.push([word])
    else phrases[phrases.length - 1].push(word)
  }
  const tail = phrases
    .map((phrase) => phrase.join(' ').replace(/[,;:]+$/, ''))
    .filter((phrase) => !restatesText(phrase, [sentence]))
    .join(' ')
  return { tail, rest }
}
