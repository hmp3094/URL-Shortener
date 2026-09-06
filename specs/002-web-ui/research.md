# Phase 0 Research: Web UI for Shortening and Stats Lookup

## 1. Re-enabling static resource serving safely

**Decision**: Remove `spring.web.resources.add-mappings: false` from `application.yml`, restoring
Spring Boot's default static-resource handling (serving `classpath:/static/**`, with `index.html`
as the automatic welcome page at `GET /`). No custom `WebMvcConfigurer.addResourceHandlers`
registration needed.

**Rationale**: The default static resource mapping is registered at the lowest dispatch priority,
below every `@Controller`-annotated mapping — `RedirectController`'s `GET /{code}`,
`LinkController`'s `GET /api/links`, `StatsController`, Actuator, and springdoc all continue to
take precedence exactly as they do today. The remaining question is whether static content can
ever collide in *shape* with the redirect route: every static filename this feature adds
(`index.html`, `style.css`, `app.js`) contains a `.`, which the alias/short-code character set
(`[a-zA-Z0-9_-]`) excludes — the same property that already keeps `/swagger-ui.html` safe from
that route today. The bare root path (`GET /`, an empty segment) is also never reachable by the
redirect route, since its minimum length is 3 characters. No new reserved-name entry is needed in
`CustomAliasValidator` for this reason — the collision this project already guards against
(a *created* alias shadowing a real route) doesn't apply to the welcome page or static assets
either, since neither is a segment a short code could ever equal.

**Alternatives considered**: an explicit resource handler scoped to a sub-path (e.g. serving only
`/ui/**`) — rejected as an unnecessary abstraction for a single page with three files, and it
would put the page one path segment away from the clean root URL a link-shortener's own front
door should have.

## 2. No framework, no build step

**Decision**: Hand-written `index.html` / `style.css` / `app.js`, calling the existing JSON API
via `fetch()`. No React/Vue, no bundler, no npm dependency tree.

**Rationale**: The page has exactly two interactions (submit a form, look up a code) against an
API that already exists — there is no client-side state management, routing, or component reuse
complex enough to justify a framework, and a build pipeline would be the first one anywhere in
this project (Maven builds the backend; nothing today runs Node). This mirrors every prior
dependency decision in this codebase: an in-process cache instead of Redis, a hand-rolled rate
limiter instead of a library — take on a dependency only when the problem is actually big enough
to need it.

**Alternatives considered**: Thymeleaf server-rendered templates — rejected because nothing on
this page needs server-side rendering; every value shown is fetched client-side from the JSON API
after the static page has already loaded, so a templating engine would add a dependency without
removing any actual complexity.

## 3. Expiration input

**Decision**: The form offers a small set of named durations (e.g. 1 hour / 1 day / 1 week /
1 month / never expires) rather than a raw seconds field, translated to the number of seconds
`POST /api/links` expects before the request is sent.

**Rationale**: `expiresInSeconds` is the correct shape for an API but not for a person — asking a
visitor to compute or guess a number of seconds fails the "shouldn't look amateurish" bar this
feature exists to clear. The translation is a few lines of JavaScript, not a new capability.

## 4. Error mapping

**Decision**: A small client-side lookup table maps each `error` code the API already returns
(`VALIDATION_ERROR`, `ALIAS_TAKEN`, `URL_ALREADY_SHORTENED`, `RATE_LIMITED`, `NOT_FOUND`) to one
plain-language sentence, shown inline near the field it concerns rather than as a generic banner.

**Rationale**: The API's `ErrorResponse` shape (`error`, `message`, `timestamp`) already
distinguishes every case FR-005 requires distinguishing — the UI's job is purely translation, not
inventing new failure detection. `VALIDATION_ERROR`'s own `message` text already names the
specific rule violated (from `CustomAliasValidator`/`DestinationUrlValidator`), so that one case
can show the API's own message directly rather than needing its own translation table entry.

## 5. Copy-to-clipboard with a fallback

**Decision**: Use the `navigator.clipboard.writeText()` API where available; when it isn't (an
insecure context, an older browser, or a permissions denial), fall back to a selectable text
field so the visitor can still copy manually, with a short inline note explaining the fallback.

**Rationale**: The Clipboard API requires a secure context (HTTPS or `localhost`) and isn't
universally available — failing silently or throwing an unhandled error on unsupported browsers
would violate this feature's own bar for never leaving a visitor without an explanation.
