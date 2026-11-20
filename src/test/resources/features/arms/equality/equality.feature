Feature: The content the graph and prose arms are given, read from the graph
  A suite of the bench's own, beside the living features: it makes the graph apply a claim late,
  never apply one, or revise one between the graph arm's run and the prose arm's, which no living
  scenario can, to see the bench wait for every claim and refuse to give the prose arm content the
  graph arm was not given (contracts/arm.md, contracts/reasoning.md).

  Scenario: a claim the graph applies after the last is waited for
    Given a frozen cohort "november" with a prospective question "rates"
    And the question "rates" has an evidence pack of "4" documents
    And the graph applies the first claim of "rates" after the last
    When the arms "graph" and "prose" forecast "rates"
    Then the arm "graph" is given every claim of the appraisal

  Scenario: a read the graph has not caught up with is made again
    Given a frozen cohort "november" with a prospective question "rates"
    And the question "rates" has an evidence pack of "4" documents
    And the graph applies the first claim of "rates" only on the third read
    When the arms "graph" and "prose" forecast "rates"
    Then the arm "graph" is given every claim of the appraisal

  Scenario: a claim the graph never applies fails both runs before the model is called
    Given a frozen cohort "november" with a prospective question "rates"
    And the question "rates" has an evidence pack of "4" documents
    And the graph never applies the first claim of "rates"
    When the arms "graph" and "prose" forecast "rates"
    Then the runs of the arms "graph" and "prose" are failed, the graph not caught up

  Scenario: a run whose shape does not hold the pair's content is failed
    Given a frozen cohort "november" with a prospective question "rates"
    And the question "rates" has an evidence pack of "4" documents
    And the appraisal of "rates" is made
    And the graph revises a claim once the arm "graph" has been given it
    When the arms "graph" and "prose" forecast "rates"
    Then the run of the arm "prose" is recorded as failed, saying the two shapes did not hold the same content
