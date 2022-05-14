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

import android.content.Context;
import android.net.Uri;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.msgpack.jackson.dataformat.MessagePackMapper;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import su.comp.bk.util.DataUtils;

public class StateManager {

   public static final String STATE_FILE_EXT = ".bkemu_state";

   public static final String STATE_INTERNAL_FILE_NAME = "internal" + STATE_FILE_EXT;

   public static State saveEntityState(StatefulEntity entity) {
      State state = new State();
      entity.saveState(state);
      return state;
   }

   public static void restoreEntityState(StatefulEntity entity, State state) {
      entity.restoreState(state);
   }

   public static byte[] getStateData(State state) throws IOException {
      ObjectMapper objectMapper = new MessagePackMapper();
      return objectMapper.writeValueAsBytes(state.toMap());
   }

   public static byte[] getCompressedStateData(State state) throws IOException {
      return DataUtils.compressData(getStateData(state));
   }

   public static State getStateFromData(byte[] stateData) throws IOException {
      ObjectMapper objectMapper = new MessagePackMapper();
      Map<String, Object> stateMap = objectMapper.readValue(stateData,
              new TypeReference<Map<String, Object>>() {});
      return new State(stateMap);
   }

   public static State getStateFromCompressedData(byte[] stateData) throws IOException {
      return getStateFromData(DataUtils.decompressData(stateData));
   }

   public static void writeStateFile(File stateFile, State state) throws IOException {
      DataUtils.writeDataFile(stateFile, getCompressedStateData(state));
   }

   public static State readStateFile(File stateFile) throws IOException {
      return getStateFromCompressedData(DataUtils.readDataFile(stateFile));
   }

   public static State readStateFile(Context context, Uri stateFileUri) throws IOException {
      return getStateFromData(DataUtils.readCompressedDataFile(context, stateFileUri));
   }

   public static File writeStateInternalFile(Context context, State state) throws IOException {
      File stateFile = getStateInternalFile(context);
      writeStateFile(stateFile, state);
      return stateFile;
   }

   public static State readStateInternalFile(Context context) throws IOException {
      return readStateFile(getStateInternalFile(context));
   }

   public static boolean deleteStateInternalFile(Context context) {
      try {
         return getStateInternalFile(context).delete();
      } catch (Exception ignored) {
         return false;
      }
   }

   private static File getStateInternalFile(Context context) throws IOException {
      File stateDir = new File(context.getFilesDir(), "state");
      if (!stateDir.exists() && !stateDir.mkdirs()) {
         throw new IOException("Can't create internal state directory: " + stateDir);
      }
      return new File(stateDir, STATE_INTERNAL_FILE_NAME);
   }
}
