package de.illogical.modo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

final public class ModoReceiver extends BroadcastReceiver {
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_MEDIA_BUTTON) && Modo.myModo != null && Modo.playlist != null && ServicePlayer.p != null) {
			
			// Pref "Ignore Mediabuttons" - true = ignore, false = process
			if (Modo.myModo.prefsIsMediabuttons == true)
				return;
			
			Bundle bundle = intent.getExtras();
			KeyEvent keyEvent = (KeyEvent)bundle.get(Intent.EXTRA_KEY_EVENT);

			synchronized (Modo.sync) {
				if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
					switch (keyEvent.getKeyCode()) {
						case KeyEvent.KEYCODE_HEADSETHOOK:
						case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
							Modo.myModo.onClick_ButtonPauseResume(null);
							break;
						case KeyEvent.KEYCODE_MEDIA_NEXT:
							if (Modo.decoder != null && Modo.decoder instanceof MikModDecoder) {
								synchronized (Modo.playlistSync) {
									Modo.playlist.advancePlayPosition();
									Modo.myModo.prepareNextFile(0);																	
								}
							} else {
								Modo.myModo.onClick_ButtonNextTrack(null);
							}
							break;
						case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
							if (Modo.decoder != null && Modo.decoder instanceof MikModDecoder) {
								synchronized (Modo.playlistSync) {
									Modo.playlist.reducePlayPosition();
									Modo.myModo.prepareNextFile(0);																	
								}
							} else {
								Modo.myModo.onClick_ButtonPrevTrack(null);
							}
							break;
					}
				}
			}
		}
	}
}
