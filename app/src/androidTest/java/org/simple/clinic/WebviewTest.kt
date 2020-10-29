package org.simple.clinic

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test

@LargeTest
class WebviewTest {

  @get:Rule
  val activityRule = ActivityScenarioRule(WebviewTestActivity::class.java)

  @Test
  fun activity_must_launch() {
  }
}
