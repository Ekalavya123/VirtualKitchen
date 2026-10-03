import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { SaveCoordinator, classifySaveError, type SaveCoordinatorOptions } from '../src/features/recipe-tool/persistence/saveCoordinator.ts'
import { FakeClock, deferred, flushMicrotasks } from './fakeClock.ts'

class HttpError extends Error {
  status: number | undefined
  constructor(message: string, status?: number) {
    super(message)
    this.status = status
  }
}

/** A coordinator over a fake clock whose `save` records the "snapshot" (a counter the test bumps) at call time. */
const setup = (overrides: Partial<SaveCoordinatorOptions> = {}) => {
  const clock = new FakeClock()
  const session = { value: 0 }
  const persisted: number[] = []
  let inFlight = 0
  let maxConcurrent = 0
  let nextSave: (() => Promise<void>) | null = null
  const coordinator = new SaveCoordinator({
    save: async () => {
      const snapshot = session.value
      inFlight += 1
      maxConcurrent = Math.max(maxConcurrent, inFlight)
      try {
        if (nextSave) {
          const run = nextSave
          nextSave = null
          await run()
        }
        persisted.push(snapshot)
      } finally {
        inFlight -= 1
      }
    },
    statusOf: (error) => (error instanceof HttpError ? error.status : undefined),
    now: () => clock.now,
    setTimer: clock.setTimer,
    clearTimer: clock.clearTimer,
    ...overrides,
  })
  const edit = () => {
    session.value += 1
    coordinator.markChanged()
  }
  return {
    clock, coordinator, persisted, edit,
    willSave: (run: () => Promise<void>) => { nextSave = run },
    maxConcurrent: () => maxConcurrent,
  }
}

describe('SaveCoordinator', () => {
  it('starts saved, becomes dirty on change, saves once after the debounce, then is saved', async () => {
    const { clock, coordinator, persisted, edit } = setup()
    assert.equal(coordinator.getState().status, 'saved')

    edit()
    assert.equal(coordinator.getState().status, 'dirty')
    await clock.advance(1499)
    assert.deepEqual(persisted, [])

    await clock.advance(1)
    assert.deepEqual(persisted, [1])
    assert.equal(coordinator.getState().status, 'saved')
    assert.equal(coordinator.getState().lastSavedAt, 1500)
  })

  it('coalesces a burst of edits into one save of the latest snapshot', async () => {
    const { clock, persisted, edit } = setup()
    for (let i = 0; i < 20; i++) {
      edit()
      await clock.advance(200)
    }
    assert.deepEqual(persisted, [])
    await clock.advance(1500)
    assert.deepEqual(persisted, [20])
  })

  it('flush saves immediately without waiting for the debounce', async () => {
    const { clock, coordinator, persisted, edit } = setup()
    edit()
    const ok = await coordinator.flush()
    assert.equal(ok, true)
    assert.deepEqual(persisted, [1])
    await clock.advance(5000)
    assert.deepEqual(persisted, [1], 'the cancelled debounce must not save again')
  })

  it('reports saving while in flight and never runs two saves concurrently', async () => {
    const { clock, coordinator, persisted, edit, willSave, maxConcurrent } = setup()
    const slow = deferred()
    willSave(() => slow.promise)

    edit()
    await clock.advance(1500)
    assert.equal(coordinator.getState().status, 'saving')

    // Edits and an explicit Save while the first request is still running.
    edit()
    edit()
    const flushed = coordinator.flush()
    await flushMicrotasks()
    assert.equal(maxConcurrent(), 1)

    slow.resolve()
    assert.equal(await flushed, true)
    // The first save carried snapshot 1; the queued one ran after it with the latest snapshot.
    assert.deepEqual(persisted, [1, 3])
    assert.equal(maxConcurrent(), 1)
    assert.equal(coordinator.getState().status, 'saved')
  })

  it('an older save finishing can never mark newer edits saved', async () => {
    const { clock, coordinator, persisted, edit, willSave } = setup()
    const slow = deferred()
    willSave(() => slow.promise)
    edit()
    await clock.advance(1500)

    edit() // arrives while snapshot 1 is in flight
    slow.resolve()
    await flushMicrotasks()
    assert.deepEqual(persisted, [1])
    assert.equal(coordinator.getState().status, 'dirty', 'revision 2 is still unsaved')

    await clock.advance(1500)
    assert.deepEqual(persisted, [1, 2])
    assert.equal(coordinator.getState().status, 'saved')
  })

  it('keeps the changes and retries network failures with capped exponential backoff', async () => {
    const { clock, coordinator, persisted, edit, willSave } = setup({ retryBaseMs: 1000, retryMaxMs: 4000, maxAutoRetries: 4 })
    const failures: number[] = []
    const failNext = () => willSave(async () => {
      failures.push(clock.now)
      throw new HttpError('offline')
    })

    failNext()
    edit()
    await clock.advance(1500)
    assert.equal(coordinator.getState().status, 'failed')
    assert.equal(coordinator.getState().error, 'offline')
    assert.equal(coordinator.getState().retryAt, 2500)

    for (const delay of [1000, 2000, 4000]) {
      failNext()
      await clock.advance(delay)
    }
    // 1st attempt at 1500, retries after 1s, 2s, 4s.
    assert.deepEqual(failures, [1500, 2500, 4500, 8500])
    assert.equal(coordinator.getState().retryAt, 12500, 'backoff capped at retryMaxMs')

    await clock.advance(4000) // 5th attempt succeeds
    assert.deepEqual(persisted, [1])
    assert.equal(coordinator.getState().status, 'saved')
    assert.equal(coordinator.getState().error, null)
  })

  it('stops retrying automatically after maxAutoRetries, until the next edit or Save', async () => {
    let attempts = 0
    const alwaysFail: SaveCoordinatorOptions['save'] = async () => {
      attempts += 1
      throw new HttpError('server down', 503)
    }
    const { clock, coordinator, edit } = setup({ save: alwaysFail, retryBaseMs: 1000, retryMaxMs: 1000, maxAutoRetries: 2 })
    edit()
    await clock.advance(60000)
    assert.equal(attempts, 3, 'first attempt + 2 retries')
    assert.equal(coordinator.getState().retryAt, null)
    assert.equal(clock.pendingTimers(), 0)
    assert.equal(coordinator.getState().status, 'failed')

    edit()
    await clock.advance(1500)
    assert.equal(attempts, 4, 'a new edit tries again')
  })

  it('does not hammer the backend on every edit while a retry is pending', async () => {
    const { clock, coordinator, persisted, edit, willSave } = setup({ retryBaseMs: 10000 })
    willSave(async () => { throw new HttpError('offline') })
    edit()
    await clock.advance(1500)
    assert.equal(coordinator.getState().status, 'failed')

    for (let i = 0; i < 5; i++) {
      edit()
      await clock.advance(1500)
    }
    assert.deepEqual(persisted, [], 'no extra attempts before the scheduled retry')
    await clock.advance(10000)
    assert.deepEqual(persisted, [6], 'the retry carried the latest snapshot')
  })

  it('does not auto-retry a validation failure, but tries again on the next edit', async () => {
    const { clock, coordinator, persisted, edit, willSave } = setup()
    willSave(async () => { throw new HttpError('Step s2 references an unknown step', 422) })
    edit()
    await clock.advance(1500)
    assert.equal(coordinator.getState().status, 'failed')
    assert.equal(coordinator.getState().errorKind, 'validation')
    assert.equal(coordinator.getState().retryAt, null)
    await clock.advance(60000)
    assert.deepEqual(persisted, [])

    edit()
    await clock.advance(1500)
    assert.deepEqual(persisted, [2])
    assert.equal(coordinator.getState().status, 'saved')
  })

  it('parks in conflict on 409 — no autosave, no flush — until resolved', async () => {
    const { clock, coordinator, persisted, edit, willSave } = setup()
    willSave(async () => { throw new HttpError('changed elsewhere', 409) })
    edit()
    await clock.advance(1500)
    assert.equal(coordinator.getState().status, 'conflict')

    edit()
    await clock.advance(60000)
    assert.equal(await coordinator.flush(), false)
    assert.deepEqual(persisted, [])

    coordinator.resolveConflict()
    assert.equal(coordinator.getState().status, 'dirty')
    assert.equal(await coordinator.flush(), true)
    assert.deepEqual(persisted, [2])
  })

  it('manual flush after failures resets the backoff and saves', async () => {
    const { clock, coordinator, persisted, edit, willSave } = setup({ retryBaseMs: 30000 })
    willSave(async () => { throw new HttpError('offline') })
    edit()
    await clock.advance(1500)
    assert.notEqual(coordinator.getState().retryAt, null)

    assert.equal(await coordinator.flush(), true)
    assert.deepEqual(persisted, [1])
    assert.equal(coordinator.getState().retryAt, null)
    assert.equal(clock.pendingTimers(), 0)
  })

  it('never saves automatically when autosave is off, but still tracks dirtiness', async () => {
    const { clock, coordinator, persisted, edit } = setup({ autosave: false })
    edit()
    await clock.advance(60000)
    assert.deepEqual(persisted, [])
    assert.equal(coordinator.getState().status, 'dirty')
    assert.equal(coordinator.hasPendingChanges(), true)
  })

  it('notifies subscribers only on visible state changes', async () => {
    const { clock, coordinator, edit } = setup()
    let notifications = 0
    coordinator.subscribe(() => { notifications += 1 })
    edit() // saved -> dirty
    edit()
    edit() // still dirty: no notification
    assert.equal(notifications, 1)
    await clock.advance(1500) // dirty -> saving -> saved
    assert.ok(notifications >= 2 && notifications <= 4)
  })
})

describe('classifySaveError', () => {
  it('maps HTTP statuses to how a failed save is handled', () => {
    assert.equal(classifySaveError(undefined), 'network')
    assert.equal(classifySaveError(503), 'network')
    assert.equal(classifySaveError(429), 'network')
    assert.equal(classifySaveError(409), 'conflict')
    assert.equal(classifySaveError(403), 'forbidden')
    assert.equal(classifySaveError(422), 'validation')
    assert.equal(classifySaveError(404), 'validation')
  })
})
