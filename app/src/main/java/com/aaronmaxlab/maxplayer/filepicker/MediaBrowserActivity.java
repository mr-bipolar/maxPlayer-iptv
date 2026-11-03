package com.aaronmaxlab.maxplayer.filepicker;

import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aaronmaxlab.maxplayer.databinding.ActivityMediaBrowserBinding;
import com.aaronmaxlab.maxplayer.filepicker.adapter.MediaFileAdapter;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.DeviceUtils;
import com.aaronmaxlab.maxplayer.utils.MediaTypes;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaBrowserActivity extends AppCompatActivity {

    public static final String EXTRA_MEDIA_URI = "media_uri";
    public static final String EXTRA_MEDIA_NAME = "media_name";
    public static final String EXTRA_MEDIA_TYPE = "media_type";

    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_PLAYLIST = "playlist";

    private static final String PREFS_NAME = "iptv_settings";
    private static final String KEY_ROOT_URI = "media_folder_uri";
    private static final String KEY_TV_ROOT_URI = "media_tv_root_uri";
    private static final String KEY_TV_CURRENT_URI = "media_tv_current_uri";
    private static final String DEFAULT_FOLDER_NAME = "Media";
    private static final String DOWNLOADS_FOLDER_RELATIVE_PATH = "Download/MaxPlayer/";

    private static final String[] CHILD_PROJECTION = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
    };

    private static final Comparator<MediaFile> BY_NAME =
            (a, b) -> a.getName().compareToIgnoreCase(b.getName());

    private final List<DocumentFile> folderStack = new ArrayList<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private ActivityMediaBrowserBinding binding;
    private ActivityResultLauncher<Intent> folderPicker;
    private OnBackPressedCallback backCallback;
    private MediaFileAdapter adapter;

    private Uri rootUri;
    private int scanToken; // main-thread only; invalidates in-flight scans
    private boolean isTv;
    private boolean tvStorageUnavailable;
    private boolean hasResumedOnce;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMediaBrowserBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UIUtils.setLightNavigationBars(this, false); // black bottom nav
        UIUtils.applySystemBarPadding(binding.getRoot());

        isTv = DeviceUtils.isTv(this);
        initializeFolderPicker();

        adapter = new MediaFileAdapter(this::onMediaClicked);
        binding.recyclerMedia.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerMedia.setHasFixedSize(true);
        binding.recyclerMedia.setAdapter(adapter);

        binding.btnChooseFolder.setOnClickListener(v -> {
            if (isTv && (rootUri == null || tvStorageUnavailable)) openTvDownloadsFolder();
            else if (isTv) selectCurrentFolder();
            else openFolderPicker();
        });
        binding.btnBackFolder.setOnClickListener(v -> navigateUp());

        // Enabled only inside a subfolder; at the root the system handles back.
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                navigateUp();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);

        restoreSavedFolder();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isTv && hasResumedOnce && getCurrentFolder() != null) {
            displayCurrentFolder();
        }
        hasResumedOnce = true;
    }

    // ---------- Folder selection ----------

    private void initializeFolderPicker() {
        folderPicker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent data = result.getData();
                    if (result.getResultCode() != RESULT_OK || data == null) return;

                    Uri selectedUri = data.getData();
                    if (selectedUri == null) return;

                    int takeFlags = data.getFlags()
                            & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    if (takeFlags == 0) takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;

                    try {
                        getContentResolver().takePersistableUriPermission(selectedUri, takeFlags);
                    } catch (SecurityException e) {
                        AppToast.warning(this, "Could not save folder access. Please select again.");
                        return;
                    }

                    DocumentFile folder = DocumentFile.fromTreeUri(this, selectedUri);
                    if (folder == null || !folder.isDirectory() || !folder.canRead()) {
                        AppToast.info(this, "Cannot read the selected folder.");
                        return;
                    }

                    rootUri = selectedUri;
                    prefs().edit().putString(KEY_ROOT_URI, selectedUri.toString()).apply();
                    if (isTv) {
                        prefs().edit()
                                .putString(KEY_TV_ROOT_URI, selectedUri.toString())
                                .putString(KEY_TV_CURRENT_URI, selectedUri.toString())
                                .apply();
                        tvStorageUnavailable = false;
                    }

                    folderStack.clear();
                    folderStack.add(folder);
                    displayCurrentFolder();
                }
        );
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

        try {
            folderPicker.launch(intent);

        } catch (ActivityNotFoundException e) {

            String message = isTv
                    ? "No folder picker is available on this TV."
                    : "No system folder picker is available on this device.";

            showEmpty(message);
            AppToast.warning(this, message);

            Log.e("FOLDER_PICKER", "Folder picker unavailable", e);
        }
    }

    private void restoreSavedFolder() {
        if (isTv) {
            if (restoreTvFolder()) return;
            if (restoreLegacyTreeFolder()) return;
            openTvDownloadsFolder();
            return;
        }

        String saved = prefs().getString(KEY_ROOT_URI, null);
        if (saved == null) {
            promptForFolder(false);
            return;
        }

        try {
            Uri uri = Uri.parse(saved);
            DocumentFile folder = DocumentFile.fromTreeUri(this, uri);

            if (folder == null || !folder.canRead()) {
                promptForFolder(true);
                return;
            }

            rootUri = uri;
            folderStack.clear();
            folderStack.add(folder);
            displayCurrentFolder();
        } catch (SecurityException e) {
            promptForFolder(true);
        }
    }

    /** Restores the TV browser's own root/current directory without requiring a system picker. */
    private boolean restoreTvFolder() {
        String savedRoot = prefs().getString(KEY_TV_ROOT_URI, null);
        if (savedRoot == null) return false;

        try {
            Uri savedRootUri = Uri.parse(savedRoot);
            Uri savedCurrentUri = Uri.parse(prefs().getString(KEY_TV_CURRENT_URI, savedRoot));
            DocumentFile current;
            if (isDownloadsRoot(savedRootUri)) {
                File currentFile = downloadsFolderFile(savedCurrentUri);
                if (currentFile == null) return false;
                current = DocumentFile.fromFile(currentFile);
            } else if ("file".equals(savedRootUri.getScheme())) {
                File savedRootFile = findKnownTvFileRoot(savedRootUri);
                File currentFile = fileWithinRoot(savedCurrentUri, savedRootFile);
                if (savedRootFile == null || currentFile == null || !canListDirectory(currentFile)) return false;
                current = DocumentFile.fromFile(currentFile);
            } else {
                DocumentFile root = DocumentFile.fromTreeUri(this, savedRootUri);
                current = DocumentFile.fromTreeUri(this, savedCurrentUri);
                if (root == null || current == null || !root.canRead() || !current.canRead()) return false;
            }

            rootUri = savedRootUri;
            folderStack.clear();
            // The saved current directory becomes the browser root; Up exits the screen rather
            // than unexpectedly returning to a parent that the user had already confirmed.
            folderStack.add(current);
            displayCurrentFolder();
            return true;
        } catch (SecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    /** A previously granted SAF tree can still be browsed on TV; retain the grant semantics. */
    private boolean restoreLegacyTreeFolder() {
        String saved = prefs().getString(KEY_ROOT_URI, null);
        if (saved == null) return false;
        try {
            Uri uri = Uri.parse(saved);
            DocumentFile folder = DocumentFile.fromTreeUri(this, uri);
            if (folder == null || !folder.isDirectory() || !folder.canRead()) return false;
            rootUri = uri;
            folderStack.clear();
            folderStack.add(folder);
            displayCurrentFolder();
            return true;
        } catch (SecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    private void openTvDownloadsFolder() {
        File folder = downloadsFolderFile();
        if (canListDirectory(folder)) {
            openTvFileFolder(folder);
            return;
        }

        // Scoped storage does not permit raw directory listing on many Android 10+ devices.
        // MediaStore can still expose entries this app is allowed to read, without creating files.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openTvMediaStoreFolder();
            return;
        }

        showTvDownloadsUnavailable("MaxPlayer cannot read Download/MaxPlayer with this Android storage configuration. "
                + "Create the folder and use Select Folder if this device provides a folder picker.");
    }

    private void openTvFileFolder(@NonNull File folder) {
        rootUri = Uri.fromFile(folder);
        folderStack.clear();
        folderStack.add(DocumentFile.fromFile(folder));
        prefs().edit()
                .putString(KEY_TV_ROOT_URI, rootUri.toString())
                .putString(KEY_TV_CURRENT_URI, rootUri.toString())
                .apply();
        tvStorageUnavailable = false;
        displayCurrentFolder();
    }

    private void openTvMediaStoreFolder() {
        rootUri = Api29.DOWNLOADS_URI;
        File folder = downloadsFolderFile();
        Uri currentUri = Uri.fromFile(folder);
        folderStack.clear();
        folderStack.add(DocumentFile.fromFile(folder));
        prefs().edit()
                .putString(KEY_TV_ROOT_URI, rootUri.toString())
                .putString(KEY_TV_CURRENT_URI, currentUri.toString())
                .apply();
        tvStorageUnavailable = false;
        displayCurrentFolder();
    }


    private void showTvDownloadsUnavailable(@NonNull String message) {
        tvStorageUnavailable = true;
        rootUri = null;
        folderStack.clear();
        showNoFolder();
        binding.txtPath.setText("Download/MaxPlayer");
        showEmpty(message);
    }

    private static boolean canListDirectory(@NonNull File directory) {
        return directory.isDirectory() && directory.canRead() && directory.listFiles() != null;
    }

    @Nullable
    private File findKnownTvFileRoot(@NonNull Uri rootUri) {
        if (!"file".equals(rootUri.getScheme()) || rootUri.getPath() == null) return null;
        try {
            String requested = new File(rootUri.getPath()).getCanonicalPath();
            File root = downloadsFolderFile();
            return root.getCanonicalPath().equals(requested) ? root : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    @Nullable
    private File fileWithinRoot(@NonNull Uri fileUri, @Nullable File root) {
        if (root == null || !"file".equals(fileUri.getScheme()) || fileUri.getPath() == null) return null;
        try {
            File candidate = new File(fileUri.getPath()).getCanonicalFile();
            String rootPath = root.getCanonicalPath();
            String candidatePath = candidate.getPath();
            return candidatePath.equals(rootPath) || candidatePath.startsWith(rootPath + File.separator)
                    ? candidate : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void selectCurrentFolder() {
        DocumentFile current = getCurrentFolder();
        if (current == null) {
            AppToast.warning(this, "Cannot select this folder because it is not accessible.");
            return;
        }
        if (isDownloadsRoot(rootUri)) {
            if (downloadsFolderFile(current.getUri()) == null) {
                AppToast.warning(this, "This directory is outside Download/MaxPlayer.");
                return;
            }
        } else if (!current.isDirectory() || !current.canRead()) {
            AppToast.warning(this, "Cannot select this folder because it is not accessible.");
            return;
        }

        prefs().edit()
                .putString(KEY_TV_ROOT_URI, rootUri.toString())
                .putString(KEY_TV_CURRENT_URI, current.getUri().toString())
                .apply();

        // Keep the SAF tree URI as the query root, while treating the chosen directory as the
        // in-app browser's new root for this session.
        DocumentFile root = current;
        folderStack.clear();
        folderStack.add(root);
        displayCurrentFolder();
    }

    private static boolean isDownloadsRoot(@Nullable Uri uri) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && uri != null && uri.equals(Api29.DOWNLOADS_URI);
    }

    private static final class Api29 {
        static final Uri DOWNLOADS_URI = getDownloadsUri();
    }

    private static Uri getDownloadsUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        }
        return null;
    }

    private File downloadsFolderFile() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MaxPlayer");
    }

    /** File paths here are identifiers only; public Downloads is accessed through MediaStore. */
    @Nullable
    private File downloadsFolderFile(@Nullable Uri uri) {
        if (uri == null || !"file".equals(uri.getScheme()) || uri.getPath() == null) return null;
        try {
            File root = downloadsFolderFile().getCanonicalFile();
            File candidate = new File(uri.getPath()).getCanonicalFile();
            String rootPath = root.getPath();
            String candidatePath = candidate.getPath();
            return candidatePath.equals(rootPath) || candidatePath.startsWith(rootPath + File.separator)
                    ? candidate : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void promptForFolder(boolean clearSaved) {
        if (clearSaved) {
            prefs().edit().remove(KEY_ROOT_URI).apply();
            folderStack.clear();
            rootUri = null;
        }
        showNoFolder();
        if (isTv) {
            showEmpty("No saved folder is available on this TV.");
        } else {
            openFolderPicker();
        }
    }

    // ---------- Display ----------

    private void showNoFolder() {
        scanToken++;
        binding.txtTitle.setText("IPTV Browser");
        binding.txtPath.setText("Choose a media folder");
        adapter.submitList(Collections.emptyList());
        showEmpty("Select a folder to browse media files.");
        binding.btnBackFolder.setEnabled(false);
        backCallback.setEnabled(false);
    }

    private void showEmpty(@Nullable String message) {
        if (message == null) {
            binding.txtEmpty.setVisibility(View.GONE);
            return;
        }
        binding.txtEmpty.setText(message);
        binding.txtEmpty.setVisibility(View.VISIBLE);
    }

    private void displayCurrentFolder() {
        DocumentFile current = getCurrentFolder();
        if (current == null) {
            showNoFolder();
            return;
        }

        String name = current.getName();
        binding.txtTitle.setText(name == null ? DEFAULT_FOLDER_NAME : name);
        binding.txtPath.setText(buildBreadcrumb());

        boolean canGoUp = folderStack.size() > 1;
        binding.btnBackFolder.setEnabled(canGoUp);
        backCallback.setEnabled(canGoUp);

        loadFolderAsync(current);
    }

    private void loadFolderAsync(@NonNull DocumentFile folder) {
        final int token = ++scanToken;
        final Uri tree = rootUri;
        final Uri folderUri = folder.getUri();

        ioExecutor.execute(() -> {
            List<MediaFile> files = scanFolder(tree, folderUri);

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || token != scanToken) return;

                if (files == null) {
                    if (isTv && "file".equals(tree.getScheme()) && folderStack.size() == 1) {
                        openTvMediaStoreFolder();
                        return;
                    }
                    String message = isTv
                            ? "Android did not allow MaxPlayer to read this folder. Use Select Folder if this TV provides a folder picker."
                            : "Cannot access this folder.";
                    if (isTv) tvStorageUnavailable = true;
                    AppToast.error(this, message);
                    if (folderStack.size() > 1) navigateUp();
                    else showEmpty(message);
                    return;
                }

                adapter.submitList(files);
                if (isTv) tvStorageUnavailable = false;
                if (!files.isEmpty()) {
                    showEmpty(null);
                } else if (isTv) {
                    tvStorageUnavailable = folderStack.size() == 1;
                    showEmpty("No supported files or navigable subfolders in " + buildBreadcrumb()
                            + ". If needed, create Download/MaxPlayer and copy files there. Android may hide files copied by another app unless MaxPlayer has folder access.");
                } else {
                    showEmpty("No supported media files in this folder");
                }
            });
        });
    }

    // ---------- Scanning (background thread) ----------

    /** One cursor query for the whole folder. Returns null on failure. */
    @Nullable
    private List<MediaFile> scanFolder(@NonNull Uri tree, @NonNull Uri folderUri) {
        List<MediaFile> directories = new ArrayList<>();
        List<MediaFile> mediaFiles = new ArrayList<>();

        if (isDownloadsRoot(tree)) return scanDownloadsFolder(folderUri);
        if ("file".equals(tree.getScheme())) return scanReadableFileFolder(tree, folderUri);

        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, DocumentsContract.getDocumentId(folderUri));

        try (Cursor c = getContentResolver().query(childrenUri, CHILD_PROJECTION, null, null, null)) {
            if (c == null) return null;

            while (c.moveToNext()) {
                String name = c.getString(1);
                if (name == null || name.isEmpty()) continue;

                Uri uri = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0));

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2))) {
                    directories.add(new MediaFile(name, uri, MediaFile.Type.FOLDER,
                            DocumentFile.fromTreeUri(this, uri)));
                    continue;
                }

                MediaFile.Type type = getMediaType(name);
                if (type != null) {
                    mediaFiles.add(new MediaFile(name, uri, type,
                            DocumentFile.fromSingleUri(this, uri)));
                }
            }
        } catch (Exception e) { // SecurityException, IllegalArgumentException, ...
            return null;
        }

        directories.sort(BY_NAME);
        mediaFiles.sort(BY_NAME);

        List<MediaFile> result = new ArrayList<>(directories.size() + mediaFiles.size());
        result.addAll(directories);
        result.addAll(mediaFiles);
        return result;
    }

    /** Raw File browsing is used only after canListDirectory has verified actual read access. */
    @Nullable
    private List<MediaFile> scanReadableFileFolder(@NonNull Uri rootUri, @NonNull Uri folderUri) {
        File root = findKnownTvFileRoot(rootUri);
        File folder = fileWithinRoot(folderUri, root);
        if (folder == null || !canListDirectory(folder)) return null;

        File[] children = folder.listFiles();
        if (children == null) return null;
        List<MediaFile> directories = new ArrayList<>();
        List<MediaFile> mediaFiles = new ArrayList<>();
        boolean inaccessibleChild = false;
        for (File child : children) {
            if (child.isDirectory()) {
                if (canListDirectory(child)) {
                    directories.add(new MediaFile(child.getName(), Uri.fromFile(child),
                            MediaFile.Type.FOLDER, DocumentFile.fromFile(child)));
                } else {
                    inaccessibleChild = true;
                }
                continue;
            }
            if (!child.isFile()) continue;
            MediaFile.Type type = getMediaType(child.getName());
            if (type != null && !canReadFile(child)) {
                inaccessibleChild = true;
            } else if (type != null) {
                Uri uri = Uri.fromFile(child);
                mediaFiles.add(new MediaFile(child.getName(), uri, type,
                        DocumentFile.fromFile(child)));
            }
        }
        if (inaccessibleChild && directories.isEmpty() && mediaFiles.isEmpty()) return null;
        directories.sort(BY_NAME);
        mediaFiles.sort(BY_NAME);
        List<MediaFile> result = new ArrayList<>(directories.size() + mediaFiles.size());
        result.addAll(directories);
        result.addAll(mediaFiles);
        return result;
    }

    /** Reads public Downloads entries through MediaStore, never by listing the raw filesystem. */
    @Nullable
    private List<MediaFile> scanDownloadsFolder(@NonNull Uri folderUri) {
        File folder = downloadsFolderFile(folderUri);
        if (folder == null) return null;

        final String currentPath;
        try {
            currentPath = downloadsRelativePath(folder);
        } catch (IOException e) {
            return null;
        }
        if (currentPath == null) return null;

        Map<String, File> childDirectories = new LinkedHashMap<>();
        List<MediaFile> mediaFiles = new ArrayList<>();
        String[] projection = {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH
        };
        boolean inaccessibleSupportedFile = false;

        try (Cursor cursor = getContentResolver().query(
                Api29.DOWNLOADS_URI, projection, null, null, null)) {
            if (cursor == null) return null;
            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH);
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameIndex);
                String relativePath = cursor.getString(pathIndex);
                if (name == null || name.isEmpty() || relativePath == null
                        || !relativePath.startsWith(DOWNLOADS_FOLDER_RELATIVE_PATH)) continue;

                if (relativePath.equals(currentPath)) {
                    MediaFile.Type type = getMediaType(name);
                    if (type == null) continue;
                    Uri uri = ContentUris.withAppendedId(
                            Api29.DOWNLOADS_URI, cursor.getLong(idIndex));
                    if (!canReadMediaStoreFile(uri)) {
                        inaccessibleSupportedFile = true;
                        continue;
                    }
                    mediaFiles.add(new MediaFile(name, uri, type,
                            DocumentFile.fromSingleUri(this, uri)));
                    continue;
                }

                if (relativePath.startsWith(currentPath)) {
                    String remainder = relativePath.substring(currentPath.length());
                    int slash = remainder.indexOf('/');
                    if (slash > 0) {
                        String directoryName = remainder.substring(0, slash);
                        childDirectories.putIfAbsent(directoryName,
                                new File(folder, directoryName));
                    }
                }
            }
        } catch (SecurityException | IllegalArgumentException e) {
            return null;
        }

        // Descendant folder rows are inferred from their contents. Probe a real child URI before
        // presenting the inferred folder as navigable; a display name alone is not access.
        List<String> inaccessibleDirectories = new ArrayList<>();
        for (Map.Entry<String, File> entry : childDirectories.entrySet()) {
            Uri probeUri = findReadableChildUri(entry.getValue());
            if (probeUri == null) inaccessibleDirectories.add(entry.getKey());
        }
        for (String name : inaccessibleDirectories) childDirectories.remove(name);

        // A denied file is an access problem, not an empty directory. Do not disguise it as one.
        if (inaccessibleSupportedFile || (!inaccessibleDirectories.isEmpty()
                && mediaFiles.isEmpty() && childDirectories.isEmpty())) return null;

        List<MediaFile> directories = new ArrayList<>();
        for (Map.Entry<String, File> entry : childDirectories.entrySet()) {
            directories.add(new MediaFile(entry.getKey(), Uri.fromFile(entry.getValue()),
                    MediaFile.Type.FOLDER, DocumentFile.fromFile(entry.getValue())));
        }
        directories.sort(BY_NAME);
        mediaFiles.sort(BY_NAME);

        List<MediaFile> result = new ArrayList<>(directories.size() + mediaFiles.size());
        result.addAll(directories);
        result.addAll(mediaFiles);
        return result;
    }

    @Nullable
    private Uri findReadableChildUri(@NonNull File directory) {
        String relativePrefix;
        try {
            relativePrefix = downloadsRelativePath(directory);
        } catch (IOException e) {
            return null;
        }
        if (relativePrefix == null) return null;

        String[] projection = {MediaStore.MediaColumns._ID};
        String selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
        try (Cursor cursor = getContentResolver().query(Api29.DOWNLOADS_URI,
                projection, selection, new String[]{relativePrefix + "%"}, null)) {
            if (cursor == null) return null;
            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            while (cursor.moveToNext()) {
                Uri uri = ContentUris.withAppendedId(Api29.DOWNLOADS_URI,
                        cursor.getLong(idIndex));
                if (canReadMediaStoreFile(uri)) return uri;
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    private boolean canReadMediaStoreFile(Uri uri) {
        try (ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri, "r")) {
            return descriptor != null;
        } catch (SecurityException | IOException e) {
            return false;
        }
    }

    private boolean canReadFile(@NonNull File file) {
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                file, ParcelFileDescriptor.MODE_READ_ONLY)) {
            return descriptor != null;
        } catch (SecurityException | IOException e) {
            return false;
        }
    }

    @Nullable
    private String downloadsRelativePath(@NonNull File folder) throws IOException {
        File root = downloadsFolderFile().getCanonicalFile();
        File canonicalFolder = folder.getCanonicalFile();
        String rootPath = root.getPath();
        String folderPath = canonicalFolder.getPath();
        if (!folderPath.equals(rootPath) && !folderPath.startsWith(rootPath + File.separator)) return null;
        if (folderPath.equals(rootPath)) return DOWNLOADS_FOLDER_RELATIVE_PATH;
        String nested = folderPath.substring(rootPath.length() + 1).replace(File.separatorChar, '/') + "/";
        return DOWNLOADS_FOLDER_RELATIVE_PATH + nested;
    }

    @Nullable
    private static MediaFile.Type getMediaType(@NonNull String fileName) {
        if (MediaTypes.isM3u(fileName)) return MediaFile.Type.PLAYLIST;

        String ext = MediaTypes.extension(fileName);
        if (MediaTypes.AUDIO_EXT.contains(ext) && !MediaTypes.VIDEO_EXT.contains(ext)) {
            return MediaFile.Type.AUDIO;
        }
        if (MediaTypes.isMedia(fileName)) return MediaFile.Type.VIDEO;
        return null;
    }

    // ---------- Navigation / result ----------

    private void onMediaClicked(@NonNull MediaFile mediaFile) {
        if (mediaFile.getType() == MediaFile.Type.FOLDER) {
            folderStack.add(mediaFile.getDocumentFile());
            displayCurrentFolder();
            return;
        }

        boolean isPlaylist = mediaFile.getType() == MediaFile.Type.PLAYLIST;

        Intent result = new Intent()
                .setData(mediaFile.getUri())
                .putExtra(EXTRA_MEDIA_URI, mediaFile.getUri().toString())
                .putExtra(EXTRA_MEDIA_NAME, mediaFile.getName())
                .putExtra(EXTRA_MEDIA_TYPE, isPlaylist ? TYPE_PLAYLIST : TYPE_VIDEO)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        setResult(RESULT_OK, result);
        finish();
    }

    private void navigateUp() {
        if (folderStack.size() > 1) {
            folderStack.remove(folderStack.size() - 1);
            displayCurrentFolder();
        }
    }

    @Nullable
    private DocumentFile getCurrentFolder() {
        return folderStack.isEmpty() ? null : folderStack.get(folderStack.size() - 1);
    }

    @NonNull
    private String buildBreadcrumb() {
        if (isDownloadsRoot(rootUri) && !folderStack.isEmpty()) {
            try {
                String path = downloadsRelativePath(new File(
                        folderStack.get(folderStack.size() - 1).getUri().getPath()));
                if (path != null && path.endsWith("/")) {
                    return path.substring(0, path.length() - 1);
                }
            } catch (IOException ignored) {
                // Fall through to the display-name breadcrumb if path normalization fails.
            }
        }
        if (isTv && rootUri != null && "file".equals(rootUri.getScheme()) && !folderStack.isEmpty()) {
            File root = findKnownTvFileRoot(rootUri);
            File current = fileWithinRoot(folderStack.get(folderStack.size() - 1).getUri(), root);
            if (root != null && current != null) {
                String label = sharedFolderLabel(root);
                String rootPath = root.getAbsolutePath();
                String currentPath = current.getAbsolutePath();
                String suffix = currentPath.equals(rootPath) ? ""
                        : currentPath.substring(rootPath.length() + 1).replace(File.separatorChar, '/');
                String path = suffix.isEmpty() ? label : label + "/" + suffix;
                return path;
            }
        }
        StringBuilder path = new StringBuilder();
        for (DocumentFile folder : folderStack) {
            if (path.length() > 0) path.append(" / ");
            String name = folder.getName();
            path.append(name == null ? DEFAULT_FOLDER_NAME : name);
        }
        return path.toString();
    }

    private String sharedFolderLabel(@NonNull File folder) {
        String externalRoot = Environment.getExternalStorageDirectory().getAbsolutePath();
        String path = folder.getAbsolutePath();
        if (path.startsWith(externalRoot + File.separator)) {
            return path.substring(externalRoot.length() + 1).replace(File.separatorChar, '/');
        }
        return path;
    }

    private android.content.SharedPreferences prefs() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        binding = null;
        super.onDestroy();
    }
}
