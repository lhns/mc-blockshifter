package de.lolhens.minecraft.blockshifter.neoforge

import de.lolhens.minecraft.blockshifter.BlockshifterMod
import de.lolhens.minecraft.blockshifter.util.EntityMover
import net.minecraft.core.registries.{BuiltInRegistries, Registries}
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.{BlockItem, CreativeModeTabs, Item}
import net.minecraft.world.level.block.Block
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent
import net.neoforged.neoforge.registries.RegisterEvent

import java.util.function.Consumer

@Mod("blockshifter")
class BlockshifterModNeoForge(modEventBus: IEventBus) {
  modEventBus.addListener(classOf[RegisterEvent], new Consumer[RegisterEvent] {
    override def accept(event: RegisterEvent): Unit = {
      event.register(Registries.BLOCK, (helper: RegisterEvent.RegisterHelper[Block]) => {
        helper.register(BlockshifterMod.RAIL_BLOCK_ID, BlockshifterMod.RAIL_BLOCK)
      })
      event.register(Registries.ITEM, (helper: RegisterEvent.RegisterHelper[Item]) => {
        helper.register(
          BlockshifterMod.RAIL_BLOCK_ID,
          new BlockItem(BlockshifterMod.RAIL_BLOCK, new Item.Properties())
        )
      })
    }
  })

  modEventBus.addListener(classOf[BuildCreativeModeTabContentsEvent], new Consumer[BuildCreativeModeTabContentsEvent] {
    override def accept(event: BuildCreativeModeTabContentsEvent): Unit = {
      if (event.getTabKey == CreativeModeTabs.REDSTONE_BLOCKS) {
        val item = BuiltInRegistries.ITEM.get(BlockshifterMod.RAIL_BLOCK_ID)
        if (item != null) event.accept(item)
      }
    }
  })

  NeoForge.EVENT_BUS.addListener(classOf[LevelTickEvent.Pre], new Consumer[LevelTickEvent.Pre] {
    override def accept(event: LevelTickEvent.Pre): Unit = {
      event.getLevel match {
        case serverLevel: ServerLevel => EntityMover(serverLevel).moveAll()
        case _ =>
      }
    }
  })
}
