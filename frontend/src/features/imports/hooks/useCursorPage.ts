import { useState } from 'react'
import useSWR from 'swr'

export type CursorPage<T> = {
  items: T[]
  nextCursor: number | null
}

type UseCursorPageOptions<T> = {
  cacheKey: string
  loadPage: (after: number | null) => Promise<CursorPage<T>>
}

export function useCursorPage<T>({ cacheKey, loadPage }: UseCursorPageOptions<T>) {
  const [cursor, setCursor] = useState<number | null>(null)
  const [history, setHistory] = useState<Array<number | null>>([])
  const [isNavigating, setIsNavigating] = useState(false)
  const [navigationError, setNavigationError] = useState<unknown>()

  // A chave do SWR permanece estável por coleção. O cursor fica no estado local para que o cache
  // mantenha somente a página atual, em vez de acumular uma entrada diferente para cada cursor visitado.
  const swr = useSWR<CursorPage<T>>(cacheKey, () => loadPage(cursor), {
    keepPreviousData: false,
    revalidateOnFocus: true,
    revalidateOnReconnect: true,
    shouldRetryOnError: false,
  })

  async function replacePage(targetCursor: number | null, nextHistory: Array<number | null>) {
    setIsNavigating(true)
    setNavigationError(undefined)

    try {
      const page = await loadPage(targetCursor)
      setCursor(targetCursor)
      setHistory(nextHistory)
      await swr.mutate(page, { revalidate: false })
    } catch (error) {
      setNavigationError(error)
    } finally {
      setIsNavigating(false)
    }
  }

  async function nextPage() {
    const nextCursor = swr.data?.nextCursor
    if (nextCursor === null || nextCursor === undefined || isNavigating) {
      return
    }

    await replacePage(nextCursor, [...history, cursor])
  }

  async function previousPage() {
    if (history.length === 0 || isNavigating) {
      return
    }

    const nextHistory = history.slice(0, -1)
    const previousCursor = history[history.length - 1] ?? null
    await replacePage(previousCursor, nextHistory)
  }

  async function refreshPage() {
    setNavigationError(undefined)
    await swr.mutate()
  }

  return {
    ...swr,
    pageNumber: history.length + 1,
    canPrevious: history.length > 0,
    canNext: swr.data?.nextCursor !== null && swr.data?.nextCursor !== undefined,
    isNavigating,
    navigationError,
    nextPage,
    previousPage,
    refreshPage,
  }
}
