# NexusTerra v0.1.1 Patch Notes

## Fixed

- Fixed the `NullPointerException: Cannot invoke "Object.hashCode()" because "key" is null` failure seen with a 300-meter radius.
- The elevation sample grid now always contains the origin coordinate `(0,0)`.
- Elevation samples now extend to the next complete 8-block boundary, preventing missing samples during edge interpolation.
- Added a null-safe fallback for the origin elevation sample.

## Network reliability

- Added automatic failover from the primary Overpass API endpoint to a secondary endpoint.
- Increased Overpass request tolerance and improved HTTP status/error logging.
- Hardened Overpass JSON parsing against incomplete or malformed responses.
- If all Overpass endpoints are temporarily unavailable, NexusTerra now generates elevation-only terrain instead of crashing.

## Command and server safety

- Added validation for latitude, longitude, and minimum radius.
- Bukkit messages and block-placement task startup now return to the main server thread.
- The destination world is captured when the command begins, so moving worlds while data downloads cannot redirect generation.
- Full exception stack traces are now written to console to make the next patch easier to diagnose.

## Version

- Source/plugin version advanced from `0.1.0` to `0.1.1`.
