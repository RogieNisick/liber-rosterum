package com.medoedy.liber;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/** Минимальный провайдер: даёт приложению камеры записать снимок в наш файл (без библиотек FileProvider). */
public final class PhotoProvider extends ContentProvider {
    static final String AUTH = "com.medoedy.liber.photos";

    static File shot(android.content.Context c) { return new File(c.getCacheDir(), "camera_shot.jpg"); }

    static Uri uri() { return Uri.parse("content://" + AUTH + "/camera_shot.jpg"); }

    @Override public boolean onCreate() { return true; }

    /** Файлы для «Поделиться» (экспорт ростеров) — cache/share/имя. */
    static File shareDir(android.content.Context c) { File d = new File(c.getCacheDir(), "share"); d.mkdirs(); return d; }

    static Uri shareUri(String name) { return Uri.parse("content://" + AUTH + "/share/" + Uri.encode(name)); }

    private File fileOf(Uri uri) {
        java.util.List<String> seg = uri.getPathSegments();
        if (seg.size() == 2 && seg.get(0).equals("share")) {
            File f = new File(shareDir(getContext()), new File(seg.get(1)).getName());
            return f;
        }
        return shot(getContext());
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileOf(uri);
        int m = mode.contains("w") ? ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(f, m);
    }

    @Override public String getType(Uri uri) {
        String n = uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment();
        if (n.endsWith(".rosz")) return "application/zip";
        if (n.endsWith(".ros")) return "application/xml";
        return "image/jpeg";
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
        File f = fileOf(u);
        android.database.MatrixCursor c = new android.database.MatrixCursor(new String[]{android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE});
        c.addRow(new Object[]{f.getName(), f.length()});
        return c;
    }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
