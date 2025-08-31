package com.odb2llm.app;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class ModelDownloader {
    private final Context context;
    private final ExecutorService executorService;
    private final Consumer<String> statusCallback;

    private static final String TAG = "ModelDownloader";

    // Asset file names
    private static final String MODEL_ASSET = "gemma3-1b-it-int4.task";
    private static final String GECKO_ASSET = "Gecko_256_quant.tflite";
    private static final String TOKENIZER_ASSET = "sentencepiece.model";

    // Internal storage file names
    private static final String MODEL_FILENAME = "model.task";
    private static final String GECKO_FILENAME = "gecko.tflite";
    private static final String TOKENIZER_FILENAME = "sentencepiece.model";

    public ModelDownloader(Context context, ExecutorService executorService, Consumer<String> statusCallback) {
        this.context = context;
        this.executorService = executorService;
        this.statusCallback = statusCallback;
    }

    public void copyModelsFromAssets() {
        executorService.execute(() -> {
            try {
                File baseDir = context.getFilesDir();

                // Copy main model from assets
                File modelFile = new File(baseDir, MODEL_FILENAME);
                if (!isFilePresentAndValid(modelFile)) {
                    copyAssetToFile(MODEL_ASSET, modelFile);
                    statusCallback.accept("Main model copied from assets to: " + modelFile.getAbsolutePath());
                } else {
                    statusCallback.accept("Main model already present: " + modelFile.getAbsolutePath());
                }

                // Copy Gecko model from assets
                File geckoFile = new File(baseDir, GECKO_FILENAME);
                if (!isFilePresentAndValid(geckoFile)) {
                    copyAssetToFile(GECKO_ASSET, geckoFile);
                    statusCallback.accept("Gecko model copied from assets to: " + geckoFile.getAbsolutePath());
                } else {
                    statusCallback.accept("Gecko model already present: " + geckoFile.getAbsolutePath());
                }

                // Copy tokenizer from assets
                File tokenizerFile = new File(baseDir, TOKENIZER_FILENAME);
                if (!isFilePresentAndValid(tokenizerFile)) {
                    copyAssetToFile(TOKENIZER_ASSET, tokenizerFile);
                    statusCallback.accept("Tokenizer copied from assets to: " + tokenizerFile.getAbsolutePath());
                } else {
                    statusCallback.accept("Tokenizer already present: " + tokenizerFile.getAbsolutePath());
                }

                statusCallback.accept("Asset copy complete");

                logDirectoryContents(baseDir);

            } catch (IOException e) {
                Log.e(TAG, "Asset copy failed", e);
                statusCallback.accept("Asset copy failed: " + e.getMessage());
            }
        });
    }

    private boolean isFilePresentAndValid(File file) {
        return file.exists() && file.length() > 0;
    }

    private void copyAssetToFile(String assetName, File outFile) throws IOException {
        try (InputStream input = context.getAssets().open(assetName);
             FileOutputStream output = new FileOutputStream(outFile)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;

            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                // Show progress for large files
                if (totalBytesRead % (1024 * 1024) == 0) { // Every MB
                    statusCallback.accept("Copying " + assetName + ": " + (totalBytesRead / (1024 * 1024)) + " MB");
                }
            }
            
            statusCallback.accept("Finished copying " + assetName + " (" + (totalBytesRead / (1024 * 1024)) + " MB)");
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
