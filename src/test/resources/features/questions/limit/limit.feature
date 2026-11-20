Feature: An import's limit
  The bench's own check of the import route, not a living feature: the limit counts the questions
  the import holds, once an event's markets are made one, never the markets the venue listed.

  Scenario: an import's limit counts questions held after one per event
    Given the venue lists an event of "3" markets and "2" events of one market each
    When the experimenter imports "3" questions, one per event
    Then "3" questions are held, one from each event
