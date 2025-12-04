# NexoPolarBridge

A small Paper plugin that improves compatibility between **Nexo (Furniture)** and **Polar AntiCheat** (with packet block tracking enabled).

## Why this exists

Polar only caches packet-sent blocks for a limited time to avoid memory leaks.

Since Nexo furniture hitboxes are represented client-side via barrier block packets (not real server blocks),
Polar may “forget” about them, causing false positives / setbacks.

To fix this, NexoPolarBridge periodically re-sends the barrier hitbox packets to players, as suggested by Polar support.
