import { QueryClient } from '@tanstack/react-query'
import { createRouter, type RouterHistory } from '@tanstack/react-router'
import { routeTree } from './routeTree.gen'

interface AppRouterOptions {
  queryClient?: QueryClient
  /** Override the browser history, e.g. with a memory history in tests. */
  history?: RouterHistory
}

export function createAppRouter({
  queryClient = new QueryClient(),
  history,
}: AppRouterOptions = {}) {
  return createRouter({
    routeTree,
    history,
    context: { queryClient },
    // Let TanStack Query decide when loader data is stale.
    defaultPreloadStaleTime: 0,
    scrollRestoration: true,
  })
}

declare module '@tanstack/react-router' {
  interface Register {
    router: ReturnType<typeof createAppRouter>
  }
}
