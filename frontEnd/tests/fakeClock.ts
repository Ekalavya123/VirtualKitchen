/** A manual clock + timer queue for testing timer-driven code without real waiting. */
export class FakeClock {
  now = 0
  private nextId = 1
  private timers = new Map<number, { at: number; callback: () => void }>()

  setTimer = (callback: () => void, ms: number) => {
    const id = this.nextId++
    this.timers.set(id, { at: this.now + ms, callback })
    return id as unknown as ReturnType<typeof setTimeout>
  }

  clearTimer = (handle: ReturnType<typeof setTimeout>) => {
    this.timers.delete(handle as unknown as number)
  }

  pendingTimers() {
    return this.timers.size
  }

  /** Advances time, firing due timers in order and letting their promise chains settle in between. */
  async advance(ms: number) {
    const target = this.now + ms
    for (;;) {
      await flushMicrotasks()
      const due = Array.from(this.timers.entries())
        .filter(([, timer]) => timer.at <= target)
        .sort((a, b) => a[1].at - b[1].at)[0]
      if (!due) break
      this.timers.delete(due[0])
      this.now = due[1].at
      due[1].callback()
    }
    this.now = target
    await flushMicrotasks()
  }
}

export const flushMicrotasks = async () => {
  for (let i = 0; i < 20; i++) await Promise.resolve()
}

/** A promise whose settlement the test controls. */
export const deferred = <T = void>() => {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}
