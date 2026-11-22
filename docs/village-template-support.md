# Village template classification

The bundled definitions describe settlement identity and planned building
functions for the resource versions below. Recognition does not establish
complete staffing, patrol coverage, or access to every building. No definition
adds these settlements to Minecraft's village tag.

| Installation | Classification and boundary |
| --- | --- |
| Minecraft 1.20.1 | Five vanilla village types; bed-bearing templates contribute residential demand. Bedless houses, farms, pens, and fragments do not. |
| ChoiceTheorem's Overhauled Village 3.4.14 | Small, medium, and large tags, plus exact profiles for all 66 village structures, including underground variants absent from those tags. Family templates include CTOV's shipped optional profession and defense pieces; a mapping does not activate their pool injections. |
| MMV 2.1.1 | Jungle, dark-forest, and swamp structure IDs and pool/template connections. MMV's own resident templates are not native Guard Villagers request sources under its default whitelist. Additional staffing remains pending population integration. |
| Better Villages 3.3.1 / Lios Overhauled Villages 0.1.1 | Known vanilla-template replacements, selected by the effective NBT resource fingerprint. Installing both does not stack two residential interpretations. Unrecognized replacements lose template-specific hints. |
| Tidal Towns 1.3.4 | Exact structure `joshie:village_ocean`, its connected templates, and the `water` limitation. Bed-bearing structures are sparse in the shipped template library; bedless buildings do not add residential demand. |
| Sky Villages 1.0.4 | Exact structure `skyvillages:skyvillage`, its connected house and bridge templates, and the `elevation` limitation. Recognition does not supply flight, safe descent, or ground access. |
| Integrated Villages 1.3.2+1.20.1-forge / Integrated API 1.8.0 | Individual profiles for airship, cabin, clockwork, kutcha, marketstead, Mediterranean, mossy mounds, oasis, pirate, sunken, tavern, and Quark Minka villages. Minka remains inactive without Quark. Airship elevation, pirate/sunken water, and clockwork mechanisms remain explicit limitations. |
| Villages&Pillages 1.0.2 | `villagesandpillages:village_witch` is explicitly hostile and excluded from friendly enrollment; its center pool is excluded from startup defense-building injection. |
| Liberty's Villagers 2.0.0 | Uses the applicable settlement/template definitions. This classification work does not replace Liberty's villager AI or establish full door, schedule, supply, or medical coexistence. |

CTOV mountain families report steep terrain; tree families report elevation;
underground families report underground access. The metadata records candidate
entrances, not tested routes. Terrain, water, and elevation validation remain
separate work.

## MMV zombie resources

The three shipped MMV start pools select normal meeting points. Zombie resources
also exist, but their names alone do not establish an abandoned village variant.
The normal `mmv:swamp/houses/large_house_5` template contains connectors to both
`mmv:village/swamp/villagers` and `mmv:village/swamp/zombie/villagers`; the latter
selects zombie nitwit or unemployed resident templates. This is evidence of a
mixed resident source in an otherwise normal house. It is not sufficient to
exclude the whole swamp settlement as abandoned. No broad zombie-pool exclusion
is applied by these definitions.

Native request eligibility is measured against the installed Guard Villagers
`structuresThatSpawnGuards` configuration and the actual generated pieces.
In packaged samples, dark forest included the vanilla iron-golem request marker;
jungle and swamp did not. Absence of requests in those samples remains a staffing
gap, despite successful residential recognition. Template classification never substitutes for an observed request, available
issuance credit, or a safe spawning location.

## Missing and optional resources

Some shipped pools refer to optional or absent templates. CTOV 3.4.14 ships underground structure definitions but does not include them
in its shipped structure sets. Naturally locating one in that installation is
therefore not an available test; activation requires a separate datapack. Its
underground graph also includes 20 template IDs absent from the supplied jars;
other families and mods also contain optional integrations or missing entries.
A pool reference alone does not prove that generation selects it. Missing
resources remain unverified and contribute no inferred bed or entrance metadata.
The definitions do not silently repair another mod's world-generation pools.

## Resource verification

Format 2 definitions bind bed and entrance positions to exact NBT fingerprints.
The winning resource provider supplies the interpretation. Definitions are
resolved during resource reload; generation uses the resulting immutable
catalog. Missing or changed resources remain unverified. Invalid profile reloads
retain previous profile choices while revoking stale resource hints. Existing population plans and spent credits
are not recalculated.

To inspect or rebuild resource evidence, pass the actual vanilla and mod JARs to
`tools/audit_village_template_resources.py --output audit.json <jars...>`, then use
`tools/generate_village_template_profiles.py --audit audit.json --output <directory>
--report report.json`. Review the outputs before replacing bundled definitions.
The audit traces JSON pools and template connectors; runtime pool changes,
processors, seed generation, and movement still require production tests.

See [definition formats and operator commands](village-compatibility-definitions.md)
and [production testing](village-compatibility-testing.md).
