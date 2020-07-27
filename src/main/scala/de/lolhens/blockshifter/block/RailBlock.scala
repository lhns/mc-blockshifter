package de.lolhens.blockshifter.block

import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.minecraft.block._
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.{BlockPos, Direction}
import net.minecraft.util.{ActionResult, BlockMirror, BlockRotation, Hand}
import net.minecraft.world.World

class RailBlock() extends FacingBlock(RailBlock.settings) {
  def getState(facing: Direction, rotated: Boolean): BlockState =
    getStateManager.getDefaultState.`with`(FacingBlock.FACING, facing).`with`(RailBlock.ROTATED, java.lang.Boolean.valueOf(rotated))

  setDefaultState(getState(facing = Direction.UP, rotated = false))

  override protected def appendProperties(stateManager: StateManager.Builder[Block, BlockState]): Unit =
    stateManager.add(FacingBlock.FACING, RailBlock.ROTATED)

  override def rotate(state: BlockState, rotation: BlockRotation): BlockState =
    state.`with`(FacingBlock.FACING, rotation.rotate(state.get(FacingBlock.FACING)))

  override def mirror(state: BlockState, mirror: BlockMirror): BlockState =
    state.rotate(mirror.getRotation(state.get(FacingBlock.FACING)))

  private def movementAxis(world: World, pos: BlockPos, facing: Direction): Direction.Axis = {
    val facingAxis = facing.getAxis
    Direction.values().iterator
      .filterNot(_.getAxis == facingAxis)
      .find { direction =>
        val state = world.getBlockState(pos.offset(direction))
        state.isOf(this) && state.get(FacingBlock.FACING) == facing
      }
      .map(_.getAxis)
      .getOrElse(List(Direction.Axis.Y, Direction.Axis.X).find(_ != facingAxis).get)
  }

  /*def axisFromBlockState(state: BlockState): Direction.Axis = {
    val facing = state.get(FacingBlock.FACING)
    val rotated = state.get(RailBlock.ROTATED)
    facing.getAxis match {
      case Direction.Axis.X => axis != Direction.Axis.Y
      case Direction.Axis.Y => axis != Direction.Axis.X
      case Direction.Axis.Z => axis != Direction.Axis.Y
    }
  }*/

  private def isRotated(world: World, pos: BlockPos, facing: Direction): Boolean = {
    val axis = movementAxis(world, pos, facing)
    facing.getAxis match {
      case Direction.Axis.X => axis != Direction.Axis.Y
      case Direction.Axis.Y => axis != Direction.Axis.X
      case Direction.Axis.Z => axis != Direction.Axis.Y
    }
  }

  override def getPlacementState(ctx: ItemPlacementContext): BlockState = {
    val facing: Direction = ctx.getPlayerLookDirection.getOpposite
    val rotated = isRotated(ctx.getWorld, ctx.getBlockPos, facing)
    getState(facing = facing, rotated = rotated)
  }


  override def neighborUpdate(state: BlockState, world: World, pos: BlockPos, block: Block, fromPos: BlockPos, notify: Boolean): Unit =
    if (block.is(this) || world.getBlockState(fromPos).isOf(this)) {
      val facing = state.get(FacingBlock.FACING)
      val rotated = isRotated(world, pos, facing)
      world.setBlockState(pos, state.`with`(RailBlock.ROTATED, java.lang.Boolean.valueOf(rotated)))
    }

  private def shiftBlocks(world: World, pos: BlockPos, facing: Direction, moveDirection: Direction): Boolean = {
    def follow(start: BlockPos, direction: Direction): Iterator[BlockPos] =
      Iterator.iterate(start)(_.offset(direction))

    def isRail(pos: BlockPos, other: Boolean): Boolean = {
      val state = world.getBlockState(pos)
      state.isOf(this) && {
        val railFacing = state.get(FacingBlock.FACING)
        railFacing == (if (other) facing.getOpposite else facing) &&
          movementAxis(world, pos, railFacing) == moveDirection.getAxis
      }
    }

    def isAir(pos: BlockPos) = world.getBlockState(pos).isAir

    val railStart = follow(pos, moveDirection.getOpposite).takeWhile(isRail(_, other = false)).toList.last
    val thisRailLength = follow(railStart, moveDirection).takeWhile(isRail(_, other = false)).size

    println("rail length: " + thisRailLength)
    println("facing: " + facing)
    println("move direction: " + moveDirection)

    follow(railStart, facing)
      .take(RailBlock.maxRailDistance)
      .zipWithIndex
      .drop(1)
      .find(e => isRail(e._1, other = true))
      .filter(_._2 > 0)
      .foreach {
        case (otherRailPos, railDistance) =>
          val otherRailLength = follow(otherRailPos, moveDirection).takeWhile(isRail(_, other = true)).size
          val railLength = Math.min(thisRailLength, otherRailLength)

          println("distance: " + railDistance)
          println("real length: " + railLength)

          def betweenRails(posOnRail: BlockPos) =
            follow(posOnRail.offset(facing), facing).take(railDistance - 1)

          val emptyRowsStart =
            follow(railStart, moveDirection)
              .take(railLength)
              .takeWhile(betweenRails(_).forall(isAir))
              .size

          if (emptyRowsStart < railLength - 1) {
            val railEnd: BlockPos = railStart.offset(moveDirection, railLength - 1)

            val emptyRowsEnd =
              follow(railEnd, moveDirection.getOpposite)
                .take(railLength)
                .takeWhile(betweenRails(_).forall(isAir))
                .size

            val overhangStart =
              if (emptyRowsStart > 0) -emptyRowsStart
              else
                follow(railStart.offset(moveDirection.getOpposite), moveDirection.getOpposite)
                  .take(emptyRowsEnd)
                  .takeWhile(!betweenRails(_).forall(isAir))
                  .size

            val overhangEnd =
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

              return true
            }
          }
      }

    false
  }

  private def shiftBlocks(world: World, pos: BlockPos, reverse: Boolean): Boolean = {
    val state = world.getBlockState(pos)
    val facing = state.get(FacingBlock.FACING)
    val axis = movementAxis(world, pos, facing)
    val direction = {
      val directions = Direction.values().iterator.filter(_.getAxis == axis)
      if (reverse) directions.next()
      directions.next()
    }
    shiftBlocks(world, pos, facing, direction)
  }

  override def onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult = {
    if (shiftBlocks(world, pos, reverse = false))
      ActionResult.SUCCESS
    else
      ActionResult.PASS
  }
}

object RailBlock {
  private val settings =
    FabricBlockSettings
      .of(Material.STONE)
      .requiresTool()
      .hardness(2.0F)

  val ROTATED: BooleanProperty = BooleanProperty.of("rotated")

  val maxRailDistance: Int = 64
}

