# Village Armories

The Village Armory is a physical, persistent ammunition store for the bounded
guard-resupply system. Version 1.2 establishes the block and its inventory.
The 1.3 Village Defense Logistics phases add the request, loaded-chunk index,
reservation, travel, and withdrawal layers separately.

## Crafting and storage

Craft one armory from six Iron Ingots, two Chests, and one Barrel:

```text
III
CBC
III
```

The armory contains nine fixed slots. Every slot accepts only TaCZ loose
ammunition or a TaCZ ammunition box. Guns, attachments, magazines represented
as unrelated items, and ordinary Minecraft items are rejected.

Breaking the block drops its stored ammunition and the armory block. Inventory
contents and squad-lock identity persist across chunk unload and server
restart. Comparator output follows inventory fullness from 0 through 15.

## Automation

The armory exposes the standard Forge `IItemHandler` capability on all six
sides. Hoppers, pipes, and storage integrations may insert compatible
ammunition or extract existing contents. The exposed capability is deliberately
non-modifiable: automation must use ordinary simulated/committed insertion and
extraction and cannot directly replace a slot with a non-ammunition item.

The block entity has no ticker. Storage changes, block breaks, menu use, and
ledger interactions are event driven. Ordinary inventory mutations persist and
update comparator output without broadcasting redundant block-entity packets;
only squad-lock changes synchronize lock state to clients.

## Public and squad-restricted access

New armories are public. A public armory is eligible to supply an unassigned or
assigned guard once bounded resupply is enabled.

To restrict an armory:

1. Bind a Squad Command Ledger to a registered squad.
2. Crouch-use that ledger on the armory.
3. Repeat with the same ledger to return the armory to public access.
4. Crouch-use a different valid bound ledger to reassign the restriction.

The lock affects guard resupply eligibility. It does not prevent players or
automation from stocking the armory. This keeps modpack logistics usable while
preventing guards from another squad from claiming the stored supply.

The world squad registry is authoritative. Blank, damaged, future-version, or
orphaned ledger bindings cannot assign a lock. Damaged or future-version lock
NBT fails closed for guard access and can be repaired by assigning a valid
bound ledger. The unreadable record is preserved through subsequent inventory
changes and saves, so it cannot silently turn into a public armory after a
restart.

## Current development boundary

Village Defense Logistics Step 6 activates the previously separated contracts.
Armories register and unregister from a transient, dimension-local section index
as their block entities load and unload. An otherwise-idle guard assesses its
bounded ammunition summary only on a stable staggered tick, then inspects at
most the configured number of indexed records and nine slots per returned
Armory. The query has no block/entity scan, persistent data, chunk ticket, or
`getChunk` path.

A successful selection owns one expiring reservation and the low-priority MOVE
goal only while travelling. Combat, squad commands, follow, Hold, patrol,
sidearm recovery, lifecycle changes, and unreadable native TaCZ actions retain
the established precedence. Final withdrawal revalidates the live weapon,
need, lock, source fingerprint, destination, distance, and feature switches
before the existing transactional transfer. `/vgg inspect` now reports
`runtime=REGISTERED` with the authoritative state, outcome, selected slot,
bounded work counts, transfer evidence, and retry time.
