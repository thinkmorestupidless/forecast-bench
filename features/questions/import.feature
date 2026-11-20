Feature: Questions imported from venues
  A question is something a venue asks about the future, with two outcomes and the criteria it
  resolves by. The bench imports questions and never writes its own. A question still open at its
  venue is prospective; one its venue has already resolved is a development question, for ever.

  Scenario: an open question is imported with its criteria, the time it closes and its venue
    Given the venue "Polymarket" lists an open question "Will the Bank of England cut rates before March 2027?"
    When the experimenter imports questions from "Polymarket"
    Then the question is held with its statement, its resolution criteria, the time it closes and its venue
    And the question is prospective

  Scenario: a question its venue has already resolved is imported as a development question
    Given the venue "Kalshi" lists a resolved question "Will UK inflation be above 3% in June 2025?"
    When the experimenter imports resolved questions from "Kalshi"
    Then the question is a development question
    And its outcome is held with it

  Scenario: a development question never becomes prospective
    Given a development question
    When the experimenter imports questions from its venue again
    Then the question is still a development question

  Scenario: a prospective question stays prospective after it resolves
    Given a prospective question in a frozen cohort
    When its venue resolves it
    Then the question is still prospective

  Scenario: a question with more than two outcomes is not imported
    Given the venue "Polymarket" lists an open question with "5" outcomes
    When the experimenter imports questions from "Polymarket"
    Then the question is not held
    And the import says it was left out for having more than two outcomes

  Scenario: a question with no resolution criteria is not imported
    Given the venue "Kalshi" lists an open question with no resolution criteria
    When the experimenter imports questions from "Kalshi"
    Then the question is not held
    And the import says it was left out for having no resolution criteria

  Scenario: a question imported twice is held once
    Given a prospective question imported from "Polymarket"
    When the experimenter imports questions from "Polymarket" again
    Then the question is held once

  Scenario: a question takes its domain from its venue's category
    Given the experimenter has named the category "Economics" of "Polymarket" as the domain "economy"
    When a question in the category "Economics" is imported from "Polymarket"
    Then the question's domain is "economy"

  Scenario: a question in a category the experimenter has not named has the domain other
    Given the experimenter has named no domain for the category "Culture" of "Polymarket"
    When a question in the category "Culture" is imported from "Polymarket"
    Then the question's domain is "other"

  Scenario: a venue that cannot be read leaves the questions held as they were
    Given "12" questions held from "Kalshi"
    When the experimenter imports questions from "Kalshi" and the venue cannot be read
    Then the import says the venue could not be read
    And "12" questions are held from "Kalshi"
