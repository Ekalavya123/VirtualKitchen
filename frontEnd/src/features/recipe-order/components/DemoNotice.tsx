import type { ReactNode } from 'react'

type DemoNoticeProps = {
  children: ReactNode
  /** 'strong' for the prominent banners (simulated payment/preparation). */
  variant?: 'subtle' | 'strong'
  icon?: string
}

/** Labels a simulated part of the order flow so it can't be mistaken for the real thing. */
export default function DemoNotice({ children, variant = 'subtle', icon = 'ℹ️' }: DemoNoticeProps) {
  return (
    <div className={`ro-demo ro-demo-${variant}`} role="note">
      <span className="ro-demo-icon" aria-hidden>{icon}</span>
      <div className="ro-demo-body">
        <span className="ro-demo-tag">Demo</span>
        <span>{children}</span>
      </div>
    </div>
  )
}
