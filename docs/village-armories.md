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

The armory remains a complete player- and automation-facing storage block, but
guards do not search for or withdraw from it in normal gameplay yet. Village
Defense Logistics Step 1 defines inactive request/state contracts, Step 3 adds
expiring reservations and transactional withdrawal, Step 4 adds bounded,
interruptible travel for an explicitly reserved target, and Step 5 adds the
schema-9 Fzzy Config policy plus truthful on-demand diagnostics. Step 2's
loaded-chunk runtime index has not yet been implemented, so none of those
dormant services is wired into a guard tick. `/vgg inspect` reports that state
as `NOT_REGISTERED` instead of implying a search occurred. Keeping selection,
ownership, travel, item movement, and inspection separate makes it possible to
test their safety without introducing background AI work prematurely.
