/*
 * Copyright (c) 2016 Àlex Magaz Graça <alexandre.magaz@gmail.com>
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
package org.gnucash.android.test.unit.export;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseHelper;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.export.ExportFormat;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.export.ofx.OfxExporter;
import org.gnucash.android.export.ofx.OfxHelper;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Book;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.test.unit.BookHelperTest;
import org.gnucash.android.test.unit.testutil.ShadowCrashlytics;
import org.gnucash.android.test.unit.testutil.ShadowUserVoice;
import org.gnucash.android.util.TimestampHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;


@RunWith(RobolectricTestRunner.class)
//package is required so that resources can be found in dev mode
@Config(sdk = 21, shadows = {ShadowCrashlytics.class, ShadowUserVoice.class})
public class OfxExporterTest extends BookHelperTest {

    private String mBookUID;
    private SQLiteDatabase mDb;

    @Before
    public void setUp() throws Exception {
        BooksDbAdapter booksDbAdapter = BooksDbAdapter.getInstance();
        Book testBook = new Book("testRootAccountUID");
        booksDbAdapter.addRecord(testBook);
        mBookUID = testBook.getUID();
        DatabaseHelper databaseHelper =
            new DatabaseHelper(GnuCashApplication.getAppContext(), testBook.getUID());
        mDb = databaseHelper.getWritableDatabase();
    }

    @After
    public void tearDown() {
        BooksDbAdapter booksDbAdapter = BooksDbAdapter.getInstance();
        booksDbAdapter.deleteBook(mBookUID);
        mDb.close();
    }

    /**
     * When there aren't new or modified transactions, the OFX exporter
     * shouldn't create any file.
     */
    @Test
    public void testWithNoTransactionsToExport_shouldNotCreateAnyFile() {
        Context context = GnuCashApplication.getAppContext();
        ExportParams exportParameters = new ExportParams(ExportFormat.OFX);
        exportParameters.setExportStartTime(TimestampHelper.getTimestampFromEpochZero());
        exportParameters.setExportTarget(ExportParams.ExportTarget.SD_CARD);
        exportParameters.setDeleteTransactionsAfterExport(false);
        OfxExporter exporter = new OfxExporter(context, exportParameters, mBookUID);
        assertThat(exporter.generateExport()).isEmpty();
    }

    /**
     * Test that OFX files are generated
     */
    //FIXME: test failing with NPE
    public void testGenerateOFXExport() {
        Context context = GnuCashApplication.getAppContext();
        AccountsDbAdapter accountsDbAdapter = new AccountsDbAdapter(mDb);

        Account account = new Account("Basic Account");
        Transaction transaction = new Transaction("One transaction");
        transaction.addSplit(new Split(Money.createZeroInstance("EUR"), account.getUID()));
        account.addTransaction(transaction);

        accountsDbAdapter.addRecord(account);

        ExportParams exportParameters = new ExportParams(ExportFormat.OFX);
        exportParameters.setExportStartTime(TimestampHelper.getTimestampFromEpochZero());
        exportParameters.setExportTarget(ExportParams.ExportTarget.SD_CARD);
        exportParameters.setDeleteTransactionsAfterExport(false);

        OfxExporter exporter = new OfxExporter(context, exportParameters, mBookUID);
        List<String> exportedFiles = exporter.generateExport();

        assertThat(exportedFiles).hasSize(1);
        File file = new File(exportedFiles.get(0));
        assertThat(file).exists().hasExtension("ofx");
        assertThat(file.length()).isGreaterThan(0L);
        file.delete();
    }

    @Test
    public void testDateTime() {
        TimeZone tz = TimeZone.getTimeZone("EST");
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(tz);
        cal.set(Calendar.YEAR, 1996);
        cal.set(Calendar.MONTH, Calendar.DECEMBER);
        cal.set(Calendar.DAY_OF_MONTH, 5);
        cal.set(Calendar.HOUR_OF_DAY, 13);
        cal.set(Calendar.MINUTE, 22);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 124);

        String formatted = OfxHelper.getOfxFormattedTime(cal.getTimeInMillis(), tz);
        assertThat(formatted).isEqualTo("19961205132200.124[-5:EST]");

        cal.set(Calendar.MONTH, Calendar.OCTOBER);
        formatted = OfxHelper.getOfxFormattedTime(cal.getTimeInMillis(), tz);
        assertThat(formatted).isEqualTo("19961005142200.124[-4:EDT]");
    }
}