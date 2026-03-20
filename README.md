# NexoPolarBridge

A small Paper plugin that improves compatibility between **Nexo (Furniture)** and **Polar AntiCheat** (with packet block tracking enabled).

## Setup

Packet block tracking must be active in the Polar configuration for proper compatibility. This is achieved by adding `TRACK_PACKET_BLOCKS` to the `developer_metadata` list in polar.yml. Note that `developer_mode` may remain disabled, as it functions independently of the metadata registry.

```yaml
# Do not touch unless otherwise instructed by Polar support.
developer_mode: false

# Do not touch unless otherwise instructed by Polar support.
developer_metadata:
  - "TRACK_PACKET_BLOCKS"
```

## Why this exists

Polar only caches packet-sent blocks for a limited time to avoid memory leaks.

Since Nexo furniture hitboxes are represented client-side via barrier block packets (not real server blocks),
Polar may “forget” about them, causing false positives / setbacks.

To fix this, NexoPolarBridge periodically re-sends the barrier hitbox packets to players, as suggested by Polar support.
