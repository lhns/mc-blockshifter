package de.lolhens.minecraft.blockshifter

import de.lolhens.minecraft.blockshifter.block.RailBlock
import de.lolhens.minecraft.blockshifter.util.EntityMover
import net.minecraft.block.Block
import net.minecraft.item.{BlockItem, Item, ItemGroup}
import net.minecraft.util.ResourceLocation
import net.minecraft.world.server.ServerWorld
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent.Phase
import net.minecraftforge.event.{RegistryEvent, TickEvent}
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.{ModContainer, ModLoadingContext}
import org.apache.logging.log4j.LogManager

@Mod("blockshifter")
object BlockshifterMod {
  val container: ModContainer = ModLoadingContext.get().getActiveContainer
  private val logger = LogManager.getLogger

  val RAIL_BLOCK_ID = new ResourceLocation(container.getModId, "rail")
  val RAIL_BLOCK: Block = new RailBlock()

  FMLJavaModLoadingContext.get.getModEventBus.addListener { _: FMLCommonSetupEvent =>
    // setup
  }

  FMLJavaModLoadingContext.get.getModEventBus.addGenericListener(classOf[Block], { event: RegistryEvent.Register[Block] =>
    event.getRegistry.register(RAIL_BLOCK.setRegistryName(RAIL_BLOCK_ID))
  })

  FMLJavaModLoadingContext.get.getModEventBus.addGenericListener(classOf[Item], { event: RegistryEvent.Register[Item] =>
    event.getRegistry.register(new BlockItem(RAIL_BLOCK, new Item.Properties().group(ItemGroup.REDSTONE)).setRegistryName(RAIL_BLOCK_ID))
  })

  MinecraftForge.EVENT_BUS.addListener { event: TickEvent.WorldTickEvent =>
    (event.phase, event.world) match {
      case (Phase.START, serverWorld: ServerWorld) =>
        EntityMover(serverWorld).moveAll()

      case _ =>
    }
  }
}
