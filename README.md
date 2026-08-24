# Montoya Payload Extractor

A Burp Suite extension (built on the Montoya API) for finding, remembering, editing, generating,
and replaying payloads/values in HTTP requests and responses — the
**Observe → Extract → Remember → Categorize → Modify → Generate → Replay → Analyze** workflow, all
from one tab.

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
