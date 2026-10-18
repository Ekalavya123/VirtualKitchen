import type { CSSProperties } from 'react'
import { parseDescription, type Annotation, type AnnotationLabelResolver } from '../../../model/stepAnnotations'
import type { ChipState } from './AnnotatedTextEditor'
import { defaultChipText, type ChipText } from './chipText'
import './annotations.css'

type AnnotatedTextProps = {
  text: string
  resolveLabel: AnnotationLabelResolver
  chipState?: (annotation: Annotation) => ChipState
  /** How a chip reads (e.g. an ingredient with its amount). */
  chipText?: ChipText
  /** Makes prepared chips clickable (opens the flow that makes them). */
  onOpenPrepared?: (annotation: Annotation) => void
  className?: string
  style?: CSSProperties
}

/** A description with its @ annotations shown as read-only chips (live names), e.g. on a node card. */
export default function AnnotatedText({ text, resolveLabel, chipState, chipText, onOpenPrepared, className, style }: AnnotatedTextProps) {
  return (
    <span className={className} style={style}>
      {parseDescription(text).map((segment, index) => {
        if (segment.type === 'text') return <span key={index}>{segment.text}</span>
        const label = (resolveLabel(segment) || '').trim() || segment.label
        const shown = chipText?.(segment, label) || defaultChipText(segment, label)
        const missing = chipState?.(segment) === 'missing'
        const chipClass = `annotation-chip annotation-chip--${segment.kind} annotation-chip--compact${missing ? ' is-missing' : ''}`
        if (segment.kind === 'prepared' && onOpenPrepared && !missing) {
          return (
            <button
              key={index}
              type="button"
              className={`${chipClass} nodrag`}
              title="Open the flow that makes it"
              onClick={(event) => {
                event.stopPropagation()
                onOpenPrepared(segment)
              }}
            >
              {shown}
            </button>
          )
        }
        return (
          <span key={index} className={chipClass} title={missing ? 'This step no longer uses it' : undefined}>
            {shown}
          </span>
        )
      })}
    </span>
  )
}
