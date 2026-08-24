# Montoya Payload Extractor

A Burp Suite extension (built on the Montoya API) for finding, remembering, editing, generating,
and replaying payloads/values in HTTP requests and responses — the
**Observe → Extract → Remember → Categorize → Modify → Generate → Replay → Analyze** workflow, all
from one tab.

## What's new in v1.8.1

**Bug fix, from direct feedback: "when intercept is on, even after forwarding all requests, the web
page keeps loading."**

- **Root cause**: with Intercept ON and no "Break On" conditions configured (the out-of-the-box
  default), *every single request got held* - including every CSS/JS/image/font/font-map a page
  loads. A real page load fires dozens of these, so new held items kept arriving faster than they
  could be clicked through - the pending queue would empty out for a moment and immediately refill,
  making the page look like it never finishes no matter how much forwarding you do. This mirrors why
  real Burp Suite ships a default filter excluding static content from its own Proxy interception
  rules - this extension had no equivalent.
- **Fix: new "Skip static assets" checkbox** (Intercept bar, on by default). While enabled and no
  explicit Break On condition is configured, requests/responses for common static-asset extensions
  (js, css, images, fonts, media, sourcemaps) are auto-forwarded without ever being held, so the
  pending queue only fills with things actually worth looking at - HTML, API/XHR calls, forms, and
  so on. The moment you add any Break On condition of your own, this skip steps aside completely and
  defers entirely to your configured rules (so a rule deliberately targeting a `.js` file still
  works).
- **Extra safety net**: history trimming (once the 2000-row cap is hit) could previously evict the
  oldest row even if it was still WAITING on a Forward/Drop decision, abandoning its held network
  thread forever - the same class of bug fixed twice in v1.8.0 for Clear/Delete Row. Now closed here
  too: a still-pending row is never silently evicted, no matter how old.

## What's new in v1.8.0

**Intercept UX fixes, from direct feedback: the tab now behaves like a real pending-action queue,
not a full traffic log.**

- **Intercept's message list now shows pending messages only.** The moment a message is forwarded
  or dropped, it disappears from the list on its own - the same behavior as Burp's own Proxy
  Intercept tab. Everything still gets logged internally (Track Value and other features still see
  the full history) - this list just stops showing you things there's nothing left to decide on.
- **New "Only in scope" filter checkbox.** Turning it on hides out-of-scope messages from this list;
  out-of-scope messages already held elsewhere are completely unaffected - still tracked, still
  forward/drop-able if you clear the filter - this only changes what's *displayed*.
- **Forward now auto-advances to the next pending message.** Click Forward, Forward & Edit, or Drop
  and the selection jumps straight to the next still-waiting request or response, so working
  through a queue of held traffic doesn't require re-clicking the list every time.
- **Forward/Forward & Edit buttons now show a direction arrow** - "→ Forward" while holding a
  request (outbound, on its way to the server) and "← Forward" while holding a response (inbound,
  on its way back to the client) - so it's obvious at a glance which leg of the round trip you're
  looking at.
- **Safety fix uncovered while making this change**: clearing the list (or deleting a single pending
  row) could previously abandon a still-held message's decision forever, leaving the real underlying
  network request permanently stuck. Both actions now forward any still-pending, non-pinned message
  as-is first - the same safety net the existing "Forward All" button already used - so nothing gets
  silently left hanging.

## What's new in v1.7.0

**Phase 3 of the interception/analysis-platform spec: Response Analysis (item 7) and the Replay
visual/functional overhaul (item 8).** Replay was "too black-and-white" and couldn't show you the
actual request/response of any step it sent - both fixed by reusing what already existed rather
than inventing new plumbing.

- **Response Analysis**: a new `ResponseDiff` compares any two responses - status, response size
  (flagged only above a real relative threshold, not on rounding noise), added/removed/changed
  headers, and a full JSON structural diff (added/removed/changed fields by path, via the same
  JSON engine the Workbench already uses for body editing). Every replay step is automatically
  diffed against the run's first step as a baseline, and the results table gets a **Flag** column -
  a colored ⚑ plus a one-line summary - whenever a step looks different enough to be worth a second
  look. This is a heuristic, not a verdict: it never claims a vulnerability, only "worth a second
  look" - the actual judgment stays with you. A live **Response size: n / avg / min / max** line
  above the results table gives the size-history tracking the spec asked for.
- **Compare Responses**: select any two rows in a replay run and open a full side-by-side view -
  two real (reused) Montoya response editors plus the structured diff summary above them.
- **Replay finally shows you the traffic**: selecting a result row now displays that step's actual
  request and response in reused, read-only Montoya editors right below the table - previously
  Replay had no way to inspect a step's real request/response at all, just the summary row.
- **Visual consistency, not a new visual language**: HTTP methods and status codes now get the same
  colored-bold treatment everywhere a table shows them - Replay's results, Intercept's REQUEST
  HISTORY, and the History tab - via one shared `StyleKit`, not three different color schemes.
- **New results-table actions** (right where the traffic already is, item 20's "consistent context
  menu" applied to Replay): Compare Selected, Generate Variations From Selected, Send to Workbench,
  Send to Intercept (adds the row to Intercept's own REQUEST HISTORY for further tagging/pinning
  there), Repeat (also covers "Clone" - a fresh resend of the same request), Pin/Unpin, Add Note/Tag,
  Delete, and Export CSV.

## What's new in v1.6.0

**Phase 2 of the interception/analysis-platform spec: Variables & Value Tracking (item 5) and
Request Mutation/Variations (item 6).** Built entirely on what v1.5.0 shipped and what already
existed - no second HTTP-sending engine, no second generator system.

- **Variables**: extract any value - a Workbench field's current value, a response field, or any
  selected text in Intercept's request/response editors - as a named `{{VARIABLE}}` via a new
  "Var" / "Extract Var" / "Extract Selected as Variable" button next to the existing per-field and
  per-selection actions. Type `{{USER_ID}}` into any later request's path, header, cookie, or body
  (Workbench's Modified Request, a Replay run, or Intercept's Forward & Edit) and it resolves to the
  stored value right before the request is actually sent - the placeholder text stays visible
  everywhere until then, so nothing is silently rewritten mid-edit. `{{UUID}}`, `{{TIMESTAMP}}`, and
  `{{RANDOM}}` are always dynamic (generated fresh on every use) and never need to be extracted by
  hand. A new **Variables** tab lists everything extracted so far, with Add/Edit/Delete and a
  **Track Value** search across History, Intercept history, and Payload Collections for every place
  a value has been seen.
- **Five new built-in generators** - Timestamp, Hex, Base64, Email, Phone - added to the existing
  generator registry alongside Sequential/Random Integer, UUID, Random String, Custom Pattern,
  Regex, Wordlist, and Custom Script, so they show up for free everywhere a generator kind is picked
  (the Workbench's "Gen" button, Replay's payload source, and the new Variables tab's "Generate
  Value").
- **Generate Variations**: Replay's dialog (opened via each field's "Play▶" button) gained a third
  payload-value source, "Smart variations of current value" - boundary/neighbor values derived from
  the field's current value (e.g. `userId=555` → `556, 554, 0, -1, ""`), fed straight into the
  existing Replay engine and its results table for the send-and-compare view (status, response size,
  timing per variation) - no new send/compare engine, no new UI beyond one radio button.
- `{{VARIABLE}}` resolution and variation generation are both plain, deterministic substitutions -
  neither one claims or flags anything as a vulnerability; that judgment stays with the analyst
  reading the comparison table.

## What's new in v1.5.0

**Phase 1 of a large "turn this into an interception/analysis platform" spec.** This release adds a
genuine Intercept tab and an Automatic Modification Engine, built on real Montoya HTTP APIs (not
simulated UI clicks), plus a consistency pass on response-size display. It intentionally does **not**
attempt the full spec in one release — see "Deferred to a future phase" below for what's next and why.

- **New Intercept tab** (first tab, before Workbench): a master ON/OFF switch with independent
  **Intercept Requests** / **Intercept Responses** checkboxes, a sortable/filterable **request
  history table** (ID, Host, Path, Method, Status, Response Size, Time, Timestamp), search/filter by
  method/status/pinned, pin/tag/note per row, and a 50/50 request/response editor pair that **reuses
  the existing Workbench editor components** (`createHttpRequestEditor`/`createHttpResponseEditor`).
- **Real interception**, not a UI simulation: `InterceptEngine implements HttpHandler` and blocks the
  calling Burp network thread on a `CompletableFuture` until you act, so held traffic is genuinely
  paused. Actions: **Forward**, **Forward & Edit**, **Drop** (request-phase only — Montoya's response
  action has no drop), **Send to Replay**, **Send to Workbench**, **Clear**. The existing passive
  payload-learning listener is registered before the new intercept handler, so learning traffic is
  never starved or blocked by an intercept hold.
- **Conditional interception / "Break On" rules** (`Rules...` dialog): match by host/path/method,
  status code (`500`, `>399`, `<300`, ...), header name/value, cookie name, parameter name, body
  contains (plain or regex), response size threshold, or "new endpoint"/"new parameter" - with
  one-click presets for the common cases (Status=500, Status=403, response contains "admin", request
  contains "userId", new endpoint, new parameter, response size > 100 KB). An empty rule list means
  intercept everything (subject to the master/direction checkboxes).
- **Automatic Modification Engine** (`Rules...` under "Automatic Editor"): an ordered, individually
  enable/disable-able list of deterministic find/replace rules (e.g. `555 -> 666`), each scoped by
  request/response direction, message location (Path, Query, Headers, Cookies, JSON Body, Form Data,
  Raw Body, Response Headers, Response Body, or Anywhere), host/path glob scope, and optional regex -
  with a live preview in the editor and **"Create Rule from Selection"** (right-click selected text in
  either editor to seed a new rule from it).
- **Response-size consistency pass**: the same human-readable formatter (`512 B` / `1.4 KB` / `1.2
  MB`) now backs Intercept's history table, the Replay results table, History, and History's detail
  view - not just one screen.
- Everything new here persists across Burp restarts using the same JSON-in-`PersistedObject` pattern
  already used for AI settings and the scope filter - no new persistence mechanism was introduced.

**Deferred to a future phase** (from the same spec, not implemented in v1.5.0): Variables & value
tracking (`{{USER_ID}}`, generators), request mutation/variation testing, response diffing/analysis,
a full visual overhaul of Replay to match Workbench styling, Identity Profiles + multi-identity
replay, an Authorization Matrix, an Application Mapper, request relationship/timeline view,
multi-step Workflow replay, business-logic/state tracking, broader traffic management (tags/favorites/
hide-static-assets), security header/cookie change monitoring, race-condition testing, response
override presets, and a global "Send To" context menu everywhere. These are substantial features in
their own right (collectively comparable to Burp's own Proxy + Intruder + Repeater), and are best
tackled as their own follow-up passes rather than one release.

## What's new in v1.4.0

A redesign pass driven directly by screenshots of an earlier, more polished build, plus a real bug
fix and several spec gaps closed:

- **"Remember All" no longer creates duplicates.** `PayloadCollection.add()` is now dedupe-aware:
  adding a value that already exists in the collection (exact string match) refreshes its
  captured-time/origin-host instead of appending a new row. Loading the same request four times in a
  row now produces one remembered value, not four.
- **Auto-remember on load.** Every request opened in the Workbench now automatically remembers its
  non-blank field values (request AND response) - gated by a new **Settings -> General ->
  "Passively learn payloads..."** master switch (on by default) and the existing scope include/exclude
  patterns. The old "Remember All Values" button is still there for an explicit manual re-trigger.
- **Workbench layout reordered to match the reference design**, without changing the underlying
  concept: a horizontal top area (**Original Request** on the left; **Modified Request** and its
  **Response** on the right, in tabs for "Original Response" vs. "Response (after Send)") sits above
  a full-width, single-line-per-field **Detected Payloads** list - so every field's action buttons
  are always visible without the horizontal scrolling the previous taller boxes needed.
- **New compact field row.** Each row now shows a small colored location badge, a location label
  (e.g. "Cookie", "JSON Body → user", "Header (response)"), the bold field name, an **editable
  dropdown** for the value - pre-populated from that field's remembered collection, country-picker
  style, so picking an old value is one click and typing a new one still works exactly like the old
  text field did - a ★ favorite toggle, Gen/Play▶/Dup buttons, and explicit ▲▼ move buttons (in
  addition to the existing drag grip).
- **New toolbar buttons**: **Re-scan Modified Request for Payloads** (re-detects fields from
  whatever you've typed directly into the Modified Request editor) and **Send Modified Request**
  (actually sends the composed request via Burp's `Http.sendRequest`, shows the response in a new
  "Response (after Send)" tab, records a History entry with the real host + status code, and learns
  any interesting values found in that response too - closing the request+response-awareness loop
  end to end) plus **Reset to Original**.
- **New top bar**: "Show only in-scope items" + a "Target: [host]" dropdown, backed by Burp's real
  `Scope.isInScope(...)` - a view-only filter that hides out-of-scope rows in Payload Collections and
  History without ever deleting anything. There's no separate "Scope" tab any more; the master
  passive-learning switch and the (now advanced/optional) host include/exclude patterns live in
  Settings.
- **Tabs renamed**: "Collections" → **Payload Collections**, "AI Suggestions" → **AI Assistant**.
- **Payload Collections redesign**: search bar; Add Value / Delete Selected / Toggle Favorite over a
  Value | Source | Host | Favorite | Created table; per-collection Rename / Merge Into / Delete
  Collection / Clear Values / Deduplicate / Import Values / Export Collection; and New Collection /
  Import Whole Database / Export Whole Database at the bottom (moved here from Settings).
- **History redesign**: **#, Time, Host, Parameter, Original, Test Value, Status** columns (every
  replay step and every Send now records the real target host and HTTP status code), plus an Export
  History (CSV) button.
- **AI Assistant redesign**: Full request / Full response checkboxes, a Focus Parameter dropdown
  (populated from the Workbench's current fields), an "Include sensitive headers/values" checkbox
  (headers like `Authorization`/`Cookie`/`X-Api-Key` and bearer tokens are redacted unless this is
  explicitly checked - defaulting from Settings but never sent silently), a free-form prompt box, a
  split DeepSeek-response / suggested-values view, and Add To [collection] / Add Selected / Add All.
- **Settings redesign**: **DeepSeek AI Configuration** (endpoint, key, model, timeout, temperature,
  max tokens, "send sensitive by default") and **General** (the passive-learning switch, a
  persistence note about Burp project files / Community Edition, and an "Advanced" link to the host
  pattern editor) - database import/export moved to Payload Collections, and **Test Connection** now
  actually calls `DeepSeekClient.testConnection` to verify the endpoint/key work.
- **Closed a spec gap**: a new **Type** button on every field row lets you manually tell the
  extension what kind of value a field holds (`PayloadCategory` - Auth Token, OTP, Password, ...),
  reassigning both the field and its backing collection - this was called for by the original spec's
  "manual: tell the extension this value belongs to type X" but had no UI until now.

## What's new in v1.3.0

v1.2.0 added Add / Duplicate / drag-reorder / an "X" remove button to the Workbench, but reorder
only moved the on-screen boxes — it never touched the actual request. v1.3.0 fixes that:

- **Add / Duplicate now really appear in the request.** A field you add or duplicate is validated
  against a trial composition before it's ever shown to you (so a bad JSON path or bad parameter
  name is rejected immediately, not silently broken), and once added it shows up for real in the
  **Modified Request** editor — as a genuine JSON key, a genuine `Cookie:` header pair, or a genuine
  HTTP header/parameter, not just a box in the sidebar.
- **Drag-to-reorder changes the real order.** For JSON body fields and cookies, dragging a field's
  grip (or using the boxes' order) changes the actual key order in the composed JSON body, or the
  actual pair order in the composed `Cookie:` header. This is a real limitation of the Montoya API,
  not a shortcut we took: Burp's extension API has no "insert this header/parameter at position N"
  method, only add/update/remove — so genuine wire-order reorder is only possible for the two field
  types the extension fully controls the serialization of (JSON bodies, via our own JSON engine;
  cookies, by rebuilding the `Cookie:` header ourselves). Headers and URL/form/multipart parameters
  still get **real add and real remove**, they just keep whatever order Burp itself assigns newly
  added entries (appended) and preserves for existing ones — their drag handle is dimmed with a
  tooltip explaining why.
- **The "X" button really removes the field**, from the Workbench *and* from the composed JSON
  body key / Cookie pair / header / URL-form-multipart parameter — not just from the sidebar list.

All of this is driven by `RequestModifier.buildComposedRequest`, which always rebuilds from the
pristine original request plus the current field list (never from a previously-modified request),
so edits can never drift or compound.

## Feature history

- **v1.1.0** — Payload generators gained a `length` option (OTP-style: e.g. 6 digits, zero-padded,
  sequential from 0 or fully random within `[0, 10^length-1]`), a free-form Custom Script generator,
  and Replay gained parallel request sending with a configurable concurrency level.
- **v1.2.0** — Workbench field boxes: Add, Duplicate, drag/move-up/move-down to reorder; Replay
  gained a "load payload values from a file" (wordlist) source.
- **v1.3.0** — see above: all of the above now apply for real to the composed request, not just the
  UI.

## Architecture

```
util/        Ids, JsonNode (dependency-free JSON DOM with path-based add/remove/replace/reorder)
parser/      FieldLocation, MessageDirection, ParsedField, HttpMessageParser (structural extraction)
detector/    NameNormalizer, InterestingKeyMatcher, PayloadCategory, PayloadDetector (naming + categorization)
db/          PayloadSource/Value/Collection/Database, PersistenceManager, ImportExportManager, ScopeFilter
modifier/    RequestModifier — the piece that turns a field list into a real HttpRequest
replay/      ReplayEngine (sequential/parallel, pause/resume/stop, stop-on-status), ReplayConfig
generator/   8 payload generators (sequential/random integer, UUID, random string, custom pattern,
             regex, wordlist, custom script) behind a common PayloadGenerator interface
script/      ScriptEngineManager + MiniScriptEngine — see "Custom Script" below
history/     HistoryEntry, HistoryManager — audit log of every substitution/add/remove/reorder
ai/          AiSettings, DeepSeekClient, AiRequestBuilder, AiSuggestionParser — DeepSeek integration
integration/ ContextMenuProvider ("Send to Payload Extractor"), PassiveTrafficListener (auto-remember)
ui/          Dialogs: GeneratorDialog, ReplayConfigDialog, AddFieldDialog, HistoryDetailDialog,
             ScopeFilterBar, SettingsPanel
ui/panels/   MainPanel, WorkbenchPanel, PayloadFieldComponent, CollectionsPanel, HistoryPanel,
             AiPanel, FieldReorderUtil, WorkbenchHooks
ExtensionState.java            Single shared state object (one PayloadDatabase, one ScopeFilter, ...)
PayloadExtractorExtension.java Entry point (implements BurpExtension)
```

### Why a hand-rolled JSON parser?

No external JSON library is bundled (this project has zero runtime dependencies beyond the Montoya
API itself, which Burp provides), both to keep the extension jar small and because it gives full
control over key **order** — a `LinkedHashMap`-backed DOM that preserves parse order is what makes
real JSON-key reordering possible at all.

### Custom Script generator

The JDK has shipped no JavaScript engine by default since Nashorn's removal in Java 15 (confirmed:
`ScriptEngineManager().getEngineFactories()` returns empty on modern JDKs), and most current Burp
installations run on a similarly modern bundled JRE. Rather than pretend "Custom Script" means real
JavaScript and have it silently fail, `ScriptEngineManager` tries a JSR-223 JavaScript engine first
(so it *will* use real JS if one happens to be on the classpath — e.g. `nashorn-core` added by
hand) and transparently falls back to `MiniScriptEngine`, a small dependency-free scripting
language, when none is found. Supported syntax:

```
otp = pad(randInt(0, 999999), 6)
"OTP-" + otp + "-" + index
```

Statements separated by `;` or newlines; the last one's value is the generated payload. Built-in
variables: `index`, `count`. Built-in functions: `pad(v,len)`, `upper(s)`, `lower(s)`, `len(s)`,
`substr(s,a,b)`, `randInt(min,max)`, `randStr(len)`, `concat(a,b,...)`, `replace(s,from,to)`,
`now()`.

## Building

Requires the Montoya API on the classpath (Maven will pull it from Maven Central automatically —
this build environment had Maven Central blocked, so the jar shipped alongside this README was
compiled directly with `javac` against a locally-built copy of the Montoya API sources instead of
via `mvn package`; either approach produces the same classes):

```
mvn clean package
```

produces `target/montoya-payload-extractor-1.4.0.jar`. Load it in Burp via
**Extensions → Installed → Add → Extension type: Java → Select file**.

## Testing

`src/test/java` has JUnit 5 tests for the JSON engine, name normalization, the payload generators,
the payload database, the field-reorder algorithm, and — the two most important ones —
`RequestModifierTest` and `ReplayEngineTest`. Both use a JDK dynamic `Proxy`-based fake
(`testutil/FakeHttpRequest`, `testutil/FakeHttp`) implementing just the Montoya interface methods
actually exercised, so the whole suite runs without a live Burp instance:

```
mvn test
```

`RequestModifierTest` is the direct regression test for the v1.3.0 fix: it asserts that after an
add + a remove + a reorder + a value edit, the **actual composed request body/header** — not the
Workbench's field list — reflects every one of those changes, in the right order, with the removed
field genuinely gone.

## Notes on the DeepSeek AI integration

Configure an API key in the **Settings** tab to enable AI-suggested payload values (via the **AI
Suggestions** tab or a field's Generate flow). The extension calls DeepSeek directly over
`java.net.http.HttpClient` — this is a call the extension itself makes to an external service, kept
deliberately separate from Burp's own request-sending machinery used for target traffic.
