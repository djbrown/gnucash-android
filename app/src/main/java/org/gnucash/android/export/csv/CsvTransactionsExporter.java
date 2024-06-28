/*
 * Copyright (c) 2018 Semyannikov Gleb <nightdevgame@gmail.com>
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

package org.gnucash.android.export.csv;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import org.gnucash.android.R;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.util.PreferencesHelper;
import org.gnucash.android.util.TimestampHelper;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Creates a GnuCash CSV transactions representation of the accounts and transactions
 *
 * @author Semyannikov Gleb <nightdevgame@gmail.com>
 */
public class CsvTransactionsExporter extends Exporter {

    private final char mCsvSeparator;

    private final DateTimeFormatter dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd");

    /**
     * Overloaded constructor.
     * Creates an exporter with an already open database instance.
     *
     * @param context The context.
     * @param params Parameters for the export
     * @param bookUID The book UID.
     */
    public CsvTransactionsExporter(@NonNull Context context,
                                   @NonNull ExportParams params,
                                   @NonNull String bookUID) {
        super(context, params, bookUID);
        mCsvSeparator = params.getCsvSeparator();
    }

    @Override
    public List<String> generateExport() throws ExporterException {
        String outputFile = getExportCacheFilePath();

        try (CsvWriter csvWriter = new CsvWriter(new FileWriter(outputFile), String.valueOf(mCsvSeparator))) {
            generateExport(csvWriter);
            close();
        } catch (IOException ex) {
            Timber.e(ex, "Error exporting CSV");
            throw new ExporterException(mExportParams, ex);
        }

        return Arrays.asList(outputFile);
    }

    /**
     * Write splits to CSV format
     *
     * @param splits Splits to be written
     */
    private void writeSplitsToCsv(@NonNull List<Split> splits, @NonNull CsvWriter writer) throws IOException, Money.CurrencyMismatchException {
        int index = 0;

        Map<String, Account> uidAccountMap = new HashMap<>();

        for (Split split : splits) {
            if (index++ > 0) { // the first split is on the same line as the transactions. But after that, we
                writer.write("" // Date
                    + mCsvSeparator // Transaction ID
                    + mCsvSeparator // Number
                    + mCsvSeparator // Description
                    + mCsvSeparator // Notes
                    + mCsvSeparator // Commodity/Currency
                    + mCsvSeparator // Void Reason
                    + mCsvSeparator // Action
                    + mCsvSeparator // Memo
                );
            }
            writer.writeToken(split.getMemo());

            //cache accounts so that we do not have to go to the DB each time
            String accountUID = split.getAccountUID();
            Account account;
            if (uidAccountMap.containsKey(accountUID)) {
                account = uidAccountMap.get(accountUID);
            } else {
                account = mAccountsDbAdapter.getRecord(accountUID);
                uidAccountMap.put(accountUID, account);
            }

            writer.writeToken(account.getFullName());
            writer.writeToken(account.getName());

            String sign = split.getType() == TransactionType.CREDIT ? "-" : "";
            writer.writeToken(sign + split.getQuantity().formattedString());
            writer.writeToken(sign + split.getQuantity().toLocaleString());
            writer.writeToken(String.valueOf(split.getReconcileState()));
            if (split.getReconcileState() == Split.FLAG_RECONCILED) {
                String recDateString = dateFormat.print(split.getReconcileDate().getTime());
                writer.writeToken(recDateString);
            } else {
                writer.writeToken(null);
            }
            writer.writeEndToken(split.getQuantity().div(split.getValue().toDouble()).toLocaleString());
        }
    }

    private void generateExport(final CsvWriter csvWriter) throws ExporterException {
        try {
            List<String> names = Arrays.asList(mContext.getResources().getStringArray(R.array.csv_transaction_headers));
            for (int i = 0; i < names.size(); i++) {
                csvWriter.writeToken(names.get(i));
            }
            csvWriter.newLine();


            Cursor cursor = mTransactionsDbAdapter.fetchTransactionsModifiedSince(mExportParams.getExportStartTime());
            Timber.d("Exporting %d transactions to CSV", cursor.getCount());
            while (cursor.moveToNext()) {
                Transaction transaction = mTransactionsDbAdapter.buildModelInstance(cursor);
                csvWriter.writeToken(dateFormat.print(transaction.getTimeMillis()));
                csvWriter.writeToken(transaction.getUID());
                csvWriter.writeToken(null);  //Transaction number

                csvWriter.writeToken(transaction.getDescription());
                csvWriter.writeToken(transaction.getNote());

                csvWriter.writeToken("CURRENCY::" + transaction.getCurrencyCode());
                csvWriter.writeToken(null); // Void Reason
                csvWriter.writeToken(null); // Action
                writeSplitsToCsv(transaction.getSplits(), csvWriter);
            }
            cursor.close();

            PreferencesHelper.setLastExportTime(TimestampHelper.getTimestampFromNow());
        } catch (Exception e) {
            Timber.e(e);
            throw new ExporterException(mExportParams, e);
        }
    }
}
