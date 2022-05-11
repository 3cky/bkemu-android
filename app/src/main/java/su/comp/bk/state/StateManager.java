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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.msgpack.jackson.dataformat.MessagePackMapper;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import su.comp.bk.util.DataUtils;

public class StateManager {

   public static State saveEntityState(StatefulEntity entity) {
      State state = new State();
      entity.saveState(state);
      return state;
   }

   public static void restoreEntityState(StatefulEntity entity, State state) {
      entity.restoreState(state);
   }

   public static byte[] writeStateData(State state) throws IOException {
      ObjectMapper objectMapper = new MessagePackMapper();
      return DataUtils.compressData(objectMapper.writeValueAsBytes(state.toMap()));
   }

   public static State readStateData(byte[] stateData) throws IOException {
      ObjectMapper objectMapper = new MessagePackMapper();
      Map<String, Object> stateMap = objectMapper.readValue(DataUtils.decompressData(stateData),
              new TypeReference<Map<String, Object>>() {});
      return new State(stateMap);
   }

   public static void writeStateFile(File stateFile, State state) throws IOException {
      DataUtils.writeDataFile(stateFile, writeStateData(state));
   }

   public static State readStateFile(File stateFile) throws IOException {
      return readStateData(DataUtils.readDataFile(stateFile));
   }

   private static File writeStateFile(Context context, State state, boolean isExternalFile)
           throws IOException {
      File stateFile = getStateFile(context, isExternalFile);
      writeStateFile(stateFile, state);
      return stateFile;
   }

   public static File writeStateExternalFile(Context context, State state) throws IOException {
      return writeStateFile(context, state, true);
   }

   public static File writeStateInternalFile(Context context, State state) throws IOException {
      return writeStateFile(context, state, false);
   }

   public static State readStateInternalFile(Context context) throws IOException {
      return readStateFile(getStateFile(context, false));
   }

   public static void deleteStateInternalFile(Context context) {
      try {
         getStateFile(context, false).delete();
      } catch (Exception ignored) {}
   }

   private static File getStateFile(Context context, boolean isExternal) throws IOException {
      File baseDir = isExternal ? context.getExternalCacheDir() : context.getFilesDir();
      File stateDir = new File(baseDir, "state");
      if (!stateDir.exists() && !stateDir.mkdirs()) {
         throw new IOException("Can't create state directory: " + stateDir);
      }
      return new File(stateDir, "state.bkemu");
   }
}
