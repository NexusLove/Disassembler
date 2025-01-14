package com.mcal.disassembler;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gc.materialdesign.widgets.ProgressDialog;
import com.gc.materialdesign.widgets.SnackBar;
import com.mcal.disassembler.nativeapi.DisassemblerDumper;
import com.mcal.disassembler.nativeapi.Dumper;
import com.nbsp.materialfilepicker.MaterialFilePicker;
import com.nbsp.materialfilepicker.ui.FilePickerActivity;

import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements MainView {
    private static final int FILE_SELECT_CODE = 0;

    static {
        System.loadLibrary("disassembler");
    }

    ProgressDialog dialog;
    private RecyclerView recentOpened;
    private ArrayList<String> paths = new ArrayList<>();
    private String path;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Settings.ACTION_MANAGE_OVERLAY_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Settings.ACTION_MANAGE_OVERLAY_PERMISSION}, 1);
            }
        }

        new Database(this);
        recentOpened = findViewById(R.id.items);
        recentOpened.setLayoutManager(new LinearLayoutManager(this));
        Cursor cursor = RecentsManager.getRecents();
        if (cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                paths.add(cursor.getString(0));
            }
        }
        recentOpened.setAdapter(new ListAdapter(paths, this));
    }

    void updateRecents() {
        paths.clear();
        Cursor cursor = RecentsManager.getRecents();
        if (cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                paths.add(cursor.getString(0));
            }
        }
        Objects.requireNonNull(recentOpened.getAdapter()).notifyDataSetChanged();
    }

    public void chooseSdcard(View view) {
        showFileChooser();
    }

    private void showFileChooser() {
        try {
            new MaterialFilePicker()
                    .withActivity(this)
                    .withRequestCode(FILE_SELECT_CODE)
                    .withFilter(Pattern.compile(".*\\.so$"))
                    .withHiddenFiles(true)
                    .withTitle(getString(R.string.pickSo))
                    .start();
        } catch (android.content.ActivityNotFoundException ex) {
            new SnackBar(this, getString(R.string.noFile)).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_SELECT_CODE) {
            if (resultCode == RESULT_OK) {
                String filePath = data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
                RecentsManager.add(filePath);
                updateRecents();
                loadSo(filePath);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void loadSo(final String path) {
        showProgressDialog();
        this.path = path;
        new Thread() {
            public void run() {
                DisassemblerDumper.load(path);
                Dumper.readData();
                MainActivity.this.toClassesActivity();
            }
        }.start();
    }

    public void showProgressDialog() {
        dialog = new ProgressDialog(MainActivity.this, getString(R.string.loading));
        dialog.show();
    }

    public void dismissProgressDialog() {
        if (dialog != null)
            dialog.dismiss();
        dialog = null;
    }

    public void toClassesActivity() {
        Bundle bundle = new Bundle();
        bundle.putString("filePath", path);
        Intent intent = new Intent(MainActivity.this, SymbolsActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);
        dismissProgressDialog();
    }
}