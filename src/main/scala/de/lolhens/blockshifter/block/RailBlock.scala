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

import scala.util.chaining._

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

  private val preferredAxes: Array[Direction.Axis] =
    Array(Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z)

  private def movementAxisFromSurrounding(world: World, pos: BlockPos, facing: Direction): Direction.Axis = {
    val facingAxis = facing.getAxis
    Direction.values
      .iterator
      .filterNot(_.getAxis == facingAxis)
      .find { direction =>
        val state = world.getBlockState(pos.offset(direction))
        state.isOf(this) && state.get(FacingBlock.FACING) == facing
      }
      .map(_.getAxis)
      .getOrElse(preferredAxes.iterator.filterNot(_ == facingAxis).next())
  }

  def movementDirectionFromBlockState(state: BlockState): Direction = {
    val facing = state.get(FacingBlock.FACING)
    val rotated = state.get(RailBlock.ROTATED)
    movementDirection(facing, rotated)
  }

  def movementDirection(facing: Direction, rotated: Boolean): Direction = {
    val facingAxis = facing.getAxis
    val opposite = Direction.values.iterator.filter(_.getAxis == facingAxis).indexOf(facing) > 0
    val movementAxis = preferredAxes.iterator.filterNot(_ == facing.getAxis).drop(if (rotated) 1 else 0).next()
    Direction.values.iterator.filter(_.getAxis == movementAxis).drop(if (opposite) 1 else 0).next()
  }

  private def isRotated(world: World, pos: BlockPos, facing: Direction): Boolean = {
    val facingAxis = facing.getAxis
    val axis = movementAxisFromSurrounding(world, pos, facing)
    preferredAxes.iterator.filterNot(_ == facingAxis).indexOf(axis) > 0
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

  private def shiftBlocks(world: World, pos: BlockPos, reverse: Boolean): Boolean = {
    val state = world.getBlockState(pos)
    val facing = state.get(FacingBlock.FACING)
    val rotated = state.get(RailBlock.ROTATED)
    val direction = movementDirection(facing, rotated).pipe(e => if (reverse) e.getOpposite else e)
    shiftBlocks(world, pos, facing, direction)
  }

  private def shiftBlocks(world: World, pos: BlockPos, facing: Direction, movementDirection: Direction): Boolean = {
    def follow(start: BlockPos, direction: Direction): Iterator[BlockPos] =
      Iterator.iterate(start)(_.offset(direction))

    def isRail(pos: BlockPos, other: Boolean): Boolean = {
      val state = world.getBlockState(pos)
      state.isOf(this) && {
        val railFacing = state.get(FacingBlock.FACING)
        railFacing == (if (other) facing.getOpposite else facing) &&
          movementDirectionFromBlockState(state).getAxis == movementDirection.getAxis
      }
    }

    def isThisRail(pos: BlockPos): Boolean = isRail(pos, other = false)

    def isOtherRail(pos: BlockPos): Boolean = isRail(pos, other = true)

    def isAir(pos: BlockPos) = world.getBlockState(pos).isAir

    val thisRailStart = follow(pos, movementDirection.getOpposite).takeWhile(isThisRail).toList.last
    val thisRailLength = follow(thisRailStart, movementDirection).takeWhile(isThisRail).size

    //println("rail length: " + thisRailLength)
    //println("facing: " + facing)
    //println("move direction: " + movementDirection)

    follow(thisRailStart, movementDirection)
      .take(thisRailLength)
      .zipWithIndex
      .flatMap {
        case (railStart, railStartOffset) =>
          follow(railStart, facing)
            .take(RailBlock.maxRailDistance)
            .zipWithIndex
            .drop(1)
            .find(e => isOtherRail(e._1))
            .filter(_._2 > 0)
            .map {
              case (otherRailPos, railDistance) =>
                (railStart, railStartOffset, otherRailPos, railDistance)
            }
      }
      .nextOption()
      .foreach {
        case (railStart, railStartOffset, otherRailPos, railDistance) =>
          val otherRailLength = follow(otherRailPos, movementDirection).takeWhile(isOtherRail).size
          val railLength = Math.min(thisRailLength - railStartOffset, otherRailLength)

          //println("distance: " + railDistance)
          //println("real length: " + railLength)

          def betweenRails(posOnRail: BlockPos) =
            follow(posOnRail.offset(facing), facing).take(railDistance - 1)

          val emptyRowsStart =
            follow(railStart, movementDirection)
              .take(railLength)
              .takeWhile(betweenRails(_).forall(isAir))
              .size

          if (emptyRowsStart < railLength - 1) {
            val railEnd: BlockPos = railStart.offset(movementDirection, railLength - 1)

            val emptyRowsEnd =
              follow(railEnd, movementDirection.getOpposite)
                .take(railLength)
                .takeWhile(betweenRails(_).forall(isAir))
                .size

            val overhangStart =
              if (emptyRowsStart > 0) -emptyRowsStart
              else
                follow(railStart.offset(movementDirection.getOpposite), movementDirection.getOpposite)
                  .take(emptyRowsEnd)
                  .takeWhile(!betweenRails(_).forall(isAir))
                  .size

            val overhangEnd =
              if (emptyRowsEnd > 0) -emptyRowsEnd
              else
                follow(railEnd.offset(movementDirection), movementDirection)
                  .take(emptyRowsStart)
                  .takeWhile(!betweenRails(_).forall(isAir))
                  .size

            val shiftStart: BlockPos = railStart.offset(movementDirection, -overhangStart)
            val shiftLength: Int = railLength + overhangStart + overhangEnd
            val shiftEnd: BlockPos = shiftStart.offset(movementDirection, shiftLength - 1)

            if (betweenRails(shiftEnd.offset(movementDirection)).forall(isAir)) {
              follow(shiftEnd, movementDirection.getOpposite)
                .take(shiftLength)
                .foreach(betweenRails(_).foreach { pos =>
                  world.setBlockState(pos.offset(movementDirection), world.getBlockState(pos))
                  world.removeBlock(pos, false)
                })

              return true
            }
          }
      }

    false
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

