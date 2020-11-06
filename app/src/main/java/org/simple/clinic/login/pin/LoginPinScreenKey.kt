package org.simple.clinic.login.pin

import android.os.Parcelable
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackextensions.services.DefaultServiceProvider
import com.zhuinden.simplestackextensions.servicesktx.add
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import org.simple.clinic.R
import org.simple.clinic.navigation.BackPressHandler
import org.simple.clinic.router.screen.FullScreenKey

// TODO: Remove added otp property
// Temporarily added to stop the current login flow from breaking
@Parcelize
data class LoginPinScreenKey(val otp: String = "") : FullScreenKey(), Parcelable, DefaultServiceProvider.HasServices {

  @IgnoredOnParcel
  override val analyticsName = "Login PIN Entry"

  override fun layoutRes() = R.layout.screen_login_pin

  override fun getScopeTag(): String {
    return ""
  }

  override fun bindServices(serviceBinder: ServiceBinder) {
    serviceBinder.add(serviceBinder.lookupService<BackPressHandler>("backpress"))
  }
}
