package org.simple.clinic.security.pin

import com.spotify.mobius.rx2.RxMobius
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.reactivex.Scheduler
import org.simple.clinic.security.ComparisonResult.DIFFERENT
import org.simple.clinic.security.ComparisonResult.SAME
import org.simple.clinic.security.PasswordHasher
import org.simple.clinic.security.pin.PinEntryUi.Mode.BruteForceLocked
import org.simple.clinic.security.pin.PinEntryUi.Mode.PinEntry
import org.simple.clinic.security.pin.PinEntryUi.Mode.Progress
import org.simple.clinic.security.pin.verification.PinVerificationMethod
import org.simple.clinic.util.scheduler.SchedulersProvider

class PinEntryEffectHandler @AssistedInject constructor(
    private val passwordHasher: PasswordHasher,
    private val bruteForceProtection: BruteForceProtection,
    private val schedulersProvider: SchedulersProvider,
    @Assisted private val uiActions: UiActions,
    @Assisted private val pinVerificationMethod: PinVerificationMethod
) {

  @AssistedInject.Factory
  interface Factory {
    fun create(
        uiActions: UiActions,
        pinVerificationMethod: PinVerificationMethod
    ): PinEntryEffectHandler
  }

  fun build(): ObservableTransformer<PinEntryEffect, PinEntryEvent> {
    return RxMobius
        .subtypeEffectHandler<PinEntryEffect, PinEntryEvent>()
        .addTransformer(ValidateEnteredPin::class.java, validateEnteredPin(schedulersProvider.computation()))
        .addTransformer(LoadPinEntryProtectedStates::class.java, loadPinEntryProtectedStates(schedulersProvider.io()))
        .addAction(HideError::class.java, { uiActions.hideError() }, schedulersProvider.ui())
        .addConsumer(ShowIncorrectPinError::class.java, { showIncorrectPinError(it.attemptsMade, it.attemptsRemaining) }, schedulersProvider.ui())
        .addConsumer(ShowIncorrectPinLimitReachedError::class.java, { uiActions.showIncorrectAttemptsLimitReachedError(it.attemptsMade) }, schedulersProvider.ui())
        .addAction(AllowPinEntry::class.java, { uiActions.setPinEntryMode(PinEntry) }, schedulersProvider.ui())
        .addConsumer(BlockPinEntryUntil::class.java, { uiActions.setPinEntryMode(BruteForceLocked(it.blockTill)) }, schedulersProvider.ui())
        .addTransformer(RecordSuccessfulAttempt::class.java, resetBruteForceProtectionState(schedulersProvider.io()))
        .addTransformer(RecordFailedAttempt::class.java, incrementIncorrectPinEntryCount(schedulersProvider.io()))
        .addAction(ShowProgress::class.java, { uiActions.setPinEntryMode(Progress) }, schedulersProvider.ui())
        .addAction(ClearPin::class.java, { uiActions.clearPin() }, schedulersProvider.ui())
        .addConsumer(DispatchPinVerified::class.java, { uiActions.dispatchAuthenticatedCallback(it.pin) }, schedulersProvider.ui())
        .addTransformer(VerifyPin::class.java, verifyPin(schedulersProvider.io()))
        .addConsumer(DispatchCorrectPinEntered::class.java, { uiActions.pinVerified(it.pinVerifiedData) }, schedulersProvider.ui())
        .build()
  }

  private fun validateEnteredPin(
      scheduler: Scheduler
  ): ObservableTransformer<ValidateEnteredPin, PinEntryEvent> {
    return ObservableTransformer { effects ->
      effects
          .observeOn(scheduler)
          .map { passwordHasher.compare(hashed = it.pinDigest, password = it.enteredPin) }
          .map { result ->
            when (result) {
              SAME -> CorrectPinEntered
              DIFFERENT -> WrongPinEntered
            }
          }
    }
  }

  private fun loadPinEntryProtectedStates(
      scheduler: Scheduler
  ): ObservableTransformer<LoadPinEntryProtectedStates, PinEntryEvent> {
    return ObservableTransformer { effects ->
      effects
          .switchMap { bruteForceProtection.protectedStateChanges().subscribeOn(scheduler) }
          .map(::PinEntryStateChanged)
    }
  }

  private fun showIncorrectPinError(
      attemptsMade: Int,
      attemptsRemaining: Int
  ) {
    if (attemptsMade == 1) {
      uiActions.showIncorrectPinErrorForFirstAttempt()
    } else {
      uiActions.showIncorrectPinErrorOnSubsequentAttempts(remaining = attemptsRemaining)
    }
  }

  private fun resetBruteForceProtectionState(
      scheduler: Scheduler
  ): ObservableTransformer<RecordSuccessfulAttempt, PinEntryEvent> {
    return ObservableTransformer { effects ->
      effects
          .flatMapCompletable { bruteForceProtection.recordSuccessfulAuthentication().subscribeOn(scheduler) }
          .andThen(Observable.empty())
    }
  }

  private fun incrementIncorrectPinEntryCount(
      scheduler: Scheduler
  ): ObservableTransformer<RecordFailedAttempt, PinEntryEvent> {
    return ObservableTransformer { effects ->
      effects
          .flatMapCompletable { bruteForceProtection.incrementFailedAttempt().subscribeOn(scheduler) }
          .andThen(Observable.empty())
    }
  }

  private fun verifyPin(
      scheduler: Scheduler
  ): ObservableTransformer<VerifyPin, PinEntryEvent> {
    return ObservableTransformer { effects ->
      effects
          .observeOn(scheduler)
          .map { it.pin }
          .map(pinVerificationMethod::verify)
          .map(::PinVerified)
    }
  }
}
