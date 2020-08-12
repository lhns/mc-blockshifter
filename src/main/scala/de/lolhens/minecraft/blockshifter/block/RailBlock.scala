package de.lolhens.minecraft.blockshifter.block

import de.lolhens.minecraft.blockshifter.util.WorldUtil
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.minecraft.block._
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.piston.PistonBehavior
import net.minecraft.entity.Entity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.util.math.{BlockPos, Box, Direction, Vec3d}
import net.minecraft.util.{BlockMirror, BlockRotation}
import net.minecraft.world.World

import scala.jdk.CollectionConverters._
import scala.util.chaining._

class RailBlock() extends FacingBlock(RailBlock.settings) {
  def getState(facing: Direction, rotated: Boolean): BlockState =
    getStateManager.getDefaultState
      .`with`(FacingBlock.FACING, facing)
      .`with`(RailBlock.ROTATED, java.lang.Boolean.valueOf(rotated))
      .`with`(RailBlock.POWERED, java.lang.Boolean.valueOf(false))

  setDefaultState(getState(facing = Direction.UP, rotated = false))

  override protected def appendProperties(stateManager: StateManager.Builder[Block, BlockState]): Unit =
    stateManager.add(FacingBlock.FACING, RailBlock.ROTATED, RailBlock.POWERED)

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

  private def isRotatedFromSurrounding(world: World, pos: BlockPos, facing: Direction): Boolean = {
    val facingAxis = facing.getAxis
    val axis = movementAxisFromSurrounding(world, pos, facing)
    preferredAxes.iterator.filterNot(_ == facingAxis).indexOf(axis) > 0
  }

  override def getPlacementState(ctx: ItemPlacementContext): BlockState = {
    val facing: Direction = ctx.getPlayerLookDirection.getOpposite
    val rotated = isRotatedFromSurrounding(ctx.getWorld, ctx.getBlockPos, facing)
    getState(facing = facing, rotated = rotated)
  }


  override def neighborUpdate(state: BlockState, world: World, pos: BlockPos, block: Block, fromPos: BlockPos, notify: Boolean): Unit = {
    var newState: BlockState = state

    if (block.is(this) || world.getBlockState(fromPos).isOf(this))
      newState.tap { state =>
        val facing = state.get(FacingBlock.FACING)
        val rotated = state.get(RailBlock.ROTATED)
        val newRotated = isRotatedFromSurrounding(world, pos, facing)
        if (newRotated != rotated) {
          newState = state.`with`(RailBlock.ROTATED, java.lang.Boolean.valueOf(newRotated))
          world.setBlockState(pos, newState, 2)
        }
      }

    val isPowered = world.isReceivingRedstonePower(pos);
    if (isPowered != state.get(RailBlock.POWERED))
      newState.tap { state =>
        newState = state.`with`(RailBlock.POWERED, java.lang.Boolean.valueOf(isPowered))
        world.setBlockState(pos, newState, 2)

        if (isPowered) {
          shiftBlocks(world, pos, newState)
        }
      }
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

  private def shiftBlocks(world: World, pos: BlockPos, state: BlockState, reverse: Boolean = false): Boolean = {
    val facing = state.get(FacingBlock.FACING)
    val rotated = state.get(RailBlock.ROTATED)
    val direction = movementDirection(facing, rotated).pipe(e => if (reverse) e.getOpposite else e)
    shiftBlocks(world, pos, facing, direction)
  }

  private val air = Blocks.AIR.getDefaultState

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

    val neighborAlreadyPowered =
      List(movementDirection, movementDirection.getOpposite)
        .iterator
        .map(pos.offset)
        .filter(isThisRail)
        .exists(world.getBlockState(_).get(RailBlock.POWERED))

    if (!neighborAlreadyPowered) {
      val thisRailStart = follow(pos, movementDirection.getOpposite).takeWhile(isThisRail).toList.last
      val thisRailLength = follow(thisRailStart, movementDirection).takeWhile(isThisRail).size

      //println("this rail length: " + thisRailLength)
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
            //println("length: " + railLength)

            val alreadyPowered =
              (follow(railStart, movementDirection).take(railLength) ++
                follow(otherRailPos, movementDirection).take(railLength))
                .filterNot(_ == pos)
                .exists(world.getBlockState(_).get(RailBlock.POWERED))

            if (!alreadyPowered) {
              def betweenRails(posOnRail: BlockPos): Iterator[BlockPos] =
                follow(posOnRail.offset(facing), facing).take(railDistance - 1)

              def isEmpty(state: BlockState): Boolean =
                state.isAir || (state.getPistonBehavior match {
                  case PistonBehavior.IGNORE | PistonBehavior.DESTROY => true
                  case _ => false
                })

              def clearFirstRow(posOnRail: BlockPos): Boolean = {
                val row = betweenRails(posOnRail).map(pos => (pos, world.getBlockState(pos))).toSeq
                val rowEmpty = row.forall {
                  case (_, state) =>
                    isEmpty(state)
                }
                if (rowEmpty) row.foreach {
                  case (pos, state) => if (!state.isAir && state.getPistonBehavior == PistonBehavior.DESTROY) {
                    val blockEntity = if (state.getBlock.hasBlockEntity) world.getBlockEntity(pos) else null
                    Block.dropStacks(state, world.getWorld, pos, blockEntity);
                  }
                }
                rowEmpty
              }

              def isRowMovable(posOnRail: BlockPos): Boolean =
                betweenRails(posOnRail).foldLeft(false) { (nonEmptyRow, pos) =>
                  val state = world.getBlockState(pos)
                  if (!PistonBlock.isMovable(state, world, pos, movementDirection, true, movementDirection))
                    return false

                  nonEmptyRow || !isEmpty(state)
                }

              val emptyRowsStart =
                follow(railStart, movementDirection)
                  .take(railLength)
                  .takeWhile(!isRowMovable(_))
                  .size

              if (emptyRowsStart < railLength - 1) {
                val railEnd: BlockPos = railStart.offset(movementDirection, railLength - 1)

                val emptyRowsEnd =
                  follow(railEnd, movementDirection.getOpposite)
                    .take(railLength)
                    .takeWhile(!isRowMovable(_))
                    .size

                val overhangStart =
                  if (emptyRowsStart > 0) -emptyRowsStart
                  else
                    follow(railStart.offset(movementDirection.getOpposite), movementDirection.getOpposite)
                      .take(emptyRowsEnd)
                      .takeWhile(isRowMovable)
                      .size

                val overhangEnd =
                  if (emptyRowsEnd > 0) -emptyRowsEnd
                  else
                    follow(railEnd.offset(movementDirection), movementDirection)
                      .take(emptyRowsStart)
                      .takeWhile(isRowMovable)
                      .size

                println("start: " + overhangStart + " end: " + overhangEnd)

                //println("overhang start: " + overhangStart)
                //println("overhang end: " + overhangEnd)

                val shiftStart: BlockPos = railStart.offset(movementDirection, -overhangStart)
                val shiftLength: Int = railLength + overhangStart + overhangEnd
                val shiftEnd: BlockPos = shiftStart.offset(movementDirection, shiftLength - 1)

                if (clearFirstRow(shiftEnd.offset(movementDirection))) {
                  //println("shift start: " + shiftStart)
                  //println("shift end: " + shiftStart)

                  val stateList: Seq[(BlockPos, BlockPos, BlockState, Option[BlockEntity])] =
                    follow(shiftEnd, movementDirection.getOpposite)
                      .take(shiftLength)
                      .flatMap(betweenRails(_).map { pos =>
                        val offset = pos.offset(movementDirection)
                        val state: BlockState = world.getBlockState(pos)
                        val entityOption: Option[BlockEntity] = Option(world.getBlockEntity(pos))
                        (pos, offset, state, entityOption)
                      })
                      .toSeq

                  stateList.foreach {
                    case (pos, _, _, _) =>
                      world.removeBlockEntity(pos)
                      world.setBlockState(pos, air, 2 | 16 | 32 | 64)
                  }

                  stateList.foreach {
                    case (_, offset, state, entityOption) =>
                      entityOption.foreach(_.cancelRemoval())
                      WorldUtil.setBlockStateWithBlockEntity(world, offset, state, entityOption.orNull, 1 | 2 | 64)
                  }

                  betweenRails(shiftStart).foreach { pos =>
                    val flags = (1 | 2 | 64) & -34
                    val depth = 512
                    air.updateNeighbors(world, pos, flags, depth)
                    air.prepare(world, pos, flags, depth)
                  }

                  val box: Box = {
                    val a: BlockPos = shiftStart.offset(facing)
                    val b: BlockPos = shiftEnd.offset(facing, railDistance - 1)

                    val minX = Math.min(a.getX, b.getX)
                    val minY = Math.min(a.getY, b.getY)
                    val minZ = Math.min(a.getZ, b.getZ)
                    val maxX = Math.max(a.getX, b.getX) + 1
                    val maxY = Math.max(a.getY, b.getY) + 1.01
                    val maxZ = Math.max(a.getZ, b.getZ) + 1

                    new Box(minX, minY, minZ, maxX, maxY, maxZ)
                  }

                  world.getNonSpectatingEntities(classOf[Entity], box).iterator.asScala.foreach { entity =>
                    val vec: Vec3d = Vec3d.of(movementDirection.getVector)
                    val newPos = entity.getPos.add(vec)
                    entity.teleport(newPos.getX, newPos.getY, newPos.getZ)
                  }

                  return true
                }
              }
            }
        }
    }

    false
  }
}

object RailBlock {
  private val settings =
    FabricBlockSettings
      .of(Material.STONE)
      .hardness(2.0F)

  val ROTATED: BooleanProperty = BooleanProperty.of("rotated")
  val POWERED: BooleanProperty = BooleanProperty.of("powered")

  val maxRailDistance: Int = 64
}
