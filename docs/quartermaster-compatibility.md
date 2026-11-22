# Quartermaster compatibility

Quartermaster stations use ordinary villager professions and job sites. Management,
trading, and equipment service share the station, but have separate permissions and
availability rules. See the [station guide](quartermaster-station.md) for player
controls and [equipment service](quartermaster-service.md) for recovery rules.

## Villager AI and job sites

Unemployed adult villagers discover the station through
`minecraft:acquirable_job_site`. Datapacks replacing that tag must retain
`villagerguardgunners:quartermaster`. The station accepts one job-site claim.
A saved operator UUID does not substitute for a live villager, matching profession,
job-site memory, and occupied POI ticket.

Equipment service runs during daytime ticks 2000–9000 while the operator is nearby
and available. A different villager schedule does not extend that window. Trading,
sleep, panic, raids, and losing the job site pause service. After an operator dies,
an unemployed adult can claim the released station through normal villager AI.
The replacement does not own the station or alter guard assignments.

Liberty's Villagers settings that prevent adult job acquisition, such as forcing
all villagers to remain babies or become nitwits, also prevent new Quartermasters.
The add-on does not override those choices. Workstation search range changes do not
change the guard service radius or the operator's required proximity to the station.

## Merchant and interaction mods

Empty-handed, non-sneaking interaction with a claimed Quartermaster opens the
overview. The listener runs at lowest Forge priority and respects canceled events.
Holding an item or sneaking leaves other villager interactions available. An earlier
mod that consumes empty-handed interaction can prevent the NPC overview from opening;
the station's Overview button remains another entry point.

Trade opens the native merchant interaction. Quartermaster definitions append to
existing offers, and the add-on's purchase restrictions identify only its own marked
offers. Other offers retain their ordinary behavior. Purchases belong to the player;
deposits into station stock are a separate action. Mods replacing merchant screens,
stripping offer identity NBT, or directly granting results need separate testing.
See [trade integration details](quartermaster-trades.md) for the exact boundaries.

## Stock, transport, and guard settings

Hoppers and pipes use the station's Forge item handler. The Pipes control grants
insertion into stock and extraction from returns, independently of player access.
Changes to a reserved stack prevent the outdated transfer. Recovery locks block
transport and management instead of allowing a second item transfer.

Linked Village Armories retain their squad supply locks. Overview visibility also
requires the player to be within eight blocks of each linked Armory. Actual guard
supply access and the player's report can therefore differ. A restricted or limited
report is not a complete inventory of village supplies.

Existing guard inventory controls preserve station assignments. Bulk management
requires authority over each guard; squad membership changes invalidate its preview.
Medical treatment and combat defer trips. Both guns' ammo types are assessed, with a
shared reserve for a common caliber. Moving a packed station preserves its identity
and supplies while invalidating menus tied to its previous placement.

## Verified installation scope

The packaged dedicated-server journey was checked on Minecraft 1.20.1 and Forge
47.4.10 with Guard Villagers 1.20.1-1.6.19, TaCZ 1.1.8-hotfix, Fzzy Config 0.7.6,
and Kotlin for Forge 4.12.0. It covers natural job acquisition, merchant purchases
and level advancement, stock-menu deposits, bulk assignment, actual guard service,
overview results, operator replacement, and restart persistence.

The same journey was checked with these additional installed mods together:

| Mod | Version |
| --- | --- |
| Liberty's Villagers | 2.0.0+forge+1.20.1 |
| Architectury | 9.2.14 |
| Cloth Config | 11.1.136 |
| Supplementaries | 1.20-3.1.43 |
| Moonlight Library | 1.20-2.16.35 |

The copied Liberty configuration used POI search range 128, pathfinding range 256,
minimum POI search distance 3, and no workstation searches at night. Adult villagers
and normal profession acquisition were enabled. Cloth Config must be present for
this Liberty build's AutoConfig classes, although its bundled dependency metadata
lists only Architectury, Forge, and Minecraft.

This verifies the Quartermaster journey with those versions and settings. It does
not establish compatibility with every merchant replacement or every Liberty setting.
Supplementaries' optional Farmer's Delight advancement reported a missing
`farmersdelight:rich_soil` item in this installation; the Quartermaster journey and
Moonlight's trade loading still completed.

The installed Forge client was also checked with this combined mod set and Liberty
configuration. Overview navigation, bulk preview/apply, stock access, native trade
purchases, disabling an open offer, input return on close, guest privacy, and
distance-based menu closure completed through the client screens.

A separate core-dependency installation passed simultaneous management and trading
checks with two actual local clients, including stale previews, merchant exclusivity,
disconnect and reconnect. A copied world saved by released 1.8 retained its service
policies, exact item data and existing offers across upgrade, packed-station movement,
chunk unload/reload and server restart.

See [packaged validation](quartermaster-validation.md) for upgrade expectations,
repeatable multiplayer and scale checks, and measurement limits.
