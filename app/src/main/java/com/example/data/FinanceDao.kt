package com.example.data

import androidx.room.*
import com.example.model.BankStatementEntryEntity
import com.example.model.CashTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    // BARGELD-TRANSAKTIONEN
    @Query("SELECT * FROM cash_transactions ORDER BY date DESC")
    fun getAllCashTransactions(): Flow<List<CashTransactionEntity>>

    @Query("SELECT * FROM cash_transactions ORDER BY date DESC")
    suspend fun getAllCashTransactionsList(): List<CashTransactionEntity>

    @Query("SELECT * FROM cash_transactions WHERE matchReconciliationMonth = :month ORDER BY date ASC")
    suspend fun getCashTransactionsForMonth(month: String): List<CashTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashTransaction(tx: CashTransactionEntity)

    @Update
    suspend fun updateCashTransaction(tx: CashTransactionEntity)

    @Delete
    suspend fun deleteCashTransaction(tx: CashTransactionEntity)

    @Query("DELETE FROM cash_transactions WHERE id = :id")
    suspend fun deleteCashTransactionById(id: String)

    // KONTOAUSZUGSEINTRÄGE
    @Query("SELECT * FROM bank_statement_entries ORDER BY date DESC")
    fun getAllBankStatementEntries(): Flow<List<BankStatementEntryEntity>>

    @Query("SELECT * FROM bank_statement_entries ORDER BY date DESC")
    suspend fun getAllBankStatementEntriesList(): List<BankStatementEntryEntity>

    @Query("SELECT * FROM bank_statement_entries WHERE monthYear = :month ORDER BY date ASC")
    suspend fun getBankStatementEntriesForMonth(month: String): List<BankStatementEntryEntity>

    @Query("SELECT * FROM bank_statement_entries WHERE isCashWithdrawal = 1 ORDER BY date DESC")
    fun getCashWithdrawals(): Flow<List<BankStatementEntryEntity>>

    @Query("SELECT * FROM bank_statement_entries WHERE isCashWithdrawal = 1 AND monthYear = :month ORDER BY date ASC")
    suspend fun getCashWithdrawalsForMonth(month: String): List<BankStatementEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBankStatementEntry(entry: BankStatementEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBankStatementEntries(entries: List<BankStatementEntryEntity>)

    @Update
    suspend fun updateBankStatementEntry(entry: BankStatementEntryEntity)

    @Query("DELETE FROM bank_statement_entries WHERE id = :id")
    suspend fun deleteBankStatementEntryById(id: String)

    @Query("DELETE FROM bank_statement_entries")
    suspend fun clearAllBankStatementEntries()
}
