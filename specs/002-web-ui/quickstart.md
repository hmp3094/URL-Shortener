# Quickstart: Validating the Web UI

Prerequisites: local stack running per `docs/getting-started.md` (`docker compose up --build` or
`mvn spring-boot:run` against a reachable Postgres). Open `http://localhost:8080/` in a browser.

## 1. Shorten a link (User Story 1)

1. Paste a valid URL into the form, leave alias and expiration at their defaults, submit.
2. Expect a short link to appear immediately, with a copy control.
3. Click the copy control; expect a brief confirmation, and paste elsewhere to confirm the
   clipboard actually holds the short URL.
4. Repeat, this time supplying a custom alias; expect the resulting short link to use exactly
   that alias.
5. Repeat, this time choosing a non-default expiration; expect the same success presentation as
   step 2 (this feature doesn't require the UI to dwell on the expiration afterward).

## 2. Handle every failure case (User Story 2)

| Submission | Expected on-page result |
|---|---|
| Empty URL field | Rejected before/without a round trip, with an inline explanation |
| Malformed or unsafe URL (e.g. `not-a-url`, `javascript:alert(1)`) | Plain-language "URL isn't valid" message |
| An alias already used in step 1.4 above | "That alias is already taken" message |
| The same URL from step 1.1, with a new alias | "This URL is already shortened" message, distinct from the alias-taken case |
| Many submissions in quick succession | "You're going too fast, try again shortly" message once the limit is hit |

## 3. Look up stats (User Story 3)

1. Look up the short code created in step 1.1; expect `clickCount: 0`, no last-used time shown
   (or an explicit "never used" indication), and its expiration status.
2. Visit the redirect for that code a couple of times, then look it up again; expect the click
   count to reflect exactly that many visits.
3. Look up a code that was never created (or one that has since expired); expect a single, plain
   "no such link" message — verify it reads identically for a never-existed code and an expired
   one, matching the underlying API's indistinguishability guarantee.

## 4. Responsiveness

Resize the browser to a phone-sized viewport (or use a device emulator). Expect: no horizontal
scrolling, all text legible, every control comfortably tappable.

## 5. Copy fallback

In a context where the Clipboard API is unavailable (e.g. serving over plain HTTP from a
non-`localhost` address), verify the fallback (a selectable field) appears instead of a silent
failure.
