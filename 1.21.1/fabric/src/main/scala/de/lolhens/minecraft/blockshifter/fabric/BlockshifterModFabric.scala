package de.lolhens.minecraft.blockshifter.fabric

import de.lolhens.minecraft.blockshifter.BlockshifterMod
import de.lolhens.minecraft.blockshifter.util.EntityMover
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.core.registries.{BuiltInRegistries, Registries}
import net.minecraft.world.item.{BlockItem, CreativeModeTabs, Item}

object BlockshifterModFabric extends ModInitializer {
  override def onInitialize(): Unit = {
    net.minecraft.core.Registry.register(
      BuiltInRegistries.BLOCK,
      BlockshifterMod.RAIL_BLOCK_ID,
      BlockshifterMod.RAIL_BLOCK
    )

    val railItem = new BlockItem(BlockshifterMod.RAIL_BLOCK, new Item.Properties())
    net.minecraft.core.Registry.register(
      BuiltInRegistries.ITEM,
      BlockshifterMod.RAIL_BLOCK_ID,
      railItem
    )

    ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register { entries =>
      entries.accept(railItem)
    }

    ServerTickEvents.START_WORLD_TICK.register { world =>
      EntityMover(world).moveAll()
    }
  }
}
