Feature: Scores
  A score says how good one forecast was once its outcome is known. The Brier score is the square
  of the distance between the forecast and what happened; the log score is the surprise at what
  happened. Lower is better for both. A score names the scoring method that made it.

  Background:
    Given a prospective question "rates" in a frozen cohort "november"

  Scenario Outline: a forecast is scored by the Brier score and the log score once its outcome is yes or no
    Given a run for "rates" with the forecast "<forecast>"
    When the outcome of "rates" is "<outcome>"
    Then the run's Brier score is "<brier>"
    And the run's log score is "<log>"

    Examples:
      | forecast | outcome | brier  | log    |
      | 0.60     | yes     | 0.1600 | 0.5108 |
      | 0.75     | yes     | 0.0625 | 0.2877 |
      | 0.90     | yes     | 0.0100 | 0.1054 |
      | 0.60     | no      | 0.3600 | 0.9163 |
      | 0.90     | no      | 0.8100 | 2.3026 |
      | 0.50     | yes     | 0.2500 | 0.6931 |

  Scenario Outline: the log score of a forecast at an end is taken at the scoring method's bound
    Given the scoring method's bound is "0.01"
    And a run for "rates" with the forecast "<forecast>"
    When the outcome of "rates" is "<outcome>"
    Then the run's log score is "<log>"

    Examples:
      | forecast | outcome | log    |
      | 1.00     | no      | 4.6052 |
      | 0.00     | yes     | 4.6052 |
      | 1.00     | yes     | 0.0101 |

  Scenario: a score names its scoring method
    Given a run for "rates" with a forecast
    When the outcome of "rates" is "yes"
    Then the run's scores name the scoring method that made them

  Scenario: a new scoring method scores again and the earlier scores are kept
    Given a run for "rates" scored by the scoring method "1"
    When the experimenter scores the cohort by the scoring method "2"
    Then the run has scores by the scoring method "2"
    And its scores by the scoring method "1" are still held

  Scenario Outline: a run without a forecast has no score
    Given a run for "rates" recorded as <ending>
    When the outcome of "rates" is "yes"
    Then the run has no score

    Examples:
      | ending    |
      | abstained |
      | failed    |

  Scenario Outline: no run is scored on an outcome that is not yes or no and settled
    Given a run for "rates" with a forecast
    When the outcome of "rates" is <outcome>
    Then the run has no score

    Examples:
      | outcome              |
      | "void"               |
      | "yes" and disputed   |

  Scenario: a revised outcome scores the run again and the earlier scores are kept
    Given a run for "rates" with the forecast "0.90" scored on the outcome "yes"
    When the outcome of "rates" is revised to "no"
    Then the run's Brier score is "0.8100"
    And its scores on the earlier outcome are still held, marked as made from a revised outcome

  Scenario: an arm's forecast for a question is the mean of its repetitions
    Given the arm "evidence" has runs for "rates" with the forecasts "0.60", "0.70" and "0.80"
    When the outcome of "rates" is "yes"
    Then the arm's forecast for "rates" is "0.70"
    And the arm's Brier score for "rates" is "0.0900"
    And the spread of the repetitions is kept beside it

  Scenario: an arm's forecast for a question uses only the repetitions that gave a forecast
    Given the arm "evidence" has runs for "rates" with the forecasts "0.60" and "0.80" and one failed run
    When the outcome of "rates" is "yes"
    Then the arm's forecast for "rates" is "0.70"

  Scenario: an arm with no forecast for a question has no score for it
    Given every run of the arm "evidence" for "rates" abstained
    When the outcome of "rates" is "yes"
    Then the arm has no score for "rates"
