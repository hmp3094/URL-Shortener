# Feature Specification: Web UI for Shortening and Stats Lookup

**Feature Branch**: `feature/web-ui`

**Created**: 2026-09-06

**Status**: Draft

**Input**: User description: "Add a simple, sleek web UI for the URL shortener: a single page served by the existing Spring Boot app that lets a visitor (1) shorten a long URL via a form (long URL, optional custom alias, optional expiration), and see the resulting short link with a one-click copy button and clear inline error messages (invalid URL, alias taken, URL already shortened, rate limited); and (2) look up an existing short code's stats (click count, created, last accessed, expires). No accounts, no link history/dashboard, no new backend capability — purely a front end for the API that already exists. This is a wholly new capability for the project (nothing like it exists today) — tracked as a greenfield scenario."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Shorten a link from a web page (Priority: P1)

A visitor with a long URL opens the site, types or pastes it into a form, and gets back a short link they can immediately copy and share — without knowing the API exists, reading documentation, or using a command line.

**Why this priority**: This is the entire reason the UI exists. Without it, the feature delivers no value on its own.

**Independent Test**: Open the page, submit a valid URL with no alias or expiration, and verify a short link appears and can be copied in one action.

**Acceptance Scenarios**:

1. **Given** the page is open, **When** a visitor submits a well-formed URL with no alias and no expiration, **Then** a short link appears on the page, ready to copy in one click.
2. **Given** a visitor wants a memorable link, **When** they submit a URL together with a custom alias that's available, **Then** the resulting short link uses exactly that alias.
3. **Given** a visitor wants a link that stops working after a while, **When** they submit a URL together with an expiration choice, **Then** the resulting short link is presented the same way as one with no expiration (the page doesn't need to dwell on it further).
4. **Given** a successful result is showing, **When** the visitor clicks the copy action, **Then** the short link is copied and the visitor gets a clear, brief confirmation that it worked.

---

### User Story 2 - Understand why shortening didn't work (Priority: P1)

A visitor submits something the system won't accept — a malformed URL, an alias someone else already has, a URL that's already been shortened, or too many attempts in a short time — and needs to understand what went wrong well enough to fix it or decide to stop.

**Why this priority**: Equal priority to User Story 1: a shortening flow that fails silently or with a raw technical error is not meaningfully different from not having a UI at all, per this project's standing principle of never leaving a failure unexplained.

**Independent Test**: Trigger each known failure case (bad URL, taken alias, duplicate URL with an alias, too many requests) and verify each produces a distinct, plain-language explanation on the page itself — no browser dialogs, no raw error payloads.

**Acceptance Scenarios**:

1. **Given** a visitor submits an empty, malformed, or unsafe URL, **When** the submission is rejected, **Then** the page explains that the URL isn't valid, without technical jargon.
2. **Given** a visitor submits an alias that's already in use, **When** the submission is rejected, **Then** the page tells them that specific alias is taken and invites them to try another.
3. **Given** a visitor submits a URL that already has a short link and also asked for a specific alias, **When** the submission is rejected, **Then** the page explains the URL is already shortened, distinctly from the "alias taken" case.
4. **Given** a visitor has submitted several requests in quick succession, **When** the system's request limit is hit, **Then** the page explains that they're going too fast and to try again shortly, rather than showing a generic failure.

---

### User Story 3 - Check how a link has performed (Priority: P2)

Anyone who has a short code — whether they created it moments ago or someone shared it with them — can look up how many times it's been used, when it was created, and whether it's still active, from the same page used to create links.

**Why this priority**: Valuable and already fully supported by the existing API, but secondary to the core shorten-and-share flow — a visitor can get full value from User Stories 1–2 without ever using this.

**Independent Test**: Look up a short code that was just created and verify its click count, creation time, and expiration status are shown; look up a code that doesn't exist (or has expired) and verify a clear "not found" message appears.

**Acceptance Scenarios**:

1. **Given** a short code that exists and is still active, **When** a visitor looks it up, **Then** the page shows its click count, when it was created, when it was last used (or that it hasn't been used yet), and whether/when it expires.
2. **Given** a short code that doesn't exist or has expired, **When** a visitor looks it up, **Then** the page shows a clear "no such link" message — the same message either way, since the system doesn't distinguish the two internally.

---

### Edge Cases

- Submitting the shortening form with the URL field empty must be caught before or instead of a round trip that comes back as an error.
- A URL near the system's maximum accepted length must not break the page's layout.
- Looking up a short code using a code that's well-formed but simply unused must behave identically to looking up one that never existed (both are "not found" to a visitor with no way to tell them apart, matching the underlying API).
- The copy action must still let the visitor get the link some way (e.g., a selectable field) if the one-click copy mechanism isn't available in their browser/context.
- The page must remain usable — readable, with tappable controls — on a phone-sized screen, not just a desktop window.
- A visitor who shortens a link and then immediately looks up its stats must see zero clicks and no last-used time, not an error.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a single web page where a visitor can submit a long URL to be shortened.
- **FR-002**: The system MUST let a visitor optionally specify a custom alias when shortening a URL.
- **FR-003**: The system MUST let a visitor optionally specify how long the resulting link should remain active.
- **FR-004**: Upon successfully shortening a URL, the system MUST display the resulting short link and let the visitor copy it to their clipboard in a single action, with a clear confirmation that the copy succeeded.
- **FR-005**: When shortening fails, the system MUST display a plain-language explanation distinguishing at least these cases: the URL itself is invalid or unsafe, the requested alias is already taken, the requested alias is malformed or reserved, the URL already has a short link under a different code, and too many requests have been made recently.
- **FR-006**: The system MUST let a visitor look up an existing short code and see its click count, creation time, last-used time (or an indication it has never been used), and expiration status.
- **FR-007**: When a looked-up short code does not exist or has expired, the system MUST display a single, non-technical "not found" message — the same message for both cases.
- **FR-008**: The page MUST remain fully usable (readable text, reachable and tappable controls, no horizontal scrolling) on both desktop and phone-sized screens.
- **FR-009**: The page MUST NOT require the visitor to create an account, log in, or provide any identifying information.
- **FR-010**: The page MUST NOT offer any capability beyond shortening a link and looking up its stats — no editing, no deletion, no history or dashboard of past links.

### Key Entities

- **Shortened Link Result**: What the page shows after a successful shortening — the short link itself, and (implicitly) the destination URL and expiration the visitor just chose. Not persisted by the page itself; the page has no memory between visits.
- **Link Stats**: What the page shows after a lookup — click count, creation time, last-used time, expiration status. Sourced entirely from the existing API; the page adds no new data of its own.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A first-time visitor can shorten a URL and copy the result without consulting any instructions or documentation.
- **SC-002**: Every rejected submission tells the visitor, in plain language, specifically why — not one visitor is left staring at a failure with no explanation.
- **SC-003**: The page is fully usable on a phone-sized screen: no horizontal scrolling, no controls too small to tap accurately.
- **SC-004**: A visitor can find out how a link has performed without leaving the page they used to create it, and without needing to know it's calling a separate "API."

## Assumptions

- **Tracked as a greenfield scenario**: nothing comparable (a served UI, static assets, any non-API-consumer-facing surface) exists in this project today; this introduces a new kind of artifact into the codebase rather than extending an existing one, as already discussed and agreed before this spec was written.
- No accounts, sessions, or persistence of any kind are introduced by this feature — every piece of data shown on the page comes from a live call to the existing API at the moment it's needed, per the feature description's explicit scope.
- The expiration choice is presented as a small set of friendly options (e.g., an hour, a day, a week, a month, or never) rather than a raw numeric field, since asking a visitor to think in seconds would fail the "shouldn't look amateurish" bar this feature was explicitly asked to clear. The chosen option is translated to whatever the API expects behind the scenes.
- The stats lookup is open to anyone who has a short code, with no additional restriction beyond what the underlying API already allows — the constitution notes that access control on analytics is only asserted once an ownership model exists, and this project has none.
- A visitor can shorten multiple links in one visit; the page does not keep or display a list of what they've previously shortened in that session.
- The one-click copy confirmation and the not-found messaging are visual/textual UX details left to implementation, not specified precisely here beyond "clear" and "plain-language."
