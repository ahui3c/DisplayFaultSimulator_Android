package tw.chehu.displayfaultsimulator

import android.view.View
import android.widget.AdapterView
import android.widget.SeekBar

class SimpleSeekListener(private val changed: () -> Unit) : SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser) changed()
    }
    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
}

class SimpleItemSelectedListener(private val selected: () -> Unit) : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = selected()
    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
}
