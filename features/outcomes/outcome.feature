Feature: Outcomes
  An outcome is how a question turned out, as its venue says: yes, no, or void. The bench reads
  outcomes from venues and never decides one. An outcome that a venue later changes is kept and
  revised, so a score can always be traced to the outcome it was made from.

  Background:
    Given a prospective question "rates" in a frozen cohort "november" with runs

  Scenario Outline: an outcome is read from the venue when the question resolves
    Given the venue resolves "rates" as "<result>"
    When the bench reads outcomes
    Then the outcome of "rates" is "<outcome>"
    And the outcome names its venue and the time it resolved

    Examples:
      | result | outcome |
      | Yes    | yes     |
      | No     | no      |

  Scenario: a question its venue cancels has the outcome void
    Given the venue cancels "rates"
    When the bench reads outcomes
    Then the outcome of "rates" is "void"

  Scenario: a question its venue has not resolved has no outcome
    Given the venue has not resolved "rates"
    When the bench reads outcomes
    Then "rates" has no outcome
    And "rates" is waiting

  Scenario: an outcome its venue marks as disputed is held as disputed
    Given the venue resolves "rates" as "Yes" and marks it disputed
    When the bench reads outcomes
    Then the outcome of "rates" is "yes" and is disputed

  Scenario: a dispute that ends leaves the outcome the venue settles on
    Given the outcome of "rates" is "yes" and is disputed
    When the venue settles the dispute as "No"
    Then the outcome of "rates" is "no" and is not disputed
    And the earlier outcome is still held

  Scenario: an outcome its venue changes is revised and the earlier one kept
    Given the outcome of "rates" is "yes"
    When the venue changes its result for "rates" to "No"
    Then the outcome of "rates" is "no"
    And the earlier outcome is still held, revised by the later one

  Scenario: the experimenter cannot state an outcome
    When the experimenter tries to state an outcome for "rates"
    Then the outcome is refused

  Scenario: a venue that cannot be read leaves outcomes as they were
    Given the outcome of "rates" is "yes"
    When the bench reads outcomes and the venue cannot be read
    Then the outcome of "rates" is "yes"
