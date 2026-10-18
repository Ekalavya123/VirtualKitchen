import type { Annotation } from '../../../model/stepAnnotations'

const KIND_PREFIX: Record<Annotation['kind'], string> = { ingredient: '', prepared: 'prepared · ', step: '↳ ' }

/** A chip's displayed text from its annotation and live words; null/undefined uses the default ("prepared · …"). */
export type ChipText = (annotation: Annotation, words: string) => string | null | undefined

/** How a chip reads by default: its words, marked by kind. */
export const defaultChipText = (annotation: Annotation, words: string) => `${KIND_PREFIX[annotation.kind]}${words}`
