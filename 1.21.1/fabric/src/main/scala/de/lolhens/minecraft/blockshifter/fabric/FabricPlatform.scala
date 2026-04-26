package de.lolhens.minecraft.blockshifter.fabric

import de.lolhens.minecraft.blockshifter.{BlockshifterMod, BlockshifterPlatform}
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.{ResourceKey, ResourceLocation}
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.{CreativeModeTab, Item}
import net.minecraft.world.level.block.Block

import java.util.function.Supplier

class FabricPlatform extends BlockshifterPlatform {
  private def rl(path: String): ResourceLocation =
    ResourceLocation.fromNamespaceAndPath(BlockshifterMod.id, path)

  override def registerBlock[B <: Block](id: String, factory: () => B): Supplier[B] = {
    val block: B = Registry.register(BuiltInRegistries.BLOCK, rl(id), factory())
    () => block
  }

  override def registerItem[I <: Item](id: String, factory: () => I): Supplier[I] = {
    val item: I = Registry.register(BuiltInRegistries.ITEM, rl(id), factory())
    () => item
  }

  override def addToCreativeTab(tabKey: ResourceKey[CreativeModeTab], item: Supplier[_ <: Item]): Unit = {
    ItemGroupEvents.modifyEntriesEvent(tabKey).register(entries => entries.accept(item.get()))
  }

  override def onServerLevelTick(callback: ServerLevel => Unit): Unit = {
    ServerTickEvents.START_WORLD_TICK.register(level => callback(level))
  }
}
