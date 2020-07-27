package de.lolhens.blockshifter.block

import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.minecraft.block._
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.{BlockPos, Direction}
import net.minecraft.util.{ActionResult, Hand}
import net.minecraft.world.World

class RailBlock() extends Block(RailBlock.settings) {
  private def shiftBlocks(world: World, pos: BlockPos, facing: Direction, moveDirection: Direction): Unit = {
    def follow(start: BlockPos, direction: Direction): Iterator[BlockPos] =
      Iterator.iterate(start)(_.offset(direction))

    def isRail(pos: BlockPos): Boolean = world.getBlockState(pos).isOf(this)

    def isAir(pos: BlockPos): Boolean = world.getBlockState(pos).isAir

    val railStart: BlockPos = follow(pos, moveDirection.getOpposite).takeWhile(isRail).toList.last
    val thisRailLength: Int = follow(railStart, moveDirection).takeWhile(isRail).size

    follow(railStart, facing)
      .take(RailBlock.maxRailDistance)
      .zipWithIndex
      .drop(1)
      .find(e => isRail(e._1))
      .filter(_._2 > 0)
      .foreach {
        case (otherRailPos, railDistance) =>
          val otherRailLength: Int = follow(otherRailPos, moveDirection).takeWhile(isRail).size
          val railLength: Int = Math.min(thisRailLength, otherRailLength)

          def betweenRails(posOnRail: BlockPos): Iterator[BlockPos] =
            follow(posOnRail.offset(facing), facing).take(railDistance - 1)

          val emptyRowsStart: Int =
            follow(railStart, moveDirection)
              .take(railLength)
              .takeWhile(betweenRails(_).forall(isAir))
              .size

          if (emptyRowsStart < railLength - 1) {
            val railEnd: BlockPos = railStart.offset(moveDirection, railLength - 1)

            val emptyRowsEnd: Int =
              follow(railEnd, moveDirection.getOpposite)
                .take(railLength)
                .takeWhile(betweenRails(_).forall(isAir))
                .size

            val overhangStart: Int =
              if (emptyRowsStart > 0) -emptyRowsStart
              else
                follow(railStart.offset(moveDirection.getOpposite), moveDirection.getOpposite)
                  .take(emptyRowsEnd)
                  .takeWhile(!betweenRails(_).forall(isAir))
                  .size

            val overhangEnd: Int =
              if (emptyRowsEnd > 0) -emptyRowsEnd
              else
                follow(railEnd.offset(moveDirection), moveDirection)
                  .take(emptyRowsStart)
                  .takeWhile(!betweenRails(_).forall(isAir))
                  .size

            val shiftStart: BlockPos = railStart.offset(moveDirection, -overhangStart)
            val shiftLength: Int = railLength + overhangStart + overhangEnd
            val shiftEnd: BlockPos = shiftStart.offset(moveDirection, shiftLength - 1)

            if (betweenRails(shiftEnd.offset(moveDirection)).forall(isAir)) {
              follow(shiftEnd, moveDirection.getOpposite)
                .take(shiftLength)
                .foreach(betweenRails(_).foreach { pos =>
                  world.setBlockState(pos.offset(moveDirection), world.getBlockState(pos))
                  world.removeBlock(pos, false)
                })
            }
          }
      }

  }

  override def onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult = {
    shiftBlocks(world, pos, Direction.NORTH, Direction.UP)
    ActionResult.SUCCESS
  }
}

object RailBlock {
  private val settings =
    FabricBlockSettings
      .of(Material.STONE)
      .requiresTool()
      .hardness(2.0F)

  val maxRailDistance: Int = 64
}

