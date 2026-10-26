import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { render, screen } from '@testing-library/react'
import { createAppRouter } from '@/router'

function renderRoute(path: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const router = createAppRouter({
    queryClient,
    history: createMemoryHistory({ initialEntries: [path] }),
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

afterEach(() => {
  vi.unstubAllGlobals()
})

test('home page shows the backend status', async () => {
  const fetchMock = vi
    .fn()
    .mockResolvedValue(
      Response.json({ status: 'ok', sqlite_version: '3.50.0' }),
    )
  vi.stubGlobal('fetch', fetchMock)

  renderRoute('/')

  expect(await screen.findByText(/SQLite 3\.50\.0/)).toBeInTheDocument()
  expect(fetchMock).toHaveBeenCalledWith('/api/status', expect.anything())
})
