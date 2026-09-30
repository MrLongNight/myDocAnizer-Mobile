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
    viewModel.setNotifyReconciliationDiscrepancies(true)

    assertEquals(true, viewModel.enableIncomeExpenseTracking.first())
    assertEquals(true, viewModel.enableCashTracker.first())
    assertEquals(true, viewModel.enableReceiptExpenses.first())
    assertEquals(true, viewModel.enableBankStatementImport.first())
    assertEquals(true, viewModel.enableMonthlyReconciliation.first())
    assertEquals(true, viewModel.notifyReconciliationDiscrepancies.first())
  }

  @Test
  fun `verify Dashboard widget configuration flow`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    viewModel.setShowBelegQuickScanWidget(true)
    viewModel.setShowCashTrackerWidget(true)
    viewModel.setShowKpiWidgets(true)
    viewModel.setShowDeadlinesWidget(true)
    viewModel.setShowFinanceWidget(true)
    viewModel.setShowReconciliationWidget(true)
    viewModel.setShowCategoryDistributionWidget(true)
    viewModel.setShowSecurityScoreWidget(true)
    viewModel.setShowCustomFieldsWidget(true)

    assertEquals(true, viewModel.showBelegQuickScanWidget.first())
    assertEquals(true, viewModel.showCashTrackerWidget.first())
    assertEquals(true, viewModel.showKpiWidgets.first())
    assertEquals(true, viewModel.showDeadlinesWidget.first())
    assertEquals(true, viewModel.showFinanceWidget.first())
    assertEquals(true, viewModel.showReconciliationWidget.first())
    assertEquals(true, viewModel.showCategoryDistributionWidget.first())
    assertEquals(true, viewModel.showSecurityScoreWidget.first())
    assertEquals(true, viewModel.showCustomFieldsWidget.first())
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

  @Test
  fun `verify BankStatementParserService parses German CSV and detects cash withdrawals`() = runBlocking {
    val sampleCsv = """
      Auftragskonto;Buchungstag;Valutadatum;Buchungstext;Verwendungszweck;Beguenstigter;Betrag;Waehrung
      DE1234567890;15.09.2026;15.09.2026;Auszahlung Geldautomat;GA-Barauszahlung Sparkasse Filiale;Sparkasse;-250,00;EUR
      DE1234567890;18.09.2026;18.09.2026;Kartenzahlung;Supermarkt Einkauf;EDEKA;-34,50;EUR
      DE1234567890;25.09.2026;25.09.2026;Überweisung;Gehalt September 2026;Arbeitgeber GmbH;2800,00;EUR
    """.trimIndent()

    val parseResult = com.example.service.BankStatementParserService.parseCsvOrText(sampleCsv, "sparkasse_export.csv")
    assertEquals(3, parseResult.totalParsed)
    assertEquals(1, parseResult.cashWithdrawalsCount)
    assertEquals(250.0, parseResult.totalCashWithdrawalsAmount, 0.001)
    assertTrue(parseResult.detectedBankFormat.contains("Sparkasse"))

    val cashEntry = parseResult.entries.first { it.isCashWithdrawal }
    assertEquals(-250.0, cashEntry.amount, 0.001)
    assertEquals("2026-09", cashEntry.monthYear)

    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    val inserted1 = viewModel.importBankStatementEntriesSync(parseResult.entries)
    assertEquals(3, inserted1)

    val insertedAgain = viewModel.importBankStatementEntriesSync(parseResult.entries)
    assertEquals(0, insertedAgain)
  }

  @Test
  fun `verify DeadlineDetectionService parses contract deadlines and due dates`() = runBlocking {
    val sampleText = """
      Sehr geehrter Kunde,
      vielen Dank für Ihren Mobilfunkvertrag bei Vodafone.
      Ihre Mindestvertragslaufzeit bis: 31.12.2026.
      Sie können spätestens kündbar bis zum 30.11.2026 kündigen.
      Der offene Rechnungsbetrag ist zahlbar bis zum 15.10.2026.
    """.trimIndent()

    val deadlines = com.example.service.DeadlineDetectionService.detectDeadlines(sampleText)
    assertTrue(deadlines.isNotEmpty())

    val cancellation = deadlines.firstOrNull { it.type == com.example.service.DeadlineType.CANCELLATION }
    assertNotNull(cancellation)
    assertEquals("30.11.2026", cancellation?.formattedDate)

    val contractEnd = deadlines.firstOrNull { it.type == com.example.service.DeadlineType.CONTRACT_END }
    assertNotNull(contractEnd)
    assertEquals("31.12.2026", contractEnd?.formattedDate)

    val payment = deadlines.firstOrNull { it.type == com.example.service.DeadlineType.PAYMENT_DUE }
    assertNotNull(payment)
    assertEquals("15.10.2026", payment?.formattedDate)

    val (primaryCancel, primaryEnd) = com.example.service.DeadlineDetectionService.extractPrimaryDeadlines(sampleText)
    assertNotNull(primaryCancel)
    assertNotNull(primaryEnd)
  }

  @Test
  fun `verify Custom Dashboard Widgets and Reordering flow`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    // 1. Initial State: Quick scan and cash tracker must be false by default
    val freshSettings = com.example.service.SettingsRepository(app)
    assertEquals(false, freshSettings.showBelegQuickScanWidget.first())
    assertEquals(false, freshSettings.showCashTrackerWidget.first())

    // 2. Create custom widget
    val customWidget = com.example.model.CustomDashboardWidget(
      id = "custom_test_1",
      title = "Garantie-Belege",
      subtitle = "Meine Rechnungen mit Garantie",
      type = com.example.model.CustomDashboardWidgetType.TAG_FILTER,
      targetTag = "#garantie",
      colorSkin = "EMERALD"
    )

    viewModel.addCustomDashboardWidget(customWidget)
    val widgetsAfterAdd = viewModel.customDashboardWidgets.first()
    assertEquals(1, widgetsAfterAdd.size)
    assertEquals("Garantie-Belege", widgetsAfterAdd[0].title)
    assertEquals("#garantie", widgetsAfterAdd[0].targetTag)

    // 3. Update custom widget checklist & note
    val checklistItems = listOf(
      com.example.model.ChecklistItem(id = "item_1", text = "Rechnung ablegen", isDone = false),
      com.example.model.ChecklistItem(id = "item_2", text = "Seriennummer notieren", isDone = true)
    )
    viewModel.updateCustomWidgetChecklist("custom_test_1", checklistItems)
    viewModel.updateCustomWidgetNote("custom_test_1", "Garantie gilt 24 Monate")

    val updatedWidgets = viewModel.customDashboardWidgets.first()
    assertEquals(2, updatedWidgets[0].checklistItems.size)
    assertEquals(true, updatedWidgets[0].checklistItems[1].isDone)
    assertEquals("Garantie gilt 24 Monate", updatedWidgets[0].noteText)

    // 4. Test reordering
    viewModel.moveDashboardWidgetUp("custom_test_1")
    val order = viewModel.dashboardWidgetOrder.first()
    assertTrue(order.contains("custom_test_1"))

    // 5. Delete custom widget
    viewModel.deleteCustomDashboardWidget("custom_test_1")
    val widgetsAfterDelete = viewModel.customDashboardWidgets.first()
    assertTrue(widgetsAfterDelete.isEmpty())

    // 6. Test per-element Period Scope settings & filtering
    viewModel.setElementPeriodScope("STANDARD_KPI", com.example.model.ElementPeriodScope.MONTH)
    val scopes = viewModel.elementPeriodScopes.first()
    assertEquals(com.example.model.ElementPeriodScope.MONTH, scopes["STANDARD_KPI"])

    val now = System.currentTimeMillis()
    val testDocs = listOf(
      com.example.model.DocumentEntity(
        id = 1L,
        title = "Beleg Heute",
        sender = "EDEKA",
        fileName = "beleg_heute.pdf",
        filePath = "/path/1.pdf",
        mainCategory = "Finanzen",
        subCategory = "Einkauf",
        createdAt = now
      ),
      com.example.model.DocumentEntity(
        id = 2L,
        title = "Beleg Alt",
        sender = "REWE",
        fileName = "beleg_alt.pdf",
        filePath = "/path/2.pdf",
        mainCategory = "Finanzen",
        subCategory = "Einkauf",
        createdAt = now - 400L * 24 * 60 * 60 * 1000
      )
    )
    val filteredYear = com.example.model.ElementPeriodScope.YEAR.filterDocuments(testDocs, now)
    assertEquals(1, filteredYear.size)
    assertEquals(1L, filteredYear[0].id)
  }

  @Test
  fun `verify extended HuggingFace models and catalog sync flow`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    val models = viewModel.availableModels.first()
    assertTrue(models.any { it.id == "deepseek-r1-distill-qwen-1.5b" })
    assertTrue(models.any { it.id == "phi-3.5-mini-instruct" })
    assertTrue(models.any { it.id == "ministral-3b-instruct" })
    assertTrue(models.any { it.id == "qwen2.5-coder-1.5b" })

    val deepSeek = models.first { it.id == "deepseek-r1-distill-qwen-1.5b" }
    assertEquals("Reasoning & Logik", deepSeek.modelCategory)
    assertTrue(deepSeek.isCuratedApproved)

    // Test Catalog Sync
    viewModel.syncModelCatalogFromRemote(forceCheck = true)
    // Wait briefly or check flow
    val notif = viewModel.newModelsNotification.first()
    // Test dismiss
    viewModel.dismissNewModelsNotification()
    assertEquals(null, viewModel.newModelsNotification.value)
  }

  @Test
  fun `verify general invoice classification and custom fields`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    // Select Qwen 2.5 1.5B
    viewModel.selectHuggingFaceModel("qwen2.5-1.5b-instruct")

    val ocrText = """
      Acme Dienstleistungen GmbH
      Rechnung Nr: RE-2026-9812
      Kundennummer: KD-49102
      Rechnungsbetrag: 249,00 €
      Fälligkeit: 30.04.2026
      Vielen Dank für Ihren Auftrag.
    """.trimIndent()

    val result = viewModel.llmService.classifyDocumentText(ocrText)
    assertEquals("Rechnung", result.docType)
    assertEquals("A02", result.mainCategoryId)
    assertEquals("B2.01", result.subCategoryId)
    assertTrue(result.title.contains("Rechnung"))
    assertTrue(result.customFields.containsKey("Rechnungsbetrag"))
    assertTrue(result.customFields["Rechnungsbetrag"]!!.contains("249,00"))
    assertTrue(result.customFields.containsKey("Fälligkeit"))
    assertEquals("30.04.2026", result.customFields["Fälligkeit"])
    assertEquals("qwen2.5-1.5b-instruct", result.modelIdUsed)
  }

  @Test
  fun `verify general contract classification and custom fields`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    // Select DeepSeek-R1 Distill
    viewModel.selectHuggingFaceModel("deepseek-r1-distill-qwen-1.5b")

    val ocrText = """
      Nordic Fitness Club
      Mitgliedsvertrag
      Vertragsnummer: VTR-88192
      Vertragsdatum: 01.03.2026
      Monatlicher Beitrag: 39,90 €
    """.trimIndent()

    val result = viewModel.llmService.classifyDocumentText(ocrText)
    assertEquals("Vertrag", result.docType)
    assertEquals("A02", result.mainCategoryId)
    assertEquals("B2.03", result.subCategoryId)
    assertTrue(result.title.contains("Vertrag"))
    assertTrue(result.customFields.containsKey("Vertragsnummer"))
    assertEquals("VTR-88192", result.customFields["Vertragsnummer"])
    assertEquals("deepseek-r1-distill-qwen-1.5b", result.modelIdUsed)
    assertTrue(result.explanation.contains("<think>"))
  }

  @Test
  fun `verify AppAuditLogger logging and file export`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    com.example.service.AppAuditLogger.init(app)

    com.example.service.AppAuditLogger.log(
      category = com.example.service.LogCategory.AI_INFERENCE,
      tag = "TestInference",
      message = "Inferenz-Test erfolgreich ausgeführt",
      details = "Model: qwen2.5-1.5b-instruct"
    )

    val logs = com.example.service.AppAuditLogger.logs.value
    assertTrue(logs.any { it.tag == "TestInference" })

    val exportedFile = com.example.service.AppAuditLogger.exportLogFile(app)
    assertTrue(exportedFile.exists())
    assertTrue(exportedFile.length() > 0)
    val content = exportedFile.readText()
    assertTrue(content.contains("myDocAnizer-Mobile - DIAGNOSE & AUDIT-PROTOKOLL"))
    assertTrue(content.contains("TestInference"))
  }
}
