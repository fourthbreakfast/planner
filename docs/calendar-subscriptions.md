# Calendar subscriptions

Settings → calendar subscriptions accepts an HTTPS iCalendar feed or a `webcal://` / `webcals://` link. Webcal links use HTTPS. Add the same source URL used in [ICSx⁵](https://icsx5.bitfire.at/): ICSx⁵ is another iCalendar subscription client, rather than a separate file format. Planner does not read ICSx⁵'s Android calendar-provider database. Existing local `.ics` import remains an independent, editable one-time import.

Use a provider's private subscription link for a calendar requiring sign-in. Username/password URLs and plain HTTP are rejected. OAuth, Basic authentication, CalDAV, and two-way calendar writes are not implemented. The original calendar remains unchanged.

## Updates and offline behavior

Refresh is available next to each local source. Desktop refreshes due sources on launch and every 30 minutes while running, including in the tray. Android refreshes on opening and while visible, and schedules a separate network-connected WorkManager job about every 30 minutes when this device has subscription URLs. Android can delay background work to save battery. This job does not start Syncthing. Refresh passes prioritize untouched and oldest-attempted sources, so a bounded background run does not repeatedly favor the same feeds.

HTTP validators and the last valid feed are cached. A `304 Not Modified` still re-expands recurring events as the calendar window advances. Network errors, invalid feeds, unsupported features, and size limits retain the last good agenda and show a per-source error. A valid empty feed removes that source's previous events.

Only source identity/name/generation and materialized events enter Planner sync and backup encoding. Private URLs, validators, errors, and cached feed text remain in this device's database. Another paired device can see and remove the source, or subscribe to the same URL to refresh it independently. Removing a source removes its events throughout the synced planner. A source tombstone prevents an offline subscribed device from resurrecting it; subscribing again creates a newer generation.

Restoring a backup brings back missing subscribed events as editable snapshots, with a stable separate identity and no subscription provenance. It does not restore private feed URLs, restart subscriptions, or revive an unsubscribed source. Events already present under their original subscription identity are skipped, and importing the same backup again does not create duplicates. Subscribe to the source URL separately to resume automatic updates; restored snapshots remain independent of later refreshes.

## Identity and reconciliation

Source IDs derive from the normalized URL. Event IDs are scoped by source and iCalendar UID; recurring occurrences also include their original occurrence identity. Moving an ordinary event keeps its ID. Moving a recurrence through `RECURRENCE-ID` keeps the original occurrence ID. Fetch and parse finish before a database transaction updates changed events and deletes absent events. The transaction rechecks source tombstones so an unsubscribe received during a download wins before reconciliation commits. Importing a local `.ics` file never places its events under subscription management.

Absolute timed events retain their start/end instants. Their stored and synced day/time fields use UTC, so devices refreshing the same source in different time zones converge on the same values. The agenda and reminders project these events into each device's current zone; publisher and device DST changes remain independent. All-day and floating times keep their written calendar dates/times. Manual events retain their existing wall-clock behavior. Restoring an absolute subscribed event as an editable backup snapshot first converts it into the restoring device's local date/time, then clears the subscription instants.

If the operating-system time zone changes while an agenda view is already open, that view picks up the new zone when its events are collected again or change; reopening the agenda/app refreshes the projection. Android's time-zone receiver reads fresh events for reminders and widgets. Absolute reminder epochs remain tied to the source instant even before an already-open view redraws.

Subscribed events open a selectable, read-only detail view. Editing, dragging, completion, and individual deletion are blocked in both UI and core rules. Remove the source in settings to unsubscribe.

## Recurrence and limits

RFC 5545 recurrence is expanded with [dmfs lib-recur](https://github.com/dmfs/lib-recur), including common `BYDAY`, ordinal weekdays, `INTERVAL`, `COUNT`, `UNTIL`, `RDATE`, `EXDATE`, and cancelled/moved overrides. Publisher IANA time zones are retained through DST before conversion to the device's display zone. A `VTIMEZONE` alias using a recognized `X-LIC-LOCATION` is accepted. Unknown/custom time zones, `EXRULE`, `RDATE` periods, and `RECURRENCE-ID;RANGE=...` fail explicitly instead of partially replacing the agenda.

Recurring feeds materialize one year behind today and two years ahead, with UTC defining the shared window boundary; the settings screen discloses the window. Ordinary finite events are retained outside that rolling window. Each feed is bounded to 4 MiB decompressed, 2,000 components, 10,000 occurrences, and a three-second parse budget with cancellation checks. HTTP uses 15-second connect/read timeouts, an overall checked 45-second budget, and at most six requests. Redirects cannot downgrade HTTPS or introduce URL credentials, and cache validators do not cross origins. Feed expansion runs off the UI thread. The Android worker additionally has a two-minute total budget.

## Third-party code

`org.dmfs:lib-recur:0.17.1` and its dmfs datetime/functional dependencies are Apache-2.0 licensed. The bundled [Apache license](licenses/lib-recur-Apache-2.0.txt) is copied from the library source. No ICSx⁵ code is included. The calendar wire format follows [RFC 5545](https://www.rfc-editor.org/rfc/rfc5545).
