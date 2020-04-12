package training.akka.persistence

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.freespec.AnyFreeSpecLike
import training.akka.persistence.FunnyBox.{AddJunk, Junk, ConfirmedAdded}

/**
 * Simplification of Akka-Samples
 * https://github.com/akka/akka-samples/blob/2.6/akka-sample-persistence-scala/src/test/scala/sample/persistence/ShoppingCartSpec.scala
 */
class FunnyBoxSpec extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
    """)
  with AnyFreeSpecLike {

  val testBoxId = "Treasure Chest"

  "Add to Box (below box capacity)" in {
    val boxActor = testKit.spawn(FunnyBox(boxId = testBoxId))
    val probe    = testKit.createTestProbe[FunnyBox.Event]

    boxActor ! AddJunk(Junk("Pens", qty = 3), probe.ref)
    probe.expectMessage(ConfirmedAdded(remainingQty = FunnyBox.boxMaxCapacity - 3))
  }

  "Add to Box (above box capacity)" in {
    val boxActor = testKit.spawn(FunnyBox(boxId = testBoxId))
    val probe    = testKit.createTestProbe[FunnyBox.Event]

    // 1st fill the Box above its capacity
    boxActor ! AddJunk(Junk("Shoes", qty = FunnyBox.boxMaxCapacity + 123), probe.ref)
    probe.expectMessage(ConfirmedAdded(remainingQty = 0))

    // 2nd: Try to add more stuffs in box
    boxActor ! AddJunk(Junk("Tires", qty = 1), probe.ref)
    probe.expectMessage(FunnyBox.BoxFull)
  }
}
