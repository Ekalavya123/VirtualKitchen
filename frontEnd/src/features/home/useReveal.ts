import { useEffect, useState, type RefObject } from 'react'

/**
 * Adds `is-visible` to every `[data-reveal]` element under `rootRef` the first time it scrolls
 * into view. The hidden starting state lives in CSS behind `prefers-reduced-motion: no-preference`,
 * so reduced-motion users (and browsers without IntersectionObserver) see everything immediately.
 */
export function useReveal(rootRef: RefObject<HTMLElement | null>) {
  useEffect(() => {
    const root = rootRef.current
    if (!root) return
    const targets = root.querySelectorAll<HTMLElement>('[data-reveal]')

    if (typeof IntersectionObserver === 'undefined') {
      targets.forEach((target) => target.classList.add('is-visible'))
      return
    }

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue
          entry.target.classList.add('is-visible')
          observer.unobserve(entry.target)
        }
      },
      { threshold: 0.15, rootMargin: '0px 0px -40px 0px' },
    )
    targets.forEach((target) => observer.observe(target))
    return () => observer.disconnect()
  }, [rootRef])
}

function prefersReducedMotion() {
  return typeof window !== 'undefined'
    && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}

/**
 * Types `text` out one character at a time once `start` turns true.
 * Returns the full text straight away when the user prefers reduced motion.
 */
export function useTypewriter(text: string, start: boolean, msPerChar = 38) {
  const [count, setCount] = useState(() => (prefersReducedMotion() ? text.length : 0))

  useEffect(() => {
    if (!start || prefersReducedMotion()) return
    const timer = window.setInterval(() => {
      setCount((current) => {
        if (current >= text.length) {
          window.clearInterval(timer)
          return current
        }
        return current + 1
      })
    }, msPerChar)
    return () => window.clearInterval(timer)
  }, [start, text, msPerChar])

  return { typed: text.slice(0, count), done: count >= text.length }
}

/** True once the element behind `ref` has been on screen (stays true afterwards). */
export function useInView(ref: RefObject<HTMLElement | null>) {
  const [inView, setInView] = useState(() => typeof IntersectionObserver === 'undefined')

  useEffect(() => {
    const element = ref.current
    if (!element || inView) return
    const observer = new IntersectionObserver(([entry]) => {
      if (entry?.isIntersecting) {
        setInView(true)
        observer.disconnect()
      }
    }, { threshold: 0.4 })
    observer.observe(element)
    return () => observer.disconnect()
  }, [ref, inView])

  return inView
}
