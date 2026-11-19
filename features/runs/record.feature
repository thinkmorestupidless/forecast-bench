Feature: Runs
  A run is one arm's answer to one question in one cohort, recorded once. The record holds
  everything the answer came from, and it is never changed: a forecast is believed because it was
  held before the outcome existed.

  Background:
    Given a frozen cohort "november" with the model "model-a" and a prospective question "rates"

  Scenario: a run is recorded with everything its answer came from
    Given a run of the arm "evidence" for "rates"
    When the experimenter reads the run
    Then the run names its cohort, its question, its arm, its repetition, its model, the version of its instructions and its evidence pack
    And the run holds exactly what the model was given, what the model answered, its forecast and the time it was recorded

  Scenario: a run records its cost, its tokens and its latency
    Given a run of the arm "graph" for "rates"
    When the experimenter reads the run
    Then the run records its cost, its tokens and its latency

  Scenario: the time of a run is set by the bench
    Given the model answers with a time of its own
    When the arm "model alone" forecasts "rates"
    Then the run is dated the time the bench recorded it

  Scenario: a run is never changed
    Given a run of the arm "model alone" for "rates" with the forecast "0.60"
    When anyone tries to change the run's forecast
    Then the change is refused
    And the run's forecast is "0.60"

  Scenario: a run is never removed
    Given a run of the arm "model alone" for "rates"
    When anyone tries to remove the run
    Then the removal is refused

  Scenario: no run is recorded for a prospective question after it closes
    Given the question "rates" has closed
    When the arm "model alone" forecasts "rates"
    Then no run is recorded
    And the experimenter is told the question has closed

  Scenario: no run is recorded for a prospective question that has an outcome
    Given the question "rates" has an outcome
    When the arm "model alone" forecasts "rates"
    Then no run is recorded

  Scenario: the experimenter reads the history of a cohort in the order it happened
    Given the cohort "november" has been forecast
    When the experimenter reads the history of the cohort
    Then they read when it was frozen, when each pack was gathered and when each run was recorded, in order

  Scenario: a run can be made again from its record without touching it
    Given a run of the arm "evidence" for "rates"
    When the experimenter reproduces the run
    Then the model is given exactly what the run holds it was given
    And the answer is shown beside the run and is not recorded as a run
