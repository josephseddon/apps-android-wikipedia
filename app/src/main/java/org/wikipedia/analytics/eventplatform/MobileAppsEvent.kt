package org.wikipedia.analytics.eventplatform

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.wikipedia.WikipediaApp
import org.wikipedia.auth.AccountUtil

@Suppress("unused")
@Serializable
sealed class MobileAppsEvent(
    @Transient private val _streamName: String = "",
    @SerialName("is_anon") @EncodeDefault(EncodeDefault.Mode.ALWAYS) private val anon: Boolean = !AccountUtil.isLoggedIn,
    @SerialName("app_session_id") @EncodeDefault(EncodeDefault.Mode.ALWAYS) private val sessionId: String = EventPlatformClient.AssociationController.sessionId,
    @SerialName("app_install_id") @EncodeDefault(EncodeDefault.Mode.ALWAYS) private val appInstallId: String = WikipediaApp.instance.appInstallID
) : EventWithDt(_streamName)

@Suppress("unused")
@Serializable
sealed class MobileAppsEventWithTemp(
    @Transient private val _streamName: String = "",
    @SerialName("is_temp") @EncodeDefault(EncodeDefault.Mode.ALWAYS) private val temp: Boolean = AccountUtil.isTemporaryAccount
) : MobileAppsEvent(_streamName)
