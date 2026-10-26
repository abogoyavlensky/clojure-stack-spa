import type { QueryClient } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { createRootRouteWithContext, Outlet } from '@tanstack/react-router'
import { TanStackRouterDevtools } from '@tanstack/react-router-devtools'
import { useEffect } from 'react'
import { useThemeStore } from '@/stores/theme'

interface RouterContext {
  queryClient: QueryClient
}

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootLayout,
  notFoundComponent: () => (
    <p className="text-muted-foreground">Page not found</p>
  ),
})

function RootLayout() {
  const theme = useThemeStore((state) => state.theme)
  const toggle = useThemeStore((state) => state.toggle)

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
  }, [theme])

  return (
    <div className="flex min-h-screen flex-col">
      <header className="flex justify-end p-4">
        <button
          type="button"
          onClick={toggle}
          className="rounded-md border px-3 py-1 text-sm hover:bg-accent"
        >
          {theme === 'light' ? 'Dark' : 'Light'} mode
        </button>
      </header>
      <main className="flex flex-grow items-center justify-center p-4">
        <Outlet />
      </main>
      <ReactQueryDevtools buttonPosition="bottom-left" />
      <TanStackRouterDevtools position="bottom-right" />
    </div>
  )
}
