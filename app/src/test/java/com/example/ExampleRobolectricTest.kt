package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.model.CustomFieldEntity
import com.example.model.CustomFieldScope
import com.example.model.CustomFieldType
import com.example.ui.DocAnizerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("myDocAnizer-Mobile", appName)
  }

  @Test
  fun `launch MainActivity successfully without crash`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java)
    val activity = controller.setup().get()
    assertNotNull(activity)
  }

  @Test
  fun `verify database custom fields schema and preset initialization`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val db = AppDatabase.getDatabase(context)
    val dao = db.customFieldDao()

    val testField = CustomFieldEntity(
      id = "test_field_1",
      name = "Test Betrag",
      type = CustomFieldType.AMOUNT,
      scope = CustomFieldScope.GLOBAL
    )
    dao.insertCustomField(testField)

    val list = dao.getAllCustomFieldsList()
    assertTrue(list.any { it.name == "Test Betrag" })
  }

  @Test
  fun `verify DocAnizerViewModel initialization and custom fields`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)
    assertNotNull(viewModel)

    val fields = viewModel.customFields.first()
    assertNotNull(fields)
  }

  @Test
  fun `verify IncomeExpenseTracking and reconciliation settings flow`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    viewModel.setEnableIncomeExpenseTracking(true)
    viewModel.setEnableCashTracker(true)
    viewModel.setEnableReceiptExpenses(true)
    viewModel.setEnableBankStatementImport(true)
    viewModel.setEnableMonthlyReconciliation(true)
    viewModel.setReconciliationToleranceDays(7)
    viewModel.setNotifyReconciliationDiscrepancies(true)

    assertEquals(true, viewModel.enableIncomeExpenseTracking.first())
    assertEquals(true, viewModel.enableCashTracker.first())
    assertEquals(true, viewModel.enableReceiptExpenses.first())
    assertEquals(true, viewModel.enableBankStatementImport.first())
    assertEquals(true, viewModel.enableMonthlyReconciliation.first())
    assertEquals(7, viewModel.reconciliationToleranceDays.first())
    assertEquals(true, viewModel.notifyReconciliationDiscrepancies.first())
  }

  @Test
  fun `verify Dashboard widget configuration flow`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    viewModel.setShowKpiWidgets(true)
    viewModel.setShowDeadlinesWidget(true)
    viewModel.setShowFinanceWidget(true)
    viewModel.setShowReconciliationWidget(true)
    viewModel.setShowCategoryDistributionWidget(true)
    viewModel.setShowSecurityScoreWidget(true)

    assertEquals(true, viewModel.showKpiWidgets.first())
    assertEquals(true, viewModel.showDeadlinesWidget.first())
    assertEquals(true, viewModel.showFinanceWidget.first())
    assertEquals(true, viewModel.showReconciliationWidget.first())
    assertEquals(true, viewModel.showCategoryDistributionWidget.first())
    assertEquals(true, viewModel.showSecurityScoreWidget.first())
  }

  @Test
  fun `verify FinanceDao cash transactions and bank statements`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val db = AppDatabase.getDatabase(context)
    val dao = db.financeDao()

    val cashTx = com.example.model.CashTransactionEntity(
      id = "test_cash_1",
      title = "Tanken Bar",
      amount = 45.0,
      type = com.example.model.CashTransactionType.EXPENSE,
      matchReconciliationMonth = "2026-04"
    )
    dao.insertCashTransaction(cashTx)

    val bankEntry = com.example.model.BankStatementEntryEntity(
      id = "test_bank_1",
      date = System.currentTimeMillis(),
      bookingText = "Geldautomat Sparkasse",
      purpose = "Barabhebung",
      amount = -200.0,
      isCashWithdrawal = true,
      monthYear = "2026-04"
    )
    dao.insertBankStatementEntry(bankEntry)

    val cashList = dao.getAllCashTransactionsList()
    val bankList = dao.getAllBankStatementEntriesList()
    val withdrawals = dao.getCashWithdrawalsForMonth("2026-04")

    assertTrue(cashList.any { it.id == "test_cash_1" })
    assertTrue(bankList.any { it.id == "test_bank_1" })
    assertEquals(1, withdrawals.size)
  }

  @Test
  fun `verify unique CustomFieldType labels and duplicate field name prevention`() = runBlocking {
    val types = CustomFieldType.values()
    val labels = types.map { it.label }
    assertEquals(types.size, labels.distinct().size)

    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    val field1 = CustomFieldEntity(
      id = "unique_field_1",
      name = "Eindeutiger Belegstatus",
      type = CustomFieldType.SELECTION,
      options = "A,B"
    )
    val field2 = CustomFieldEntity(
      id = "unique_field_2",
      name = "Eindeutiger Belegstatus",
      type = CustomFieldType.TEXT
    )

    val added1 = viewModel.addCustomFieldSync(field1)
    val added2 = viewModel.addCustomFieldSync(field2)

    assertTrue(added1)
    assertFalse(added2) // Duplikat Name abgelehnt!

    val dao = AppDatabase.getDatabase(app).customFieldDao()
    val list = dao.getAllCustomFieldsList().filter { it.name.trim().equals("Eindeutiger Belegstatus", ignoreCase = true) }
    assertEquals(1, list.size)
  }
}
