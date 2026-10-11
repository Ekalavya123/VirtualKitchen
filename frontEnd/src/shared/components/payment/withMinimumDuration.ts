/** Resolves with `promise`'s result, but never sooner than `ms` — so the processing animation is always seen. */
export async function withMinimumDuration<T>(promise: Promise<T>, ms = 1900): Promise<Awaited<T>> {
  const [result] = await Promise.allSettled([promise, new Promise(resolve => window.setTimeout(resolve, ms))])
  if (result.status === 'rejected') throw result.reason
  return result.value
}
