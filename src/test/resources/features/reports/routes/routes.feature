Feature: Report routes
  The bench's own check that the report routes are served beside the cohort's.

  Scenario: a cohort's report is served in every form
    Given a forecast cohort "november"
    Then its report, its page, a comparison and a calibration table are served
