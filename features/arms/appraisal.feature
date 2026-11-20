Feature: The appraisal, the graph arm and the prose arm
  The appraisal reads a question's evidence pack once and records what it shows: claims, each from
  one document, each supporting or contradicting the yes hypothesis, and the open points the
  evidence does not settle. Two arms then forecast from that one appraisal. The graph arm reads it
  as structure: the case for, the case against, the open points. The prose arm reads the same
  claims, documents and open points as one passage. What they are given differs in shape and in
  nothing else, so a difference between their scores is a difference structure made.

  Background:
    Given a frozen cohort "november" with a prospective question "rates"
    And the question "rates" has an evidence pack of "4" documents

  Scenario: the appraisal is made once for each question of a cohort
    Given the cohort names the arms "graph" and "prose"
    When the cohort is forecast
    Then one appraisal is held for "rates" in the cohort "november"

  Scenario: the appraisal records the question with a yes hypothesis and a no hypothesis in the reasoning service
    When the appraisal of "rates" is made
    Then the reasoning service holds the question "rates" with a yes hypothesis and a no hypothesis

  Scenario: each claim derives from one document of the pack and takes one stance on the yes hypothesis
    Given the appraisal of "rates" is made
    When the experimenter reads the claims of the appraisal
    Then each claim is held in the reasoning service with its statement, the document it derives from and one stance on the yes hypothesis

  Scenario: the appraisal records the open points the evidence does not settle
    Given the appraisal of "rates" is made
    When the experimenter reads the open points of the appraisal
    Then the appraisal holds its open points, each a statement of something the evidence does not settle

  Scenario: a claim that derives from no document of the pack fails the appraisal
    Given the appraisal states a claim that derives from no document of the pack
    When the appraisal of "rates" is checked
    Then the appraisal is failed, naming the claim

  Scenario: the graph arm and the prose arm abstain on a failed appraisal
    Given the appraisal of "rates" is failed
    When the arms "graph" and "prose" forecast "rates"
    Then each run is recorded as abstained, saying the appraisal failed

  Scenario: the graph arm is given the case for, the case against and the open points
    Given the appraisal of "rates" holds "3" claims that support, "2" claims that contradict and "1" open point
    When the arm "graph" forecasts "rates"
    Then the model is given the case for the yes hypothesis with "3" claims, the case against it with "2" claims and "1" open point
    And each claim is given with the document it derives from

  Scenario: the prose arm is given the same claims, documents and open points as one passage
    Given the appraisal of "rates" holds "3" claims that support, "2" claims that contradict and "1" open point
    When the arm "prose" forecasts "rates"
    Then the model is given one passage that says each of the "5" claims with its stance and its document, and the "1" open point
    And the passage follows the order of the documents in the pack

  Scenario: the graph arm and the prose arm are given the same content
    When the arms "graph" and "prose" forecast "rates"
    Then every claim, stance, document and open point given to one is given to the other
    And neither is given anything the other is not, apart from the shape

  Scenario: the graph arm and the prose arm are given what the model-alone arm is given
    Given the appraisal of "rates" is made
    When the arm "graph" forecasts "rates" and the arm "prose" forecasts "rates"
    Then each model is given the statement, the resolution criteria, the time the question closes and the time of the run

  Scenario: neither the graph arm nor the prose arm is given the documents themselves
    Given a document of the pack that no claim derives from
    When the arms "graph" and "prose" forecast "rates"
    Then no model is given a document's excerpt except through a claim that derives from it

  Scenario: the graph arm's forecast is held in the reasoning service as a belief resting on its reasons
    Given the arm "graph" forecasts "rates" with the forecast "0.72" and names "2" claims as its reasons
    When the run is recorded
    Then the reasoning service holds a belief in the yes hypothesis of "0.72" resting on those "2" claims

  Scenario: the graph arm fails when the reasoning service cannot be reached
    Given the reasoning service cannot be reached
    When the arm "graph" forecasts "rates"
    Then the run is recorded as failed, saying the reasoning service could not be reached

  Scenario: the appraisal names the version of its instructions and its model
    Given the appraisal of "rates" is made
    When the experimenter reads the appraisal
    Then the appraisal names the version of its instructions and the cohort's model

  Scenario: the cost of the appraisal is counted once for each arm that reads it
    Given the appraisal of "rates" cost "0.04"
    When the arms "graph" and "prose" forecast "rates"
    Then the cost of each run includes "0.04" for the appraisal
