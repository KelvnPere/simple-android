package org.simple.clinic.navigation

import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.StateChange
import com.zhuinden.simplestackextensions.fragments.DefaultFragmentKey

fun Backstack.goBackWithoutBackPressInterception() {
  val newHistory = getHistory<DefaultFragmentKey>()
      .buildUpon()
      .removeLast()
      .build<DefaultFragmentKey>()

  setHistory(newHistory.toList(), StateChange.BACKWARD)
}
