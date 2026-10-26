# Clojure Stack SPA

A quick way to start a full-stack web app with a Clojure JSON API and a React single-page frontend.

The stack is picked for you: one backend, one frontend, one database, and the
wiring between them already done. You write features, not configuration.

## Usage

Create a project with [frame](https://github.com/abogoyavlensky/frame):

```shell
frame new https://github.com/abogoyavlensky/clojure-stack-spa myproject
```

Then start development (with [mise](https://mise.jdx.dev/getting-started.html)):

```shell
cd myproject
mise trust && mise install
bb deps
bb clj-repl
(reset)
```

`(reset)` starts the backend on `http://localhost:8000` and the Vite dev server
on `http://localhost:5173`. Open `http://localhost:5173`: the frontend reloads
on change and API calls are proxied to the backend.

Check out the generated project's README.md for more details.

## Features

- 🏗️ Clojure backend with Integrant and Reitit/Ring, serving a JSON API and the built frontend
- ⚛️ React frontend with TypeScript, Vite, TanStack Router and TanStack Query
- 🎨 Tailwind CSS v4 with shadcn/ui ready to add components, light and dark themes
- 📦 SQLite with migrations
- 🔒 Secure defaults: encrypted session cookie, CSRF protection for the API, CORS off by default, no error details in production
- 🔁 One REPL command starts the backend and the frontend dev server
- 🧪 Backend and frontend tests, linting and formatting
- 🐳 Multi-stage Dockerfile producing a small JRE image
- ⚡ GitHub Actions checks for backend and frontend

## Stack

### Backend
- **Integrant**: component lifecycle
- **Reitit/Ring, Jetty**: routing and HTTP server
- **Malli**: config, request and response validation
- **Muuntaja**: JSON with snake_case keys on the wire, kebab-case in Clojure

### Database
- **SQLite**: file-based database
- **next.jdbc, HoneySQL**: database access and SQL as data
- **Ragtime**: migrations

### Frontend
- **React 19, TypeScript**
- **Vite**: dev server and build
- **TanStack Router**: type-safe file-based routing
- **TanStack Query**: server state and caching
- **Zustand**: client state
- **Tailwind CSS v4, shadcn/ui**: styling and components

### Development
- **Babashka**: project tasks
- **clj-kondo, cljfmt**: Clojure linting and formatting
- **eftest, cloverage**: Clojure tests and coverage
- **Biome**: TypeScript linting and formatting
- **Vitest, Testing Library**: frontend tests
- **mise**: tool versions

## Project structure

```
├── dev/                   # REPL helpers and the Vite dev server component
├── frontend/              # React app (Vite project)
├── resources/
│   ├── migrations/        # Database migrations
│   ├── public/            # Built frontend (generated)
│   ├── config.edn         # Application config
│   └── config.dev.edn     # Development config
├── src/<name>/            # Backend: API routes, handlers, server, database
├── test/<name>/           # Backend tests
├── bb.edn                 # Project tasks
├── deps.edn               # Clojure dependencies
└── Dockerfile             # Production image
```

## Template development

The template lives in `template/` and mirrors the generated project. `frame.edn`
configures it; see the [frame docs](https://github.com/abogoyavlensky/frame)
for the template syntax.

Install the tools, including frame, with mise:

```shell
mise trust && mise install
```

Tasks:

```shell
bb new     # generate a project from the local template into tmp-prj/
bb check   # generate a project, install its deps and run all of its checks
```

## License

MIT License
Copyright (c) 2026 Andrey Bogoyavlenskiy
