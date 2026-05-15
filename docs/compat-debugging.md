# Compatibility Debugging Guide

This guide explains the extra compatibility diagnostics for Folia, Bedrock/Geyser/Floodgate detection, teleport sync, keep-alive timing, and block-cache fallback behavior.

This branch intentionally does not replace Java Edition movement models. Java movement physics should stay tied to precise client/server behavior. Bedrock-specific movement tuning is kept behind Bedrock checks where exact client source behavior is not available.

## Enabling Console Detail

Detailed compatibility lines are opt-in because they can be noisy on live servers.

```yaml
logging:
  debug:
    to-console: true
  backend:
    console:
      active: true
```

The normal violation/debug settings still control whether a check has enough context to print.

## Reading A Detail Line

Most detail lines use the same shape:

```text
[NCP][CheckName][detail] player=name subcheck=SPECIFIC_BRANCH summary=short_reason{...} ... tags=...
```

Important fields:

| Field | Meaning |
| --- | --- |
| `subcheck` | The concrete branch behind the umbrella check name. |
| `summary` | Short human-readable diagnosis for quick scanning. |
| `tags` | Full diagnostic markers for deeper analysis. |
| `thread` / `async` | Whether packet data arrived off the primary/region thread. |
| `teleportQueue` | Current teleport/server-position synchronization state. |

## Teleport And Portal Sync

Folia and modern teleport plugins can use async teleports, portal callbacks, or direct server-position packets. In those cases, packet-level `NET_MOVING` may see the position jump before Bukkit movement history is synchronized.

Useful tags:

| Tag | Meaning |
| --- | --- |
| `server_position_jump_stale_packet_model` | `NET_MOVING` matched a stale pre-teleport packet to a recent server-side position jump. |
| `teleport_command_stale_packet_model` | A command such as `/home` or `/rtp` was recently issued, and an old packet still matched that command-position context. |
| `server_position_jump_recovery_grace` | One last-resort stale packet was consumed after a server-side jump when movement history was invalid. |

If teleport false positives remain, include the `NET_MOVING` detail line, the matching `[NCP][NetMoving][teleport]` line, and a few lines before/after the teleport command or portal event.

## Folia Block Cache Fallback

Folia region ownership can make direct block reads unsafe from packet or async paths. The Bukkit block cache now checks normal region ownership, retries with exact-location ownership when possible, and only then returns `AIR` or legacy data `0` as the final safe fallback.

Repeated fallback events are rate-limited and logged with resolved/final-fallback counts when console diagnostics are enabled.

## Other Check Summaries

| Check | Summary shape | What it helps identify |
| --- | --- | --- |
| FastBreak | `summary=block_timing{...}` | Block/tool timing, missing break time, configurable grace budget. |
| KeepAliveFrequency | `summary=keepalive_bucket{...}` | Bucket timing, duplicate/fast keep-alive replies, Folia async timing. |
| NetMoving | `summary=net_moving{...}` | Extreme packet movement versus teleport/server-position stale-packet contexts. |

## Bedrock Movement Compatibility

SurvivalFly keeps Java Edition movement on its original path. The Bedrock compatibility envelopes only run for players marked as Bedrock by Floodgate/Geyser detection or the Bedrock compatibility permission.

Current Bedrock-only tags:

| Tag | Meaning |
| --- | --- |
| `bedrock_partial_support_h` / `bedrock_partial_support_y` | Bedrock packet fit a bounded partial-support envelope near stairs, slabs, lanterns, carpets, layered snow, scaffolding, or similar support blocks. |
| `bedrock_climbable_h` / `bedrock_climbable_y` | Bedrock packet fit a bounded climbable/scaffolding envelope. |
| `bedrock_water_h` / `bedrock_water_y` | Bedrock packet fit a bounded water movement residual envelope. |
| `bedrock_h_model_miss` / `bedrock_y_model_miss` | Bedrock context was detected, but the movement still exceeded the current envelope. Include the full detail/violation line in reports. |

## Bug Reports

When reporting a false positive, include:

1. The full `[NCP][...][detail]` line, not only the short `[NC+] [VL]` line.
2. What the player was doing in plain terms, such as "Bedrock player running up stairs" or "RTP command."
3. Whether the player was Bedrock or Java.
4. The block or environment involved, especially stairs, slabs, lanterns, carpet, vines, scaffolding, water, boats, or portals.
5. A few lines before and after the flag if teleport, respawn, knockback, firework, or combat happened.
