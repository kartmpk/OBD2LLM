package com.odb2llm.app;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements LifecycleObserver, FragmentManager.OnBackStackChangedListener {
    private ChatViewModel chatViewModel;
    private View fragmentContainer;
    private ProgressBar progressBar;
    private TextView progressText;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportFragmentManager().addOnBackStackChangedListener(this);

        fragmentContainer = findViewById(R.id.fragment);
        progressBar = findViewById(R.id.progressBar);
        progressText = findViewById(R.id.progressText); // Initialize progressText

        progressBar.setMax(100); // Optional if determinate

        // Initially show progress bar, hide fragment container
        progressBar.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
        fragmentContainer.setVisibility(View.GONE);

        // Start loading AI models from app bundle
        new ModelDownloader(this, executor, this::onModelLoadStatus).copyModelsFromAssets();
    }

    private void onModelLoadStatus(String status) {
        Log.d("MainActivity", "Model load status: " + status);
        
        runOnUiThread(() -> {
            if (status.startsWith("Progress:")) {
                // Existing progress handling (if used)
                int percent = 0;
                try {
                    percent = Integer.parseInt(status.replace("Progress:", "").trim());
                } catch (NumberFormatException ignored) {
                }
                progressBar.setProgress(percent);
                progressText.setText("Loading AI models... " + percent + "%");
            } else {
                // Show other status messages in the progressText
                progressText.setText(status);
            }

            if ("✓ All model files ready".equals(status)) {
                progressText.setText("AI models loaded! Initializing OBD2 assistant...");
                
                // Show progress during model initialization
                progressBar.setIndeterminate(true);
                progressText.setText("Loading diagnostic knowledge base...");

                chatViewModel = new ViewModelProvider(this).get(ChatViewModel.class);
                Log.d("MainActivity", "Starting knowledge base memorization...");
                chatViewModel.memorizeChunksFromJava("sample_context.txt", () -> {
                    Log.d("MainActivity", "Knowledge base memorization complete, starting MediaPipe initialization...");
                    runOnUiThread(() -> {
                        progressText.setText("Initializing AI assistant...");
                        
                        // Wait for MediaPipe LLM to be fully initialized
                        // This prevents "LLM inference is not initialized yet!" errors
                        progressText.setText("Starting AI inference engine...");
                        
                        // Use proper MediaPipe initialization check instead of arbitrary delays
                        // This ensures the LLM is actually ready before showing the UI
                        Log.d("MainActivity", "Waiting for MediaPipe initialization...");
                        chatViewModel.waitForMediaPipeInitialization(new Runnable() {
                            @Override
                            public void run() {
                                Log.d("MainActivity", "MediaPipe initialization complete, calling onMediaPipeReady...");
                                onMediaPipeReady();
                            }
                        });
                    });
                });
            }
        });
    }
    
    private void onMediaPipeReady() {
        runOnUiThread(() -> {
            progressText.setText("AI engine ready! Loading OBD2 interface...");
            
            // Small delay to show the ready message
            new android.os.Handler().postDelayed(() -> {
                progressBar.setVisibility(View.GONE);
                progressText.setVisibility(View.GONE);
                fragmentContainer.setVisibility(View.VISIBLE);

                if (getSupportFragmentManager().findFragmentByTag("devices") == null) {
                    getSupportFragmentManager().beginTransaction()
                            .add(R.id.fragment, new DevicesFragment(), "devices")
                            .commit();
                }
            }, 1000); // Show ready message for 1 second
        });
    }

    @Override
    public void onBackStackChanged() {
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(
                getSupportFragmentManager().getBackStackEntryCount() > 0);
    }
}
