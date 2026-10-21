package com.nutomic.syncthingandroid.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.Constants;

import java.util.Arrays;
import java.util.List;

public class FolderTypeDialogActivity extends ThemedAppCompatActivity {

    public static final String EXTRA_FOLDER_TYPE = ".activities.FolderTypeDialogActivity.FOLDER_TYPE";
    public static final String EXTRA_RESULT_FOLDER_TYPE = ".activities.FolderTypeDialogActivity.EXTRA_RESULT_FOLDER_TYPE";

    private String selectedType;

    private TextView mTypeDescription;

    private OnBackPressedCallback mBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            saveConfiguration();
            finish();
        }
    };

    private static final List<String> mTypes = Arrays.asList(
        Constants.FOLDER_TYPE_SEND_RECEIVE,
        Constants.FOLDER_TYPE_SEND_ONLY,
        Constants.FOLDER_TYPE_RECEIVE_ONLY,
        Constants.FOLDER_TYPE_RECEIVE_ENCRYPTED
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_foldertype_dialog);
        if (savedInstanceState == null) {
            selectedType = getIntent().getStringExtra(EXTRA_FOLDER_TYPE);
        }
        initiateFinishBtn();
        initiateSpinner();
        mTypeDescription = findViewById(R.id.folderTypeDescriptionDialog);
        updateTypeDescription(selectedType);

        // Register OnBackPressedCallback
        getOnBackPressedDispatcher().addCallback(this, mBackPressedCallback);
    }

    private void initiateFinishBtn() {
        Button finishBtn = findViewById(R.id.finish_btn);
        finishBtn.setOnClickListener(v -> {
            saveConfiguration();
            finish();
        });
    }

    private void saveConfiguration() {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_RESULT_FOLDER_TYPE, selectedType);
        setResult(AppCompatActivity.RESULT_OK, intent);
    }

    private void initiateSpinner() {
        Spinner folderTypeSpinner = findViewById(R.id.folderTypeSpinner);
        folderTypeSpinner.setSelection(mTypes.indexOf(selectedType));
        folderTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String type = mTypes.get(position);
                if (!type.equals(selectedType)) {
                    selectedType = type;
                }
                updateTypeDescription(type);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // This is not allowed.
            }
        });
    }

    /** The folder type copy lives inside the dialog, updating with the selection. */
    private void updateTypeDescription(String type) {
        if (mTypeDescription == null) {
            return;
        }
        String text;
        switch (type) {
            case Constants.FOLDER_TYPE_SEND_RECEIVE:
                text = getString(R.string.folder_type_sendreceive_description);
                break;
            case Constants.FOLDER_TYPE_SEND_ONLY:
                text = getString(R.string.folder_type_sendonly_description);
                break;
            case Constants.FOLDER_TYPE_RECEIVE_ONLY:
                text = getString(R.string.folder_type_receiveonly_description);
                break;
            case Constants.FOLDER_TYPE_RECEIVE_ENCRYPTED:
                text = getString(R.string.folder_type_receive_encrypted_description);
                break;
            default:
                text = "";
        }
        mTypeDescription.setText(text);
    }
}
