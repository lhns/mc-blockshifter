package de.lolhens.minecraft.blockshifter.fabric;

import net.fabricmc.api.ModInitializer;

public class BlockshifterModFabricEntrypoint implements ModInitializer {
    @Override
    public void onInitialize() {
        BlockshifterModFabric$.MODULE$.onInitialize();
    }
}
