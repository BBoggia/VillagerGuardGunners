# Quartermaster trade example

This Minecraft 1.20.1 datapack adds a Journeyman offer for 30 rounds of TaCZ
7.62×39 ammo. Its base price is eight emeralds and one iron ingot, with four uses
per restock. This is a format example; adjust its balance for your modpack.

Copy this folder into a test world's `datapacks` directory, or zip its contents
with `pack.mcmeta` at the archive root. Run `/reload`, then use a new Quartermaster
or one that has not yet unlocked Journeyman offers. Existing initialized levels
keep their saved offers.

The offer ID is `vgg_example:rifle_ammo`. To disable saved copies, replace its JSON
with `{"version": 1, "enabled": false}` and reload. Removing the datapack also
blocks its saved marked offers. Supplies purchased through this trade go to the
player and must be deposited for guards to use them.

See the repository's `docs/quartermaster-trades.md` for supported fields, gun-sale
configuration, disabled definitions, and saved-offer behavior.
