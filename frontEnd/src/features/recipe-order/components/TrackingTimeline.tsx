import type { ReactNode } from 'react'
import type { RecipeOrderTimestamps } from '../../../types/recipeOrder'
import { formatDateTime } from '../model/orderFormat'
import type { TrackingStage } from '../model/trackingModel'

type TrackingTimelineProps = {
  stages: TrackingStage[]
  timestamps: RecipeOrderTimestamps | null | undefined
  /** Rendered inside the cooking stage while it's in progress (the current recipe step). */
  cookingDetail?: ReactNode
}

const STATE_LABEL = { done: 'Done', current: 'In progress', upcoming: 'Upcoming' } as const

/** The preparation and delivery stages, each done / in progress / upcoming, with when it was reached. */
export default function TrackingTimeline({ stages, timestamps, cookingDetail }: TrackingTimelineProps) {
  return (
    <ol className="ro-timeline">
      {stages.map((stage) => {
        const reachedAt = stage.state === 'upcoming'
          ? ''
          : formatDateTime(timestamps?.[stage.timestampKey] ?? (stage.id === 'delivered' ? timestamps?.completedAt : null))
        return (
          <li key={stage.id} className={`ro-timeline-item ro-timeline-${stage.state}`} aria-current={stage.state === 'current' ? 'step' : undefined}>
            <span className="ro-timeline-marker" aria-hidden>{stage.state === 'done' ? '✓' : ''}</span>
            <div className="ro-timeline-body">
              <div className="ro-timeline-head">
                <strong>{stage.label}</strong>
                <span className="ro-visually-hidden">— {STATE_LABEL[stage.state]}</span>
                {stage.stepProgress && (
                  <span className="ro-badge ro-badge-accent">
                    Step {stage.stepProgress.index + 1} of {stage.stepProgress.total}
                  </span>
                )}
                {reachedAt && <time className="ro-timeline-time">{reachedAt}</time>}
              </div>
              {stage.id === 'cooking' && stage.state === 'current' && cookingDetail}
            </div>
          </li>
        )
      })}
    </ol>
  )
}
