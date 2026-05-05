package de.lolhens.minecraft.blockshifter

import de.lolhens.minecraft.blockshifter.block.RailBlock
import de.lolhens.minecraft.blockshifter.config.BlockshifterConfig
import de.lolhens.minecraft.blockshifter.util.EntityMover
import net.minecraft.core.registries.Registries
import net.minecraft.resources.{ResourceKey, ResourceLocation}
import net.minecraft.world.item.{BlockItem, CreativeModeTab, Item}

import java.util.function.Supplier

object BlockshifterMod {
  val id: String = "blockshifter"

  val RAIL_BLOCK_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(id, "rail")

  private var railBlockRef: Supplier[RailBlock] = _

  def RAIL_BLOCK: RailBlock = {
    val ref = railBlockRef
    if (ref == null)
      throw new IllegalStateException("RAIL_BLOCK accessed before BlockshifterMod.init() registered it")
    ref.get()
  }

  lazy val config: BlockshifterConfig = BlockshifterConfig.loadOrCreate(id)

  def init(): Unit = {
    val platform = BlockshifterPlatform.current

    railBlockRef = platform.registerBlock("rail", () => new RailBlock())

    val railItem: Supplier[BlockItem] =
      platform.registerItem("rail", () => new BlockItem(railBlockRef.get(), new Item.Properties()))

    val redstoneBlocksTab: ResourceKey[CreativeModeTab] =
      ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace("redstone_blocks"))
    platform.addToCreativeTab(redstoneBlocksTab, railItem)

    platform.onServerLevelTick(level => EntityMover(level).moveAll())

    config  // force config load (creates the file on first run)
  }
}
