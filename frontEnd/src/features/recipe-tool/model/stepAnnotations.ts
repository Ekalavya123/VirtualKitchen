/**
 * Structured @ annotations inside a step's description (`actionDescription`, still a plain string):
 *
 *   Add @[onions](ingredient:107) and @[marinated chicken](prepared:12:<stepId>), then heat for 5 minutes.
 *
 * An annotation points at one of the step's own targets — the step's `actionOn` stays the single source of truth
 * (quantities, units and styles live there, never in the text):
 * - `ingredient:<ingredientId>` or `ingredient:custom:<name>` → an `actionOn.ingredients` entry
 * - `prepared:<processId>:<stepId>` (or `prepared:<processId>` for legacy whole-process uses) → `actionOn.processes`
 * - `step:<stepId>` → `actionOn.steps` (an earlier step's result in the same process)
 *
 * The id is the reference; the bracketed label is only a snapshot of the name, used when the target is gone or
 * by readers that can't resolve ids. Text without annotations is ordinary text, so older descriptions keep working.
 * Only these three kinds are recognised; anything else that merely looks similar stays plain text.
 *
 * The backend twin is recipe/model/StepAnnotations.java; both are tested against
 * backend/src/test/resources/annotations/annotation-cases.json. Pure, so it runs under node --test.
 */

export type AnnotationKind = 'ingredient' | 'prepared' | 'step'

export type Annotation = { kind: AnnotationKind; id: string; label: string }

export type DescriptionSegment = { type: 'text'; text: string } | ({ type: 'annotation' } & Annotation)

const ANNOTATION_PATTERN = /@\[((?:\\.|[^\\\]])*)\]\((ingredient|prepared|step):((?:\\.|[^\\)])*)\)/g

const unescape = (value: string) => value.replace(/\\(.)/g, '$1')
const escapeLabel = (value: string) => value.replace(/[\\\]]/g, (char) => `\\${char}`)
const escapeId = (value: string) => value.replace(/[\\)]/g, (char) => `\\${char}`)

export const CUSTOM_INGREDIENT_PREFIX = 'custom:'

/** Splits a description into text and annotations (adjacent text merged; empty text dropped). */
export const parseDescription = (text: string | null | undefined): DescriptionSegment[] => {
  const source = text ?? ''
  const segments: DescriptionSegment[] = []
  const pushText = (value: string) => {
    if (!value) return
    const last = segments[segments.length - 1]
    if (last?.type === 'text') last.text += value
    else segments.push({ type: 'text', text: value })
  }
  let cursor = 0
  for (const match of source.matchAll(ANNOTATION_PATTERN)) {
    const index = match.index ?? 0
    pushText(source.slice(cursor, index))
    const id = unescape(match[3]).trim()
    const label = unescape(match[1])
    if (id) segments.push({ type: 'annotation', kind: match[2] as AnnotationKind, id, label })
    else pushText(label)
    cursor = index + match[0].length
  }
  pushText(source.slice(cursor))
  return segments
}

export const formatAnnotation = (annotation: Annotation) =>
  `@[${escapeLabel(annotation.label)}](${annotation.kind}:${escapeId(annotation.id)})`

export const serializeSegments = (segments: readonly DescriptionSegment[]) =>
  segments.map((segment) => (segment.type === 'text' ? segment.text : formatAnnotation(segment))).join('')

export const hasAnnotations = (text: string | null | undefined) =>
  parseDescription(text).some((segment) => segment.type === 'annotation')

/** The annotations in order of appearance. */
export const annotationsOf = (text: string | null | undefined): Annotation[] =>
  parseDescription(text).flatMap((segment) =>
    (segment.type === 'annotation' ? [{ kind: segment.kind, id: segment.id, label: segment.label }] : []))

/** Resolves an annotation's live label; null/'' falls back to the snapshot label. */
export type AnnotationLabelResolver = (annotation: Annotation) => string | null | undefined

const labelOf = (annotation: Annotation, resolve?: AnnotationLabelResolver) =>
  (resolve?.(annotation) || '').trim() || annotation.label

/** The description as readable prose: each annotation becomes its (live, when resolvable) label. */
export const toPlainText = (text: string | null | undefined, resolve?: AnnotationLabelResolver) =>
  parseDescription(text).map((segment) => (segment.type === 'text' ? segment.text : labelOf(segment, resolve))).join('')

/**
 * Rewrites annotations: the mapper returns a new annotation (or a partial change), `null` to turn it back into its
 * plain label, or undefined to keep it as is. Text without annotations is returned unchanged.
 */
export const mapAnnotations = (
  text: string | null | undefined,
  mapper: (annotation: Annotation) => Partial<Annotation> | null | undefined,
): string => {
  const source = text ?? ''
  const segments = parseDescription(source)
  if (!segments.some((segment) => segment.type === 'annotation')) return source
  return serializeSegments(segments.map((segment): DescriptionSegment => {
    if (segment.type === 'text') return segment
    const change = mapper(segment)
    if (change === undefined) return segment
    if (change === null) return { type: 'text', text: segment.label }
    return { ...segment, ...change }
  }))
}

/** Points annotations at new ids (recipe copies, merges, id migrations); `null` from the mapper unwraps. */
export const remapAnnotationIds = (
  text: string | null | undefined,
  mapId: (annotation: Annotation) => string | null | undefined,
) => mapAnnotations(text, (annotation) => {
  const id = mapId(annotation)
  if (id === undefined || id === annotation.id) return undefined
  return id === null ? null : { id }
})

/** Turns the matching annotations back into their plain labels. */
export const unwrapAnnotations = (text: string | null | undefined, predicate: (annotation: Annotation) => boolean) =>
  mapAnnotations(text, (annotation) => (predicate(annotation) ? null : undefined))

/** Updates each snapshot label to the live one, so readers that can't resolve ids still read current names. */
export const refreshSnapshotLabels = (text: string | null | undefined, resolve: AnnotationLabelResolver) =>
  mapAnnotations(text, (annotation) => {
    const live = (resolve(annotation) || '').trim()
    return live && live !== annotation.label ? { label: live } : undefined
  })

// --- ids of a step's targets ---

/** The annotation id of an `actionOn.ingredients` entry: its catalog id, or `custom:<name>`. */
export const ingredientAnnotationId = (ingredient: { ingredientId?: unknown; customIngredientName?: unknown }) => {
  const id = typeof ingredient.ingredientId === 'string' ? ingredient.ingredientId.trim() : String(ingredient.ingredientId ?? '')
  if (id === 'custom') {
    const name = typeof ingredient.customIngredientName === 'string' ? ingredient.customIngredientName.trim() : ''
    return name ? `${CUSTOM_INGREDIENT_PREFIX}${name}` : ''
  }
  return id
}

/** The annotation id of an `actionOn.processes` entry. */
export const preparedAnnotationId = (reference: { processId: number | string; stepId?: string }) =>
  (reference.stepId ? `${reference.processId}:${reference.stepId}` : String(reference.processId))

/** Parses a prepared annotation id back into a reference (null when malformed). */
export const parsePreparedAnnotationId = (id: string): { processId: number; stepId?: string } | null => {
  const [processPart, ...stepParts] = id.split(':')
  const processId = Number(processPart)
  if (!processPart.trim() || !Number.isInteger(processId)) return null
  const stepId = stepParts.join(':').trim()
  return stepId ? { processId, stepId } : { processId }
}

/** Compares annotation ids the way they are matched to targets (custom ingredient names ignore case). */
export const sameAnnotationTarget = (annotation: Annotation, kind: AnnotationKind, id: string) =>
  annotation.kind === kind && (kind === 'ingredient' ? annotation.id.toLowerCase() === id.toLowerCase() : annotation.id === id)

/** The annotation ids of a step's own targets, by kind. */
export type StepAnnotationTargets = Record<AnnotationKind, readonly string[]>

/** Annotations that point at nothing the step actually uses (its target was removed from `actionOn`). */
export const unbackedAnnotations = (text: string | null | undefined, targets: StepAnnotationTargets): Annotation[] =>
  annotationsOf(text).filter((annotation) =>
    !targets[annotation.kind].some((id) => sameAnnotationTarget(annotation, annotation.kind, id)))
