# Large TaCZ Gun-Pack Login Sync

## Problem

TaCZ 1.1.8-hotfix serializes all common gun-pack data into one Forge login
payload. Minecraft 1.20.1 rejects custom payloads larger than 1,048,576 bytes.
With enough installed packs, joining either an existing or newly created world
therefore fails with the generic `Invalid player data` screen while the log
contains:

```text
java.lang.IllegalArgumentException: Payload may not be larger than 1048576 bytes
```

The failure is not corrupt player NBT. Deleting player data or recreating the
world cannot repair it because the exception happens while TaCZ synchronizes
the server's common resource cache to the joining client.

## Compatibility behavior

Villager Guard Gunners now intercepts only TaCZ's common-data synchronization
event and measures the exact payload produced by TaCZ's own packet codec.

- Payloads at or below 900 KiB keep TaCZ's native synchronization unchanged.
- Larger payloads are divided into replacement packets containing at most
  512 KiB of data each.
- Every transfer has a schema version, transfer identity, declared total size,
  chunk count, and CRC32 checksum.
- The client publishes the cache to TaCZ only after all chunks have arrived,
  their metadata agrees, the exact byte count matches, and the checksum passes.
- A malformed, conflicting, incomplete, or unsupported transfer is never
  partially exposed to TaCZ.

The protocol has hard limits of 64 chunks and 32 MiB. It performs work only on
login or datapack synchronization, adds no entity scan or tick handler, and
does not modify Minecraft's packet limit. A partial transfer is discarded on
logout or when a new transfer begins.

### Packet-limit mods

This path is intentionally independent of mods such as Packet Fixer. Those
mods alter Minecraft's global packet, compression, NBT, or timeout limits; this
add-on changes only TaCZ's common-data send and keeps every replacement message
below the unmodified vanilla 1 MiB custom-payload limit. When both are present,
Villager Guard Gunners handles this TaCZ transfer while Packet Fixer remains
free to handle unrelated large packets. No Packet Fixer detection or load-order
dependency is required.

Installing two different global packet-limit mods can still make those two
mods conflict with each other, especially if both modify the same Minecraft
constant. This add-on is not a second global limit patch and should not add to
that mixin conflict. A deliberately customized global maximum below 512 KiB is
outside this protocol's tested assumptions.

If encoding or sending an oversized replacement fails, the known-invalid TaCZ
packet remains suppressed so the connection is not deliberately failed by the
same 1 MiB exception. The server and client log the bounded transfer size or a
failure reason to make future diagnosis possible.

## Validation

Automated coverage verifies TaCZ-native encode/decode equivalence, individual
packet size, out-of-order reassembly after the first chunk, duplicate conflicts,
checksum failures, malformed bounds, atomic client publication order, release
JAR contents, and the live TaCZ cache inside the transformed Forge GameTest
runtime.

Before publishing a release, also test the original affected setup rather than
only a new test world:

1. Install the same client and server versions of the add-on and all TaCZ packs.
2. Join the previously failing save and confirm the log reports replacement of
   the oversized TaCZ sync followed by successful client application.
3. Confirm several guns from different packs render and operate normally.
4. Run `/reload`, then repeat the cross-pack checks.
5. Repeat on a dedicated server if that is part of the release target.

Malformed recipes and missing or mismatched gun-pack index entries reported by
TaCZ are separate content-pack issues. This compatibility path prevents the
aggregate cache size from breaking login; it does not rewrite invalid pack
data or conceal those warnings.
