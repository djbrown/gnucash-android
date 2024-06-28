/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.importer;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.OpenableColumns;
import android.widget.Toast;

import org.gnucash.android.R;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.ui.common.GnucashProgressDialog;
import org.gnucash.android.ui.util.TaskDelegate;
import org.gnucash.android.util.BackupManager;
import org.gnucash.android.util.BookUtils;

import java.io.InputStream;

import timber.log.Timber;

/**
 * Imports a GnuCash (desktop) account file and displays a progress dialog.
 * The AccountsActivity is opened when importing is done.
 */
public class ImportAsyncTask extends AsyncTask<Uri, Void, Boolean> {
    private final Activity mContext;
    private TaskDelegate mDelegate;
    private final boolean mBackup;
    private ProgressDialog mProgressDialog;
    private String mImportedBookUID;

    public ImportAsyncTask(Activity context) {
        this(context, null);
    }

    public ImportAsyncTask(Activity context, TaskDelegate delegate) {
        this(context, delegate, false);
    }

    public ImportAsyncTask(Activity context, TaskDelegate delegate, boolean backup) {
        this.mContext = context;
        this.mDelegate = delegate;
        this.mBackup = backup;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        mProgressDialog = new GnucashProgressDialog(mContext);
        mProgressDialog.setTitle(R.string.title_progress_importing_accounts);
        mProgressDialog.setCancelable(true);
        mProgressDialog.setOnCancelListener(dialogInterface -> cancel(true));
        mProgressDialog.show();
    }

    @Override
    protected Boolean doInBackground(Uri... uris) {
        if (mBackup) {
            BackupManager.backupActiveBook();
        }

        Uri uri = uris[0];
        try {
            InputStream accountInputStream = mContext.getContentResolver().openInputStream(uri);
            mImportedBookUID = GncXmlImporter.parse(accountInputStream);
        } catch (final Throwable e) {
            Timber.e(e, "Error importing: %s", uri);

            mContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext,
                            mContext.getString(R.string.toast_error_importing_accounts) + "\n" + e.getLocalizedMessage(),
                            Toast.LENGTH_LONG).show();
                }
            });

            return false;
        }

        Cursor cursor = mContext.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            String displayName = cursor.getString(nameIndex);
            // Remove short file type extension, e.g. ".xml" or ".gnca".
            int indexFileType = displayName.lastIndexOf('.');
            if ((indexFileType > 0) && (indexFileType + 5 >= displayName.length())) {
                displayName = displayName.substring(0, indexFileType);
            }
            ContentValues contentValues = new ContentValues();
            contentValues.put(DatabaseSchema.BookEntry.COLUMN_DISPLAY_NAME, displayName);
            contentValues.put(DatabaseSchema.BookEntry.COLUMN_SOURCE_URI, uri.toString());
            BooksDbAdapter.getInstance().updateRecord(mImportedBookUID, contentValues);

            cursor.close();
        }

        //set the preferences to their default values
        mContext.getSharedPreferences(mImportedBookUID, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(mContext.getString(R.string.key_use_double_entry), true)
                .apply();

        return true;
    }

    @Override
    protected void onPostExecute(Boolean importSuccess) {
        try {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        } catch (IllegalArgumentException ex) {
            //TODO: This is a hack to catch "View not attached to window" exceptions
            //FIXME by moving the creation and display of the progress dialog to the Fragment
        } finally {
            mProgressDialog = null;
        }

        int message = importSuccess ? R.string.toast_success_importing_accounts : R.string.toast_error_importing_accounts;
        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();

        if (mImportedBookUID != null)
            BookUtils.loadBook(mContext, mImportedBookUID);

        if (mDelegate != null)
            mDelegate.onTaskComplete();
    }
}
