package training.akka.persistence

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import org.slf4j.Logger
import training.akka.cqrs.CborSerializable

/**
 * Akka Training 2020-04-15 by Francisco & Douglas
 * this box will be able to accept objects as long as is not full. So a maxCapacity should be included it's state.
 * should be possible addItem, such as Item(description: String, size: Int)
 * should not be possible addItem, if
 * maxCapacity is already surpassed or the object to add surpasses it.
 * after adding an item it should get back info about how much it still can hold
 */
object FunnyBox {
  val boxMaxCapacity: Int = 99

  case class Junk(description: String, qty: Int)

  // Command
  sealed trait Command
  case class AddJunk(item: Junk, replyTo: ActorRef[Event]) extends Command

  // Event
  // CborSerializable needed for persisting in mySQL
  // Example of warning from akka.serialization.Serialization:
  // Using the Java serializer for class [training.akka.persistence.FunnyBox$JunkAdded] which is not recommended because of performance implications.
  sealed trait Event extends CborSerializable
  case class JunkAdded(item: Junk) extends Event
  case class ConfirmedAdded(remainingQty: Int) extends Event
  case object BoxFull extends Event

  // State
  case class BoxState(maxCapacity: Int, currentCapacity: Int) {
    def isBoxFull: Boolean = currentCapacity >= maxCapacity

    // cannot add more than box capacity (the excess stuffs will be dumped)
    // ie. remainingQuantity will be never negative
    def remainingQuantity: Int = math.max(0, maxCapacity - currentCapacity)
  }

  def apply(boxId: String): Behavior[Command] = {
    // Accessing the ActorContext (here to use the logger)
    // https://doc.akka.io/docs/akka/current/typed/persistence.html#accessing-the-actorcontext
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, BoxState](
        persistenceId = PersistenceId("FunnyBox", boxId),
        emptyState = BoxState(maxCapacity = boxMaxCapacity, currentCapacity = 0),
        commandHandler = (state, command) => boxCmdHandler(state, command, context.log),
        eventHandler = (state, event) => boxEventHandler(state, event, context.log)
      )
    }
  }

  private def boxCmdHandler(
    state: BoxState,
    cmd: Command,
    logger: Logger
  ): Effect[Event, BoxState] = {
    cmd match {
      case AddJunk(newItem, replyTo) =>
        if (state.isBoxFull) {
          logger.info(s"The box is FULL, ${newItem.toString} cannot be added")
          replyTo ! BoxFull
          Effect.none
        } else {
          logger.info(s"Adding to box ${newItem.toString}")
          Effect
            .persist(JunkAdded(newItem) )
            .thenRun(newState => replyTo ! ConfirmedAdded(remainingQty = newState.remainingQuantity))
        }
    }
  }

  private def boxEventHandler(
    currentState: BoxState,
    event: Event,
    logger: Logger
  ): BoxState = {
    event match {
      case BoxFull =>
        logger.info(s"Box was filled-up: $currentState")
        currentState

      case JunkAdded(item) =>
        logger.info(s"previous state: $currentState")
        val newState = currentState.copy(currentCapacity = currentState.currentCapacity + item.qty)
        logger.info(s"new state: $newState, after adding $item")
        newState
    }
  }

}
