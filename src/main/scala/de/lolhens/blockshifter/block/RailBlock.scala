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

    val railStartPos: BlockPos = follow(pos, moveDirection.getOpposite).takeWhile(isRail).toList.last
    val thisRailLength: Int = follow(railStartPos, moveDirection).takeWhile(isRail).size
    println("first length: " + thisRailLength)
    follow(railStartPos, facing)
      .take(RailBlock.maxRailDistance)
      .zipWithIndex
      .drop(1)
      .find(e => isRail(e._1))
      .filter(_._2 > 0)
      .foreach {
        case (otherRailPos, railDistance) =>
          println("distance: " + railDistance)
          val otherRailLength: Int = follow(otherRailPos, moveDirection).takeWhile(isRail).size
          val railLength: Int = Math.min(thisRailLength, otherRailLength)

          def betweenRails(posOnRail: BlockPos): Iterator[BlockPos] =
            follow(posOnRail.offset(facing), facing).take(railDistance - 1)

          val emptyRows: Int = {
            val railEndPos: BlockPos = railStartPos.offset(moveDirection, railLength - 1)
            follow(railEndPos, moveDirection.getOpposite)
              .take(railLength)
              .takeWhile(betweenRails(_).forall(isAir))
              .size
          }

          println("empty rows: " + emptyRows)

          val overhang: Int = Math.max(0, {
            follow(railStartPos, moveDirection.getOpposite)
              .take(emptyRows + 1)
              .takeWhile(!betweenRails(_).forall(isAir))
              .size
          } - 1)

          println("overhang: " + overhang)

          val rowsToMove = railLength - 1 - Math.max(0, emptyRows - 1)
          val rowsToMoveOffset = Math.max(0, rowsToMove - 1)

          val firstRowToMove: BlockPos = railStartPos.offset(moveDirection, rowsToMoveOffset)

          if (betweenRails(firstRowToMove.offset(moveDirection)).forall(isAir)) {
            follow(firstRowToMove, moveDirection.getOpposite)
              .take(rowsToMove + overhang)
              .foreach(betweenRails(_).foreach { pos =>
                world.setBlockState(pos.offset(moveDirection), world.getBlockState(pos))
                world.removeBlock(pos, false)
              })
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

