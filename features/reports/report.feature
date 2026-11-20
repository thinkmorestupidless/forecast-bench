Feature: Reports
  A report says what a cohort has shown so far and what it has not. It gives each arm's scores with
  the number of questions behind them, compares arms only on the questions both forecast, and never
  gives a difference without its interval and its count. What it cannot yet say, it says it cannot.

  Background:
    Given a frozen prospective cohort "november" with the arms "venue price", "model alone", "evidence", "graph" and "prose"

  Scenario: a report gives each arm its scores and the number of questions behind them
    Given "120" questions of the cohort have an outcome of yes or no
    When the experimenter asks for the report of the cohort
    Then each arm is given its mean Brier score, its mean log score and the number of questions scored

  Scenario: a report gives each model arm its cost for each forecast and its latency
    Given each model arm has runs with a forecast
    When the experimenter asks for the report of the cohort
    Then each model arm is given its mean cost for each forecast, its mean tokens and its mean latency

  Scenario: a comparison uses only the questions both arms have a score for
    Given the arm "graph" has a score for "100" questions and the arm "prose" has a score for "104" questions
    And "98" questions have a score for both
    When the experimenter asks for the comparison of "graph" with "prose"
    Then the comparison is made on "98" questions

  Scenario: a comparison gives the mean difference, its interval and its count
    Given "150" questions have a score for both "graph" and "prose"
    When the experimenter asks for the comparison of "graph" with "prose"
    Then the comparison gives the mean difference of their Brier scores, its interval and the count "150"

  Scenario Outline: a comparison says how far it can be trusted by its count
    Given "<count>" questions have a score for both "graph" and "prose"
    When the experimenter asks for the comparison of "graph" with "prose"
    Then the comparison is marked "<standing>"

    Examples:
      | count | standing      |
      | 12    | too few       |
      | 29    | too few       |
      | 30    | preliminary   |
      | 99    | preliminary   |
      | 100   | counted       |

  Scenario: a comparison marked too few gives no difference
    Given "5" questions have a score for both "graph" and "prose"
    When the experimenter asks for the comparison of "graph" with "prose"
    Then the comparison gives its count and no difference

  Scenario: a comparison says which arm is better only when its interval leaves out nought
    Given a comparison of "graph" with "prose" whose interval runs from "-0.031" to "-0.004"
    When the experimenter reads the comparison
    Then it says "graph" has the lower Brier score

  Scenario: a comparison whose interval holds nought says no difference is shown
    Given a comparison of "graph" with "prose" whose interval runs from "-0.020" to "0.011"
    When the experimenter reads the comparison
    Then it says no difference is shown

  Scenario: the report leads with the comparison of the graph arm with the prose arm
    Given every pair of arms has questions with a score for both
    When the experimenter asks for the report of the cohort
    Then the first comparison is "graph" with "prose"
    And every other pair of arms is compared after it

  Scenario: a calibration table gives each band its forecasts, how often yes happened and its interval
    Given "200" forecasts of the arm "graph" on questions with an outcome of yes or no
    When the experimenter asks for the calibration table of "graph"
    Then each band gives its count of forecasts, their mean forecast, how often the outcome was yes and the interval of that

  Scenario: a calibration table has ten bands of equal width
    When the experimenter asks for the calibration table of "graph"
    Then the table has "10" bands of equal width from "0" to "1"

  Scenario: a band with fewer than ten forecasts is marked too few
    Given a band of the calibration table of "graph" with "7" forecasts
    When the experimenter reads the calibration table
    Then that band is marked "too few"

  Scenario: a report gives the scores by domain and by horizon
    Given scored questions in the domains "economy" and "politics"
    When the experimenter asks for the report of the cohort
    Then each arm's scores are also given for each domain and for each horizon, each with its count

  Scenario Outline: a question's horizon is the time from its forecast window to the time it closes
    Given a question that closes "<days>" days after the forecast window ends
    When the experimenter asks for the report of the cohort
    Then the question is counted in the horizon "<horizon>"

    Examples:
      | days | horizon |
      | 3    | days    |
      | 7    | days    |
      | 8    | weeks   |
      | 28   | weeks   |
      | 29   | months  |
      | 200  | months  |

  Scenario: a report says what it leaves out
    Given the cohort has "200" questions of which "150" have an outcome of yes or no, "40" are waiting, "6" are void and "4" are disputed
    When the experimenter asks for the report of the cohort
    Then the report gives the counts "150" scored, "40" waiting, "6" void and "4" disputed

  Scenario: a report gives each arm its abstained and failed runs
    Given the arm "evidence" has "90" runs with a forecast, "6" runs abstained and "4" runs failed
    When the experimenter asks for the report of the cohort
    Then the arm "evidence" is given the counts "90" with a forecast, "6" abstained and "4" failed

  Scenario: a report of a development cohort says on its face that it shows nothing
    Given a frozen development cohort "rehearsal" with scored runs
    When the experimenter asks for the report of the cohort "rehearsal"
    Then the report is marked "development: the model may know these outcomes"

  Scenario: a report over several cohorts leaves out development cohorts
    Given the prospective cohort "november" and a development cohort "rehearsal"
    When the experimenter asks for a report over both
    Then the report counts the cohort "november" only
    And it says the cohort "rehearsal" was left out as development

  Scenario: a question in several cohorts is counted once in a report over them
    Given a question in the prospective cohorts "november" and "december"
    When the experimenter asks for a report over both
    Then each comparison counts that question once, by the mean of its differences

  Scenario: a report asked for twice gives the same figures
    Given no outcome and no run has been recorded since the last report
    When the experimenter asks for the report of the cohort again
    Then every figure is the same as before

  Scenario: a report names the scoring method, the cohort's model and the time it was made
    Given the cohort is scored by the scoring method "1"
    When the experimenter asks for the report of the cohort
    Then the report names its scoring method, the cohort's model, each arm's instructions by version and the time it was made

  Scenario: a report with no scored question says nothing is known yet
    Given no question of the cohort has an outcome
    When the experimenter asks for the report of the cohort
    Then the report says nothing is known yet and gives the count of questions waiting
