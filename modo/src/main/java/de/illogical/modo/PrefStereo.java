package de.illogical.modo;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

final public class PrefStereo extends DialogPreference implements OnSeekBarChangeListener  {

    private SeekBar sb;
    private TextView tv;
    private int stereoSeparation;

    public PrefStereo(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PrefStereo(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult)
            getEditor().putInt(getKey(), stereoSeparation).commit();
    }

    protected View onCreateDialogView() {
        View v = super.onCreateDialogView();

        sb = (SeekBar)v.findViewById(R.id.seekBarStereo);
        sb.setOnSeekBarChangeListener(this);
        sb.setMax(128);
        sb.setProgress(getPersistedInt(128));

        tv = (TextView)v.findViewById(R.id.textStereo);
        tv.setText(getPersistedInt(128) * 100 / 128 + "%");

        return v;
    }

    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        stereoSeparation = progress;
        if (fromUser)
            tv.setText(progress * 100 / 128 + "%");
    }

    public void onStartTrackingTouch(SeekBar seekBar) {}
    public void onStopTrackingTouch(SeekBar seekBar) {}
}
