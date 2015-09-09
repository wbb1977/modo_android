package de.illogical.modo;

import java.util.HashMap;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.nfc.Tag;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;

import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;

import com.lb.material_preferences_library.PreferenceActivity;

final public class Prefs extends PreferenceActivity
implements OnSharedPreferenceChangeListener {

    private HashMap<String, String> descriptions = new HashMap<String, String>(10);

    @Override
    protected int getPreferencesXmlId() {
        return R.layout.prefs;
    }

    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().hide();
        getToolbar().setTitle(R.string.menu_settings);

        //addPreferencesFromResource(R.layout.prefs);
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);


        for (int i = 0, count = getPreferenceScreen().getPreferenceCount(); i < count; ++i) {
            Preference p = getPreferenceScreen().getPreference(i);

            // ugly, nested loop for categories, make better sometime
            if (p instanceof PreferenceCategory) {
                PreferenceCategory pc = (PreferenceCategory)p;
                for (int j = 0; j < pc.getPreferenceCount(); j++) {
                    p = pc.getPreference(j);
                    if (p.getKey() == null)
                        continue;
                    descriptions.put(p.getKey(), p.getSummary().toString());
                    updatePrefernce(p, p.getKey());
                }
                continue;
            }
            //

            if (p.getKey() == null)
                continue;
            descriptions.put(p.getKey(), p.getSummary().toString());
            updatePrefernce(p, p.getKey());
        }
    }

    private void updatePrefernce(Preference p, String key) {
        if (p == null || key == null || descriptions.containsKey(key) == false)
            return;

        if (p instanceof ListPreference) {
            ListPreference lp = (ListPreference)p;
            p.setSummary("[ " + lp.getEntry() + " ]\n" + descriptions.get(key));
        } else if (p instanceof PrefStereo) {
            p.setSummary("[ " + p.getSharedPreferences().getInt(key, 128) * 100 / 128 + "% ]\n" + descriptions.get(key));
        } else {
            p.setSummary(descriptions.get(key));
        }
    }

    @SuppressWarnings("deprecation")
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updatePrefernce(findPreference(key), key);
    }

}
