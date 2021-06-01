/*
 * Created: 06.08.2012
 *
 * Copyright (C) 2012 Victor Antonovich (v.antonovich@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package su.comp.bk.ui.keyboard;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ToggleButton;

import androidx.appcompat.widget.AppCompatToggleButton;

/**
 * {@link ToggleButton} with overridden toggle() method used for keyboard modifier buttons.
 */
public class ModifierButton extends AppCompatToggleButton {

    public ModifierButton(Context context) {
        super(context);
    }

    public ModifierButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public ModifierButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void toggle() {
        // Do nothing, button state is controlled programmatically
    }
}
