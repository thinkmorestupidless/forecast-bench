Feature: Cohorts
  A cohort is one experiment: a set of questions, the arms that forecast them, one model, a forecast
  window and a budget. It is drafted, then frozen, and only a frozen cohort is forecast. Nothing
  about a frozen cohort changes, which is what lets its results be believed.

  Background:
    Given a drafted cohort "november" with the model "model-a"

  Scenario: a cohort names its questions, its arms, its model, its forecast window and its budget
    When the experimenter reads the cohort "november"
    Then it names its questions, its arms, its model, its forecast window and its budget

  Scenario: the experimenter adds questions and arms to a drafted cohort
    When the experimenter adds a prospective question and the arm "model alone" to the cohort
    Then the cohort names that question and that arm

  Scenario Outline: a frozen cohort cannot be changed
    Given the cohort "november" is frozen
    When the experimenter tries to change its <part>
    Then the change is refused
    And the cohort is as it was frozen

    Examples:
      | part            |
      | questions       |
      | arms            |
      | model           |
      | forecast window |
      | repetitions     |

  Scenario: no arm forecasts a cohort that is not frozen
    When the experimenter asks for the cohort "november" to be forecast
    Then nothing is forecast
    And the experimenter is told the cohort is not frozen

  Scenario: a prospective cohort refuses a development question
    Given the cohort "november" is a prospective cohort
    When the experimenter adds a development question to the cohort
    Then the question is refused

  Scenario: a development cohort refuses a prospective question
    Given a drafted development cohort "rehearsal"
    When the experimenter adds a prospective question to the cohort "rehearsal"
    Then the question is refused

  Scenario: a cohort cannot be frozen with a question that closes before its forecast window ends
    Given the cohort names a question that closes before the forecast window ends
    When the experimenter freezes the cohort
    Then the experimenter is refused, naming the question

  Scenario: a cohort cannot be frozen with no questions or no arms
    Given the cohort names no questions
    When the experimenter freezes the cohort
    Then the experimenter is refused

  Scenario: each arm forecasts each question once for each repetition
    Given the cohort names "3" questions, "2" arms and "2" repetitions
    And the cohort is frozen
    When the cohort is forecast
    Then "12" runs are recorded

  Scenario: a cohort stops making runs when its budget is spent
    Given the cohort is frozen with a budget of "5.00"
    When the cost of its runs reaches "5.00"
    Then no further run is made
    And the cohort says which runs were not made

  Scenario: a run not made within the forecast window is never made
    Given the cohort is frozen and its forecast window has ended
    When the experimenter asks for the cohort to be forecast
    Then nothing is forecast
    And the cohort says which runs were not made

  Scenario: a question may be in a later cohort
    Given a prospective question in the frozen cohort "november"
    When the experimenter adds that question to a drafted cohort "december"
    Then the cohort "december" names that question
