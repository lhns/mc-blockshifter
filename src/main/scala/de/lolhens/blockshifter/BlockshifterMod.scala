package de.lolhens.blockshifter

import de.lolhens.blockshifter.block.RailBlock
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.metadata.ModMetadata
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.item.{BlockItem, Item, ItemGroup}
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry

import scala.jdk.CollectionConverters._

object BlockshifterMod extends ModInitializer {
  val metadata: ModMetadata = {
    FabricLoader.getInstance().getEntrypointContainers("main", classOf[ModInitializer])
      .iterator().asScala.find(this eq _.getEntrypoint).get.getProvider.getMetadata
  }

  val railBlockId = new Identifier(metadata.getId, "rail")
  val railBlock: RailBlock = new RailBlock()
  val railBlockEntity: BlockEntityType[_] = railBlock.blockEntityType

  override def onInitialize(): Unit = {
    Registry.register(Registry.BLOCK, railBlockId, railBlock)
    Registry.register(Registry.ITEM, railBlockId, new BlockItem(railBlock, new Item.Settings().group(ItemGroup.BUILDING_BLOCKS)))
    Registry.register(Registry.BLOCK_ENTITY_TYPE, railBlockId, railBlockEntity)

    //ServerTickEvents.START_WORLD_TICK.register()
    //ServerTickEvents.END_WORLD_TICK.register()
  }
}
