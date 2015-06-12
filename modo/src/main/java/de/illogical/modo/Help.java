package de.illogical.modo;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.TextView;

final public class Help extends Activity {

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.help);
		
		TextView v = (TextView)findViewById(R.id.textHelpTitle);
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        v.getRootView().setBackgroundColor(prefs.getInt("overlay_color", 0xaa000000));
	}
}
