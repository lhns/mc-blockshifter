package de.lolhens.minecraft.blockshifter.fabric

import de.lolhens.minecraft.blockshifter.{BlockshifterMod, BlockshifterPlatform}
import net.fabricmc.api.ModInitializer

object BlockshifterModFabric extends ModInitializer {
  override def onInitialize(): Unit = {
    BlockshifterPlatform.install(new FabricPlatform)
    BlockshifterMod.init()
  }
}
