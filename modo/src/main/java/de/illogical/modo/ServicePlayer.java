package de.illogical.modo;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

final public class ServicePlayer extends Service {

	class LocalBinder extends Binder {
		ServicePlayer getService() {
			return ServicePlayer.this;
        }
    }

	private IBinder mBinder = new LocalBinder();
	
	private boolean isForeground = false;
	static Player p = null;

	boolean isForeground() {
		return isForeground;	
	}
	
	void setIsForeground(boolean newForegroundState) {
		isForeground = newForegroundState;
	}
	
	void startPlayerThread() {
		if (p == null) {
			p = new Player(getApplicationContext());
			p.pausePlayer();
			p.setPriority(Thread.MAX_PRIORITY);
			p.start();
		}
	}
	
	void sendNoMoreGUIUpdates() {
		if (p != null)
			p.setHandler(null);
	}
	
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		return START_STICKY;
	}
	
	public void onDestroy() {
		if (p != null) {
			p.setHandler(null);
			p.stopPlayer();
			p = null;
		}
		super.onDestroy();
	}
	
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}
}