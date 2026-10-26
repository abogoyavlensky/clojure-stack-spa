# {{ project-name }}

TODO: add project description

Full-stack web application: a Clojure JSON API and a React single-page app.

## Stack

- **Backend:** Integrant, Reitit/Ring, Jetty, Malli, SQLite with next.jdbc, HoneySQL and Ragtime
- **Frontend:** React, TypeScript, Vite, TanStack Router and Query, Zustand, Tailwind CSS, shadcn/ui-ready
- **Tooling:** Babashka tasks, clj-kondo, cljfmt, eftest, Biome, Vitest, mise

## Project structure

```
├── dev/                   # Development-only code: REPL helpers, Vite dev server component
├── frontend/              # React app (Vite project)
│   └── src/
│       ├── routes/        # File-based routes (TanStack Router)
│       ├── lib/api.ts     # API client and query definitions
│       └── stores/        # Client state (Zustand)
├── resources/
│   ├── migrations/        # Database migrations
│   ├── public/            # Built frontend (generated, not committed)
│   ├── config.edn         # Application config
│   └── config.dev.edn     # Development config: adds the Vite dev server
├── src/{{ project-name | snake_case }}/
│   ├── core.clj           # Application entry point
│   ├── db.clj             # Database component and query helpers
│   ├── handlers.clj       # API handlers
│   ├── routes.clj         # API routes
│   ├── server.clj         # HTTP server component and middleware
│   └── utils/             # Config, system and server helpers
├── test/                  # Backend tests
├── bb.edn                 # Project tasks
├── deps.edn               # Clojure dependencies
└── Dockerfile             # Production image
```

## Development

Install Java, Clojure, Babashka, Node and the other tools with [mise](https://mise.jdx.dev/) and fetch the dependencies:

```shell
mise trust && mise install
bb deps
```

Start the REPL and run the system:

```shell
bb clj-repl
(reset)
```

`(reset)` starts the backend at `http://localhost:8000` and the Vite dev server at
`http://localhost:5173`. Open `http://localhost:5173`: Vite serves the frontend
with hot reload and proxies `/api` requests to the backend. Backend code is
reloaded on every request.

To run the Vite dev server in a separate terminal instead, remove the
`:{{ project-name }}.vite/dev-server` component from `resources/config.dev.edn`
and run `bb frontend-dev`.

### API

API routes live under `/api` in `src/{{ project-name | snake_case }}/routes.clj`.
JSON keys are snake_case on the wire and kebab-case keywords in Clojure.
Request and response bodies are validated with Malli; invalid requests get a
400 response with human-readable errors under `humanized`.

The backend serves the built frontend for every other GET request, so
client-side routes work on a full page reload.

### Security defaults

- The session is an encrypted cookie: `HttpOnly`, `SameSite=Lax` and `Secure` in production.
- POST, PUT, PATCH and DELETE requests must send JSON or the `X-Requested-With`
  header, which blocks cross-site form submissions (CSRF). The frontend API
  client in `frontend/src/lib/api.ts` sets the header on every request.
- CORS is off: the frontend is served from the same origin. To call the API
  from another domain, add it to `:allowed-origins` in `resources/config.edn`.
- Error details are returned only in development and tests.

### Checks

```shell
bb check           # all checks for backend and frontend
bb check-backend   # cljfmt, clj-kondo and tests
bb check-frontend  # Biome, TypeScript and Vitest
```

Run `bb lint-init` once before the first `bb lint` to import linting configs
of the dependencies. See all tasks with `bb tasks`.

## Production

Build the uberjar with the frontend included:

```shell
bb build
java -jar target/standalone.jar
```

Or build the Docker image:

```shell
docker build -t {{ project-name }} .
docker run -p 8000:80 -e SESSION_SECRET_KEY=change-me -v $(pwd)/db:/app/db {{ project-name }}
```

Production requires the `SESSION_SECRET_KEY` environment variable. The SQLite
database is stored in `/app/db`; mount a volume there to keep it.
