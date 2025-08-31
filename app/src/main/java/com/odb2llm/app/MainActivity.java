package com.odb2llm.app;

import android.os.Bundle;
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
                //      progressText.setText(status);
            }

            if ("Asset copy complete".equals(status)) {
                progressText.setText("AI models loaded! Initializing OBD2 assistant...");
                
                // Show progress during model initialization
                progressBar.setIndeterminate(true);
                progressText.setText("Loading diagnostic knowledge base...");

                chatViewModel = new ViewModelProvider(this).get(ChatViewModel.class);
                chatViewModel.memorizeChunksFromJava("sample_context.txt", () -> {
                    runOnUiThread(() -> {
                        progressText.setText("Initializing AI assistant...");
                        
                        // Wait for MediaPipe LLM to be fully initialized
                        // This prevents "LLM inference is not initialized yet!" errors
                        new android.os.Handler().postDelayed(() -> {
                            progressText.setText("Starting AI inference engine...");
                            
                            // Wait for MediaPipe to complete initialization
                            // Based on logs: "Replacing 1373 out of 1373 node(s)" and "Initialized"
                            new android.os.Handler().postDelayed(() -> {
                                progressText.setText("AI engine ready! Loading OBD2 interface...");
                                
                                // Final delay to ensure everything is stable
                                new android.os.Handler().postDelayed(() -> {
                                    progressBar.setVisibility(View.GONE);
                                    progressText.setVisibility(View.GONE);
                                    fragmentContainer.setVisibility(View.VISIBLE);

                                    if (getSupportFragmentManager().findFragmentByTag("devices") == null) {
                                        getSupportFragmentManager().beginTransaction()
                                                .add(R.id.fragment, new DevicesFragment(), "devices")
                                                .commit();
                                    }
                                }, 1500); // Final stability delay
                            }, 3000); // Wait for MediaPipe initialization (based on your logs)
                        }, 1000); // Show initialization message for 1 second
                    });
                });
            }
        });
    }


    @Override
    public void onBackStackChanged() {
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(
                getSupportFragmentManager().getBackStackEntryCount() > 0);
    }
}
