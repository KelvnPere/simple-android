package org.simple.clinic.facility.change.confirm

import com.spotify.mobius.First
import com.spotify.mobius.First.first
import com.spotify.mobius.Init

class ConfirmFacilityChangeInit : Init<ConfirmFacilityChangeModel, ConfirmFacilityChangeEffect> {
  override fun init(model: ConfirmFacilityChangeModel): First<ConfirmFacilityChangeModel, ConfirmFacilityChangeEffect> {
    val effects = mutableSetOf<ConfirmFacilityChangeEffect>()

    if (!model.hasLoadedCurrentFacility)
      effects.add(LoadCurrentFacility)

    return first(model, effects)
  }
}
