package com.aaronmaxlab.maxplayer.filepicker;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.databinding.ActivityMediaBrowserBinding;
import com.aaronmaxlab.maxplayer.filepicker.adapter.MediaFileAdapter;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MediaBrowserActivity extends AppCompatActivity {

    public static final String EXTRA_MEDIA_URI = "media_uri";
    public static final String EXTRA_MEDIA_NAME = "media_name";
    public static final String EXTRA_MEDIA_TYPE = "media_type";

    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_PLAYLIST = "playlist";

    private static final String PREFS_NAME = "iptv_settings";
    private static final String KEY_ROOT_URI = "media_folder_uri";

    private final List<DocumentFile> folderStack = new ArrayList<>();

    private ActivityResultLauncher<Intent> folderPicker;

    private MediaFileAdapter adapter;
    private RecyclerView recyclerMedia;
    private TextView txtTitle;
    private TextView txtPath;
    private TextView txtEmpty;
    private ImageButton btnBackFolder;
    private Button btnChooseFolder;

    private Uri rootUri;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        ActivityMediaBrowserBinding binding = ActivityMediaBrowserBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        // bottom nav set black
        UIUtils.setLightNavigationBars(this, false);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initializeViews();
        initializeFolderPicker();

        adapter = new MediaFileAdapter(this::onMediaClicked);

        recyclerMedia.setLayoutManager(
                new LinearLayoutManager(this)
        );
        recyclerMedia.setAdapter(adapter);

        btnChooseFolder.setOnClickListener(v -> openFolderPicker());
        btnBackFolder.setOnClickListener(v -> navigateUp());

        restoreSavedFolder();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (folderStack.size() > 1) {
                    navigateUp();
                } else {
                    // Disable this callback so the system handles the default back action (e.g., exiting activity)
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void initializeViews() {
        recyclerMedia = findViewById(R.id.recyclerMedia);
        txtTitle = findViewById(R.id.txtTitle);
        txtPath = findViewById(R.id.txtPath);
        txtEmpty = findViewById(R.id.txtEmpty);
        btnBackFolder = findViewById(R.id.btnBackFolder);
        btnChooseFolder = findViewById(R.id.btnChooseFolder);
    }

    private void initializeFolderPicker() {
        folderPicker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK
                            || result.getData() == null) {
                        return;
                    }

                    Intent data = result.getData();
                    Uri selectedUri = data.getData();

                    if (selectedUri == null) {
                        return;
                    }

                    int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                    // Fallback logic guarantees Lint that a valid FLAG constant is passed
                    if (takeFlags == 0) {
                        takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    }

                    try {
                        getContentResolver().takePersistableUriPermission(selectedUri, takeFlags);
                    } catch (SecurityException e) {
                        Toast.makeText(
                                this,
                                "Could not save folder access. Please select again.",
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    DocumentFile selectedFolder =
                            DocumentFile.fromTreeUri(this, selectedUri);

                    if (selectedFolder == null
                            || !selectedFolder.isDirectory()
                            || !selectedFolder.canRead()) {
                        Toast.makeText(
                                this,
                                "Cannot read the selected folder.",
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    rootUri = selectedUri;

                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putString(KEY_ROOT_URI, rootUri.toString())
                            .apply();

                    folderStack.clear();
                    folderStack.add(selectedFolder);

                    displayCurrentFolder();
                }
        );
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        folderPicker.launch(intent);
    }

    private void restoreSavedFolder() {
        String savedUri = getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
        ).getString(KEY_ROOT_URI, null);

        if (savedUri == null) {
            showNoFolder();
            openFolderPicker();
            return;
        }

        try {
            rootUri = Uri.parse(savedUri);

            DocumentFile savedFolder =
                    DocumentFile.fromTreeUri(this, rootUri);

            if (savedFolder == null || !savedFolder.canRead()) {
                clearSavedFolder();
                showNoFolder();
                openFolderPicker();
                return;
            }

            folderStack.clear();
            folderStack.add(savedFolder);
            displayCurrentFolder();

        } catch (SecurityException e) {
            clearSavedFolder();
            showNoFolder();
            openFolderPicker();
        }
    }

    private void clearSavedFolder() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .remove(KEY_ROOT_URI)
                .apply();

        folderStack.clear();
        rootUri = null;
    }

    private void showNoFolder() {
        txtTitle.setText("IPTV Browser");
        txtPath.setText("Choose a media folder");
        adapter.submitList(Collections.emptyList());
        txtEmpty.setText("Select a folder to browse media files.");
        txtEmpty.setVisibility(View.VISIBLE);
        btnBackFolder.setEnabled(false);
    }

    private void displayCurrentFolder() {
        if (folderStack.isEmpty()) {
            showNoFolder();
            return;
        }

        DocumentFile currentFolder = getCurrentFolder();

        if (currentFolder == null || !currentFolder.canRead()) {
            Toast.makeText(
                    this,
                    "Cannot access this folder.",
                    Toast.LENGTH_SHORT
            ).show();
            navigateUp();
            return;
        }

        txtTitle.setText(
                currentFolder.getName() == null
                        ? "Media"
                        : currentFolder.getName()
        );

        txtPath.setText(buildBreadcrumb());
        btnBackFolder.setEnabled(folderStack.size() > 1);

        List<MediaFile> mediaFiles = scanFolder(currentFolder);

        adapter.submitList(mediaFiles);

        boolean empty = mediaFiles.isEmpty();
        txtEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        txtEmpty.setText("No supported media files in this folder");
    }

    private List<MediaFile> scanFolder(DocumentFile folder) {
        List<MediaFile> directories = new ArrayList<>();
        List<MediaFile> mediaFiles = new ArrayList<>();

        try {
            for (DocumentFile file : folder.listFiles()) {
                if (!file.canRead()) {
                    continue;
                }

                String name = file.getName();

                if (name == null || name.isEmpty()) {
                    continue;
                }

                if (file.isDirectory()) {
                    directories.add(new MediaFile(
                            name,
                            file.getUri(),
                            MediaFile.Type.FOLDER,
                            file
                    ));
                    continue;
                }

                if (!file.isFile()) {
                    continue;
                }

                MediaFile.Type type = getMediaType(name);

                if (type != null) {
                    mediaFiles.add(new MediaFile(
                            name,
                            file.getUri(),
                            type,
                            file
                    ));
                }
            }
        } catch (SecurityException e) {
            Toast.makeText(
                    this,
                    "Permission denied while reading folder.",
                    Toast.LENGTH_SHORT
            ).show();
        }

        Comparator<MediaFile> byName =
                (a, b) -> a.getName().compareToIgnoreCase(b.getName());

        directories.sort(byName);
        mediaFiles.sort(byName);

        List<MediaFile> result = new ArrayList<>(directories);
        result.addAll(mediaFiles);

        return result;
    }

    private MediaFile.Type getMediaType(String fileName) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);

        if (lowerName.endsWith(".m3u") ||
                lowerName.endsWith(".m3u8") ||
                lowerName.endsWith(".pls") ||
                lowerName.endsWith(".xspf")) {

            return MediaFile.Type.PLAYLIST;
        }

        String[] videoExtensions = {
                ".mp4", ".mkv", ".avi", ".mov", ".webm",
                ".ts", ".m2ts", ".mpg", ".mpeg", ".m4v",
                ".wmv", ".flv", ".vob", ".3gp", ".ogv"
        };

        for (String extension : videoExtensions) {
            if (lowerName.endsWith(extension)) {
                return MediaFile.Type.VIDEO;
            }
        }

        return null;
    }

    private void onMediaClicked(MediaFile mediaFile) {
        if (mediaFile.getType() == MediaFile.Type.FOLDER) {
            folderStack.add(mediaFile.getDocumentFile());
            displayCurrentFolder();
            return;
        }

        Intent result = new Intent();
        result.setData(mediaFile.getUri());

        result.putExtra(EXTRA_MEDIA_URI, mediaFile.getUri().toString());
        result.putExtra(EXTRA_MEDIA_NAME, mediaFile.getName());

        if (mediaFile.getType() == MediaFile.Type.PLAYLIST) {
            result.putExtra(EXTRA_MEDIA_TYPE, TYPE_PLAYLIST);
        } else {
            result.putExtra(EXTRA_MEDIA_TYPE, TYPE_VIDEO);
        }

        result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        setResult(RESULT_OK, result);
        finish();
    }

    private DocumentFile getCurrentFolder() {
        if (folderStack.isEmpty()) {
            return null;
        }

        return folderStack.get(folderStack.size() - 1);
    }

    private String buildBreadcrumb() {
        StringBuilder path = new StringBuilder();

        for (DocumentFile folder : folderStack) {
            if (path.length() > 0) {
                path.append(" / ");
            }

            String name = folder.getName();
            path.append(name == null ? "Media" : name);
        }

        return path.toString();
    }

    private void navigateUp() {
        if (folderStack.size() > 1) {
            folderStack.remove(folderStack.size() - 1);
            displayCurrentFolder();
        }
    }




}