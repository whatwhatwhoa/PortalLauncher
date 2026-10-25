package io.github.whatwhatwhoa.portallauncher

import android.service.notification.NotificationListenerService

/**
 * Reads no notification. Android tells an app what is playing, how far in and lets it seek
 * (MediaSessionManager) only while the app holds notification access, and this is what holds it
 * for the quick panel's seek bar (F21). Off until the owner turns it on from the panel.
 */
class MediaListener : NotificationListenerService()
