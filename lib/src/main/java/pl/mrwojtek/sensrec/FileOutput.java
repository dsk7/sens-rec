/*
 * (C) Copyright 2013, 2015 Wojciech Mruczkiewicz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Wojciech Mruczkiewicz
 */

package pl.mrwojtek.sensrec;

import android.os.Environment;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Records sensors data to file.
 */
public class FileOutput {

    public static final int ERROR_MEDIA_NOT_MOUNTED = 1;
    public static final int ERROR_DIRECTORY_MISSING = 2;
    public static final int ERROR_OPENING_FILE = 3;
    public static final int ERROR_ALREADY_STARTED = 4;

    private static final String TAG = "SensRec";

    private RecorderOutput output;
    private SensorsRecorder recorder;
    private OnFileListener onFileListener;
    private FileOutputStream stream;
    private DataOutputStream writer;
    private boolean started;
    private Lock writeLock = new ReentrantLock();

    public FileOutput(RecorderOutput output, SensorsRecorder recorder) {
        this.output = output;
        this.recorder = recorder;
    }

    public void setOnFileListener(OnFileListener onFileListener) {
        this.onFileListener = onFileListener;
    }

    public Record newRecord() {
        return new Record();
    }

    public void start(String fileName) {
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Log.e(TAG, "External files directory is not mounted");
            notifyError(ERROR_MEDIA_NOT_MOUNTED);
            return;
        }

        File directory = recorder.getContext().getExternalFilesDir(null);
        if (directory == null) {
            Log.e(TAG, "Error accessing external files directory");
            notifyError(ERROR_DIRECTORY_MISSING);
            return;
        }

        String currentName = nextFreeName(directory.list(), fileName);

        Log.i(TAG, "Logging to " + currentName);

        writeLock.lock();
        try {
            if (started) {
                Log.e(TAG, "Trying to start second file write");
                notifyError(ERROR_ALREADY_STARTED);
                return;
            } else {
                started = true;
            }

            stream = new FileOutputStream(new File(directory, currentName), false);
            writer = new DataOutputStream(stream);
            recorder.recordStart(output.formattedRecord(newRecord()));
            notifyStart(currentName);
        } catch (FileNotFoundException ex) {
            Log.e(TAG, "Error opening file " + currentName + ": " + ex.getMessage());
            stop(false);
            if (onFileListener != null) {
                onFileListener.onError(ERROR_OPENING_FILE);
            }
        } finally {
            writeLock.unlock();
        }
    }

    private String nextFreeName(String[] files, String fileName) {
        // List all recorded files
        Set<String> set = new HashSet<>(Arrays.asList(files));

        // Find the new file name
        int index = 1;
        String name;
        while (true) {
            name = String.format(fileName, index++);
            if (!set.contains(name)) {
                return name;
            }
        }
    }

    public void stop() {
        stop(true);
    }

    private void stop(boolean quiet) {
        writeLock.lock();
        try {
            if (!started) {
                return;
            } else {
                started = false;
            }

            if (writer != null) {
                recorder.recordStop(output.formattedRecord(newRecord()));
                try {
                    writer.close();
                } catch (IOException ex) {
                    Log.e(TAG, "Error closing file writer: " + ex.getMessage());
                } finally {
                    writer = null;
                }
            }
        } finally {
            writeLock.unlock();
        }

        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ex) {
                Log.e(TAG, "Error closing file stream: " + ex.getMessage());
            } finally {
                stream = null;
            }
        }

        if (!quiet) {
            notifyStop();
        }
    }

    private void notifyError(int errorId) {
        if (onFileListener != null) {
            onFileListener.onError(errorId);
        }
    }

    private void notifyStart(String fileName) {
        if (onFileListener != null) {
            onFileListener.onStart(fileName);
        }
    }

    private void notifyStop() {
        if (onFileListener != null) {
            onFileListener.onStop();
        }
    }

    public interface OnFileListener {
        void onError(int errorId);
        void onStart(String fileName);
        void onStop();
    }

    private class Record implements Output.Record {

        @Override
        public Output.Record start(short typeId, short deviceId) {
            writeLock.lock();
            return this;
        }

        @Override
        public void save() {
            writeLock.unlock();
        }

        @Override
        public Output.Record write(short value) {
            try {
                if (writer != null) {
                    writer.writeShort(value);
                }
            } catch (IOException ex) {
                Log.e(TAG, "Error writing short: " + ex.getMessage());
                stop(false);
                writeLock.unlock();
            }
            return this;
        }

        @Override
        public Output.Record write(int value) {
            try {
                if (writer != null) {
                    writer.writeInt(value);
                }
            } catch (IOException ex) {
                Log.e(TAG, "Error writing int: " + ex.getMessage());
                stop(false);
                writeLock.unlock();
            }
            return this;
        }

        @Override
        public Output.Record write(long value) {
            try {
                if (writer != null) {
                    writer.writeLong(value);
                }
            } catch (IOException ex) {
                Log.e(TAG, "Error writing long: " + ex.getMessage());
                stop(false);
                writeLock.unlock();
            }
            return this;
        }

        @Override
        public Output.Record write(float value) {
            try {
                if (writer != null) {
                    writer.writeFloat(value);
                }
            } catch (IOException ex) {
                Log.e(TAG, "Error writing float: " + ex.getMessage());
                stop(false);
                writeLock.unlock();
            }
            return this;
        }

        @Override
        public Output.Record write(String value) {
            try {
                if (writer != null) {
                    writer.writeBytes(value);
                }
            } catch (IOException ex) {
                Log.e(TAG, "Error writing String: " + ex.getMessage());
                stop(false);
                writeLock.unlock();
            }
            return this;
        }
    }

}