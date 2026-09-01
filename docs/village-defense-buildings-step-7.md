# Village Defense Buildings Step 7: public village Armories

Step 7 activates the Armory/Quartermaster building as a physical ammunition
resupply point. Each newly generated Armory shell may replace its existing
functional anchor with one public VGG Armory block and fill it once from the
bounded `villagerguardgunners:chests/village_armory` loot table. Barracks,
Sentry Posts, integrated Watchtowers, and the passive Training Yard receive no
new behavior in this step.

## Placement and initial stock

The Armory is placed at the same transformed `village_anchor:armory` cell that
Step 4 already consumes. Rotation is passed to the existing generated-Armory
placer, so the block faces with the rotated building. Placement requires the
cell's chunk to be loaded and the cell itself to remain air; another block or
an Armory owned by another identity is never replaced.

The generated block uses the village building UUID as its persistent owner and
is public by default. Any otherwise-eligible guard can therefore discover it
through the existing loaded Armory index and resupply rules. Players may later
apply the existing Squad Command Ledger lock if they want to reserve it for a
specific squad. No quartermaster entity, profession, trade table, or inventory
proxy is introduced.

The bundled initial-stock table has seven constant one-roll pools containing
only validated TaCZ loose ammunition. It remains an ordinary datapack loot
table, so modpacks may replace it under the same resource ID. The existing
generated-loot evaluator retains its 64-stack and 256-definition-component
ceilings, rejects variable rolls and non-ammunition output, and commits the
complete inventory plan all-or-none.

## Persistence and failure behavior

The existing `ARMORY` and `INITIAL_STOCK` actions are reserved together in one
synchronized SavedData mutation before placement. That makes the one-time
placement and the decision to stock or leave empty a single at-most-once
boundary. Exact repeats cannot duplicate ammunition. If the Armory block was
saved first but its action data was not, a matching generated identity is
recognized and the claims are repaired without replacing it or starting a
second stock transaction. If both claims exist and the block is later broken,
it is not regenerated.

As with garrisons, SavedData and chunk data are not one atomic disk transaction.
An exceptional process stop after the claims but before the chunk save may
therefore leave no Armory; the system intentionally fails closed instead of
risking duplicate stock. A temporarily non-live generation chunk receives at
most the shared 40 one-tick checks and is never loaded or ticketed by VGG.

## Configuration

Config schema 15 adds two settings while preserving every schema-14 building
and garrison value:

- `villageBuildings.placeArmoryBlocks` defaults to `true` and controls whether
  an eligible new Armory shell receives the VGG Armory block.
- `villageBuildings.stockArmories` defaults to `true` and controls its one-time
  initial stock. Disabling this leaves the placed Armory empty.

Both are subordinate to the default-off `villageBuildings.enabled` master
switch. The existing `villageBuildings.armories` setting continues to control
whether Armory shells are published into village pools at all.

## Runtime and performance boundary

Step 7 adds zero recurring building controllers or permanent ticks. Generation
performs constant-time SavedData lookups, one loaded-chunk check, one block-cell
check, and at most one nine-slot initialization. Once loaded, the Armory uses
the existing transient section index and staggered guard resupply path; it does
not create a village scan, block-entity scan, chunk ticket, forced chunk,
retro-generation pass, repair manager, or new navigation loop.
