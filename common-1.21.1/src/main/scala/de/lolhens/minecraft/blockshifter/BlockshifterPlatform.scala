package de.lolhens.minecraft.blockshifter

import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.{CreativeModeTab, Item}
import net.minecraft.world.level.block.Block

import java.nio.file.Path
import java.util.function.Supplier

trait BlockshifterPlatform {
  def configDir: Path

  def registerBlock[B <: Block](id: String, factory: () => B): Supplier[B]

  def registerItem[I <: Item](id: String, factory: () => I): Supplier[I]

  def addToCreativeTab(tabKey: ResourceKey[CreativeModeTab], item: Supplier[_ <: Item]): Unit

  def onServerLevelTick(callback: ServerLevel => Unit): Unit
}

object BlockshifterPlatform {
  @volatile private var impl: BlockshifterPlatform = _

  def install(platform: BlockshifterPlatform): Unit = impl = platform

  def current: BlockshifterPlatform = {
    val p = impl
    if (p == null)
      throw new IllegalStateException(
        "BlockshifterPlatform not installed — each loader's entrypoint must call BlockshifterPlatform.install(...) before BlockshifterMod.init()"
      )
    p
  }
}
