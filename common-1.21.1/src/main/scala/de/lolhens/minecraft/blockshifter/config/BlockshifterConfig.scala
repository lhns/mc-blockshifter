package de.lolhens.minecraft.blockshifter.config

import de.lolhens.minecraft.blockshifter.config.Config.Commented
import de.lolhens.minecraft.blockshifter.config.Config.Implicits.{*, given}
import io.circe.Codec
import io.circe.derivation.ConfiguredCodec

case class BlockshifterConfig(
  updateConfig: Commented[Boolean] = true ->
    "Automatically update config when the structure changes in new versions",

  moveBlockEntities: Commented[Boolean] = false ->
    ("Allow rails to move blocks with PushReaction.BLOCK or block entities (chests, furnaces, etc.).\n" +
      "When false (default), only blocks vanilla pistons would move are shifted; block-entity-bearing\n" +
      "blocks are left in place. When true, the rail moves them with their contents preserved via the\n" +
      "WorldUtil/LevelChunkMixin path.")
) derives ConfiguredCodec

object BlockshifterConfig extends Config[BlockshifterConfig] {
  override lazy val default: BlockshifterConfig = BlockshifterConfig()

  override def shouldUpdateConfig(config: BlockshifterConfig): Boolean = config.updateConfig.value

  override protected def codec: Codec[BlockshifterConfig] = makeCodec
}
