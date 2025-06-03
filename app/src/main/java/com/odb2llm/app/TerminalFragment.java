package com.odb2llm.app;
import static com.odb2llm.app.OBDUtils.decodeOBDResponse;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
import android.text.style.AlignmentSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.fragment.app.Fragment;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.odb2llm.app.ChatViewModel;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private int lastAppendedLength = 0;
    private String previousResponse = "";
    private enum Connected { False, Pending, True }
    private ExecutorService executorService;
    private String deviceAddress;
    private SerialService service;
    public  TextView receiveText;
    private TextView sendText;
    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;
    private TextEmbeddingsViewModel textEmbeddingsViewModel;
    private ChatViewModel chatviewModel;

    public static final String INTRO_MESSAGE =
            "Ask me anything about your vehicle!\n\n" +
                    "Try these examples..\n" +
                    "- Read engine RPM\n" +
                    "- Check vehicle speed\n" +
                    "- Get fuel level\n" +
                    "- Read DTC error codes\n" +
                    "- What does code P0420 mean?\n" +
                    "- Why is coolant important?\n";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        textEmbeddingsViewModel = new ViewModelProvider(this).get(TextEmbeddingsViewModel.class);
        textEmbeddingsViewModel.setUpMLModel(requireActivity().getApplicationContext());
        executorService = Executors.newSingleThreadExecutor(); // or cachedThreadPool, depending on your needs

        setHasOptionsMenu(true);
        assert getArguments() != null;
        deviceAddress = getArguments().getString("device");

        Log.d("chatviewModel", "memorizing chunks");
        chatviewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        chatviewModel.memorizeChunksFromJava("sample_context.txt");

    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        executorService.shutdownNow();
        requireActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        //new ModelDownloader(requireContext(), executorService, this::status).downloadFile();
        status(INTRO_MESSAGE);
        if(service != null)
            service.attach(this);
        else {
            requireActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
            requireActivity().bindService(new Intent(getActivity(), SerialService.class), this, 0); // this binds and triggers onServiceConnected
        }
        chatviewModel.getLastResponseText().observe(getViewLifecycleOwner(), responseText -> {
            if (responseText != null && !responseText.isEmpty()) {
                if (responseText.startsWith(previousResponse)) {
                    // Append only the new part
                    String newText = responseText.substring(previousResponse.length());
                    if (!newText.isEmpty()) {
                        receiveText.append(newText);
                    }
                } else {
                    // If response changed, append full text
                    receiveText.append("\n" + responseText);
                }
                previousResponse = responseText;
            }
        });

    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            requireActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        sendText = view.findViewById(R.id.send_text);
        View sendBtn = view.findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));
        return view;
    }

    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);

            // status("Connecting to OBD2 Module ");
            Log.d(OBDUtils.TAG, "connecting to obd2 Module");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(requireActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void OBD2inference(String prompt) {
        String updatedPrompt = prompt;

        try {
            InferenceModel inferenceModel = InferenceModel.Companion.getInstance(getActivity());
            updatedPrompt = inferenceModel.generateResponse(prompt);  // Get the generated response.
            Log.d("LLMInference", "Generated response: " + updatedPrompt);
            if (getActivity() != null) {
                SpannableStringBuilder spannablePrompt = new SpannableStringBuilder(updatedPrompt + '\n'); // Combine updatedPrompt and newline
                int promptLength = updatedPrompt.length(); // Save prompt length for span application

                // Ensure prompt length doesn't exceed the spannable text length
                if (promptLength > spannablePrompt.length()) {
                    Log.e("SpanError", "Prompt length exceeds spannable text length. Adjusting.");
                    promptLength = spannablePrompt.length();
                    spannablePrompt.setSpan(
                            new ForegroundColorSpan(getResources().getColor(R.color.colorRecieveText)),
                            0,
                            promptLength,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                } else {
                    spannablePrompt.setSpan(
                            new ForegroundColorSpan(getResources().getColor(R.color.colorRecieveText)),
                            0,
                            promptLength,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }

                // Apply span with validated range
                spannablePrompt.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE), 0, promptLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                getActivity().runOnUiThread(() -> receiveText.append(spannablePrompt));
            }
        } catch (Exception e) {
            Log.e(OBDUtils.TAG, "Unexpected error occurred: " + e.getMessage(), e);
        }
    }

    private void send(String str) {
        sendText.setText("");
        SpannableString prompt = new SpannableString("\n" + str + "\n\n");
        prompt.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, prompt.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        prompt.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_NORMAL), 0, prompt.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(prompt);  // Append the message to receiveText immediately

        String decodedobd2code = textEmbeddingsViewModel.calculateSimilarity(str);

        /* give a creative answer */
        if ("No match found".equals(decodedobd2code) || decodedobd2code == null) {
            if (str.trim().split("\\s+").length > 0) {
              //  OBD2inference("<start_of_turn>user Respond in not more than 10 words only" + str + "<end_of_turn>model>");

                chatviewModel.requestResponse(str);
                //OBD2inferenceAsync("<start_of_turn>user Respond in not more than 10 words only" + str + "<end_of_turn>model>");
            }
        } else {    /* send obd2 code across */
            str = decodedobd2code.substring(0, 4); // Take the first character
            Log.d(OBDUtils.TAG, "sending odb2code:" + str);

            if (connected != Connected.True) {
                Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                byte[] data;
                String newline = TextUtil.newline_crlf;
                data = (str + newline).getBytes();
                service.write(data);
            } catch (Exception e) {
                onSerialIoError(e);
            }
        }
    }

    private void receive(ArrayDeque<byte[]> datas) {
        SpannableStringBuilder spn = new SpannableStringBuilder();
        for (byte[] data : datas) {
            String msg = new String(data);
            if (!msg.isEmpty()) {
                // don't show CR as ^M if directly before LF
                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                // special handling if CR and LF come in separate fragments
                if (pendingNewline && msg.charAt(0) == '\n') {
                    if(spn.length() >= 2) {
                        spn.delete(spn.length() - 2, spn.length());
                    } else {
                        Editable edt = receiveText.getEditableText();
                        if (edt != null && edt.length() >= 2)
                            edt.delete(edt.length() - 2, edt.length());
                    }
                }
                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }
            spn.append(TextUtil.toCaretString(msg, !newline.isEmpty()));
        }
        if(String.valueOf(spn).contains("NO DATA")) {
            SpannableStringBuilder prompt = new SpannableStringBuilder("No data from OBD\n");
            prompt.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorRecieveText)), 0, prompt.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            prompt.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE), 0, prompt.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            getActivity().runOnUiThread(() -> receiveText.append(prompt));
        }

        String comment_on = decodeOBDResponse(String.valueOf(spn));
        if (!(comment_on.toLowerCase().contains("invalid") ||
                comment_on.toLowerCase().contains("no pid") ||
                comment_on.toLowerCase().contains("no message"))) {

            SpannableStringBuilder prompt = new SpannableStringBuilder(comment_on+"\n");
            prompt.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorRecieveText)), 0, prompt.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            prompt.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE), 0, prompt.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            requireActivity().runOnUiThread(() -> receiveText.append(prompt));

            Log.d("ODB2llm", "msg from OBD2" + spn + "meaning: " + comment_on);
            if (comment_on.split("\\s+").length > 3 && getActivity() != null) {
               //OBD2inference("<start_of_turn>user As an automotive mechanic, provide only a 10-word comment on" + comment_on + "nothing else. " +
              //        "Do not include 'Sure,' 'Here is,' or any additional text. Respond with exactly 5 words <end_of_turn>");
               // chatviewModel.requestResponse("describe" + comment_on);
                requireActivity().runOnUiThread(() -> receiveText.append(comment_on+"\n"));

            }
        }
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    @Override
    public void onSerialConnect() {
        Log.d(OBDUtils.TAG, "connected to module");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        ArrayDeque<byte[]> datas = new ArrayDeque<>();
        datas.add(data);
        receive(datas);
    }

    public void onSerialRead(ArrayDeque<byte[]> datas) {
        receive(datas);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

}
