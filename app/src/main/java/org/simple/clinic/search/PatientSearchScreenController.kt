package org.simple.clinic.search

import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.ObservableTransformer
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.ofType
import org.simple.clinic.ReplayUntilScreenIsDestroyed
import org.simple.clinic.patient.PatientSearchCriteria
import org.simple.clinic.patient.PatientSearchCriteria.Name
import org.simple.clinic.patient.PatientSearchCriteria.PhoneNumber
import org.simple.clinic.patient.businessid.Identifier
import org.simple.clinic.platform.analytics.Analytics
import org.simple.clinic.search.PatientSearchValidationError.INPUT_EMPTY
import org.simple.clinic.widgets.UiEvent

private typealias Ui = PatientSearchUi
private typealias UiChange = (Ui) -> Unit

class PatientSearchScreenController @AssistedInject constructor(
    @Assisted private val additionalIdentifier: Identifier?
) : ObservableTransformer<UiEvent, UiChange> {

  @AssistedInject.Factory
  interface InjectionFactory {
    fun create(additionalIdentifier: Identifier?): PatientSearchScreenController
  }

  /**
   * Regular expression that matches digits with interleaved white spaces
   **/
  private val digitsRegex = Regex("[\\s*\\d+]+")

  override fun apply(events: Observable<UiEvent>): ObservableSource<UiChange> {
    val replayedEvents = ReplayUntilScreenIsDestroyed(events)
        .compose(InputValidator())
        .replay()

    return Observable.mergeArray(
        showValidationErrors(replayedEvents),
        resetValidationErrors(replayedEvents),
        openSearchResults(replayedEvents),
        openPatientSummary(replayedEvents),
        toggleAllPatientsVisibility(replayedEvents),
        toggleSearchButtonVisibility(replayedEvents)
    )
  }

  private fun showValidationErrors(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<SearchQueryValidationResult.Invalid>()
        .flatMapIterable { it.errors }
        .doOnNext { Analytics.reportInputValidationError(it.analyticsName) }
        .map {
          { ui: Ui ->
            when (it) {
              INPUT_EMPTY -> ui.setEmptyTextFieldErrorVisible(true)
            }
          }
        }
  }

  private fun resetValidationErrors(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<SearchQueryTextChanged>()
        .map { { ui: Ui -> ui.setEmptyTextFieldErrorVisible(false) } }
  }

  private fun openSearchResults(events: Observable<UiEvent>): Observable<UiChange> {
    val searchClicks = events
        .ofType<SearchClicked>()

    val inputValidationResultStream = events.ofType<SearchQueryValidationResult>()

    val successfullyValidatedInputStream = Observables
        .combineLatest(searchClicks, inputValidationResultStream) { _, validationResult -> validationResult }
        .ofType<SearchQueryValidationResult.Valid>()

    return successfullyValidatedInputStream
        .map { it.text }
        .map(this::searchCriteriaFromInput)
        .map(this::openSearchResultsBasedOnInput)
  }

  private fun searchCriteriaFromInput(inputString: String): PatientSearchCriteria {
    return when {
      digitsRegex.matches(inputString) -> PhoneNumber(inputString.filterNot { it.isWhitespace() }, additionalIdentifier)
      else -> Name(inputString, additionalIdentifier)
    }
  }

  private fun openSearchResultsBasedOnInput(patientSearchCriteria: PatientSearchCriteria): UiChange {
    return { ui: Ui -> ui.openSearchResultsScreen(patientSearchCriteria) }
  }

  private fun openPatientSummary(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<PatientItemClicked>()
        .map { it.patientUuid }
        .map { patientUuid -> { ui: Ui -> ui.openPatientSummary(patientUuid) } }
  }

  private fun toggleAllPatientsVisibility(events: Observable<UiEvent>): ObservableSource<UiChange> {
    return events
        .ofType<SearchQueryTextChanged>()
        .map { it.text.isNotBlank() }
        .map { isSearchQueryPresent ->
          { ui: Ui ->
            if (isSearchQueryPresent) {
              ui.hideAllPatientsInFacility()
            } else {
              ui.showAllPatientsInFacility()
            }
          }
        }
  }

  private fun toggleSearchButtonVisibility(events: Observable<UiEvent>): ObservableSource<UiChange> {
    return events
        .ofType<SearchQueryTextChanged>()
        .map { it.text.isNotBlank() }
        .map { isSearchQueryPresent ->
          { ui: Ui ->
            if (isSearchQueryPresent) {
              ui.showSearchButton()
            } else {
              ui.hideSearchButton()
            }
          }
        }
  }
}
