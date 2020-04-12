package training.akka.funcvsoo

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.freespec.AnyFreeSpecLike

/**
 * Imitate AkkaQuickstartSpec from https://developer.lightbend.com/guides/akka-quickstart-scala/
 */
class HelloDemoSpec extends ScalaTestWithActorTestKit
  with AnyFreeSpecLike
{
  "Tell FrenchReceptionist actor a command" in {
    val guardianProbe = createTestProbe[HelloMessage]()
    val actorToTest   = spawn(FrenchReceptionist())
    val testMessage   = HelloCommand(guestName = "MrBean", guardianProbe.ref)

    actorToTest ! testMessage
    guardianProbe.expectMessage(HelloDone(confirmText = s"Greeted ${testMessage.guestName} in FRENCH", actorToTest.ref))
  }

  "Tell SpanishReceptionist actor a command" in {
    val guardianProbe = createTestProbe[HelloMessage]()
    val actorToTest   = spawn(SpanishReceptionist())
    val testMessage   = HelloCommand(guestName = "MrBean", guardianProbe.ref)

    actorToTest ! testMessage
    guardianProbe.expectMessage(HelloDone(confirmText = s"Greeted ${testMessage.guestName} in SPANISH", actorToTest.ref))
  }

}

