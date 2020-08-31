package de.lolhens.minecraft.blockshifter.block

import de.lolhens.minecraft.blockshifter.util.{EntityMover, WorldUtil}
import net.minecraft.block._
import net.minecraft.block.material.{Material, PushReaction}
import net.minecraft.entity.Entity
import net.minecraft.item.BlockItemUseContext
import net.minecraft.state.{BooleanProperty, StateContainer}
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.math.vector.Vector3d
import net.minecraft.util.math.{AxisAlignedBB, BlockPos}
import net.minecraft.util.{Direction, Mirror, Rotation}
import net.minecraft.world.World
import net.minecraft.world.server.ServerWorld

import scala.jdk.CollectionConverters._
import scala.util.chaining._

class RailBlock() extends DirectionalBlock(RailBlock.settings) {
  def getState(facing: Direction, rotated: Boolean): BlockState =
    getStateContainer.getBaseState
      .`with`(DirectionalBlock.FACING, facing)
      .`with`(RailBlock.ROTATED, java.lang.Boolean.valueOf(rotated))
      .`with`(RailBlock.POWERED, java.lang.Boolean.valueOf(false))

  setDefaultState(getState(facing = Direction.UP, rotated = false))

  override protected def fillStateContainer(stateManager: StateContainer.Builder[Block, BlockState]): Unit =
    stateManager.add(DirectionalBlock.FACING, RailBlock.ROTATED, RailBlock.POWERED)

  override def rotate(state: BlockState, rotation: Rotation): BlockState =
    state.`with`(DirectionalBlock.FACING, rotation.rotate(state.get(DirectionalBlock.FACING)))

  override def mirror(state: BlockState, mirror: Mirror): BlockState =
    state.rotate(mirror.toRotation(state.get(DirectionalBlock.FACING)))

  private val preferredAxes: Array[Direction.Axis] =
    Array(Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z)

  private def movementAxisFromSurrounding(world: World, pos: BlockPos, facing: Direction): Direction.Axis = {
    val facingAxis = facing.getAxis
    Direction.values
      .iterator
      .filterNot(_.getAxis == facingAxis)
      .find { direction =>
        val state = world.getBlockState(pos.offset(direction))
        state.isIn(this) && state.get(DirectionalBlock.FACING) == facing
      }
      .map(_.getAxis)
      .getOrElse(preferredAxes.iterator.filterNot(_ == facingAxis).next())
  }

  private def isRotatedFromSurrounding(world: World, pos: BlockPos, facing: Direction): Boolean = {
    val facingAxis = facing.getAxis
    val axis = movementAxisFromSurrounding(world, pos, facing)
    preferredAxes.iterator.filterNot(_ == facingAxis).indexOf(axis) > 0
  }

  override def getStateForPlacement(ctx: BlockItemUseContext): BlockState = {
    val facing: Direction = ctx.getNearestLookingDirection.getOpposite
    val rotated = isRotatedFromSurrounding(ctx.getWorld, ctx.getPos, facing)
    getState(facing = facing, rotated = rotated)
  }


  override def neighborChanged(state: BlockState, world: World, pos: BlockPos, block: Block, fromPos: BlockPos, notify: Boolean): Unit = {
    var newState: BlockState = state

    if (block.func_235332_a_(this) || world.getBlockState(fromPos).isIn(this))
      newState.tap { state =>
        val facing = state.get(DirectionalBlock.FACING)
        val rotated = state.get(RailBlock.ROTATED)
        val newRotated = isRotatedFromSurrounding(world, pos, facing)
        if (newRotated != rotated) {
          newState = state.`with`(RailBlock.ROTATED, java.lang.Boolean.valueOf(newRotated))
          world.setBlockState(pos, newState, 2)
        }
      }

    val isPowered = world.isBlockPowered(pos)
    if (isPowered != state.get(RailBlock.POWERED))
      newState.tap { state =>
        newState = state.`with`(RailBlock.POWERED, java.lang.Boolean.valueOf(isPowered))
        world.setBlockState(pos, newState, 2)

        if (isPowered) {
          world match {
            case serverWorld: ServerWorld =>
              shiftBlocks(serverWorld, pos, newState)

            case _ =>
          }
        }
      }
  }

  def movementDirectionFromBlockState(state: BlockState): Direction = {
    val facing = state.get(DirectionalBlock.FACING)
    val rotated = state.get(RailBlock.ROTATED)
    movementDirection(facing, rotated)
  }

  def movementDirection(facing: Direction, rotated: Boolean): Direction = {
    val facingAxis = facing.getAxis
    val opposite = Direction.values.iterator.filter(_.getAxis == facingAxis).indexOf(facing) > 0
    val movementAxis = preferredAxes.iterator.filterNot(_ == facing.getAxis).drop(if (rotated) 1 else 0).next()
    Direction.values.iterator.filter(_.getAxis == movementAxis).drop(if (opposite) 1 else 0).next()
  }

  private def shiftBlocks(world: ServerWorld, pos: BlockPos, state: BlockState, reverse: Boolean = false): Boolean = {
    val facing = state.get(DirectionalBlock.FACING)
    val rotated = state.get(RailBlock.ROTATED)
    val direction = movementDirection(facing, rotated).pipe(e => if (reverse) e.getOpposite else e)
    shiftBlocks(world, pos, facing, direction)
  }

  private val air = Blocks.AIR.getDefaultState

  private def shiftBlocks(world: ServerWorld, pos: BlockPos, facing: Direction, movementDirection: Direction): Boolean = {
    def follow(start: BlockPos, direction: Direction): Iterator[BlockPos] =
      Iterator.iterate(start)(_.offset(direction))

    def isRail(pos: BlockPos, other: Boolean): Boolean = {
      val state = world.getBlockState(pos)
      state.isIn(this) && {
        val railFacing = state.get(DirectionalBlock.FACING)
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
                state.isAir || (state.getPushReaction match {
                  case PushReaction.IGNORE | PushReaction.DESTROY => true
                  case _ => false
                })

              def isMovable(pos: BlockPos, state: BlockState): Boolean =
                PistonBlock.canPush(state, world, pos, movementDirection, true, movementDirection)

              def areAllEmpty(iterator: IterableOnce[BlockState]): Boolean =
                iterator.iterator.forall(isEmpty)

              def areAllMovable(iterator: IterableOnce[(BlockPos, BlockState)]): Boolean =
                iterator.iterator.forall(e => isMovable(e._1, e._2))

              def isRow(posOnRail: BlockPos, f: Seq[(BlockPos, BlockState)] => Boolean): Boolean =
                f(betweenRails(posOnRail).map(pos => (pos, world.getBlockState(pos))).toSeq)

              def isRowEmptyOrImmovable(posOnRail: BlockPos): Boolean =
                isRow(posOnRail, blocks => areAllEmpty(blocks.iterator.map(_._2)) || !areAllMovable(blocks))

              def clearFirstRow(posOnRail: BlockPos): Boolean = {
                val row = betweenRails(posOnRail).map(pos => (pos, world.getBlockState(pos))).toSeq
                val rowEmpty = areAllEmpty(row.iterator.map(_._2))
                if (rowEmpty) row.foreach {
                  case (pos, state) => if (!state.isAir && state.getPushReaction == PushReaction.DESTROY) {
                    val blockEntity = if (state.getBlock.hasTileEntity(state)) world.getTileEntity(pos) else null
                    Block.spawnDrops(state, world, pos, blockEntity);
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
                val railEnd: BlockPos = railStart.offset(movementDirection, railLength - 1)

                val emptyRowsEnd =
                  follow(railEnd, movementDirection.getOpposite)
                    .take(railLength)
                    .takeWhile(isRowEmptyOrImmovable)
                    .size

                val overhangStart =
                  if (emptyRowsStart > 0) -emptyRowsStart
                  else
                    follow(railStart.offset(movementDirection.getOpposite), movementDirection.getOpposite)
                      .take(emptyRowsEnd)
                      .takeWhile(!isRowEmptyOrImmovable(_))
                      .size

                val overhangEnd =
                  if (emptyRowsEnd > 0) -emptyRowsEnd
                  else
                    follow(railEnd.offset(movementDirection), movementDirection)
                      .take(emptyRowsStart)
                      .takeWhile(!isRowEmptyOrImmovable(_))
                      .size

                //println("overhang start: " + overhangStart)
                //println("overhang end: " + overhangEnd)

                val shiftStart: BlockPos = railStart.offset(movementDirection, -overhangStart)
                val shiftLength: Int = railLength + overhangStart + overhangEnd
                val shiftEnd: BlockPos = shiftStart.offset(movementDirection, shiftLength - 1)

                if (clearFirstRow(shiftEnd.offset(movementDirection))) {
                  //println("shift start: " + shiftStart)
                  //println("shift end: " + shiftStart)

                  val allRowsMovable =
                    follow(shiftEnd, movementDirection.getOpposite)
                      .take(shiftLength)
                      .forall(isRow(_, areAllMovable))

                  if (allRowsMovable) {
                    val stateList: Seq[(BlockPos, BlockPos, BlockState, Option[TileEntity])] =
                      follow(shiftEnd, movementDirection.getOpposite)
                        .take(shiftLength)
                        .flatMap(betweenRails(_).map { pos =>
                          val offset = pos.offset(movementDirection)
                          val state: BlockState = world.getBlockState(pos)
                          val entityOption: Option[TileEntity] = Option(world.getTileEntity(pos))
                          (pos, offset, state, entityOption)
                        })
                        .toSeq

                    stateList.foreach {
                      case (pos, _, _, _) =>
                        world.removeTileEntity(pos)
                        world.setBlockState(pos, air, 2 | 16 | 32 | 64)
                    }

                    stateList.foreach {
                      case (_, offset, state, entityOption) =>
                        entityOption.foreach(_.validate())
                        WorldUtil.setBlockStateWithBlockEntity(world, offset, state, entityOption.orNull, 1 | 2 | 64)
                    }

                    betweenRails(shiftStart).foreach { pos =>
                      val flags = (1 | 2 | 64) & -34
                      val depth = 512
                      air.func_241482_a_(world, pos, flags, depth)
                      air.func_241483_b_(world, pos, flags, depth)
                    }

                    val box: AxisAlignedBB = {
                      val a: BlockPos = shiftStart.offset(facing)
                      val b: BlockPos = shiftEnd.offset(facing, railDistance - 1)

                      val minX = Math.min(a.getX, b.getX)
                      val minY = Math.min(a.getY, b.getY)
                      val minZ = Math.min(a.getZ, b.getZ)
                      val maxX = Math.max(a.getX, b.getX) + 1
                      val maxY = Math.max(a.getY, b.getY) + 1.01
                      val maxZ = Math.max(a.getZ, b.getZ) + 1

                      new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ)
                    }

                    val movementVector: Vector3d = Vector3d.func_237491_b_(movementDirection.getDirectionVec)
                    world.getEntitiesWithinAABB(classOf[Entity], box).iterator.asScala.foreach { entity =>
                      /*val newPos = entity.getPos.add(movementVector)
                      entity.teleport(newPos.getX, newPos.getY, newPos.getZ)*/
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
  private val settings =
    AbstractBlock.Properties
      .create(Material.ROCK)
      .hardnessAndResistance(2.0F)

  val ROTATED: BooleanProperty = BooleanProperty.create("rotated")
  val POWERED: BooleanProperty = BooleanProperty.create("powered")

  val maxRailDistance: Int = 64
}
