package de.illogical.modo;

import android.content.Context;
import android.content.Intent;

/**
 * Created by wb on 8/18/15.
 */
final public class NotificationReceiver extends android.content.BroadcastReceiver {

    public void onReceive(Context context, Intent intent) {

        if (Modo.myModo != null && Modo.playlist != null && ServicePlayer.p != null) {
            synchronized (Modo.sync) {
                switch (intent.getAction()) {
                    case "de.illogical.modo.finish":
                        Modo.myModo.finish();
                        break;
                    case Modo.INTENT_NOTIFICATION_PLAY:
                        Modo.myModo.MediaPlay();
                        break;
                    case Modo.INTENT_NOTIFICATION_PAUSE:
                        Modo.myModo.MediaPause();
                        break;
                    case Modo.INTENT_NOTIFICATION_NEXT:
                        if (Modo.decoder != null && Modo.decoder instanceof MikModDecoder) {
                            synchronized (Modo.playlistSync) {
                                Modo.playlist.advancePlayPosition();
                                Modo.myModo.prepareNextFile(0);
                            }
                        } else {
                            Modo.myModo.onClick_ButtonNextTrack(null);
                        }
                        break;
                    case Modo.INTENT_NOTIFICATION_PREV:
                        if (Modo.decoder != null && Modo.decoder instanceof MikModDecoder) {
                            synchronized (Modo.playlistSync) {
                                Modo.playlist.reducePlayPosition();
                                Modo.myModo.prepareNextFile(0);
                            }
                        } else {
                            Modo.myModo.onClick_ButtonPrevTrack(null);
                        }
                        break;
                    case Modo.INTENT_NOTIFICATION_STOP:
                        Modo.myModo.finish();
                        break;
                    default:
                        android.util.Log.d("IntentReceiver", "Unhandled intent: " + intent.getAction());
                        break;
                }
            }
        }
    }
}