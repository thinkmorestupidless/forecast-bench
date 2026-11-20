Feature: A step that fails
  A suite of the bench's own, beside the living features: an edge throws inside a step, which no
  living scenario can make happen, to see the step fail over, what it was doing left settled, and
  the cohort's round end all the same.

  Scenario: a gathering that throws notes the pack as failed, and the round still ends
    Given a frozen cohort "november" with a prospective question "rates"
    And a search for "rates" throws
    When the arms "model-alone" and "evidence" forecast "rates"
    Then the arm "model-alone" forecast "rates"
    And the run of the arm "evidence" is not made, the pack failed when its step did

  Scenario: a venue that throws when read again leaves the venue unreadable, and the round still ends
    Given a frozen cohort "november" with a prospective question "rates"
    And reading "rates" at its venue throws when the round reads it
    When the arms "venue-price" and "model-alone" forecast "rates"
    Then the arm "model-alone" forecast "rates"
    And the run of the arm "venue-price" is failed, the venue unreadable
