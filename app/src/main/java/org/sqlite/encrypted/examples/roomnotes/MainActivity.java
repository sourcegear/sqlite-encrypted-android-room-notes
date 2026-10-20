package org.sqlite.encrypted.examples.roomnotes;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import org.sqlite.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private static final String ACTIVATION_KEY_PLACEHOLDER =
            "replace-with-your-see-activation-key";
    private static final String ACTIVATION_PROBE_DATABASE_KEY =
            "sqlite-encrypted-activation-probe";

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final List<Note> notes = new ArrayList<>();

    private ArrayAdapter<Note> adapter;
    private NotesDatabase database;
    private EditText search;
    private EditText title;
    private EditText body;
    private Button save;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        search = findViewById(R.id.search);
        title = findViewById(R.id.title);
        body = findViewById(R.id.body);
        save = findViewById(R.id.save);
        status = findViewById(R.id.status);
        ListView list = findViewById(R.id.notes);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, notes);
        list.setAdapter(adapter);

        save.setEnabled(false);
        save.setOnClickListener(view -> saveNote());
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            deleteNote(notes.get(position));
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                refreshNotes(value.toString());
            }
            @Override public void afterTextChanged(Editable value) {}
        });

        databaseExecutor.execute(this::openDatabase);
    }

    private void openDatabase() {
        try {
            String activationKey = requireActivationKey();
            System.loadLibrary("sqliteX");
            SQLiteDatabase.activateSEE(activationKey);
            verifySEEActivation();
            String databaseKey = new DatabaseKeyStore(this).getOrCreate();
            database = Room.databaseBuilder(this, NotesDatabase.class, "notes.db")
                    .openHelperFactory(new SeeOpenHelperFactory(databaseKey))
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .build();
            database.getOpenHelper().getWritableDatabase();

            runOnUiThread(() -> {
                save.setEnabled(true);
                status.setText("Encrypted database open. Long-press a note to delete it.");
                refreshNotes(search.getText().toString());
            });
        } catch (Exception error) {
            showError("Could not open the encrypted Room database", error);
        }
    }

    private String requireActivationKey() {
        String activationKey = BuildConfig.SQLITE_ENCRYPTED_ACTIVATION_KEY.trim();
        if (activationKey.isEmpty() || activationKey.equals(ACTIVATION_KEY_PLACEHOLDER)) {
            throw new IllegalStateException(
                    "Missing SEE activation key. Set SQLITE_ENCRYPTED_ACTIVATION_KEY "
                            + "or -PseeActivationKey, then completely restart the application.");
        }
        if (activationKey.startsWith("see-")) {
            throw new IllegalStateException(
                    "SQLITE_ENCRYPTED_ACTIVATION_KEY must not include the see- prefix. "
                            + "That prefix is used only with the PRAGMA form.");
        }
        return activationKey;
    }

    private void verifySEEActivation() {
        File probeFile = new File(getCacheDir(), "sqlite-encrypted-activation-probe.db");
        SQLiteDatabase probeDatabase = null;
        SQLiteDatabase.deleteDatabase(probeFile);
        try {
            probeDatabase = SQLiteDatabase.openOrCreateDatabase(
                    probeFile, null, ACTIVATION_PROBE_DATABASE_KEY);
        } catch (RuntimeException cause) {
            throw new IllegalStateException(
                    "SEE activation failed. Verify that SQLITE_ENCRYPTED_ACTIVATION_KEY "
                            + "is valid for this SQLite version and Android, then completely "
                            + "restart the application.",
                    cause);
        } finally {
            if (probeDatabase != null) {
                probeDatabase.close();
            }
            SQLiteDatabase.deleteDatabase(probeFile);
        }
    }

    private void saveNote() {
        String cleanTitle = title.getText().toString().trim();
        String cleanBody = body.getText().toString().trim();
        if (cleanTitle.isEmpty() || cleanBody.isEmpty()) {
            Toast.makeText(this, "Enter a title and note", Toast.LENGTH_SHORT).show();
            return;
        }

        save.setEnabled(false);
        databaseExecutor.execute(() -> {
            try {
                database.notes().insert(new Note(cleanTitle, cleanBody, System.currentTimeMillis()));
                runOnUiThread(() -> {
                    title.setText("");
                    body.setText("");
                    save.setEnabled(true);
                    refreshNotes(search.getText().toString());
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> save.setEnabled(true));
                showError("Could not save the note", error);
            }
        });
    }

    private void deleteNote(Note note) {
        databaseExecutor.execute(() -> {
            try {
                database.notes().delete(note.id);
                runOnUiThread(() -> refreshNotes(search.getText().toString()));
            } catch (RuntimeException error) {
                showError("Could not delete the note", error);
            }
        });
    }

    private void refreshNotes(String term) {
        if (database == null) {
            return;
        }
        databaseExecutor.execute(() -> {
            List<Note> result = term.trim().isEmpty()
                    ? database.notes().list()
                    : database.notes().search(term.trim());
            runOnUiThread(() -> {
                notes.clear();
                notes.addAll(result);
                adapter.notifyDataSetChanged();
            });
        });
    }

    private void showError(String message, Throwable error) {
        runOnUiThread(() -> {
            status.setText(message + ": " + error.getMessage());
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onDestroy() {
        if (database != null) {
            database.close();
        }
        databaseExecutor.shutdown();
        super.onDestroy();
    }
}
