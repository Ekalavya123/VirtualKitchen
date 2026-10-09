import type { CSSProperties, ReactNode } from 'react'
import ToolbarMenu, { MenuItem, type ToolbarMenuPlacement } from './ToolbarMenu'

type AddNodeMenuProps = {
  /** Adds a STEP node to the open process's canvas — omitted (item hidden) without a canvas. */
  onAddStep?: () => void
  onAddCondition?: () => void
  /** Opens the new-subprocess form — omitted (item hidden) where subprocesses can't be created. */
  onAddSubprocess?: () => void
  label?: ReactNode
  buttonStyle?: CSSProperties
  buttonClassName?: string
  placement?: ToolbarMenuPlacement
}

/**
 * The editor's one "+ Add" entry point: Step / Condition / Subprocess in a small menu, instead of a
 * permanent button per kind. Only re-exposes existing actions; renders nothing when none is available.
 */
export default function AddNodeMenu({
  onAddStep,
  onAddCondition,
  onAddSubprocess,
  label = '+ Add',
  buttonStyle,
  buttonClassName,
  placement = 'top-start',
}: AddNodeMenuProps) {
  if (!onAddStep && !onAddCondition && !onAddSubprocess) return null
  return (
    <ToolbarMenu
      label={label}
      ariaLabel="Add a step, condition or subprocess"
      title="Add to this recipe"
      buttonStyle={buttonStyle}
      buttonClassName={buttonClassName}
      placement={placement}
      width={232}
    >
      <>
        {onAddStep && (
          <MenuItem icon="▭" label="Step" description="A cooking action in this process" onSelect={onAddStep} />
        )}
        {onAddCondition && (
          <MenuItem icon="◇" label="Condition" description="A yes / no check that branches the flow" onSelect={onAddCondition} />
        )}
        {onAddSubprocess && (
          <MenuItem icon="🧩" label="Subprocess" description="A reusable part of this recipe" onSelect={onAddSubprocess} />
        )}
      </>
    </ToolbarMenu>
  )
}
