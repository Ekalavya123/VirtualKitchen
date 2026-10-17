import type { CSSProperties, ReactNode } from 'react'
import ToolbarMenu, { MenuItem, type ToolbarMenuPlacement } from './ToolbarMenu'

type AddNodeMenuProps = {
  /** Adds a STEP node to the open process's canvas — omitted (item hidden) without a canvas. */
  onAddStep?: () => void
  onAddCondition?: () => void
  /** Opens the Preparation (creating it if needed) — omitted (item hidden) where it can't be edited. */
  onAddSubprocess?: () => void
  label?: ReactNode
  buttonStyle?: CSSProperties
  buttonClassName?: string
  placement?: ToolbarMenuPlacement
}

/**
 * The editor's one "+ Add" entry point: Step / Condition / Preparation in a small menu, instead of a
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
      ariaLabel="Add a step, condition or preparation"
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
          <MenuItem icon="🔪" label="Preparation" description="Open the Preparation, where each flow makes a named component" onSelect={onAddSubprocess} />
        )}
      </>
    </ToolbarMenu>
  )
}
