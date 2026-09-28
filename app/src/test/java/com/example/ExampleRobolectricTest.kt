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
}
