package org.simple.clinic.registerorlogin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.SimpleStateChanger
import com.zhuinden.simplestack.StateChange
import com.zhuinden.simplestack.navigator.Navigator
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.reactivex.Observable
import org.simple.clinic.ClinicApp
import org.simple.clinic.di.InjectorProviderContextWrapper
import org.simple.clinic.empty.EmptyScreenKey
import org.simple.clinic.feature.Features
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.navigation.BackPressHandler
import org.simple.clinic.navigation.BottomSheetSupportFragmentStateChanger
import org.simple.clinic.platform.analytics.Analytics
import org.simple.clinic.registration.phone.RegistrationPhoneScreenKey
import org.simple.clinic.router.ScreenResultBus
import org.simple.clinic.router.screen.ActivityPermissionResult
import org.simple.clinic.router.screen.ActivityResult
import org.simple.clinic.router.screen.FullScreenKey
import org.simple.clinic.selectcountry.SelectCountryScreenKey
import org.simple.clinic.util.LocaleOverrideContextWrapper
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.util.wrap
import java.util.Locale
import javax.inject.Inject

class AuthenticationActivity : AppCompatActivity(), SimpleStateChanger.NavigationHandler, AuthenticationUiActions {

  companion object {
    private const val EXTRA_OPEN_FOR = "AuthenticationActivity.EXTRA_OPEN_FOR"

    fun forNewLogin(context: Context): Intent {
      return Intent(context, AuthenticationActivity::class.java).apply {
        putExtra(EXTRA_OPEN_FOR, OpenFor.NewLogin)
      }
    }

    fun forReauthentication(context: Context): Intent {
      return Intent(context, AuthenticationActivity::class.java).apply {
        putExtra(EXTRA_OPEN_FOR, OpenFor.Reauthentication)
      }
    }
  }

  @Inject
  lateinit var locale: Locale

  @Inject
  lateinit var features: Features

  @Inject
  lateinit var effectHandlerFactory: AuthenticationEffectHandler.Factory

  private val fragmentStateChanger by unsafeLazy {
    BottomSheetSupportFragmentStateChanger(supportFragmentManager, android.R.id.content)
  }

  private val backstack: Backstack by unsafeLazy { Navigator.getBackstack(this) }

  private val screenResults: ScreenResultBus = ScreenResultBus()

  private val openFor: OpenFor by unsafeLazy {
    intent.extras!!.getSerializable(EXTRA_OPEN_FOR)!! as OpenFor
  }

  private val delegate: MobiusDelegate<AuthenticationModel, AuthenticationEvent, AuthenticationEffect> by unsafeLazy {
    MobiusDelegate.forActivity(
        events = Observable.never(),
        effectHandler = effectHandlerFactory.create(this).build(),
        init = AuthenticationInit(),
        defaultModel = AuthenticationModel.create(openFor)
    )
  }

  private lateinit var component: AuthenticationActivityComponent

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    delegate.onRestoreInstanceState(savedInstanceState)

    Navigator
        .configure()
        .setScopedServices { serviceBinder ->
          val backPressHandler = BackPressHandler(android.R.id.content, supportFragmentManager)
          serviceBinder.addService("backpress", backPressHandler)
        }
        .setStateChanger(SimpleStateChanger(this))
        .install(this, findViewById(android.R.id.content), listOf(EmptyScreenKey()))
  }

  override fun attachBaseContext(baseContext: Context) {
    setupDiGraph()

    val wrappedContext = baseContext
        .wrap { LocaleOverrideContextWrapper.wrap(it, locale) }
        .wrap { InjectorProviderContextWrapper.wrap(it, component) }
        .wrap { ViewPumpContextWrapper.wrap(it) }

    super.attachBaseContext(wrappedContext)
  }

  override fun onStart() {
    super.onStart()
    delegate.start()
  }

  override fun onStop() {
    delegate.stop()
    super.onStop()
  }

  private fun setupDiGraph() {
    component = ClinicApp.appComponent
        .authenticationActivityComponent()
        .activity(this)
        .screenResults(screenResults)
        .build()
    component.inject(this)
  }

  private fun onScreenChanged(outgoing: FullScreenKey?, incoming: FullScreenKey) {
    val outgoingScreenName = outgoing?.analyticsName ?: ""
    val incomingScreenName = incoming.analyticsName
    Analytics.reportScreenChange(outgoingScreenName, incomingScreenName)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    screenResults.send(ActivityResult(requestCode, resultCode, data))
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    screenResults.send(ActivityPermissionResult(requestCode))
  }

  override fun onBackPressed() {
    if (!Navigator.onBackPressed(this)) {
      super.onBackPressed()
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    delegate.onSaveInstanceState(outState)
    super.onSaveInstanceState(outState)
  }

  override fun openCountrySelectionScreen() {
    backstack.setHistory(listOf(SelectCountryScreenKey()), StateChange.REPLACE)
  }

  override fun openRegistrationPhoneScreen() {
    backstack.setHistory(listOf(RegistrationPhoneScreenKey()), StateChange.REPLACE)
  }

  override fun onNavigationEvent(stateChange: StateChange) {
    fragmentStateChanger.handleStateChange(stateChange)

    val outgoing: Any? = stateChange.topPreviousKey()
    val incoming: Any = stateChange.topNewKey()

    // Added temporarily to prevent a crash
    // TODO (vs) 05/11/20: Decouple reporting of analytics from the type of screen key
    if(outgoing is FullScreenKey && incoming is FullScreenKey) {
      onScreenChanged(outgoing, incoming)
    }
  }
}
