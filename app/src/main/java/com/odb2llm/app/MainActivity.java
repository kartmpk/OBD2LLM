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

        // Start downloading models
        new ModelDownloader(this, executor, this::onModelDownloadStatus).downloadModels();
    }

    private void onModelDownloadStatus(String status) {

        runOnUiThread(() -> {
            if (status.startsWith("Progress:")) {
                // Existing progress handling (if used)
                int percent = 0;
                try {
                    percent = Integer.parseInt(status.replace("Progress:", "").trim());
                } catch (NumberFormatException ignored) {
                }
                progressBar.setProgress(percent);
                progressText.setText("Loading... " + percent + "%");
            } else {
                // Show other status messages in the progressText
                //      progressText.setText(status);
            }

            if ("Download complete".equals(status)) {
                progressText.setText("Loading chatOBD2..Almost there.. ");

                chatViewModel = new ViewModelProvider(this).get(ChatViewModel.class);
                chatViewModel.memorizeChunksFromJava("sample_context.txt", () -> {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        progressText.setVisibility(View.GONE);
                        fragmentContainer.setVisibility(View.VISIBLE);

                        if (getSupportFragmentManager().findFragmentByTag("devices") == null) {
                            getSupportFragmentManager().beginTransaction()
                                    .add(R.id.fragment, new DevicesFragment(), "devices")
                                    .commit();
                        }
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
