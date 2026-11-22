# Settlement compatibility definitions

Villager Guard Gunners uses one settlement classification for village discovery,
new managed-generation plans, and automatic coverage. Pack authors can classify
structures and template functions, exclude settlements from friendly enrollment,
and select a verified piece reader. Recognition alone does not establish that
guards can reach every building, cross water, or navigate elevated villages.

## Format and resource precedence

Place JSON files at
`data/<namespace>/village_compatibility/<name>.json`. The profile ID is
`<namespace>:<name>`. The highest-priority resource pack supplies each ID; files
with the same ID replace the whole profile. Different IDs participate together.

```json
{
  "format": 1,
  "mods": {"example_villages": "[1.2.0]"},
  "priority": 20,
  "intent": "friendly",
  "reader": "villagerguardgunners:native",
  "structures": ["example_villages:plains"],
  "structure_tags": [],
  "templates": {
    "example_villages:houses/cottage": "residence",
    "example_villages:roads/straight": "road",
    "example_villages:decoration/garden": "decoration"
  },
  "target_pools": []
}
```

This example requires that mod and structure to exist. It is not a bundled
adapter. Format 1 defines classification and reader selection; staffing units,
additional spawn sources, entrances, traversal hints, and per-mod population
presets are outside this format.

| Field | Meaning |
| --- | --- |
| `format` | Required exact integer `1` or `2`. |
| `mods` | Optional map of mod IDs to restricted Maven ranges, such as `[1.2.0]` or `[1.2,1.3)`. All conditions must match. |
| `priority` | Integer from 0 through 100; defaults to 0. |
| `intent` | Required: `friendly`, `hostile`, `abandoned`, or `unsupported`. Only friendly settlements enroll. |
| `reader` | Defaults to `villagerguardgunners:native`; also accepts the gated `integrated_api:single` reader. |
| `structures` | Exact structure IDs; selected structures must exist when the profile is active. |
| `structure_tags` | Structure tag IDs, without `#`; active tags must exist after binding. Either selector list can match. |
| `templates` | Exact template IDs mapped to `residence`, `road`, `gathering`, `defense`, `service`, `decoration`, or `unsupported`. |
| `target_pools` | Existing pool IDs to exclude from startup defense-building injection when intent is not friendly. This does not insert new buildings. |

At least one structure selector is required. An explicit nonfriendly match takes
precedence over all friendly matches. Within that group, the highest profile
priority wins. Equally ranked profiles with different meanings yield an
unsupported classification, with their IDs and providers available for inspection.
Equivalent profiles can coexist.

Without a profile, the five vanilla village IDs and members of
`minecraft:village` use native recognition. The existing
`minecraft:village/**/houses/**` convention supplies residential classification
unless an exact template mapping overrides it. Format 2 disables this name-based
fallback: unmapped or changed resources remain unverified. Existing configured extra
residential templates still affect managed demand when no exact mapping exists.
Classifying a template as a residence does not create a native spawn request.
Existing Guard Villagers markers, population caps, and garrison quotas remain
in effect. Other template kinds are available to consumers; they do not by
themselves activate a service or defense building.

## Fingerprint-bound template metadata

Format 2 keeps the same selectors, priorities, applicability conditions, and
reader choices. Each `templates` value becomes an array of known resource
variants. For example, an entry can contain:

```json
{
  "kind": "residence",
  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "beds": [[2, 1, 3]],
  "entrances": [[0, 1, 3]]
}
```

Replace the illustrative hash with the SHA256 of the actual NBT resource bytes.
The resource manager selects the winning template; only its matching fingerprint
supplies metadata. A missing, oversized, changed, or unmapped template has no
inherited residential or entrance hints. This also handles Better Villages and
Lios replacing the same vanilla template: the winner's metadata is used once.

`beds` contains local bed-head positions, and `entrances` contains local lower-door
or named building entrance-connector positions. Road attachment connectors are
excluded from building entrance hints. Each list supports 64 unique integer
triples, with coordinates from -512 through 512. A residence needs at least one
bed position; other kinds cannot carry residential capacity. Positions rotate
and translate with the native piece. Overlapping bed positions are counted once
when creating residential demand. Bedless multipart fragments do not add homes.
The metadata describes planned templates; processors and actual terrain can
change placed blocks. Entrance candidates do not establish clearance, access,
water crossing, or a usable route.

Optional `size_family` is `unspecified`, `small`, `medium`, or `large`.
`traversal_limits` accepts `water`, `elevation`, `steep_terrain`, `mechanisms`,
`underground`, and `unverified_variant`. These are reported limitations, not
movement capabilities or blanket exclusions from recognition.

The bundled definitions cover the pinned resources in
[village template support](village-template-support.md). They do not change
population caps, refill saved quotas, or invent native guard requests.

## Validation and reload

Applicability conditions are validated first. An absent mod or an unmatched
version makes the profile inactive, so its optional reader and registry IDs are
not resolved. Active profiles reject unknown fields, unknown readers, invalid
IDs, fractional integers, duplicate keys, duplicate IDs, and unsupported formats.
All files must still be syntactically valid JSON, including inactive files.

Limits are 128 profiles, 2 MiB per file, 16 MiB total profile data, eight nested
JSON levels, 16 mod conditions, 256 combined structure selectors, and 256 target
pools per profile. Format 1 supports 512 template mappings; format 2 supports
4,096 mappings with up to eight fingerprints each. Template evidence is bounded
to 16,384 resources, 2 MiB per resource, and 128 MiB total reads per reload.
Resolution supports up to 16,384 registered structure types. Parsing and resolution run during reload;
generation reads an immutable snapshot without scanning tags or profiles.

A valid `/reload` publishes the complete catalog after server tags bind and
increments its generation. An invalid profile reload reports errors and retains
the previous profile choices. Because Minecraft can still replace template
resources during that reload, retained fingerprint-bound hints are rechecked.
Changed or unavailable templates lose their old hints, and the generation
advances so coverage can discard stale observations. If resource evidence is
unchanged, the exact previous catalog and generation remain in place. On initial
load, invalid custom profiles leave native village recognition available within
the registry limit. Discovery refreshes its loaded metadata reviews after tag reload. Coverage discards assignments from an older
catalog generation and reviews them again before issuing new work.

Reload does not rewrite saved village identities, frozen population plans,
quotas, spent credits, or issuance journals. New plans use the current catalog.
Already frozen source bindings retain verified template identity. Defense pool
injection happens at server startup, so changes to `target_pools` require a server
restart; an ordinary reload cannot remove already injected pool entries.

## Piece readers and Integrated Villages

The native reader handles Minecraft single and legacy pool elements. Readers
return the native piece position, rotation, bounding box, template ID, and a
support status. Unsupported elements retain geometry for discovery but cannot
supply an inferred template or managed spawn source. Readers do not load chunks
or replace structure serialization. Minecraft can expand generated piece bounds
and recalculate them from templates on load. Native serialization checks therefore
compare stable placement/template identity, record changed bounds, and require
restored geometry to remain stable on a subsequent save/load cycle.

The optional reader resolves Integrated API's `IASinglePoolElement` only with
Integrated API **1.8.0** installed. It reads the verified public template field;
that class is not a Minecraft `SinglePoolElement`. Inline templates and unknown
element classes remain unsupported. The bundled clockwork profile also requires
Integrated Villages **1.3.2+1.20.1-forge**. It establishes recognition and template
identity. Separate bundled profiles classify each shipped Integrated variant;
Minka additionally requires Quark. Traversal limitations remain independent of recognition.

Integrated Villages 1.3.2 contributes a required
`integrated_villages:swamp_village` entry whose structure is absent in the tested
artifact. The correction runs before required-reference resolution and removes
only that required, non-tag addition from that mod's verified resource source.
Every unrelated addition, removal, and pack replacement retains its usual
meaning. A missing entry contributed by another pack still fails normally.
Forge's removal array does not undo an earlier unresolved required reference in
the pinned loader. See the [Forge tag documentation](https://docs.minecraftforge.net/en/1.20.1/resources/server/tags/)
and [TagLoader patch](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.20.x/patches/minecraft/net/minecraft/tags/TagLoader.java.patch).

The correction requires Forge **47.4.10**, the exact Integrated Villages version,
the installed mod's resource source, an absent swamp structure, and this SHA256
of the contributed `minecraft:tags/worldgen/structure/village.json` resource:

```text
1a2586240d3fe8c1fedf536e751da30c1ba9bdb25dfd97d93ed8c4ce065fbc9b
```

Changed resources or versions receive no unverified mutation. A pack supplying
the missing structure also deactivates the correction. No optional mod becomes
a required build dependency. Public dependency declarations remain in
`META-INF/mods.toml`.

## Operator inspection

These read-only commands inherit permission level 2 from `/vgg`:

- `/vgg villages compatibility status`: format and active generation, active
  profile IDs and providers, selected readers, priorities, inactive reasons,
  rejected-reload errors, and tag-correction status.
- `/vgg villages compatibility inspect <structure>`: matching profile IDs,
  winning providers, intent, exclusion or unsupported reason, reader, and
  template mapping count, verified mapping count, size family, traversal limits,
  and total mapped bed/entrance positions. These totals describe the definition,
  not one generated village.
- `/vgg villages compatibility template <structure> <template>`: the effective
  resource provider, fingerprint, interpretation, verification reason, and local
  bed/entrance positions.

Use [the production compatibility runner](village-compatibility-testing.md) for
packaged-JAR tests. A successful launch is only one part of compatibility;
recognition, population persistence, tag composition, and behavior need their
own assertions.
