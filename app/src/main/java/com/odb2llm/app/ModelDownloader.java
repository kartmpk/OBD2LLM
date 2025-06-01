package com.odb2llm.app;
import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class ModelDownloader {
    private final Context context;
    private final ExecutorService executorService;
    private final Consumer<String> statusCallback;
    private static final String TAG = "OBDUtils"; // match your logging
    private static final String MODEL_URL = "";
    public static final String INTRO_MESSAGE =
            "Ask me anything about your vehicle!\n\n" +
                    "Try these examples..\n" +
                    "- Read engine RPM\n" +
                    "- Check vehicle speed\n" +
                    "- Get fuel level\n" +
                    "- Read DTC error codes\n" +
                    "- What does code P0420 mean?\n" +
                    "- Why is coolant important?\n";

    public ModelDownloader(Context context, ExecutorService executorService, Consumer<String> statusCallback) {
        this.context = context;
        this.executorService = executorService;
        this.statusCallback = statusCallback;
    }

    public void downloadFile() {
        executorService.execute(() -> {
            String destinationPath = context.getFilesDir().getAbsolutePath() + "/llm/model.bin";
            File file = new File(destinationPath);

            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                Log.d(TAG, "Failed to create directories: " + parentDir.getAbsolutePath());
                return;
            }

            if (file.exists()) {
                Log.d(TAG, "File already exists. Skipping download.");
                return;
            }

            statusCallback.accept("Downloading model.. Should be done in a few minutes");

            InputStream inputStream = null;
            FileOutputStream outputStream = null;

            try {
                URL url = new URL(MODEL_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    inputStream = connection.getInputStream();
                    outputStream = new FileOutputStream(file);

                    byte[] buffer = new byte[1024];
                    int bytesRead;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }

                    statusCallback.accept(INTRO_MESSAGE);
                } else {
                    Log.d(TAG, "Response Code: " + connection.getResponseCode());
                    InputStream errorStream = connection.getErrorStream();
                    if (errorStream != null) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream))) {
                            StringBuilder errorResponse = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                errorResponse.append(line);
                            }
                            Log.e(TAG, "Error response: " + errorResponse);
                        }
                    }
                    statusCallback.accept("Download failed!");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error during file download", e);
                statusCallback.accept("Download failed!");
            }
        });
    }
}