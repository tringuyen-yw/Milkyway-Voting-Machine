package training.akka.cqrs

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.{Sink, Source}
import akka.{Done, NotUsed}
import com.typesafe.config.ConfigFactory
import org.scalatest.freespec.AsyncFreeSpecLike
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import training.akka.persistence.FunnyBox
import training.akka.persistence.FunnyBox.ConfirmedAdded

import scala.concurrent.Future


class FunnyBoxGoCQRSSpec
  extends ScalaTestWithActorTestKit(ConfigFactory.load())
  //with AnyFreeSpecLike
  with AsyncFreeSpecLike
{
  //=======================================================================
  // Step1: generate some events which cause change in the actor state
  //=======================================================================
  "Emit some Events (by telling Box actor to make some state changes)" in {
    val boxActor = testKit.spawn(FunnyBox(boxId = randomBoxId()))
    val probe    = testKit.createTestProbe[FunnyBox.Event]

    boxActor ! FunnyBox.AddJunk(FunnyBox.Junk("Laser Printer", qty = 3), probe.ref)
    //boxActor ! FunnyBox.AddJunk(FunnyBox.Junk("3D Printer", qty = 2), probe.ref)

    probe.expectMessage(ConfirmedAdded(remainingQty = FunnyBox.boxMaxCapacity - 3))

    // as we don't have a Future result, return `succeed`
    // to satisfy the return type Future[Assertion] expected by Scalatest Async
    succeed
  }

  //=======================================================================
  // Step2: Read the journal written by Akka Persistence
  // Akka persistence had been configured to save all the FunnyBox state changes
  // in the `cqrsdemo.journal` table.
  // In this test, we want to make sure we can read those events
  // Doc: Persistence Query https://doc.akka.io/docs/akka/current/persistence-query.html
  //=======================================================================
  "Reading the source events journal written by Akka Persistence" in {
    val readJournal: JdbcReadJournal =
      PersistenceQuery(system)
        .readJournalFor[JdbcReadJournal](
          JdbcReadJournal.Identifier // application.conf must have this key
        )

    val allPersistenceIds: Source[String, NotUsed] =
      readJournal.currentPersistenceIds()
      // readJournal.persistenceIds() // NOT working: cause exception

    val sourceEvents: Future[Seq[String]] = allPersistenceIds.runWith(Sink.seq[String])

    sourceEvents.map{ srcEvents =>
      // DEBUG: visualize the event stream
      println(s"${Console.CYAN}(Visual Debug) Event Stream content:\n" +
        s"${srcEvents.mkString("  ", "\n  ", "")}${Console.RESET}")

      srcEvents should not be empty
    }
  }

  //=======================================================================
  // Step3: Read-Projection: Flow Akka-stream Source to mySql Sink
  // Transform the journal of the EventSource
  // (the records in `cqrsdemo.journal` mySql table)
  // into an event stream represented by an Akka-Stream Source
  // Doc: https://doc.akka.io/docs/alpakka/current/slick.html
  //
  // NOTE: we choose to save the read projection in a table named myCqrsProjection
  // Which should be created before this test is executed
  //
  // USE cqrsdemo;
  // CREATE TABLE myCqrsProjection (
  //     lineid INT NOT NULL AUTO_INCREMENT,
  //     eventcontent VARCHAR(255) NOT NULL,
  //     PRIMARY KEY (lineid)
  //);
  //=======================================================================
  "Read-Projection: Consume journal + Transform + Save back to mySql" in {
    val readJournal: JdbcReadJournal =
      PersistenceQuery(system)
        .readJournalFor[JdbcReadJournal](
          JdbcReadJournal.Identifier // application.conf must have this key
        )

    val allPersistenceIds: Source[String, NotUsed] =
      readJournal.currentPersistenceIds()

    // Using a Slick Flow or Sink
    // https://doc.akka.io/docs/alpakka/current/slick.html#using-a-slick-flow-or-sink
    val databaseConfig = DatabaseConfig.forConfig[JdbcProfile]("akka-persistence-jdbc.shared-databases.slick")
    implicit val session: SlickSession = SlickSession.forConfig(databaseConfig)

    //Good practice: close DB session when Actor System is terminate
    //Unfortunately, this syntax no longer works with Akka typed 2.6x
    //system.registerOnTermination(() => session.close())

    // This import enables the use of the Slick sql"...",
    // sqlu"...", and sqlt"..." String interpolators.
    // See "http://slick.lightbend.com/doc/3.2.1/sql.html#string-interpolation"
    import session.profile.api._

    val saveReadPrjInDB: Future[Done] = allPersistenceIds.runWith(
      Slick.sink { event =>
        // here is the intelligence of the read projection:
        // pretend to do some complex transformation of the source event
        val transformedEvent = event.toUpperCase

        // then save the read projection labor in DB for data consumption by end users
        sqlu"INSERT INTO myCqrsProjection (eventcontent) VALUES ($transformedEvent)"
      }
    )

    saveReadPrjInDB.map { doneOk =>
      println(s"${Console.GREEN}----Read Projection SUCCESSFUL---${Console.RESET}")
      doneOk shouldBe a [Done]
    }
  }


  /**
   * Generate a random boxId.
   * This allow to better verify the Akka persistence journal
   * As we are sure that each run, the new record will be different than the previous one
   * Example:
   * {{{
   * mysql> SELECT ordering, persistence_id, sequence_number, deleted, tags FROM journal;
   * +----------+-------------------------------------+-----------------+---------+------+
   * | ordering | persistence_id                      | sequence_number | deleted | tags |
   * +----------+-------------------------------------+-----------------+---------+------+
   * |        3 | FunnyBox|WeirdBox-mWeCNm            |               1 |       0 | NULL |
   * |        4 | FunnyBox|WeirdBox-Q5c9CG            |               1 |       0 | NULL |
   * |        6 | FunnyBox|WeirdBox-R4IElcuElwzFTbZmv |               1 |       0 | NULL |
   * |        1 | FunnyBox|WeirdBox-yVXvXiuOV         |               1 |       0 | NULL |
   * |        5 | FunnyBox|WeirdBox-z                 |               1 |       0 | NULL |
   * |        2 | FunnyBox|WeirdBox-ZZXOOcO           |               1 |       0 | NULL |
   * +----------+-------------------------------------+-----------------+---------+------+
   * 6 rows in set (0.00 sec)
   * }}}
   */
  private def randomBoxId(): String = {
    import scala.util.Random
    val rndChars = Random.alphanumeric.take(1 + Random.nextInt(20))
    s"WeirdBox-${rndChars.mkString}"
  }
}

