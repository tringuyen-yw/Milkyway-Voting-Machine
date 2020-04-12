## Akka Training April 2020

- Akka typed
- Akka Clustering
- Cluster Sharding
- Akka Persistence
- CQRS with Akka

Below is a brief description of the code

## Akka typed coding style: Functional vs Object-Oriented
[HelloDemo](./src/main/scala/training/akka/funcvsoo/HelloDemo.scala) shows an example of writing an actor with 2 different styles.
The "functional style" is possible since Akka type (version 2.6x).

Which style is better is discussed in detail in Akka documentation: [Style guide "Functional versus object-oriented style"](https://doc.akka.io/docs/akka/current/typed/style-guide.html#functional-versus-object-oriented-style)


## Akka Persistence
This exercise is a minimal demo of Akka persistence, inspired from [akka-sample-persistence-scala](https://github.com/akka/akka-samples/tree/2.6/akka-sample-persistence-scala)

The goal is to model a "Box", whith the following behavior:

- this box will be able to accept objects as long as is not full. So a maxCapacity should be included it's state.
- should be possible addItem, such as Item(description: String, qty: Int)
- should not be possible addItem, if maxCapacity is already surpassed or the object to add surpasses it.
- after adding an item it should get back info about how much it still can hold

Example of implementation:
- [FunnyBox](./src/main/scala/training/akka/persistence/FunnyBox.scala)
- [FunnyBoxSpec](./src/test/scala/training/akka/persistence/FunnyBoxSpec.scala)

The notable observation is the use of an in-memory data store to persist the state in `FunnyBoxSpec`
which we can override the settings in `application.conf` by

```
class FunnyBoxSpec extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
    """)
```

## Event Sourcing & CQRS
The Box example above is re-used to show how we can use Akka to implement the CQRS pattern.
The [FunnyBoxGoCQRSSpec](./src/test/scala/training/akka/cqrs/FunnyBoxGoCQRSSpec.scala) exercise is a strict minimal demo.
A more alaborate example is found at [akka-sample-cqrs-scala](https://github.com/akka/akka-samples/tree/2.6/akka-sample-cqrs-scala)

A cool highlight of our minimal example is the use of JDBC as storage system while the `akka-sample-cqrs-scala` uses the `Cassandra engine` built-in Akka library.

- The in-memory data store is replaced by a local mySql instance
- States changes in the Box actor are saved in mySql (the "write-side")
- Then these events are read, transformed and saved to another storage (read projection, aka "the read-side").
This "re-saved" data will be consumed by end users. The storage system is normally optimized for read by multiple users. For this exercise, we also re-use mySql

This excercise is much more involved and requires additional documentations:

Concepts:
- [Event Sourcing](https://doc.akka.io/docs/akka/current/typed/persistence.html#event-sourcing)
- [CQRS pattern](https://doc.akka.io/docs/akka/current/typed/cqrs.html)

For coding:
- [Akka Persistence JDBC](https://doc.akka.io/docs/akka-persistence-jdbc/3.5.2/index.html#akka-persistence-jdbc): How to prepare a relational storage to be used as source or target
- [Persistence Query](https://doc.akka.io/docs/akka/current/persistence-query.html): How to use `ReadJournal` to read source events
- [Slick (JDBC)](https://doc.akka.io/docs/alpakka/current/slick.html): How to use `Slick` to perform read / write on a JDBC database

### Docker image for local mySql
The supplied [docker-compose.yml](./src/test/resources/docker-compose.yml) allows you to start a local mySql

```
$ docker-compose -f ./src/test/resources/docker-compose.yml up
```

The mySql server is brand new and needs some schema initialization. This requires to run some SQL statements. We will connect to the local mySql instance.
Open a new terminal session, and open a "mysql shell" by conecting to the docker instance named `mysql-test` we just stated.
```
$ docker exec -it mysql-test bash

#--once bash comes up, at the root prompt # connect to the local mySql by
mysql -hlocalhost -uroot -proot
```

Now we can follow the instructions described in [Akka Persistence JDBC](https://doc.akka.io/docs/akka-persistence-jdbc/3.5.2/index.html#akka-persistence-jdbc): How to prepare a relational storage to be used as source or target

Prepare [MySQL Schema](https://github.com/akka/akka-persistence-jdbc/blob/v3.5.2/src/test/resources/schema/mysql/mysql-schema.sql)

Please note, the given instructions supposes you use a database named `mysql`. This default choice is confusing. Indeed for first time learners, when we see `mysql` is it a setting, parameter, storage name, database name ?!?!
So in our example we will use a database named `cqrsdemo`. Type the SQL statements below in the "mysql shell" `mysql>`

```
mysql> CREATE DATABASE cqrsdemo;
USE cqrsdemo;

DROP TABLE IF EXISTS journal;

CREATE TABLE IF NOT EXISTS journal (
  ordering SERIAL,
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  deleted BOOLEAN DEFAULT FALSE,
  tags VARCHAR(255) DEFAULT NULL,
  message BLOB NOT NULL,
  PRIMARY KEY(persistence_id, sequence_number)
);

CREATE UNIQUE INDEX journal_ordering_idx ON journal(ordering);

DROP TABLE IF EXISTS snapshot;

CREATE TABLE IF NOT EXISTS snapshot (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  created BIGINT NOT NULL,
  snapshot BLOB NOT NULL,
  PRIMARY KEY (persistence_id, sequence_number)
);
```

### Configure Akka Persistence to use MySql
Create [src/test/resources/application.conf](./src/test/resources/application.conf) as instructed in
[Sharing the database connection pool between the journals](https://doc.akka.io/docs/akka-persistence-jdbc/3.5.2/index.html#sharing-the-database-connection-pool-between-the-journals)

Select the config for MySql. The template given by [mysql-shared-db-application.conf](https://github.com/akka/akka-persistence-jdbc/blob/v3.5.2/src/test/resources/mysql-shared-db-application.conf) still requires some customization to fit our exercise.
The changes are documented in the comments of [application.conf](./src/test/resources/application.conf). Which are summarized here

- we hardcode the host name of the mySql server (instead of using env variable)
- we add a new key `dbname = "cqrsdemo"` (instead of the hardcoded `mysql` as DB name in the template)
- we remove `include "general.conf"` which is not needed for our example

### Loading the application.conf
It is not obvious but the Scalatest, will NOT pickup the `application.conf` automatically!
You must load it explicitly by
```
class FunnyBoxGoCQRSSpec
  extends ScalaTestWithActorTestKit(ConfigFactory.load())
  with AnyFreeSpecLike
```

### CQRS step 1: Testing the "write-side"
With all the setting & config above, Akka performed the "write-side" for us, automatically!
All we need is to tell the Box actor to make for state changes. Pretty much the same test as in `FunnyBoxSpec` 

This is done by the test `Emit some Events (by telling Box actor to make some state changes)` in [FunnyBoxGoCQRSSpec](./src/test/scala/training/akka/cqrs/FunnyBoxGoCQRSSpec.scala)
Hopefully, the test will run successfully and we can verify that Akka had persisted every state changes
by checking the journal table in MySql shell `mysql>`

```
mysql> SELECT ordering, persistence_id, sequence_number, deleted, tags FROM journal;

+----------+-------------------------------------+-----------------+---------+------+
| ordering | persistence_id                      | sequence_number | deleted | tags |
+----------+-------------------------------------+-----------------+---------+------+
|        3 | FunnyBox|WeirdBox-mWeCNm            |               1 |       0 | NULL |
|        4 | FunnyBox|WeirdBox-Q5c9CG            |               1 |       0 | NULL |
|        6 | FunnyBox|WeirdBox-R4IElcuElwzFTbZmv |               1 |       0 | NULL |
|        1 | FunnyBox|WeirdBox-yVXvXiuOV         |               1 |       0 | NULL |
|        5 | FunnyBox|WeirdBox-z                 |               1 |       0 | NULL |
|        2 | FunnyBox|WeirdBox-ZZXOOcO           |               1 |       0 | NULL |
+----------+-------------------------------------+-----------------+---------+------+
```

### CQRS step 2: reading the journal
Now that the source events are saved in the `cqrsdemo.journal` table, we can try to Read the Journal of the events

This is done by the test `Reading the source events journal written by Akka Persistence` in [FunnyBoxGoCQRSSpec](./src/test/scala/training/akka/cqrs/FunnyBoxGoCQRSSpec.scala).

The test asserts that the events captured into a `Seq[String]` should result in a non-empty collection.
Additionally the event stream content is output in the console for some fun:

```
(Visual Debug) Event Stream content:
  FunnyBox|WeirdBox-yVXvXiuOV
  FunnyBox|WeirdBox-ZZXOOcO
  FunnyBox|WeirdBox-mWeCNm
  FunnyBox|WeirdBox-Q5c9CG
  FunnyBox|WeirdBox-z
  FunnyBox|WeirdBox-R4IElcuElwzFTbZmv
  FunnyBox|WeirdBox-rovbiHUh2vItAFCB5co3
  FunnyBox|WeirdBox-qNRIWrZJ9yUF
```

### CQRS step 3: "read-projection" + saving projection results
If "CQRS step 2" was successful, ie, we are able to read back all the event sources.
Now all we need to do is todo something useful and persist the results for the end users to consume this nicely prepared data. 
This is called "read projection".

- Make some transformations on the raw data of event source. Here we just uppercase the string content of the event.
- Save the transformed data back to mySql. This is the goal of the read projection. ie. make the nicely prepared n a storage system optimized for reads.

Here we also use mySql as target storage for the read projection.
As per CQRS pattern, the read-side should be independent from the write-side. We must store the read projection results in a different table. The example used in `FunnyBoxGoCQRSSpec` choose arbitratyli a table named `myCqrsProjection` which we should create in mySql shell:

```
USE cqrsdemo;
CREATE TABLE myCqrsProjection(
  lineid INT NOT NULL AUTO_INCREMENT,
  eventcontent VARCHAR(255) NOT NULL,
  PRIMARY KEY (lineid)
);

//---Testing if table works OK
INSERT INTO myCqrsProjection (eventcontent) VALUES ('Manual Input #1');
INSERT INTO myCqrsProjection (eventcontent) VALUES ('Manual Input #2');
SELECT * FROM myCqrsProjection;
```

An example of implementation is shown in the test `Read-Projection: Consume journal + Transform + Save back to mySql` in [FunnyBoxGoCQRSSpec](./src/test/scala/training/akka/cqrs/FunnyBoxGoCQRSSpec.scala).

Hopefully the test will be successful, which we can check the read-projection results have been persisted correctly by using mySql shell:

```
mysql> SELECT * FROM myCqrsProjection;
+--------+----------------------------------------+
| lineid | eventcontent                           |
+--------+----------------------------------------+
|      1 | Manual Input #1                        |
|      2 | Manual Input #2                        |
|     .. | ... etc ...                            |
|     43 | FUNNYBOX|WEIRDBOX-YVXVXIUOV            |
|     44 | FUNNYBOX|WEIRDBOX-ZZXOOCO              |
|     45 | FUNNYBOX|WEIRDBOX-MWECNM               |
|     46 | FUNNYBOX|WEIRDBOX-Q5C9CG               |
|     47 | FUNNYBOX|WEIRDBOX-Z                    |
|     48 | FUNNYBOX|WEIRDBOX-R4IELCUELWZFTBZMV    |
|     49 | FUNNYBOX|WEIRDBOX-ROVBIHUH2VITAFCB5CO3 |
|     50 | FUNNYBOX|WEIRDBOX-QNRIWRZJ9YUF         |
+--------+----------------------------------------+
```

the `eventcontent` contains a string which represents the persistenceId, example: `FUNNYBOX|WEIRDBOX-YVXVXIUOV`
Which is  `FunnyBox|boxId` uppercased. 

## CHALLENGES
The CQRS example is way too minimal and actually may have some bugs.

### Challenge 1: Save the state CONTENT 
In the short timespan of our training, we didn't have time to go into the details of checking
that the actual content of the FunnyBox state was persisted. We merely save and read back the persistenceId internal to Akka persistence.
A better idea would be to:

- generate random events (filling FunnyBox with random item and random qty).
- check that the Akka persistence must save the content of the state
- make some better projection logic, for example items and their count. Instead of merely uppercasing the event source content.

### Challenge 2: Save read projection on Cassandra.
Instead of persisting the read projection results on mySql. 
An interesting exercise would be to save it to Cassandra (look for inspiration in [akka-sample-cqrs-scala](https://github.com/akka/akka-samples/tree/2.6/akka-sample-cqrs-scala))

### Challenge 3: Complete the [Milkyway Voting Machine](./MilkywayVotingMachine.md)
So far only the draft of the idea exists :-)

(end)
