package de.lolhens.minecraft.blockshifter.neoforge

import de.lolhens.minecraft.blockshifter.{BlockshifterMod, BlockshifterPlatform}
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod

@Mod("blockshifter")
class BlockshifterModNeoForge(modEventBus: IEventBus) {
  BlockshifterPlatform.install(new NeoForgePlatform(modEventBus))
  BlockshifterMod.init()
}
