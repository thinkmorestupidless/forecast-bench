Feature: Evidence packs
  An evidence pack is the documents every arm that takes evidence is given for one question in one
  cohort. The gatherer makes it by search when the cohort is frozen, and from then on it does not
  change: every arm reads the same pack, and a run can be explained by reading it again.

  Background:
    Given a frozen cohort "november" with a prospective question "rates"

  Scenario: the gatherer makes one evidence pack for each question of a frozen cohort
    When the gatherer has finished
    Then the question "rates" has one evidence pack in the cohort "november"

  Scenario: a document is held with its source, its address, the time it was published and an excerpt
    Given the question "rates" has an evidence pack
    When the experimenter reads the documents of the pack
    Then each document of the pack is held with its source, its address, the time it was published and an excerpt

  Scenario: an evidence pack records the gatherer's version and the searches it made
    Given the question "rates" has an evidence pack
    When the experimenter reads the pack
    Then the pack names the gatherer's version, each search made and the time it was gathered

  Scenario: an evidence pack does not change once it is gathered
    Given the question "rates" has an evidence pack
    When the gatherer is asked to gather for the cohort "november" again
    Then the pack is as it was

  Scenario: no arm that takes evidence forecasts a question before its pack is gathered
    Given the question "rates" has no evidence pack yet
    When the cohort is forecast
    Then no run of an arm that takes evidence is recorded for "rates"

  Scenario: every arm that takes evidence is given the same pack
    Given the cohort names the arms "evidence", "graph" and "prose"
    When the cohort is forecast
    Then each run of those arms for "rates" names the same evidence pack

  Scenario: a pack holds no more documents than the gatherer's limit
    Given the gatherer's limit is "12" documents
    When the search finds "40" documents
    Then the pack holds "12" documents

  Scenario: a question the search finds nothing for has an empty pack
    When the search finds no documents for "rates"
    Then the question "rates" has an empty evidence pack

  Scenario: an arm that takes evidence abstains on an empty pack
    Given the question "rates" has an empty evidence pack
    When the arm "evidence" forecasts "rates"
    Then the run is recorded as abstained, saying there was no evidence

  Scenario: a search that fails leaves the question without a pack and says so
    When the search fails for "rates"
    Then the question "rates" has no evidence pack
    And the cohort says the pack was not gathered and why

  Scenario: a development cohort's pack holds only documents published before its pretended time
    Given a frozen development cohort "rehearsal" with the pretended time "2025-05-01"
    When the search finds a document published on "2025-05-20"
    Then that document is not in the pack

  Scenario: a pack holds no document that names a venue
    Given a frozen development cohort "rehearsal" with the pretended time "2025-05-01"
    When the search finds a document published before it that gives the probability of "Kalshi"
    Then that document is not in the pack

  Scenario: a development cohort's pack holds no document without a time it was published
    Given a frozen development cohort "rehearsal" with the pretended time "2025-05-01"
    When the search finds a document with no time it was published
    Then that document is not in the pack
