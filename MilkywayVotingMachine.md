# THE MILKYWAY VOTING MACHINE

## OVERVIEW
Design a voting machine at galaxy scale using Akka typed:

- Allow vote on a given list of choices, eg. `Set[String]`
- A voter is represented by `User(name: String, location: String)`
- A vote is represented by `Vote(user: User, choice: String, timestamp: Timestamp)`
- A vote campaign is represented by `VoteCampaign(name: String, choices: Set[String], startTime: Timestamp, endTime: Timestamp)`
- It is possible for a `User` to cast more than 1 vote. The voting system has its internal logic to handle duplicates
- A `User` must be authenticated to have the vote accepted. The authentication scheme is described in section "USER GENERATOR"

A main app with generate users and submitting vote. When the vote is over, the main app gathers the results and displays the stats.

## JUDGE
The judge roles are:
- declares the vote is opened + specify the duration of the vote
- When the vote is over, the judge declares the top 3 choices which had gathered the most vote count.

Exceptions:
- asking the judge for results before the vote begins or while vote is still running


## VOTING BOOTH
Collect votes from users all over the galaxy. The challenge is the number of users. Potentially in the range of mega-trillion users.

The role of the voting booth is:
- Authenticate that the user is valid
- Reject the vote if the user has already casted a vote
- Keep track of a cheater list (A list of `User` and the number of vote this user has made)
- Keep track of the count of Un-authenticated voters. NOTE: no duplic count. The same un-authenticated `User` who casted vote N times is counted as 1


NOTE: Incoming vote is accepted only if the vote duration is not yet expired. 

Exceptions:
- vote submitted outside of the vote duration (before or after)
- duplicate (the user had already voted)
- un-authenticated user

## STATISTICIAN
The statistician gives some stats:
- nb of voters per choice
- nb of voters per location
- top 10 cheaters
- total count of valid voters
- total count of invalid voters (users who failed authentication)
- a CSV of all the votes for verification (for both valid and invalid users)

Exceptions:
- same as for Judge, no stats available if the vote is not yet over

## USER GENERATOR
Utility to generate a random user

A **valid** `User` is defined as
- the name is non-empty and pure alpha [A-Z][a-z]
- the location must exist in fixed list of locations (potentially very large)
  Seq("Canis Majoris", "Betelgeuse", "Antares", "Sirius A", "Pollux", "Aldebaran", "Rigel", "Pistol Star", "Sagittarius A", "M87 BlackHole")

The odds of generating a user:
- 90% of valid user
- 10% of invalid user


## THE MAIN APP
The vote campaign specs
- Campaign name = What is your favorite source code editor
- Choices = Set("IntelliJ", "VSCode", "VIM", "Emacs", "Atom", "Notepad++")
- Duration = 10 seconds (The time during which vote submissions are accepted after the campaign started)

During the vote: accept vote submissions
- Every 100 millisecs, emit a vote from a random generated user
- Must submit duplicated vote (same user votes more than once). However, keep the duplicates low. For example, no more than of 5% on the total vote.

When the vote is over: Display the results 
- Winner choice, total count of voters
- horizontal bar graph nb of voters per choice, ordered by descending count
- horizontal bar graph nb of voters per location, ordered by descending count
- top 10 cheaters, ordered by descending count

Simulate error situations and check that the expected exception had occured

(end)
