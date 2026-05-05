package de.lolhens.minecraft.blockshifter.util

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

import scala.collection.mutable

class EntityMover private() {
  private var _queue: Map[Entity, Vec3] = Map.empty

  def queueMove(entity: Entity, vector: Vec3): Unit =
    _queue = _queue.updated(entity, _queue.get(entity)
      .map { queuedVector =>
        def minmax(a: Double, b: Double): Double =
          if (a > 0 && b < 0 || a.isNaN || b.isNaN) Double.NaN
          else if (a < 0) math.min(a, b)
          else math.max(a, b)

        new Vec3(
          minmax(queuedVector.x, vector.x),
          Math.max(queuedVector.y, vector.y),
          minmax(queuedVector.z, vector.z)
        )
      }
      .getOrElse(vector)
    )

  def moveAll(): Unit = {
    val queue = _queue
    _queue = Map.empty
    queue.foreachEntry { (entity, vector) =>
      val newPos = entity.position().add(
        if (vector.x.isNaN) 0 else vector.x,
        if (vector.y.isNaN) 0 else vector.y,
        if (vector.z.isNaN) 0 else vector.z
      )
      entity.teleportTo(newPos.x, newPos.y, newPos.z)
    }
  }
}

object EntityMover {
  private val worldEntityMovers = new mutable.WeakHashMap[ServerLevel, EntityMover]

  def apply(world: ServerLevel): EntityMover =
    worldEntityMovers.getOrElseUpdate(world, new EntityMover())
}
