Feature: Arms
  An arm is one way of answering a question with a probability. Every arm answers under the same
  terms: it is given what its kind is given and nothing else, and it answers with a probability,
  abstains, or fails. The arms here are the ones every later arm is measured against.

  Background:
    Given a frozen cohort "november" with the model "model-a" and a prospective question "rates"

  Scenario: an arm answers a question with a probability
    Given the model answers "0.62"
    When the arm "model alone" forecasts "rates"
    Then a run is recorded with the forecast "0.62"

  Scenario: the venue-price arm's forecast is the venue's probability observed in the forecast window
    Given the venue's probability for "rates" is "0.58" when observed in the forecast window
    When the arm "venue price" forecasts "rates"
    Then a run is recorded with the forecast "0.58"
    And the run names no model

  Scenario: the venue-price arm abstains when the venue gives no probability
    Given the venue gives no probability for "rates" in the forecast window
    When the arm "venue price" forecasts "rates"
    Then the run is recorded as abstained, saying the venue gave no probability

  Scenario: the model-alone arm is given the statement, the resolution criteria and the time the question closes
    When the arm "model alone" forecasts "rates"
    Then the model is given the statement, the resolution criteria, the time the question closes and the time of the run
    And the model is given nothing else

  Scenario: no model arm is given the venue's probability
    When the arms "model alone", "evidence", "graph" and "prose" forecast "rates"
    Then no model is given the venue's probability

  Scenario: the evidence arm is given what the model-alone arm is given and the evidence pack
    Given the question "rates" has an evidence pack
    When the arm "evidence" forecasts "rates"
    Then the model is given what the model-alone arm is given and every document of the pack

  Scenario: every model arm of a cohort uses the cohort's model
    Given the cohort names the arms "model alone", "evidence", "graph" and "prose"
    When the cohort is forecast
    Then each run names the model "model-a"

  Scenario: a model arm's instructions are named by version in each run
    When the arm "evidence" forecasts "rates"
    Then the run names the version of the arm's instructions

  Scenario: an arm may abstain and say why
    Given the model answers that it cannot give a probability
    When the arm "model alone" forecasts "rates"
    Then the run is recorded as abstained with the reason the model gave

  Scenario Outline: an answer that is not a probability is a failed run
    Given the model answers "<answer>"
    When the arm "model alone" forecasts "rates"
    Then the run is recorded as failed, saying the answer was not a probability

    Examples:
      | answer   |
      | 1.4      |
      | -0.2     |
      | likely   |

  Scenario: a model that cannot be reached is a failed run
    Given the model cannot be reached
    When the arm "model alone" forecasts "rates"
    Then the run is recorded as failed, saying the model could not be reached

  Scenario: a failed run is made again only when the experimenter asks, and both runs are kept
    Given a failed run of the arm "model alone" for "rates"
    When the experimenter asks for the failed runs of the cohort to be made again within the forecast window
    Then a second run is recorded for the arm "model alone" and "rates"
    And the failed run is still held

  Scenario: a run with a forecast is never made again
    Given a run of the arm "model alone" for "rates" with a forecast
    When the experimenter asks for the failed runs of the cohort to be made again
    Then no further run is recorded for the arm "model alone" and "rates"

  Scenario: an arm the bench does not know is refused when added to a cohort
    Given a drafted cohort "december"
    When the experimenter adds the arm "crystal ball" to the cohort "december"
    Then the arm is refused
