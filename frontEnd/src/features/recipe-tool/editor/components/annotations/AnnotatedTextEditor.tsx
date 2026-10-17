import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { ClipboardEvent, KeyboardEvent, MouseEvent } from 'react'
import { createPortal } from 'react-dom'
import { formatAnnotation, parseDescription, type Annotation, type AnnotationLabelResolver } from '../../../model/stepAnnotations'
import './annotations.css'

/**
 * A plain-text editor whose @ annotations are atomic chips (see model/stepAnnotations.ts). A small hand-written
 * contenteditable on purpose — no rich-text library: text stays ordinary text nodes, each annotation is one
 * non-editable <span>, and the DOM is serialized back to the annotated string on every input.
 *
 * Typing "@" opens a suggestion list (from `getOptions`, filtered by what follows the "@"): ↑/↓ move, Enter/Tab
 * pick, Esc closes and leaves the typed text as it is. Paste inserts plain text only. Chips show live names
 * (`resolveLabel`); clicking one calls `onChipActivate`.
 */

export type AnnotationOption = {
  key: string
  annotation: Annotation
  /** Heading the option is listed under ("In this step", "Ingredients", "Prepared", …). */
  group: string
  title: string
  hint?: string
  icon?: string
  /** Opens `onChipActivate` right after it's inserted (e.g. to enter an amount). */
  activateOnInsert?: boolean
}

export type ChipState = 'ok' | 'missing'

type AnnotatedTextEditorProps = {
  value: string
  /** The new annotated text; `inserted` is the annotation just picked from the list, if any. */
  onChange: (value: string, inserted?: Annotation) => void
  getOptions: (query: string) => AnnotationOption[]
  resolveLabel: AnnotationLabelResolver
  chipState?: (annotation: Annotation) => ChipState
  onChipActivate?: (annotation: Annotation, anchor: DOMRect) => void
  placeholder?: string
  ariaLabel?: string
  autoFocus?: boolean
}

/** An "@query" being typed; its text node is kept in a ref (DOM nodes don't belong in state). */
type Mention = { start: number; end: number; query: string; rect: DOMRect }

const KIND_PREFIX: Record<Annotation['kind'], string> = { ingredient: '', prepared: 'prepared · ', step: '↳ ' }

const annotationOfChip = (element: HTMLElement): Annotation | null => {
  const { annotationKind, annotationId, annotationLabel } = element.dataset
  if (!annotationKind || !annotationId) return null
  return { kind: annotationKind as Annotation['kind'], id: annotationId, label: annotationLabel ?? '' }
}

const chipsOf = (root: HTMLElement) => [...root.querySelectorAll<HTMLElement>('[data-annotation-kind]')]

/** Gives a chip its live name and state; the live name also becomes the snapshot label written back. */
const paintChip = (chip: HTMLElement, annotation: Annotation, resolveLabel: AnnotationLabelResolver, chipState?: (annotation: Annotation) => ChipState) => {
  const live = (resolveLabel(annotation) || '').trim() || annotation.label
  const missing = chipState?.(annotation) === 'missing'
  const text = `${KIND_PREFIX[annotation.kind]}${live}`
  if (chip.textContent !== text) chip.textContent = text
  if (chip.dataset.annotationLabel !== live) chip.dataset.annotationLabel = live
  const className = `annotation-chip annotation-chip--${annotation.kind}${missing ? ' is-missing' : ''}`
  if (chip.className !== className) chip.className = className
  chip.title = missing ? 'This step no longer uses it' : annotation.kind === 'prepared' ? 'Prepared component — click to open its flow' : 'Click to edit'
}

const createChip = (annotation: Annotation, resolveLabel: AnnotationLabelResolver, chipState?: (annotation: Annotation) => ChipState) => {
  const chip = document.createElement('span')
  chip.contentEditable = 'false'
  chip.dataset.annotationKind = annotation.kind
  chip.dataset.annotationId = annotation.id
  chip.dataset.annotationLabel = annotation.label
  paintChip(chip, annotation, resolveLabel, chipState)
  return chip
}

/** A trailing newline only shows (and holds the caret) with something after it. */
const ensureTrailingFiller = (root: HTMLElement) => {
  root.querySelectorAll('br[data-filler]').forEach((filler) => filler.remove())
  const text = root.textContent ?? ''
  if (text.endsWith('\n')) {
    const filler = document.createElement('br')
    filler.dataset.filler = 'true'
    root.appendChild(filler)
  }
}

const serializeNode = (node: Node): string => {
  if (node.nodeType === Node.TEXT_NODE) return node.nodeValue ?? ''
  if (!(node instanceof HTMLElement)) return ''
  const annotation = annotationOfChip(node)
  if (annotation) return formatAnnotation(annotation)
  if (node.tagName === 'BR') return node.dataset.filler ? '' : '\n'
  const inner = [...node.childNodes].map(serializeNode).join('')
  // Block elements a browser may still create (pasted or dropped content) read as their own line.
  return node.tagName === 'DIV' || node.tagName === 'P' ? `\n${inner}` : inner
}

const serialize = (root: HTMLElement) => [...root.childNodes].map(serializeNode).join('')

const insertTextAtCaret = (text: string) => {
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0) return
  const range = selection.getRangeAt(0)
  range.deleteContents()
  const node = document.createTextNode(text)
  range.insertNode(node)
  range.setStartAfter(node)
  range.collapse(true)
  selection.removeAllRanges()
  selection.addRange(range)
}

export default function AnnotatedTextEditor({
  value,
  onChange,
  getOptions,
  resolveLabel,
  chipState,
  onChipActivate,
  placeholder,
  ariaLabel,
  autoFocus,
}: AnnotatedTextEditorProps) {
  const rootRef = useRef<HTMLDivElement>(null)
  const lastValue = useRef<string | null>(null)
  const [mention, setMention] = useState<Mention | null>(null)
  const mentionNode = useRef<Text | null>(null)
  // The highlighted option, remembered per query (typing more starts again from the top).
  const [active, setActive] = useState({ query: '', index: 0 })
  const options = mention ? getOptions(mention.query.trim()) : []
  const activeIndex = mention && active.query === mention.query ? Math.min(active.index, Math.max(options.length - 1, 0)) : 0
  const setActiveIndex = (index: number) => setActive({ query: mention?.query ?? '', index })

  // Rebuild the DOM only when the value changed from outside (undo, another editor); typing already matches it.
  useLayoutEffect(() => {
    const root = rootRef.current
    if (!root || value === lastValue.current) return
    root.replaceChildren(...parseDescription(value).map((segment) =>
      (segment.type === 'text' ? document.createTextNode(segment.text) : createChip(segment, resolveLabel, chipState))))
    ensureTrailingFiller(root)
    lastValue.current = value
  }, [value, resolveLabel, chipState])

  // Names and states change without the text changing (a renamed output, a removed target).
  useLayoutEffect(() => {
    const root = rootRef.current
    if (!root) return
    chipsOf(root).forEach((chip) => {
      const annotation = annotationOfChip(chip)
      if (annotation) paintChip(chip, annotation, resolveLabel, chipState)
    })
  })

  useEffect(() => {
    if (autoFocus) rootRef.current?.focus()
  }, [autoFocus])

  const emit = useCallback((inserted?: Annotation) => {
    const root = rootRef.current
    if (!root) return
    ensureTrailingFiller(root)
    const next = serialize(root)
    lastValue.current = next
    onChange(next, inserted)
  }, [onChange])

  /** An "@query" right before the caret (at the start or after a space, so e-mail addresses don't trigger it). */
  const detectMention = useCallback(() => {
    const selection = window.getSelection()
    const root = rootRef.current
    if (!selection || !root || selection.rangeCount === 0 || !selection.isCollapsed) return setMention(null)
    const node = selection.anchorNode
    if (!node || node.nodeType !== Node.TEXT_NODE || !root.contains(node)) return setMention(null)
    const before = (node.nodeValue ?? '').slice(0, selection.anchorOffset)
    const start = before.lastIndexOf('@')
    const query = start >= 0 ? before.slice(start + 1) : ''
    if (start < 0 || (start > 0 && !/\s/.test(before[start - 1])) || query.length > 30 || /\n/.test(query) || /^\s/.test(query)) {
      return setMention(null)
    }
    const range = document.createRange()
    range.setStart(node, start)
    range.setEnd(node, start + 1)
    mentionNode.current = node as Text
    setMention({ start, end: selection.anchorOffset, query, rect: range.getBoundingClientRect() })
  }, [])

  const pick = useCallback((option: AnnotationOption) => {
    const root = rootRef.current
    const node = mentionNode.current
    if (!mention || !root || !node || !root.contains(node)) return
    const text = node.nodeValue ?? ''
    const afterText = text.slice(mention.end)
    node.nodeValue = text.slice(0, mention.start)
    const chip = createChip(option.annotation, resolveLabel, chipState)
    const after = document.createTextNode(afterText.startsWith(' ') ? afterText : ` ${afterText}`)
    node.after(chip, after)
    const selection = window.getSelection()
    const range = document.createRange()
    range.setStart(after, 1)
    range.collapse(true)
    selection?.removeAllRanges()
    selection?.addRange(range)
    setMention(null)
    emit(option.annotation)
    if (option.activateOnInsert && onChipActivate) {
      const annotation = annotationOfChip(chip) ?? option.annotation
      requestAnimationFrame(() => onChipActivate(annotation, chip.getBoundingClientRect()))
    }
  }, [mention, resolveLabel, chipState, emit, onChipActivate])

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (mention && options.length > 0) {
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault()
        const step = event.key === 'ArrowDown' ? 1 : -1
        setActiveIndex((activeIndex + step + options.length) % options.length)
        return
      }
      if (event.key === 'Enter' || event.key === 'Tab') {
        event.preventDefault()
        pick(options[activeIndex])
        return
      }
    }
    if (mention && event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      setMention(null)
      return
    }
    if (event.key === 'Enter') {
      event.preventDefault()
      insertTextAtCaret('\n')
      emit()
    }
  }

  const handlePaste = (event: ClipboardEvent<HTMLDivElement>) => {
    event.preventDefault()
    insertTextAtCaret(event.clipboardData.getData('text/plain'))
    emit()
  }

  const handleClick = (event: MouseEvent<HTMLDivElement>) => {
    const chip = (event.target as HTMLElement).closest<HTMLElement>('[data-annotation-kind]')
    const annotation = chip ? annotationOfChip(chip) : null
    if (chip && annotation) onChipActivate?.(annotation, chip.getBoundingClientRect())
  }

  const groups = options.reduce<Array<{ group: string; items: Array<{ option: AnnotationOption; index: number }> }>>((list, option, index) => {
    const last = list[list.length - 1]
    if (last?.group === option.group) last.items.push({ option, index })
    else list.push({ group: option.group, items: [{ option, index }] })
    return list
  }, [])

  const listTop = mention ? Math.min(mention.rect.bottom + 4, window.innerHeight - 260) : 0
  const listLeft = mention ? Math.max(8, Math.min(mention.rect.left, window.innerWidth - 300)) : 0

  return (
    <>
      <div
        ref={rootRef}
        className={`annotated-editor nodrag nowheel nopan${value ? '' : ' is-empty'}`}
        contentEditable
        suppressContentEditableWarning
        role="textbox"
        aria-multiline="true"
        aria-label={ariaLabel}
        aria-autocomplete="list"
        aria-expanded={Boolean(mention && options.length > 0)}
        data-placeholder={placeholder}
        spellCheck
        onInput={(event) => {
          emit()
          if (!(event.nativeEvent as InputEvent).isComposing) detectMention()
        }}
        onKeyDown={handleKeyDown}
        onKeyUp={(event) => {
          if (event.key.startsWith('Arrow') && !(mention && (event.key === 'ArrowDown' || event.key === 'ArrowUp'))) detectMention()
        }}
        onPaste={handlePaste}
        onDrop={(event) => event.preventDefault()}
        onClick={handleClick}
        onBlur={() => setMention(null)}
      />
      {mention && options.length > 0 && createPortal(
        <div
          className="annotation-suggestions nodrag nowheel"
          role="listbox"
          style={{ top: listTop, left: listLeft }}
          // Keeps focus (and the caret) in the editor while choosing.
          onMouseDown={(event) => event.preventDefault()}
        >
          {groups.map(({ group, items }) => (
            <div key={group} className="annotation-suggestions-group">
              <div className="annotation-suggestions-heading">{group}</div>
              {items.map(({ option, index }) => (
                <div
                  key={option.key}
                  role="option"
                  aria-selected={index === activeIndex}
                  className={`annotation-suggestion annotation-suggestion--${option.annotation.kind}${index === activeIndex ? ' is-active' : ''}`}
                  onMouseEnter={() => setActiveIndex(index)}
                  onClick={() => pick(option)}
                >
                  <span className="annotation-suggestion-icon" aria-hidden>{option.icon ?? (option.annotation.kind === 'prepared' ? '🔪' : option.annotation.kind === 'step' ? '↳' : '🥕')}</span>
                  <span className="annotation-suggestion-title">
                    {option.annotation.kind === 'prepared' && <span className="annotation-suggestion-prefix">prepared · </span>}
                    {option.title}
                  </span>
                  {option.hint && <span className="annotation-suggestion-hint">{option.hint}</span>}
                </div>
              ))}
            </div>
          ))}
        </div>,
        document.body,
      )}
    </>
  )
}
