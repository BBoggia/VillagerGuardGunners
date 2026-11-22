# Quartermaster trades

Quartermasters sell supplies through the normal villager trading screen. Open
**Trade** from the overview, or use ordinary villager trading when available.
Purchases go to your inventory. Deposit supplies in a Quartermaster station or
Village Armory yourself if you want guards to use them.

## Default offers

Prices below are base prices in emeralds. Villager reputation, discounts and demand
can change them. Uses reset through normal villager restocking at a claimed job site.

| Level | Purchase | Base price | Uses per restock | Villager XP |
| --- | --- | ---: | ---: | ---: |
| Novice | 4 bandages | 2 | 12 | 2 |
| Novice | Shield | 5 | 3 | 2 |
| Apprentice | 16 rounds of 9mm ammo | 4 | 8 | 5 |
| Apprentice | Orders Ledger | 6 | 2 | 5 |
| Journeyman | 16 rounds of 7.62×39 ammo | 6 | 8 | 10 |
| Journeyman | Iron helmet | 8 | 3 | 10 |
| Expert | 16 rounds of 5.56×45 ammo | 6 | 6 | 15 |
| Expert | Squad Command Ledger | 10 | 2 | 15 |
| Master | 8 shotgun shells | 6 | 6 | 30 |
| Master | Recovery cot | 12 | 2 | 30 |

Optional gun sales add an unloaded Glock 17 at Expert for 32 emeralds and an
unloaded AK-47 at Master for 48 emeralds. Each has one use per restock and gives
15 or 30 villager XP, respectively. **Gun sales start disabled.**

These are purchase-only offers; the defaults do not buy items back for emeralds.
Crafting remains useful: ammo trades sell smaller quantities than the normal TaCZ
crafting batches, and equipment purchases are limited. There are no diamond armor
or enchanted equipment offers.

## Server configuration

The Fzzy Config file `config/villagerguardgunners/guard_gunners.toml` contains:

- `quartermasterTrades.suppliesEnabled`: enables supply trades; default `true`.
- `quartermasterTrades.gunSalesEnabled`: enables gun sales independently; default `false`.

The in-game settings section is **Quartermaster Trades**. These controls are separate
from combat, medical treatment, station stock access and equipment service.
Disabling a category also blocks its saved offers, including an already-open trade
screen. Re-enabling it restores usable saved offers whose items still exist.

Existing Quartermasters receive the offers for their current level and earlier
levels once. Other mods' offers remain present. Once a level has been initialized,
changing definitions or enabling another category does not reroll that level.
Use a new villager or unlock a new level to generate new offers. Losing and reclaiming
the profession follows vanilla's normal offer reset for untraded villagers.

Trade uses, prices, demand and discounts are saved normally. Restocking keeps the
same items and prices apart from vanilla demand changes. Actual trading pauses the
Quartermaster's equipment service; closing the merchant screen lets service resume.

## Datapack definitions

The [installable example pack](../examples/quartermaster-trades/README.md) adds a
two-input Journeyman ammo trade. Copy its folder into a test world's `datapacks`
directory and reload before generating that villager's offers.

Place JSON files in `data/<namespace>/quartermaster_trades/<name>.json`. The file's
resource ID identifies that offer. For example:

```json
{
  "version": 1,
  "level": 2,
  "inputs": [
    {"type": "item", "id": "minecraft:emerald", "count": 4}
  ],
  "output": {"type": "ammo", "id": "tacz:9mm", "count": 16},
  "max_uses": 8,
  "xp": 5,
  "price_multiplier": 0.05
}
```

All fields in this example are required. `enabled` is optional and defaults to
`true`. Inputs contain one or two stacks. Supported stack types are:

- `item`: a registered item ID with no custom NBT. TaCZ gun and ammo items require
  their typed forms below.
- `ammo`: a native TaCZ ammo ID, producing loose ammo.
- `gun`: a native TaCZ gun ID, output only. Count must be one. Guns are unloaded,
  with no attachments or custom NBT supplied by the trade definition.

Limits are 128 files, 16 enabled definitions per level, levels 1–5, stack counts
1–64, uses 1–64, villager XP 1–30, and price multiplier 0–1. Actual stack limits
still apply; for example, an unstackable item cannot have count two. IDs are at
most 96 characters and each JSON file is at most 16,384 characters. Unknown fields,
fractional integer values, invalid versions and malformed definitions reject the
whole trade reload and retain the last working generation.

Native item, gun and ammo availability is checked after server assets load. A
missing optional item or unsupported stack omits that offer with one diagnostic
per trade ID per reload. It does not prevent other valid definitions from loading.
All valid enabled offers for a newly initialized level are added in resource-ID
order. They are additional to offers supplied through vanilla or other mods.

Override a definition with exactly this content to disable it, including saved
copies of that offer:

```json
{"version": 1, "enabled": false}
```

For example, overriding
`data/villagerguardgunners/quartermaster_trades/bandages.json` disables the built-in
bandage trade. Removing a custom definition also blocks saved offers with that ID.
Changing a definition's price, output or level affects future generation; saved
copies keep their original values until explicitly disabled or removed.

## Merchant integration contract

Offer-local `VillagerGuardGunnersTrade` NBT stores version 1, its definition ID, and the original input count.
It is stored on the merchant offer, never on the purchased item. Unknown or malformed
identity records are preserved and blocked. Ordinary unmarked offers are unaffected.
Native item IDs are revalidated on saved offers, so removed gun or ammo definitions
cannot remain purchasable. A missing second payment item also blocks a saved offer
instead of reducing its price. Pack authors remain responsible for balancing custom
prices, buyback trades and interactions with other mods' economies.

Villager Forge persistent data tracks which levels have been initialized. Saved
values survive restarts and prevent duplication, including for previously empty
or already-leveled Quartermasters. Existing marked IDs are also deduplicated. The
initializer never clears another mod's offers. A profession change away from
Quartermaster resets this record with vanilla's offer reset. Unknown versions of
the initialization record remain preserved and prevent new initialization.

The ordinary merchant menu handles payment, XP, full inventories, closing and
restocking. Add-on checks run before result extraction and shift-click movement.
Changing offer availability clears stale result previews and updates open merchant
screens through vanilla packets. Input slots remain owned by the native merchant
container and are returned through its normal close behavior. Trading has no access
to station stock, linked Armories, guard equipment, or the service journal.

Mods which replace merchant menus, strip custom offer NBT, or directly grant results
outside vanilla's result-slot and quick-move paths need separate integration.
Unrelated offers and price/stock adjustments retain their ordinary behavior.

See [Quartermaster compatibility](quartermaster-compatibility.md) for tested
Liberty's Villagers and Supplementaries versions, job-site requirements, and
interaction behavior.
