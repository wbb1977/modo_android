package de.illogical.modo;

import java.util.HashMap;

import com.att.preference.colorpicker.ColorPickerPreference;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.text.Html;
import android.view.View;

final public class Prefs extends PreferenceActivity
implements OnSharedPreferenceChangeListener {

    private HashMap<String, String> descriptions = new HashMap<String, String>(10);
    private View root;

    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.layout.prefs);

        root = findViewById(android.R.id.content).getRootView();
        root.setBackgroundColor(getPreferenceScreen().getSharedPreferences().getInt("overlay_color", 0xaa000000));

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
            p.setSummary(Html.fromHtml(String.format("<i>%s</i><br>%s", lp.getEntry(), descriptions.get(key))));
        } else if (p instanceof ColorPickerPreference){
            p.setSummary(Html.fromHtml(String.format("%s", descriptions.get(key))));
            root.setBackgroundColor(((ColorPickerPreference) p).getValue());
        } else if (p instanceof PrefStereo) {
            p.setSummary(Html.fromHtml(String.format("<i>%s%%</i><br>%s", p.getSharedPreferences().getInt(key, 128) * 100 / 128, descriptions.get(key))));
        } else {
            p.setSummary(Html.fromHtml(String.format("%s", descriptions.get(key))));
        }
    }

    @SuppressWarnings("deprecation")
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updatePrefernce(findPreference(key), key);
    }

}
