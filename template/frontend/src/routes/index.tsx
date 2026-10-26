import { useSuspenseQuery } from '@tanstack/react-query'
import { createFileRoute } from '@tanstack/react-router'
import { statusQuery } from '@/lib/api'

export const Route = createFileRoute('/')({
  // Start fetching before the page renders; the component reads the cache.
  loader: ({ context }) => context.queryClient.ensureQueryData(statusQuery),
  component: Home,
})

function Home() {
  const { data } = useSuspenseQuery(statusQuery)

  return (
    <div className="max-w-xl text-center">
      <h1 className="mb-4 text-4xl font-bold">
        <span>Clojure Stack SPA</span>
      </h1>
      <p className="mb-6 text-muted-foreground">
        Backend is <strong className="text-foreground">{data.status}</strong>,
        connected to SQLite {data.sqlite_version}.
      </p>
      <p className="text-sm text-muted-foreground">
        Edit{' '}
        <code className="rounded bg-muted px-1">
          frontend/src/routes/index.tsx
        </code>{' '}
        to change this page, or add an API route in{' '}
        <code className="rounded bg-muted px-1">src/…/routes.clj</code>.
      </p>
    </div>
  )
}
