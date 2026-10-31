# GeyserDetector

Detects Bedrock players on a Velocity proxy and exposes them to backend plugins,
so any plugin on the Paper/Folia side can ask "is this player on Bedrock?" without
depending on Floodgate or Geyser internals.

```
                    ┌──────────────────────────┐
   Bedrock client ──▶│  Velocity proxy          │
                    │  ┌────────────────────┐  │
                    │  │ GeyserDetector     │  │  asks Geyser's API
                    │  │ (Velocity)         │  │  who is a Bedrock client
                    │  └─────────┬──────────┘  │
                    └────────────┼─────────────┘
                                 │  plugin channel
                                 │  geyserdetector:main
                    ┌────────────▼─────────────┐
                    │  Folia / Paper backend   │
                    │  ┌────────────────────┐  │
                    │  │ GeyserDetector     │  │  → static API
                    │  │ (Paper)            │  │  → Bukkit events
                    │  └────────────────────┘  │  → ServicesManager
                    └──────────────────────────┘
```

## Modules

| Module | Artifact | Runs on | Purpose |
|---|---|---|---|
| `geyserdetector-api` | `geyserdetector-api` | — | Shared types, events, wire protocol. Compile against this. |
| `geyserdetector-velocity` | `GeyserDetector-Velocity` | Velocity proxy | Detects Bedrock clients, relays them downstream. |
| `geyserdetector-paper` | `GeyserDetector-Paper` | Folia / Paper | Receives detection, publishes the API to other plugins. |

## Build

```bash
mvn clean install
```

Produces three jars. The API is installed into your local repository so other
projects can depend on it.

> The upstream APIs (`velocity-api`, `paper-api`) are compiled for a newer Java
> than the 17 we target, so `javac` prints `major version 69 is newer` warnings.
> These are harmless. To silence them, point `JAVA_HOME` at JDK 25 before building.

## Deploy

| Jar | Destination |
|---|---|
| `geyserdetector-velocity/target/GeyserDetector-Velocity-2.0.0.jar` | `0_proxy/plugins/` |
| `geyserdetector-paper/target/GeyserDetector-Paper-2.0.0.jar` | `2_smp/plugins/` |

Both servers must be restarted. The proxy plugin logs a warning if Geyser is not
installed, since detection cannot work without it.

## Using the API

Add the dependency with `provided` scope — the classes are already inside the
GeyserDetector plugin jar at runtime.

```xml
<dependency>
    <groupId>com.gxlydlyf</groupId>
    <artifactId>geyserdetector-api</artifactId>
    <version>2.0.0</version>
    <scope>provided</scope>
</dependency>
```

Declare GeyserDetector as a dependency in your `plugin.yml`:

```yaml
depend: [GeyserDetector]
```

### Path 1 — static facade

The common case. Never throws when the plugin is absent.

```java
if (GeyserDetectorApi.isBedrock(player)) {
    BedrockPlayer bp = GeyserDetectorApi.lookup(player).orElseThrow();
    player.sendMessage("Hello Bedrock player on " + bp.platformName() + " " + bp.version());
}
```

### Path 2 — Bukkit events

```java
@EventHandler
public void onDetected(BedrockPlayerDetectedEvent event) {
    BedrockPlayer bp = event.bedrockPlayer();
    getLogger().info(event.getPlayer().getName() + " is Bedrock, xuid=" + bp.xuid());
}

@EventHandler
public void onForgotten(BedrockPlayerForgottenEvent event) {
    // drop any per-player Bedrock state you were holding
}
```

Both events are **not cancellable** — detection reports a fact, it does not gate
anything.

### Path 3 — service registry

For plugins that prefer late binding over a compile-time reference:

```java
RegisteredServiceProvider<BedrockDetector> reg =
        Bukkit.getServicesManager().getRegistration(BedrockDetector.class);
BedrockDetector detector = reg == null ? null : reg.getProvider();
```

## Sending forms

A Bedrock player can be shown a native Cumulus form. The form itself is built on
your server and carried to the proxy as JSON, so both ends do not have to share a
Cumulus version — the one inside Geyser is used on the proxy side.

```java
CustomForm form = CustomForm.builder()
        .title("Login")
        .label("Please enter your password")
        .input("Password", "")
        .build();

GeyserDetectorApi.sendForm(player, form);
```

`SimpleForm` and `ModalForm` work the same way. `sendForm` returns `false` — rather
than throwing — when the player is not a Bedrock client, is not tracked, or no
proxy is connected.

Answers arrive at the listener you register:

```java
GeyserDetectorApi.registerResponseListener((uuid, json) -> {
    if (json == null) {
        // the player dismissed the form
        return;
    }
    // json holds the submitted values, e.g. ["hunter2"]
});
```

`json` is the raw payload Cumulus produced, indexed the same way the form's
components were declared. Registering replaces any previous listener; pass `null`
to clear it.

To dismiss a form programmatically:

```java
GeyserDetectorApi.closeForm(player);
```

The same three operations are on the `BedrockDetector` interface for consumers
that hold the service rather than calling the static facade, and
`FormSerializer.toJson(form)` is available when you want to build the payload
yourself.

**Consumers must declare Cumulus with `provided` scope**, because it appears in
the `sendForm` signature:

```xml
<dependency>
    <groupId>org.geysermc.cumulus</groupId>
    <artifactId>cumulus</artifactId>
    <version>1.1.2</version>
    <scope>provided</scope>
</dependency>
```

### Telling "not Bedrock" from "not detecting"

`isBedrock` returns `false` when nothing is supplying data. Check availability
when the distinction matters:

```java
if (GeyserDetectorApi.available()) {
    // false genuinely means Java edition
} else {
    // detection is not running; nothing is known
}
```

`GeyserDetectorApi.source()` returns `PROXY` or `NONE`.

## Available data

| Field | Notes |
|---|---|
| `uniqueId()` | Java UUID — identical on proxy and backend under modern forwarding |
| `username()` | Java username the player connected with |
| `platform()` | `BedrockPlatform` enum; `displayName()` for a readable label |
| `version()` | Bedrock client version, e.g. `1.21.44` |
| `xuid()` | Xbox Live id; `hasXuid()` when you need to test for it |

Platforms: Android, iOS, macOS, Amazon, Gear VR, HoloLens, Windows (UWP),
Windows, Dedicated, tvOS, PlayStation 4, Nintendo Switch, Xbox, Windows Phone,
Linux. Unrecognised values degrade to `UNKNOWN` rather than throwing, so a Geyser
update cannot break detection.

## Commands and placeholders

```
/geyserdetector status         detection state and last contact
/geyserdetector list           tracked Bedrock players
/geyserdetector info <player>  platform / version / xuid for one player
```

Permission: `geyserdetector.command` (default op). Aliases: `/gdet`, `/gd`.

Placeholders (requires PlaceholderAPI):

```
%geyserdetector_bedrock%    true / false
%geyserdetector_platform%   Android, iOS, Windows, ...
%geyserdetector_version%    Bedrock client version
%geyserdetector_xuid%       Xbox Live id
```

## How it stays correct

**Sync on startup.** The backend asks the proxy for a full picture on enable, and
the proxy replies with begin/add…/end. Any player the batch did not list is
dropped, so a backend restart or proxy reload reconciles instead of going stale.

**Quit is the backstop.** A proxy that dies without disconnecting its players
would leave entries behind, so `PlayerQuitEvent` also clears the entry.

**Folia-safe.** Every scheduler call goes through the region-aware schedulers
(`getGlobalRegionScheduler`, `getAsyncScheduler`, `Entity.getScheduler`).
`Bukkit.getScheduler()` is never called, which is what makes the backend work on
Folia. `plugin.yml` declares `folia-supported: true`.

**Channel work is off-thread.** The channel callback runs on a network thread, so
it only touches the concurrent registry; event dispatch is handed to the player's
own region thread.

**Client brand is not trusted.** A Java client can claim to be "Geyser", so the
brand is only logged (to surface a misconfigured setup) and never used as the
detection source. Geyser's own API is authoritative.

## Wire protocol

Channel `geyserdetector:main`, versioned binary frames:

```
byte 0   protocol version (currently 1)
byte 1   message type
...      type-specific body
```

| Type | Direction | Body |
|---|---|---|
| `HELLO` | proxy → backend | — |
| `SYNC_REQUEST` | backend → proxy | — |
| `SYNC_BEGIN` | proxy → backend | — |
| `SYNC_END` | proxy → backend | — |
| `ADD` | proxy → backend | uuid, username, platform, version, xuid |
| `REMOVE` | proxy → backend | uuid |
| `SEND_FORM` | backend → proxy | uuid, form JSON |
| `FORM_RESPONSE` | proxy → backend | uuid, response JSON (null when dismissed) |
| `CLOSE_FORM` | backend → proxy | uuid |

Both sides share `BedrockCodec` from the API module, so the two cannot drift
apart. Frames carry a version byte, and a mismatch is reported rather than
silently misparsed. Strings are length-prefixed and capped, so a hostile frame
cannot force a large allocation.
