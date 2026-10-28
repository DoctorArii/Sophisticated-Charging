# Changelog

## 1.2.0
- New versions: NeoForge 26.3, 26.2, 26.1.2 and 1.21.11, Forge 1.19.2 and 1.18.2, and Fabric 1.20.1 (with the
  unofficial Fabric port of Sophisticated Backpacks).
- Works with much older Sophisticated Backpacks versions: 1.21.1 from 3.20.12 and 1.20.1 from 3.20.5 (packs like
  All the Mods 10 To the Sky and FTB Skies 2 that ship an older Sophisticated Backpacks).
- A placed backpack now shows a newly added battery to cables and chargers right away.

## 1.1.0
- The pool's charge is shown on the backpack itself: the battery on the side of the backpack model (worn, held or
  placed) now shows the whole pool. With a Battery Upgrade in the same backpack, it shows the total of both.
- A gold charge bar under the icon of backpacks with a Charging Upgrade or a Battery Upgrade, in every inventory and
  the hotbar. Can be turned off per player (`showChargeBar` in `sophisticatedcharging-client.toml`).
- The backpack's tooltip shows how much charge is left ("Charge: 73%").
- Fixed a bug that could prevent the Charging Upgrades from being equipped.

## 1.0.0
First release, for Minecraft 1.21.1 (NeoForge) and 1.20.1 (Forge).
- Charging Upgrade: pools every battery in the backpack (any mod's Forge Energy batteries, plus the backpack's Battery
  Upgrade) and charges armor and held items from it. The backpack shows up as one big battery to cables and chargers.
- Advanced Charging Upgrade: faster, can also charge the whole inventory, Curios slots and items stored in the backpack,
  with a filter.
- Server config and item tags to ban batteries and charge targets (by item, mod or tag), a battery size limit, and
  automatic exclusion of infinite/creative batteries.
