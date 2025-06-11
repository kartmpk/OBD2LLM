package com.odb2llm.app;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class ModelDownloader {
    private final Context context;
    private final ExecutorService executorService;
    private final Consumer<String> statusCallback;

    private static final String TAG = "ModelDownloader";

    private static final String MODEL_URL = "https://storage.googleapis.com/kagglesdsdata/models/242281/282755/gemma3-1B-it-int4.task?X-Goog-Algorithm=GOOG4-RSA-SHA256&X-Goog-Credential=gcp-kaggle-com%40kaggle-161607.iam.gserviceaccount.com%2F20250611%2Fauto%2Fstorage%2Fgoog4_request&X-Goog-Date=20250611T132808Z&X-Goog-Expires=259200&X-Goog-SignedHeaders=host&X-Goog-Signature=53ad4986c7aefeb0693c37921e356e0f9c5d0490e5fa241900e2b4fa1182608691fa601fe2bde00cbbd3bbdce17bec9805a253da36e1432d172ca28e0d476211f6008f5ac51f0a2a9b3cbbd4ac6e2295cdd363b5290a931fd754454eac5240c748d593e9466b94051ef1a4d42974cfaca1eea106b69d38ca8b6729dd82ee216cbfcd70640d68372a406f9c6bf82ffcdda37f90922639dbaef33bfa95589b6efda451632927753f2c4da706f75ca70663655a10c4842afb53d600ea1f6d6b60db043514fc65aacf64e135698b4856f1199aca5029237b8f89c00ddb375d313e4ebc7a2661d0bd0bc4074427358f96386d9acc1537f5608151db4198b8fe648285";
    private static final String GECKO_URL =
            "https://huggingface.co/litert-community/Gecko-110m-en/resolve/main/Gecko_256_quant.tflite";
    private static final String TOKENIZER_URL =
            "https://huggingface.co/litert-community/Gecko-110m-en/resolve/main/sentencepiece.model";

    private static final String MODEL_FILENAME = "model.task";
    private static final String GECKO_FILENAME = "gecko.tflite";
    private static final String TOKENIZER_FILENAME = "sentencepiece.model";

    public ModelDownloader(Context context, ExecutorService executorService, Consumer<String> statusCallback) {
        this.context = context;
        this.executorService = executorService;
        this.statusCallback = statusCallback;
    }

    public void downloadModels() {
        executorService.execute(() -> {
            try {
                File baseDir = context.getFilesDir();

                File modelFile = new File(baseDir, MODEL_FILENAME);
                if (!isFilePresentAndValid(modelFile)) {
                    downloadFileTo(MODEL_URL, modelFile);
                 //   statusCallback.accept("Main model downloaded to: " + modelFile.getAbsolutePath());
                } else {
                  //  statusCallback.accept("Main model already present: " + modelFile.getAbsolutePath());
                }

                File geckoFile = new File(baseDir, GECKO_FILENAME);
                if (!isFilePresentAndValid(geckoFile)) {
                    downloadFileTo(GECKO_URL, geckoFile);
                    statusCallback.accept("Gecko model downloaded to: " + geckoFile.getAbsolutePath());
                } else {
                    statusCallback.accept("Gecko model already present: " + geckoFile.getAbsolutePath());
                }

                File tokenizerFile = new File(baseDir, TOKENIZER_FILENAME);
                if (!isFilePresentAndValid(tokenizerFile)) {
                    downloadFileTo(TOKENIZER_URL, tokenizerFile);
                    statusCallback.accept("Tokenizer downloaded to: " + tokenizerFile.getAbsolutePath());
                } else {
                    statusCallback.accept("Tokenizer already present: " + tokenizerFile.getAbsolutePath());
                }

                statusCallback.accept("Download complete");

                logDirectoryContents(baseDir);

            } catch (IOException e) {
                Log.e(TAG, "Download failed", e);
                statusCallback.accept("Download failed: " + e.getMessage());
            }
        });
    }

    private boolean isFilePresentAndValid(File file) {
        return file.exists() && file.length() > 0;
    }

    private void downloadFileTo(String urlString, File outFile) throws IOException {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();

            // Set User-Agent to avoid 403 from Hugging Face
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            // Optional: set timeouts
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);

            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + responseCode + ": " + connection.getResponseMessage());
            }

            int contentLength = connection.getContentLength();
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(outFile)) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesRead = 0;

                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    if (contentLength > 0) {
                        int progress = (int) (100 * totalBytesRead / contentLength);
                        statusCallback.accept("Progress:" + progress);
                    }
                }
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void logDirectoryContents(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            Log.i(TAG, "Files in directory: " + dir.getAbsolutePath());
            for (File file : files) {
                Log.i(TAG, " - " + file.getName() + " (" + file.length() + " bytes)");
            }
        } else {
            Log.w(TAG, "No files found or directory not accessible.");
        }
    }
}
