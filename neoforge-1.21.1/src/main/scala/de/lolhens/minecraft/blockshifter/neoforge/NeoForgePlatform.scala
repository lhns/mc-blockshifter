package de.lolhens.minecraft.blockshifter.neoforge

import de.lolhens.minecraft.blockshifter.{BlockshifterMod, BlockshifterPlatform}
import net.minecraft.core.registries.Registries
import net.minecraft.resources.{ResourceKey, ResourceLocation}
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.{CreativeModeTab, Item}
import net.minecraft.world.level.block.Block
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent
import net.neoforged.neoforge.registries.RegisterEvent

import java.util.function.Supplier
import scala.collection.mutable

class NeoForgePlatform(modBus: IEventBus) extends BlockshifterPlatform {
  import NeoForgePlatform._

  private val pendingBlocks =
    mutable.ArrayBuffer.empty[(String, () => Block, LazyRef[Block])]
  private val pendingItems =
    mutable.ArrayBuffer.empty[(String, () => Item, LazyRef[Item])]
  private val creativeTabAdditions =
    mutable.ArrayBuffer.empty[(ResourceKey[CreativeModeTab], Supplier[_ <: Item])]
  private val tickCallbacks =
    mutable.ArrayBuffer.empty[ServerLevel => Unit]

  modBus.addListener { (e: RegisterEvent) =>
    val key = e.getRegistryKey
    if (key == Registries.BLOCK) flushBlocks(e)
    else if (key == Registries.ITEM) flushItems(e)
  }

  modBus.addListener { (e: BuildCreativeModeTabContentsEvent) =>
    creativeTabAdditions.foreach { case (tabKey, item) =>
      if (e.getTabKey == tabKey) e.accept(item.get())
    }
  }

  NeoForge.EVENT_BUS.addListener { (e: LevelTickEvent.Pre) =>
    e.getLevel match {
      case sl: ServerLevel => tickCallbacks.foreach(_(sl))
      case _               =>
    }
  }

  private def rl(path: String): ResourceLocation =
    ResourceLocation.fromNamespaceAndPath(BlockshifterMod.id, path)

  override def registerBlock[B <: Block](id: String, factory: () => B): Supplier[B] = {
    val slot = new LazyRef[B]
    pendingBlocks.append(
      (id, factory.asInstanceOf[() => Block], slot.asInstanceOf[LazyRef[Block]])
    )
    slot
  }

  override def registerItem[I <: Item](id: String, factory: () => I): Supplier[I] = {
    val slot = new LazyRef[I]
    pendingItems.append(
      (id, factory.asInstanceOf[() => Item], slot.asInstanceOf[LazyRef[Item]])
    )
    slot
  }

  override def addToCreativeTab(tabKey: ResourceKey[CreativeModeTab], item: Supplier[_ <: Item]): Unit =
    creativeTabAdditions.append((tabKey, item))

  override def onServerLevelTick(callback: ServerLevel => Unit): Unit =
    tickCallbacks.append(callback)

  private def flushBlocks(e: RegisterEvent): Unit =
    pendingBlocks.foreach { case (id, factory, slot) =>
      val b = factory()
      e.register(Registries.BLOCK, rl(id), () => b)
      slot.set(b)
    }

  private def flushItems(e: RegisterEvent): Unit =
    pendingItems.foreach { case (id, factory, slot) =>
      val i = factory()
      e.register(Registries.ITEM, rl(id), () => i)
      slot.set(i)
    }
}

object NeoForgePlatform {
  private final class LazyRef[T] extends Supplier[T] {
    @volatile private var value: T = _
    def set(v: T): Unit = value = v
    override def get(): T = {
      val v = value
      if (v == null)
        throw new IllegalStateException("Registry entry accessed before RegisterEvent fired")
      v
    }
  }
}
