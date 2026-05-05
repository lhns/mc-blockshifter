package de.lolhens.minecraft.blockshifter.block

import de.lolhens.minecraft.blockshifter.util.{EntityMover, WorldUtil}
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.piston.PistonBaseBlock
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState, StateDefinition}
import net.minecraft.world.level.block.{Block, Blocks, DirectionalBlock, Mirror, Rotation}
import net.minecraft.world.level.material.PushReaction
import net.minecraft.world.phys.{AABB, Vec3}

import scala.jdk.CollectionConverters._
import scala.util.chaining._

class RailBlock() extends DirectionalBlock(RailBlock.settings) {
  def getState(facing: Direction, rotated: Boolean): BlockState =
    getStateDefinition.any
      .setValue(DirectionalBlock.FACING, facing)
      .setValue(RailBlock.ROTATED, java.lang.Boolean.valueOf(rotated))
      .setValue(RailBlock.POWERED, java.lang.Boolean.valueOf(false))

  registerDefaultState(getState(facing = Direction.UP, rotated = false))

  override protected def createBlockStateDefinition(builder: StateDefinition.Builder[Block, BlockState]): Unit =
    builder.add(DirectionalBlock.FACING, RailBlock.ROTATED, RailBlock.POWERED)

  override def rotate(state: BlockState, rotation: Rotation): BlockState =
    state.setValue(DirectionalBlock.FACING, rotation.rotate(state.getValue(DirectionalBlock.FACING)))

  override def mirror(state: BlockState, mirror: Mirror): BlockState =
    state.rotate(mirror.getRotation(state.getValue(DirectionalBlock.FACING)))

  override protected def codec: com.mojang.serialization.MapCodec[_ <: DirectionalBlock] =
    RailBlock.CODEC

  private val preferredAxes: Array[Direction.Axis] =
    Array(Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z)

  private def preferredMovementAxisFromSurrounding(world: Level,
                                                   pos: BlockPos,
                                                   facing: Direction,
                                                   preferredAxis: Option[Direction.Axis],
                                                   rotateSurrounding: Boolean = true): Option[Direction.Axis] = {
    val facingAxis = facing.getAxis
    val connectedAxes = Direction.values
      .iterator
      .filterNot(_.getAxis == facingAxis)
      .filter { direction =>
        val offset: BlockPos = pos.relative(direction)
        val state = world.getBlockState(offset)
        state.is(this) && (state.getBlock match {
          case rail: RailBlock =>
            val otherFacing = state.getValue(DirectionalBlock.FACING)
            otherFacing == facing && {
              val otherRotated = state.getValue(RailBlock.ROTATED)
              val otherMovementAxis = movementAxis(otherFacing, otherRotated)
              otherMovementAxis == direction.getAxis || (rotateSurrounding && {
                rail.preferredMovementAxisFromSurrounding(
                  world,
                  offset,
                  otherFacing,
                  Some(otherMovementAxis),
                  rotateSurrounding = false
                )
                  .forall(_ == direction.getAxis)
              })
            }
          case _ => false
        })
      }
      .map(_.getAxis)
      .toArray

    preferredAxis.filter(connectedAxes.contains)
      .orElse(connectedAxes.headOption)
  }

  private def movementAxisFromSurrounding(world: Level,
                                          pos: BlockPos,
                                          facing: Direction,
                                          preferredAxis: Option[Direction.Axis]): Direction.Axis = {
    val facingAxis = facing.getAxis
    preferredMovementAxisFromSurrounding(world, pos, facing, preferredAxis)
      .getOrElse {
        val axes = preferredAxes
          .iterator
          .filterNot(_ == facingAxis)
          .toArray

        preferredAxis.filter(axes.contains)
          .getOrElse(axes.head)
      }
  }

  private def isRotatedFromSurrounding(world: Level, pos: BlockPos, facing: Direction, preferRotated: Option[Boolean]): Boolean = {
    val preferredAxis = preferRotated.map(movementAxis(facing, _))
    val axis = movementAxisFromSurrounding(world, pos, facing, preferredAxis)
    preferredAxes.iterator.filterNot(_ == facing.getAxis).indexOf(axis) > 0
  }

  override def getStateForPlacement(ctx: BlockPlaceContext): BlockState = {
    val facing: Direction = ctx.getNearestLookingDirection.getOpposite
    val rotated = isRotatedFromSurrounding(ctx.getLevel, ctx.getClickedPos, facing, None)
    getState(facing = facing, rotated = rotated)
  }

  override def neighborChanged(state: BlockState, world: Level, pos: BlockPos, block: Block, fromPos: BlockPos, notify: Boolean): Unit = {
    var newState: BlockState = state

    if (block == this || world.getBlockState(fromPos).is(this))
      newState.tap { state =>
        val facing = state.getValue(DirectionalBlock.FACING)
        val rotated = state.getValue(RailBlock.ROTATED)
        val newRotated = isRotatedFromSurrounding(world, pos, facing, Some(rotated))
        if (newRotated != rotated) {
          newState = state.setValue(RailBlock.ROTATED, java.lang.Boolean.valueOf(newRotated))
          world.setBlock(pos, newState, 3)
        }
      }

    val isPowered = world.hasNeighborSignal(pos)
    if (isPowered != state.getValue(RailBlock.POWERED))
      newState.tap { state =>
        newState = state.setValue(RailBlock.POWERED, java.lang.Boolean.valueOf(isPowered))
        world.setBlock(pos, newState, 2)

        if (isPowered) {
          world match {
            case serverWorld: ServerLevel =>
              shiftBlocks(serverWorld, pos, newState)

            case _ =>
          }
        }
      }
  }

  def movementDirectionFromBlockState(state: BlockState): Direction = {
    val facing = state.getValue(DirectionalBlock.FACING)
    val rotated = state.getValue(RailBlock.ROTATED)
    movementDirection(facing, rotated)
  }

  def movementAxis(facing: Direction, rotated: Boolean): Direction.Axis =
    preferredAxes.iterator.filterNot(_ == facing.getAxis).drop(if (rotated) 1 else 0).next()

  def movementDirection(facing: Direction, rotated: Boolean): Direction = {
    val opposite = Direction.values.iterator.filter(_.getAxis == facing.getAxis).indexOf(facing) > 0
    val axis = movementAxis(facing, rotated)
    Direction.values.iterator.filter(_.getAxis == axis).drop(if (opposite) 1 else 0).next()
  }

  private def shiftBlocks(world: ServerLevel, pos: BlockPos, state: BlockState, reverse: Boolean = false): Boolean = {
    val facing = state.getValue(DirectionalBlock.FACING)
    val rotated = state.getValue(RailBlock.ROTATED)
    val direction = movementDirection(facing, rotated).pipe(e => if (reverse) e.getOpposite else e)
    shiftBlocks(world, pos, facing, direction)
  }

  private val air = Blocks.AIR.defaultBlockState

  private def shiftBlocks(world: ServerLevel, pos: BlockPos, facing: Direction, movementDirection: Direction): Boolean = {
    def follow(start: BlockPos, direction: Direction): Iterator[BlockPos] =
      Iterator.iterate(start)(_.relative(direction))

    def isRail(pos: BlockPos, other: Boolean): Boolean = {
      val state = world.getBlockState(pos)
      state.is(this) && {
        val railFacing = state.getValue(DirectionalBlock.FACING)
        railFacing == (if (other) facing.getOpposite else facing) &&
          movementDirectionFromBlockState(state).getAxis == movementDirection.getAxis
      }
    }

    def isThisRail(pos: BlockPos): Boolean = isRail(pos, other = false)

    def isOtherRail(pos: BlockPos): Boolean = isRail(pos, other = true)

    val neighborAlreadyPowered =
      List(movementDirection, movementDirection.getOpposite)
        .iterator
        .map[BlockPos](pos.relative)
        .filter(isThisRail)
        .exists(world.getBlockState(_).getValue(RailBlock.POWERED))

    if (!neighborAlreadyPowered) {
      val thisRailStart = follow(pos, movementDirection.getOpposite).takeWhile(isThisRail).toList.last
      val thisRailLength = follow(thisRailStart, movementDirection).takeWhile(isThisRail).size

      follow(thisRailStart, movementDirection)
        .take(thisRailLength)
        .zipWithIndex
        .flatMap {
          case (railStart, railStartOffset) =>
            follow(railStart, facing)
              .take(RailBlock.maxRailDistance)
              .zipWithIndex
              .drop(1)
              .scanLeft[(Int, Option[(BlockPos, Int)])]((0, None)) {
                case (nop@(recursion, _), otherRail@(otherRailPos, _)) =>
                  if (isOtherRail(otherRailPos)) {
                    if (recursion <= 0)
                      (0, Some(otherRail))
                    else
                      (recursion - 1, None)
                  } else if (isThisRail(otherRailPos)) {
                    (recursion + 1, None)
                  } else {
                    nop
                  }
              }
              .collectFirst(Function.unlift(_._2))
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

            val alreadyPowered =
              (follow(railStart, movementDirection).take(railLength) ++
                follow(otherRailPos, movementDirection).take(railLength))
                .filterNot(_ == pos)
                .exists(world.getBlockState(_).getValue(RailBlock.POWERED))

            if (!alreadyPowered) {
              def betweenRails(posOnRail: BlockPos): Iterator[BlockPos] =
                follow(posOnRail.relative(facing), facing).take(railDistance - 1)

              def isEmpty(pos: BlockPos, state: BlockState): Boolean =
                state.isAir || (state.getPistonPushReaction match {
                  case PushReaction.IGNORE | PushReaction.DESTROY => true
                  case _ => false
                })

              def isMovable(pos: BlockPos, state: BlockState): Boolean =
                PistonBaseBlock.isPushable(state, world, pos, movementDirection, true, movementDirection)

              def areAllEmpty(iterator: IterableOnce[(BlockPos, BlockState)]): Boolean =
                iterator.iterator.forall(e => isEmpty(e._1, e._2))

              def areAllMovable(iterator: IterableOnce[(BlockPos, BlockState)]): Boolean =
                iterator.iterator.forall(e => isMovable(e._1, e._2))

              def isRow(posOnRail: BlockPos, f: Seq[(BlockPos, BlockState)] => Boolean): Boolean =
                f(betweenRails(posOnRail).map(pos => (pos, world.getBlockState(pos))).toSeq)

              def isRowEmptyOrImmovable(posOnRail: BlockPos): Boolean =
                isRow(posOnRail, blocks => areAllEmpty(blocks) || !areAllMovable(blocks))

              def clearFirstRow(posOnRail: BlockPos): Boolean = {
                val row = betweenRails(posOnRail).map(pos => (pos, world.getBlockState(pos))).toSeq
                val rowEmpty = areAllEmpty(row)
                if (rowEmpty) row.foreach {
                  case (pos, state) => if (!state.isAir && state.getPistonPushReaction == PushReaction.DESTROY) {
                    val blockEntity = if (state.hasBlockEntity) world.getBlockEntity(pos) else null
                    Block.dropResources(state, world, pos, blockEntity)
                  }
                }
                rowEmpty
              }

              val emptyRowsStart =
                follow(railStart, movementDirection)
                  .take(railLength)
                  .takeWhile(isRowEmptyOrImmovable)
                  .size

              if (emptyRowsStart < railLength - 1) {
                val railEnd: BlockPos = railStart.relative(movementDirection, railLength - 1)

                val emptyRowsEnd =
                  follow(railEnd, movementDirection.getOpposite)
                    .take(railLength)
                    .takeWhile(isRowEmptyOrImmovable)
                    .size

                val overhangStart =
                  if (emptyRowsStart > 0) -emptyRowsStart
                  else
                    follow(railStart.relative(movementDirection.getOpposite), movementDirection.getOpposite)
                      .take(emptyRowsEnd)
                      .takeWhile(!isRowEmptyOrImmovable(_))
                      .size

                val overhangEnd =
                  if (emptyRowsEnd > 0) -emptyRowsEnd
                  else
                    follow(railEnd.relative(movementDirection), movementDirection)
                      .take(emptyRowsStart)
                      .takeWhile(!isRowEmptyOrImmovable(_))
                      .size

                val shiftStart: BlockPos = railStart.relative(movementDirection, -overhangStart)
                val shiftLength: Int = railLength + overhangStart + overhangEnd
                val shiftEnd: BlockPos = shiftStart.relative(movementDirection, shiftLength - 1)

                if (clearFirstRow(shiftEnd.relative(movementDirection))) {
                  val allRowsMovable =
                    follow(shiftEnd, movementDirection.getOpposite)
                      .take(shiftLength)
                      .forall(isRow(_, areAllMovable))

                  if (allRowsMovable) {
                    val stateList: Seq[(BlockPos, BlockPos, BlockState, Option[net.minecraft.world.level.block.entity.BlockEntity])] =
                      follow(shiftEnd, movementDirection.getOpposite)
                        .take(shiftLength)
                        .flatMap(betweenRails(_).map { pos =>
                          val offset: BlockPos = pos.relative(movementDirection)
                          val state: BlockState = world.getBlockState(pos)
                          val entityOption = Option(world.getBlockEntity(pos))
                          (pos, offset, state, entityOption)
                        })
                        .toSeq

                    stateList.foreach {
                      case (pos, offset, state, entityOption) =>
                        val flags = 2 | 16 | 32 | 64
                        world.removeBlockEntity(pos)
                        world.setBlock(pos, air, flags)
                        entityOption.foreach(_.clearRemoved())
                        WorldUtil.setBlockStateWithBlockEntity(world, offset, state, entityOption.orNull, flags)
                    }

                    def updateBlockAndNeighbors(pos: BlockPos, state: BlockState, extraFlags: Int = 0): Unit = {
                      val flags = (1 | 2 | extraFlags) & -34
                      world.neighborChanged(pos, state.getBlock, pos)
                      state.updateNeighbourShapes(world, pos, flags)
                      state.updateIndirectNeighbourShapes(world, pos, flags)
                    }

                    stateList.foreach {
                      case (_, offset, state, _) =>
                        val newState = Block.updateFromNeighbourShapes(state, world, offset)
                        Block.updateOrDestroy(state, newState, world, offset, 2 | 16 | 64)
                        updateBlockAndNeighbors(offset, newState, extraFlags = 64)
                    }

                    betweenRails(shiftStart).foreach { pos =>
                      updateBlockAndNeighbors(pos, air, extraFlags = 64)
                    }

                    val box: AABB = {
                      val a: BlockPos = shiftStart.relative(facing)
                      val b: BlockPos = shiftEnd.relative(facing, railDistance - 1)

                      val minX = Math.min(a.getX, b.getX)
                      val minY = Math.min(a.getY, b.getY)
                      val minZ = Math.min(a.getZ, b.getZ)
                      val maxX = Math.max(a.getX, b.getX) + 1
                      val maxY = Math.max(a.getY, b.getY) + 1.01
                      val maxZ = Math.max(a.getZ, b.getZ) + 1

                      new AABB(minX, minY, minZ, maxX, maxY, maxZ)
                    }

                    val movementVector: Vec3 = Vec3.atLowerCornerOf(movementDirection.getNormal)
                    world.getEntitiesOfClass(classOf[Entity], box).iterator.asScala.foreach { entity =>
                      EntityMover(world).queueMove(entity, movementVector)
                    }

                    return true
                  }
                }
              }
            }
        }
    }

    false
  }
}

object RailBlock {
  private val settings: BlockBehaviour.Properties =
    BlockBehaviour.Properties.of()
      .strength(2.0F)

  val CODEC: com.mojang.serialization.MapCodec[RailBlock] =
    com.mojang.serialization.MapCodec.unit(() => BlockshifterModForwardRef.RAIL_BLOCK)

  val ROTATED: BooleanProperty = BooleanProperty.create("rotated")
  val POWERED: BooleanProperty = BooleanProperty.create("powered")

  val maxRailDistance: Int = 64
}

private object BlockshifterModForwardRef {
  // Indirection so RailBlock companion object's CODEC can refer back to the singleton
  // without creating a forward-reference cycle in the same file.
  def RAIL_BLOCK: RailBlock = de.lolhens.minecraft.blockshifter.BlockshifterMod.RAIL_BLOCK
}
