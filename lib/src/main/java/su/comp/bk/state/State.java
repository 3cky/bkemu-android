/*
 * Copyright (C) 2022 Victor Antonovich (v.antonovich@gmail.com)
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
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package su.comp.bk.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public class State {
   private final Map<String, Object> stateMap = new TreeMap<>();

   public State(Map<String, Object> initialStateMap) {
      if (initialStateMap != null) {
         stateMap.putAll(initialStateMap);
      }
   }

   public State() {
      this(null);
   }

   private void putValue(String key, Object value) {
      if (value != null) {
         stateMap.put(key, value);
      }
   }

   private Object getValue(String key, Object defaultValue) {
      Object value = stateMap.get(key);
      return (value != null) ? value : defaultValue;
   }

   public void putBoolean(String key, boolean value) {
      putValue(key, value);
   }

   public boolean getBoolean(String key, boolean defaultValue) {
      return (boolean) getValue(key, defaultValue);
   }

   public boolean getBoolean(String key) {
      return getBoolean(key, false);
   }

   public void putInt(String key, int value) {
      putValue(key, value);
   }

   public int getInt(String key, int defaultValue) {
      return (int) getValue(key, defaultValue);
   }

   public int getInt(String key) {
      return getInt(key, 0);
   }

   public void putLong(String key, long value) {
      putValue(key, value);
   }

   public long getLong(String key, long defaultValue) {
      Object val = getValue(key, defaultValue);
      return (val instanceof Integer) ? ((Integer) val).longValue() : (long) val;
   }

   public long getLong(String key) {
      return getLong(key, 0L);
   }

   public void putString(String key, String value) {
      putValue(key, value);
   }

   public String getString(String key, String defaultValue) {
      return (String) getValue(key, defaultValue);
   }

   public String getString(String key) {
      return getString(key, null);
   }

   public void putIntegerArrayList(String key, ArrayList<Integer> values) {
      putValue(key, values);
   }

   public ArrayList<Integer> getIntegerArrayList(String key) {
      return (ArrayList<Integer>) getValue(key, null);
   }

   public void putByteArray(String key, byte[] values) {
      putValue(key, values);
   }

   public byte[] getByteArray(String key) {
      return (byte[]) getValue(key, null);
   }

   public Map<String, Object> toMap() {
      return Collections.unmodifiableMap(stateMap);
   }

   @Override
   public String toString() {
      return "State{" +
              "stateMap=" + stateMap +
              '}';
   }
}
