package training.akka.funcvsoo

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop, Signal}

/**
 * What is the recommended syntax to build an Akka typed actor?
 * 1) Functional: HelloFrench.apply defines the Behavior[HelloMessage]
 *    calling directly the Behaviors.receive combinator
 * 2) Object-Oriented style: HelloSpanish.apply setups
 *    a class extending AbstractBehavior[HelloMessage] which overrides def onMessage()
 *
 * ANSWER:
 * Style guide "Functional versus object-oriented style"
 * https://doc.akka.io/docs/akka/current/typed/style-guide.html#functional-versus-object-oriented-style
 */
object HelloDemo extends App {
  val guardianActor = ActorSystem(HelloMain(), "userRootActor")
  guardianActor ! HelloCommand(guestName = "AkkaMania", guardianActor)
}

sealed trait HelloMessage

/**
 * Command the actor to say hello
 * @param ackTo is needed to receive the acknowledgement event that the cmd has been executed
 *              this is a good practice to respond to a command and also to make
 *              unit testing possible (otherwise how can we know the actor has executed the cmd?)
 */
final case class HelloCommand(guestName: String, ackTo: ActorRef[HelloMessage]) extends HelloMessage

final case class HelloDone(confirmText: String, doneBy: ActorRef[HelloCommand]) extends HelloMessage

object HelloMain {
  def apply(): Behavior[HelloMessage] =
    Behaviors.receive{
      case (context, message: HelloCommand) =>
        context.log.info(s"HelloMain receives $message")
        val frenchActor  = context.spawn(FrenchReceptionist(), "HelloFrench")
        val spanishActor = context.spawn(SpanishReceptionist(), "HelloSpanish")
        frenchActor  ! message
        spanishActor ! message
        Behaviors.stopped
      case (context, message: HelloDone) =>
        context.log.info(s"Good job ${message.doneBy.toString}")
        Behaviors.same
    }
}

/**
 * Build an actor, using directly Behaviors factory methods
 * This syntax is used in
 * [Akka Quickstart with Scala](https://developer.lightbend.com/guides/akka-quickstart-scala/)
 */
object FrenchReceptionist {
  def apply(): Behavior[HelloMessage] =
    Behaviors.receive[HelloMessage]{
      case (context, message: HelloCommand) =>
        context.log.info(s"BONJOUR MADAME/MONSIEUR ${message.guestName}")
        message.ackTo ! HelloDone(confirmText = s"Greeted ${message.guestName} in FRENCH", context.self)
        Behaviors.stopped
      case _ =>
        Behaviors.same
    }
    .receiveSignal{
      case (context, signal: PostStop) =>
        context.log.info("'{}' actor stopped", this.getClass.getSimpleName)
        Behaviors.same
    }
}


/**
 * Build an actor via a subclass of AbstractBehavior[T]
 * This style is used in Akka Tutorial
 * [The Akka actor hierarchy](https://doc.akka.io/docs/akka/current/typed/guide/tutorial_1.html#the-akka-actor-hierarchy)
 */
object SpanishReceptionist {
  def apply(): Behavior[HelloMessage] =
    Behaviors.setup(context => new SpanishReceptionist(context))
}

class SpanishReceptionist private(context: ActorContext[HelloMessage])
  extends AbstractBehavior[HelloMessage](context) {

  override def onMessage(message: HelloMessage): Behavior[HelloMessage] = {
    message match {
      case msg: HelloCommand =>
        context.log.info(s"HOLA SEÑORA/SEÑOR ${msg.guestName}")
        msg.ackTo ! HelloDone(confirmText = s"Greeted ${msg.guestName} in SPANISH", context.self)
        Behaviors.stopped
      case _ =>
        Behaviors.same
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[HelloMessage]] = {
    case PostStop =>
      context.log.info("'{}' actor stopped", this.getClass.getSimpleName)
      this
  }
}

