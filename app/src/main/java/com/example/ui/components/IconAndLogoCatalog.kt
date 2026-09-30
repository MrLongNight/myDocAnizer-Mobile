package com.example.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.AssuredWorkload
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PermIdentity
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Definition eines konfigurierbaren Icons
 */
data class IconCatalogItem(
    val id: String,
    val name: String,
    val group: String,
    val icon: ImageVector
)

/**
 * Definition eines Firmenlogos / Marken-Badges
 */
data class CompanyLogoItem(
    val id: String,
    val name: String,
    val category: String,
    val brandColor: Color,
    val textColor: Color,
    val badgeLabel: String,
    val keywords: List<String>
)

/**
 * Reichhaltiger Katalog aller verfügbaren Icons für Zusatzfelder, Ordner & Dokumente
 */
val ALL_APP_ICONS: List<IconCatalogItem> = listOf(
    // 1. FINANZEN & WERTE
    IconCatalogItem("euro", "Euro (€)", "Finanzen", Icons.Default.Euro),
    IconCatalogItem("wallet", "Geldbörse / Konto", "Finanzen", Icons.Default.AccountBalanceWallet),
    IconCatalogItem("bank", "Bank / Institut", "Finanzen", Icons.Default.AccountBalance),
    IconCatalogItem("credit_card", "Kreditkarte / IBAN", "Finanzen", Icons.Default.CreditCard),
    IconCatalogItem("receipt", "Beleg / Rechnung", "Finanzen", Icons.Default.ReceiptLong),
    IconCatalogItem("receipt_simple", "Kassenzettel", "Finanzen", Icons.Default.Receipt),
    IconCatalogItem("savings", "Sparziel / Rücklage", "Finanzen", Icons.Default.Savings),
    IconCatalogItem("percent", "Rabatt / MwSt. (%)", "Finanzen", Icons.Default.Percent),
    IconCatalogItem("trending_up", "Gewinn / Rendite", "Finanzen", Icons.Default.TrendingUp),
    IconCatalogItem("shopping", "Einkauf / Kasse", "Finanzen", Icons.Default.ShoppingCart),
    IconCatalogItem("payments", "Zahlungsverkehr", "Finanzen", Icons.Default.Payments),
    IconCatalogItem("price_change", "Preisänderung / Tarif", "Finanzen", Icons.Default.PriceChange),
    IconCatalogItem("calculate", "Steuer / Kalkulation", "Finanzen", Icons.Default.Calculate),
    IconCatalogItem("request_quote", "Angebot / Kostenvoranschlag", "Finanzen", Icons.Default.RequestQuote),
    IconCatalogItem("currency_exchange", "Währungsumtausch", "Finanzen", Icons.Default.CurrencyExchange),
    IconCatalogItem("paid", "Bezahlt / Quittung", "Finanzen", Icons.Default.Paid),
    IconCatalogItem("atm", "Bargeld / Geldautomat", "Finanzen", Icons.Default.LocalAtm),

    // 2. FRISTEN & TERMINE
    IconCatalogItem("event", "Kalender / Datum", "Fristen & Termine", Icons.Default.Event),
    IconCatalogItem("schedule", "Uhrzeit / Dauer", "Fristen & Termine", Icons.Default.Schedule),
    IconCatalogItem("hourglass", "Fristablauf / Countdown", "Fristen & Termine", Icons.Default.HourglassEmpty),
    IconCatalogItem("reminder", "Erinnerung / Alarm", "Fristen & Termine", Icons.Default.NotificationsActive),
    IconCatalogItem("alarm", "Wecker / Pünktlich", "Fristen & Termine", Icons.Default.Alarm),
    IconCatalogItem("update", "Verlängerung / Turnus", "Fristen & Termine", Icons.Default.Update),
    IconCatalogItem("today", "Heute / Stichtag", "Fristen & Termine", Icons.Default.Today),
    IconCatalogItem("calendar_month", "Monatsplaner", "Fristen & Termine", Icons.Default.CalendarMonth),
    IconCatalogItem("date_range", "Zeitraum / Von-Bis", "Fristen & Termine", Icons.Default.DateRange),
    IconCatalogItem("event_available", "Termin bestätigt", "Fristen & Termine", Icons.Default.EventAvailable),
    IconCatalogItem("timer", "Kurzfristig / Timer", "Fristen & Termine", Icons.Default.Timer),
    IconCatalogItem("pending", "Ausstehend / Schwebend", "Fristen & Termine", Icons.Default.Pending),

    // 3. VERTRÄGE, RECHT & SICHERHEIT
    IconCatalogItem("description", "Vertrag / Dokument", "Vertrag & Recht", Icons.Default.Description),
    IconCatalogItem("article", "Klausel / Paragraf", "Vertrag & Recht", Icons.Default.Article),
    IconCatalogItem("assignment", "Vereinbarung / Protokoll", "Vertrag & Recht", Icons.Default.Assignment),
    IconCatalogItem("shield", "Versicherung / Schutz", "Vertrag & Recht", Icons.Default.Shield),
    IconCatalogItem("security", "Sicherheit & Tresor", "Vertrag & Recht", Icons.Default.Security),
    IconCatalogItem("gavel", "Recht / Notar / Urteil", "Vertrag & Recht", Icons.Default.Gavel),
    IconCatalogItem("lock", "Gesperrt / Vertraulich", "Vertrag & Recht", Icons.Default.Lock),
    IconCatalogItem("key", "Zugang / Lizenz / PIN", "Vertrag & Recht", Icons.Default.Key),
    IconCatalogItem("verified", "Geprüft & Gültig", "Vertrag & Recht", Icons.Default.Verified),
    IconCatalogItem("policy", "Police / AGB", "Vertrag & Recht", Icons.Default.Policy),
    IconCatalogItem("fact_check", "Revision / Geprüft", "Vertrag & Recht", Icons.Default.FactCheck),
    IconCatalogItem("history_edu", "Urkunde / Notariell", "Vertrag & Recht", Icons.Default.HistoryEdu),

    // 4. IDENTIFIKATION & AUSWEISE
    IconCatalogItem("badge", "Ausweis / Kundennr.", "Identifikation", Icons.Default.Badge),
    IconCatalogItem("card_membership", "Mitgliedskarte", "Identifikation", Icons.Default.CardMembership),
    IconCatalogItem("fingerprint", "Biometrie / Identität", "Identifikation", Icons.Default.Fingerprint),
    IconCatalogItem("qr_code", "QR-Code / Barcode", "Identifikation", Icons.Default.QrCode),
    IconCatalogItem("pin", "Kennziffer / PLZ", "Identifikation", Icons.Default.Pin),
    IconCatalogItem("star", "Priorität / Favorit", "Identifikation", Icons.Default.Star),
    IconCatalogItem("flag", "Meilenstein / Wichtig", "Identifikation", Icons.Default.Flag),
    IconCatalogItem("bookmark", "Lesezeichen / Merkliste", "Identifikation", Icons.Default.Bookmark),
    IconCatalogItem("label", "Etikett / Kategorie", "Identifikation", Icons.Default.Label),
    IconCatalogItem("tag", "Schlagwort (#Tag)", "Identifikation", Icons.Default.Tag),
    IconCatalogItem("contact_page", "Visitenkarte / Kontakt", "Identifikation", Icons.Default.ContactPage),
    IconCatalogItem("person", "Person / Familie", "Identifikation", Icons.Default.Person),

    // 5. WOHNEN, ENERGIE & HAUSHALT
    IconCatalogItem("home", "Wohnung / Mietvertrag", "Wohnen & Haus", Icons.Default.Home),
    IconCatalogItem("house", "Eigenheim / Immobilie", "Wohnen & Haus", Icons.Default.House),
    IconCatalogItem("apartment", "Mehrfamilienhaus / WEG", "Wohnen & Haus", Icons.Default.Apartment),
    IconCatalogItem("bolt", "Strom / Energie", "Wohnen & Haus", Icons.Default.Bolt),
    IconCatalogItem("water", "Wasser & Abwasser", "Wohnen & Haus", Icons.Default.WaterDrop),
    IconCatalogItem("fire", "Heizung & Gas", "Wohnen & Haus", Icons.Default.LocalFireDepartment),
    IconCatalogItem("wifi", "Internet & WLAN", "Wohnen & Haus", Icons.Default.Wifi),
    IconCatalogItem("build", "Handwerker & Reparatur", "Wohnen & Haus", Icons.Default.Build),
    IconCatalogItem("cleaning", "Hausreinigung & Pflege", "Wohnen & Haus", Icons.Default.CleaningServices),
    IconCatalogItem("power", "Elektro & Anschlüsse", "Wohnen & Haus", Icons.Default.Power),

    // 6. MOBILITÄT & KFZ
    IconCatalogItem("car", "PKW / Auto", "Mobilität & KFZ", Icons.Default.DirectionsCar),
    IconCatalogItem("electric_car", "Elektroauto / Wallbox", "Mobilität & KFZ", Icons.Default.ElectricCar),
    IconCatalogItem("gas", "Tanken / Kraftstoff", "Mobilität & KFZ", Icons.Default.LocalGasStation),
    IconCatalogItem("bike", "Motorrad & Roller", "Mobilität & KFZ", Icons.Default.TwoWheeler),
    IconCatalogItem("bus", "ÖPNV / Bus & Bahn", "Mobilität & KFZ", Icons.Default.DirectionsBus),
    IconCatalogItem("train", "Deutsche Bahn / Zug", "Mobilität & KFZ", Icons.Default.Train),
    IconCatalogItem("flight", "Flug / Reise", "Mobilität & KFZ", Icons.Default.Flight),
    IconCatalogItem("shipping", "Lieferung & Paket", "Mobilität & KFZ", Icons.Default.LocalShipping),
    IconCatalogItem("speed", "TÜV / Inspektion", "Mobilität & KFZ", Icons.Default.Speed),

    // 7. GESUNDHEIT & PFLEGE
    IconCatalogItem("health", "Krankenkasse / Vital", "Gesundheit", Icons.Default.Favorite),
    IconCatalogItem("medical", "Arztbericht / Befund", "Gesundheit", Icons.Default.MedicalServices),
    IconCatalogItem("medication", "Rezept / Apotheke", "Gesundheit", Icons.Default.Medication),
    IconCatalogItem("hospital", "Klinik & Stationär", "Gesundheit", Icons.Default.LocalHospital),
    IconCatalogItem("vaccines", "Impfpass & Vorsorge", "Gesundheit", Icons.Default.Vaccines),
    IconCatalogItem("healing", "Reha & Therapie", "Gesundheit", Icons.Default.Healing),
    IconCatalogItem("health_safety", "Arbeitsschutz / Notfall", "Gesundheit", Icons.Default.HealthAndSafety),
    IconCatalogItem("medical_info", "Patientenakte", "Gesundheit", Icons.Default.MedicalInformation),

    // 8. ARBEIT, BILDUNG & BEHÖRDEN
    IconCatalogItem("work", "Arbeitgeber & Gehalt", "Arbeit & Amt", Icons.Default.Work),
    IconCatalogItem("business", "Unternehmen / Gewerbe", "Arbeit & Amt", Icons.Default.Business),
    IconCatalogItem("business_center", "Projekt & Mandat", "Arbeit & Amt", Icons.Default.BusinessCenter),
    IconCatalogItem("school", "Schule & Zeugnis", "Arbeit & Amt", Icons.Default.School),
    IconCatalogItem("domain", "Amt & Behörde", "Arbeit & Amt", Icons.Default.Domain),
    IconCatalogItem("mail", "Briefpost & Amtspost", "Arbeit & Amt", Icons.Default.Mail),
    IconCatalogItem("mark_email", "Zugestellt & Bestätigt", "Arbeit & Amt", Icons.Default.MarkEmailRead),
    IconCatalogItem("engineering", "Technik & Ingenieur", "Arbeit & Amt", Icons.Default.Engineering),

    // 9. FREIZEIT, FAMILIE & ALLGEMEIN
    IconCatalogItem("family", "Familie & Kinder", "Familie & Freizeit", Icons.Default.FamilyRestroom),
    IconCatalogItem("child", "Kindergeld & Kita", "Familie & Freizeit", Icons.Default.ChildCare),
    IconCatalogItem("pets", "Haustier & Tierarzt", "Familie & Freizeit", Icons.Default.Pets),
    IconCatalogItem("fitness", "Sport & Fitnessstudio", "Familie & Freizeit", Icons.Default.FitnessCenter),
    IconCatalogItem("movie", "Streaming & Kino", "Familie & Freizeit", Icons.Default.Movie),
    IconCatalogItem("music", "Musik & Abo", "Familie & Freizeit", Icons.Default.MusicNote),
    IconCatalogItem("gift", "Gutschein & Geschenk", "Familie & Freizeit", Icons.Default.CardGiftcard),
    IconCatalogItem("cloud", "Cloud & Backup", "Familie & Freizeit", Icons.Default.Cloud),
    IconCatalogItem("notes", "Notiz & Vermerk", "Familie & Freizeit", Icons.Default.Notes),
    IconCatalogItem("link", "Weblink / Portal", "Familie & Freizeit", Icons.Default.Link),

    // 10. ORDNER-BASIS
    IconCatalogItem("folder", "Standard-Ordner", "Ordner-Symbole", Icons.Default.Folder),
    IconCatalogItem("folder_open", "Geöffneter Ordner", "Ordner-Symbole", Icons.Default.FolderOpen),
    IconCatalogItem("folder_special", "Spezial-Ordner", "Ordner-Symbole", Icons.Default.FolderSpecial)
)

/**
 * Umfangreicher Katalog bekannter Firmenlogos & Behörden mit echten Markenfarben & Badges
 */
val ALL_COMPANY_LOGOS: List<CompanyLogoItem> = listOf(
    // TELEKOMMUNIKATION & INTERNET
    CompanyLogoItem("telekom", "Deutsche Telekom", "Telekom & Netze", Color(0xFFE20074), Color.White, "T", listOf("telekom", "t-mobile", "t-home", "magenta", "congstar")),
    CompanyLogoItem("vodafone", "Vodafone", "Telekom & Netze", Color(0xFFE60000), Color.White, "V", listOf("vodafone", "unitymedia", "kabel deutschland", "otelo")),
    CompanyLogoItem("o2", "O₂ / Telefónica", "Telekom & Netze", Color(0xFF0019A5), Color.White, "O₂", listOf("o2", "telefonica", "blau", "freenet", "ay yildiz")),
    CompanyLogoItem("1und1", "1&1 Telecom", "Telekom & Netze", Color(0xFF003D88), Color.White, "1&1", listOf("1&1", "drillisch", "ionos", "win sim", "sim.de")),
    CompanyLogoItem("pyur", "PŸUR", "Telekom & Netze", Color(0xFF00A3DA), Color.White, "PŸ", listOf("pyur", "tele columbus")),
    CompanyLogoItem("netcologne", "NetCologne", "Telekom & Netze", Color(0xFFE3001B), Color.White, "NC", listOf("netcologne")),

    // BANKEN & FINANZEN
    CompanyLogoItem("sparkasse", "Sparkasse", "Banken & Finanzen", Color(0xFFE3000F), Color.White, "S", listOf("sparkasse", "kreissparkasse", "stadtsparkasse", "deka", "lbbw")),
    CompanyLogoItem("deutsche_bank", "Deutsche Bank", "Banken & Finanzen", Color(0xFF0018A8), Color.White, "DB", listOf("deutsche bank", "maxblue", "norisbank")),
    CompanyLogoItem("ing", "ING Bank", "Banken & Finanzen", Color(0xFFFF6200), Color.White, "ING", listOf("ing", "ing-diba", "diba")),
    CompanyLogoItem("commerzbank", "Commerzbank", "Banken & Finanzen", Color(0xFFFFD200), Color(0xFF1E293B), "CB", listOf("commerzbank", "comdirect")),
    CompanyLogoItem("volksbank", "Volksbank Raiffeisen", "Banken & Finanzen", Color(0xFF0066B3), Color.White, "VR", listOf("volksbank", "raiffeisenbank", "vr bank", "union investment", "dz bank")),
    CompanyLogoItem("postbank", "Postbank", "Banken & Finanzen", Color(0xFFFFCC00), Color(0xFF003087), "PB", listOf("postbank")),
    CompanyLogoItem("dkb", "DKB", "Banken & Finanzen", Color(0xFF005B9A), Color.White, "DKB", listOf("dkb", "deutsche kreditbank")),
    CompanyLogoItem("traderepublic", "Trade Republic", "Banken & Finanzen", Color(0xFF111111), Color.White, "TR", listOf("trade republic", "traderepublic")),
    CompanyLogoItem("scalable", "Scalable Capital", "Banken & Finanzen", Color(0xFF00C08B), Color.White, "SC", listOf("scalable", "scalable capital")),
    CompanyLogoItem("paypal", "PayPal", "Banken & Finanzen", Color(0xFF003087), Color.White, "PP", listOf("paypal")),
    CompanyLogoItem("n26", "N26 Bank", "Banken & Finanzen", Color(0xFF36A18B), Color.White, "N26", listOf("n26")),
    CompanyLogoItem("klarna", "Klarna", "Banken & Finanzen", Color(0xFFFFB3C7), Color(0xFF0A0A0A), "K.", listOf("klarna")),
    CompanyLogoItem("santander", "Santander", "Banken & Finanzen", Color(0xFFEC0000), Color.White, "SAN", listOf("santander")),
    CompanyLogoItem("targobank", "Targobank", "Banken & Finanzen", Color(0xFF002F6C), Color.White, "TB", listOf("targobank")),

    // VERSICHERUNGEN
    CompanyLogoItem("allianz", "Allianz Versicherung", "Versicherungen", Color(0xFF003781), Color.White, "AZ", listOf("allianz", "allianz direct", "allianz lebensversicherung")),
    CompanyLogoItem("huk", "HUK-Coburg", "Versicherungen", Color(0xFFFFE600), Color(0xFF1E293B), "HUK", listOf("huk", "huk24", "huk-coburg")),
    CompanyLogoItem("axa", "AXA Versicherung", "Versicherungen", Color(0xFFD0021B), Color.White, "AXA", listOf("axa", "dbv")),
    CompanyLogoItem("ergo", "ERGO Versicherung", "Versicherungen", Color(0xFFCC0000), Color.White, "ERGO", listOf("ergo", "ergo direkt", "d.a.s.")),
    CompanyLogoItem("devk", "DEVK", "Versicherungen", Color(0xFF008837), Color.White, "DEVK", listOf("devk")),
    CompanyLogoItem("generali", "Generali", "Versicherungen", Color(0xFFC61619), Color.White, "G", listOf("generali", "aachenmünchener", "cosmosdirekt")),
    CompanyLogoItem("ruv", "R+V Versicherung", "Versicherungen", Color(0xFF003399), Color.White, "R+V", listOf("r+v", "kravag")),
    CompanyLogoItem("signal_iduna", "Signal Iduna", "Versicherungen", Color(0xFF003066), Color.White, "SI", listOf("signal iduna", "deutscher ring")),
    CompanyLogoItem("gothaer", "Gothaer", "Versicherungen", Color(0xFF005B82), Color.White, "GOTH", listOf("gothaer")),
    CompanyLogoItem("vhv", "VHV Versicherungen", "Versicherungen", Color(0xFF003E7E), Color.White, "VHV", listOf("vhv", "vhv versicherung")),
    CompanyLogoItem("barmenia", "Barmenia", "Versicherungen", Color(0xFF005A9C), Color.White, "BAR", listOf("barmenia")),

    // KRANKENKASSEN & GESUNDHEIT
    CompanyLogoItem("tk", "Techniker Krankenkasse", "Gesundheit", Color(0xFF004F9F), Color.White, "TK", listOf("techniker", "tk", "techniker krankenkasse")),
    CompanyLogoItem("barmer", "Barmer", "Gesundheit", Color(0xFF008034), Color.White, "B", listOf("barmer", "barmer gek")),
    CompanyLogoItem("aok", "AOK", "Gesundheit", Color(0xFF007A3D), Color.White, "AOK", listOf("aok", "aok plus", "aok bayern", "aok baden-württemberg", "aok hessen", "aok nordost")),
    CompanyLogoItem("dak", "DAK Gesundheit", "Gesundheit", Color(0xFFF39200), Color.White, "DAK", listOf("dak", "dak gesundheit")),
    CompanyLogoItem("ikk", "IKK classic", "Gesundheit", Color(0xFFE3001B), Color.White, "IKK", listOf("ikk", "ikk classic")),
    CompanyLogoItem("kkh", "KKH Kaufmännische", "Gesundheit", Color(0xFFE4002B), Color.White, "KKH", listOf("kkh")),
    CompanyLogoItem("sbk", "SBK Siemens", "Gesundheit", Color(0xFF00646E), Color.White, "SBK", listOf("sbk", "siemens betriebskrankenkasse")),

    // ENERGIE & VERSORGER
    CompanyLogoItem("eon", "E.ON Energie", "Energie & Versorger", Color(0xFFE2001A), Color.White, "E.ON", listOf("eon", "e.on", "innogy")),
    CompanyLogoItem("vattenfall", "Vattenfall", "Energie & Versorger", Color(0xFF1A3B8B), Color.White, "VF", listOf("vattenfall")),
    CompanyLogoItem("enbw", "EnBW", "Energie & Versorger", Color(0xFFFF6600), Color.White, "EnBW", listOf("enbw", "yello")),
    CompanyLogoItem("stadtwerke", "Stadtwerke", "Energie & Versorger", Color(0xFF009FE3), Color.White, "SW", listOf("stadtwerke", "mainova", "swm", "enercity", "stadtwerke münchen")),
    CompanyLogoItem("rwe", "RWE", "Energie & Versorger", Color(0xFF004B87), Color.White, "RWE", listOf("rwe")),
    CompanyLogoItem("lichtblick", "LichtBlick", "Energie & Versorger", Color(0xFF108B3E), Color.White, "LB", listOf("lichtblick")),

    // EINKAUF, HANDEL & TECH
    CompanyLogoItem("amazon", "Amazon", "Handel & Tech", Color(0xFFFF9900), Color(0xFF131921), "AMZ", listOf("amazon", "aws", "prime")),
    CompanyLogoItem("apple", "Apple", "Handel & Tech", Color(0xFF000000), Color.White, "AAPL", listOf("apple", "icloud", "itunes", "app store")),
    CompanyLogoItem("google", "Google", "Handel & Tech", Color(0xFF4285F4), Color.White, "GOOG", listOf("google", "google play", "youtube", "workspace")),
    CompanyLogoItem("microsoft", "Microsoft", "Handel & Tech", Color(0xFF00A4EF), Color.White, "MS", listOf("microsoft", "office 365", "xbox", "azure")),
    CompanyLogoItem("edeka", "EDEKA", "Handel & Tech", Color(0xFF003B7A), Color(0xFFFFD200), "EDEKA", listOf("edeka", "netto")),
    CompanyLogoItem("rewe", "REWE", "Handel & Tech", Color(0xFFCC071E), Color.White, "REWE", listOf("rewe", "penny")),
    CompanyLogoItem("lidl", "Lidl", "Handel & Tech", Color(0xFF0050AA), Color(0xFFFFD500), "LIDL", listOf("lidl", "schwarz gruppe")),
    CompanyLogoItem("aldi", "ALDI", "Handel & Tech", Color(0xFF00205B), Color(0xFF00A2E8), "ALDI", listOf("aldi", "aldi süd", "aldi nord")),
    CompanyLogoItem("kaufland", "Kaufland", "Handel & Tech", Color(0xFFE30613), Color.White, "KL", listOf("kaufland")),
    CompanyLogoItem("dm", "dm-drogerie", "Handel & Tech", Color(0xFF7B2D82), Color.White, "dm", listOf("dm", "dm-drogerie")),
    CompanyLogoItem("rossmann", "Rossmann", "Handel & Tech", Color(0xFFE30613), Color.White, "R", listOf("rossmann")),
    CompanyLogoItem("mediamarkt", "MediaMarkt / Saturn", "Handel & Tech", Color(0xFFDF0000), Color.White, "MM", listOf("mediamarkt", "saturn", "media markt")),
    CompanyLogoItem("otto", "OTTO", "Handel & Tech", Color(0xFFDC0015), Color.White, "OTTO", listOf("otto", "otto versand")),
    CompanyLogoItem("netflix", "Netflix", "Handel & Tech", Color(0xFFE50914), Color.White, "NFX", listOf("netflix")),
    CompanyLogoItem("spotify", "Spotify", "Handel & Tech", Color(0xFF1DB954), Color.White, "SPOT", listOf("spotify")),
    CompanyLogoItem("ikea", "IKEA", "Handel & Tech", Color(0xFF0051BA), Color(0xFFFFDA1A), "IKEA", listOf("ikea")),

    // MOBILITÄT & REISEN
    CompanyLogoItem("adac", "ADAC", "Mobilität & Reise", Color(0xFFFFCC00), Color(0xFF1E293B), "ADAC", listOf("adac")),
    CompanyLogoItem("deutsche_bahn", "Deutsche Bahn", "Mobilität & Reise", Color(0xFFEC1C24), Color.White, "DB", listOf("deutsche bahn", "bahn", "db fernverkehr", "db regio", "bahncard")),
    CompanyLogoItem("shell", "Shell", "Mobilität & Reise", Color(0xFFFBCE07), Color(0xFFDD1D21), "Shell", listOf("shell")),
    CompanyLogoItem("aral", "Aral", "Mobilität & Reise", Color(0xFF004A99), Color.White, "Aral", listOf("aral", "bp")),
    CompanyLogoItem("tuev", "TÜV", "Mobilität & Reise", Color(0xFF005A9C), Color.White, "TÜV", listOf("tüv", "tuev", "tüv süd", "tüv rheinland", "tüv nord")),
    CompanyLogoItem("dekra", "DEKRA", "Mobilität & Reise", Color(0xFF007A3D), Color.White, "DEKRA", listOf("dekra")),
    CompanyLogoItem("lufthansa", "Lufthansa", "Mobilität & Reise", Color(0xFF05164D), Color(0xFFFFAC00), "LH", listOf("lufthansa", "eurowings", "miles and more")),
    CompanyLogoItem("bmw", "BMW", "Mobilität & Reise", Color(0xFF0066B1), Color.White, "BMW", listOf("bmw")),
    CompanyLogoItem("mercedes", "Mercedes-Benz", "Mobilität & Reise", Color(0xFF1E293B), Color.White, "MB", listOf("mercedes", "mercedes-benz", "daimler")),
    CompanyLogoItem("vw", "Volkswagen", "Mobilität & Reise", Color(0xFF153564), Color.White, "VW", listOf("volkswagen", "vw")),

    // BEHÖRDEN & STAAT
    CompanyLogoItem("finanzamt", "Finanzamt / Steuern", "Behörden & Amt", Color(0xFF1F2937), Color.White, "FA", listOf("finanzamt", "steuererklärung", "bundeszentralamt für steuern", "elster", "einkommensteuer")),
    CompanyLogoItem("buergeramt", "Bürgeramt / Rathaus", "Behörden & Amt", Color(0xFF374151), Color.White, "AMT", listOf("bürgeramt", "rathaus", "stadtverwaltung", "meldebehörde", "kreisverwaltung")),
    CompanyLogoItem("drv", "Deutsche Rentenversicherung", "Behörden & Amt", Color(0xFF003366), Color.White, "DRV", listOf("rentenversicherung", "drv", "deutsche rentenversicherung", "bfa", "lva")),
    CompanyLogoItem("arbeitsagentur", "Agentur für Arbeit", "Behörden & Amt", Color(0xFFC61E1A), Color.White, "BA", listOf("arbeitsagentur", "agentur für arbeit", "jobcenter")),
    CompanyLogoItem("gez", "Rundfunkbeitrag (GEZ)", "Behörden & Amt", Color(0xFF004A99), Color.White, "ARD", listOf("rundfunkbeitrag", "beitragsservice", "gez", "ard zdf")),
    CompanyLogoItem("zoll", "Zoll / Bundesfinanzen", "Behörden & Amt", Color(0xFF004B87), Color.White, "ZOLL", listOf("zoll", "hauptzollamt", "kraftfahrzeugsteuer")),
    CompanyLogoItem("familienkasse", "Familienkasse (Kindergeld)", "Behörden & Amt", Color(0xFF059669), Color.White, "KID", listOf("familienkasse", "kindergeld", "kinderzuschlag"))
)

/**
 * Liefert das ImageVector für eine gegebene Icon-ID zurück
 */
fun getAppIconVector(iconId: String): ImageVector {
    return ALL_APP_ICONS.firstOrNull { it.id.equals(iconId, ignoreCase = true) }?.icon
        ?: when (iconId.lowercase()) {
            "folder" -> Icons.Default.Folder
            "folder_open" -> Icons.Default.FolderOpen
            "folder_special" -> Icons.Default.FolderSpecial
            "euro", "money", "amount" -> Icons.Default.Euro
            "date", "calendar", "event" -> Icons.Default.Event
            "shield", "insurance" -> Icons.Default.Shield
            "home", "house" -> Icons.Default.Home
            "car", "kfz" -> Icons.Default.DirectionsCar
            "health", "arzt" -> Icons.Default.Favorite
            "work", "job" -> Icons.Default.Work
            "receipt", "invoice" -> Icons.Default.ReceiptLong
            "bill" -> Icons.Default.Receipt
            "badge", "id" -> Icons.Default.Badge
            "security" -> Icons.Default.Security
            "bolt", "electricity" -> Icons.Default.Bolt
            "wifi", "internet" -> Icons.Default.Wifi
            "notes" -> Icons.Default.Notes
            "mail" -> Icons.Default.Mail
            "cloud" -> Icons.Default.Cloud
            "contract" -> Icons.Default.Description
            else -> Icons.Default.Label
        }
}

/**
 * Erkennt anhand des Textes/Absenders automatisch das passende Firmenlogo und Icon für KI-Regeln
 */
fun detectSuggestedLogoAndIcon(sender: String, text: String, category: String): Pair<String, String> {
    val searchCorp = "${sender.lowercase()} ${text.take(600).lowercase()}"

    // 1. Suche nach Firmenlogo-Treffern
    for (logo in ALL_COMPANY_LOGOS) {
        if (logo.keywords.any { searchCorp.contains(it) }) {
            val matchingIcon = when (logo.category) {
                "Telekom & Netze" -> "wifi"
                "Banken & Finanzen" -> "bank"
                "Versicherungen" -> "shield"
                "Gesundheit" -> "health"
                "Energie & Versorger" -> "bolt"
                "Handel & Tech" -> "shopping"
                "Mobilität & Reise" -> "car"
                "Behörden & Amt" -> "gavel"
                else -> "description"
            }
            return Pair(matchingIcon, logo.id)
        }
    }

    // 2. Suche nach thematischen Icons falls kein direktes Firmenlogo passt
    val catLower = category.lowercase()
    val textLower = searchCorp
    val suggestedIcon = when {
        textLower.contains("strom") || textLower.contains("energie") || textLower.contains("gas") || catLower.contains("strom") || catLower.contains("energie") -> "bolt"
        textLower.contains("wohnung") || textLower.contains("miete") || textLower.contains("haus") || textLower.contains("nebenkosten") || catLower.contains("wohnung") -> "home"
        textLower.contains("bank") || textLower.contains("finanz") || textLower.contains("konto") || textLower.contains("iban") || catLower.contains("finanz") -> "bank"
        textLower.contains("beleg") || textLower.contains("quittung") || textLower.contains("kassenbon") -> "receipt_simple"
        textLower.contains("rechnung") || textLower.contains("invoice") -> "receipt"
        textLower.contains("vertrag") || textLower.contains("abo") || textLower.contains("mobilfunk") || catLower.contains("vertrag") -> "description"
        textLower.contains("versicherung") || textLower.contains("police") || catLower.contains("versicherung") -> "shield"
        textLower.contains("kfz") || textLower.contains("auto") || textLower.contains("tüv") || catLower.contains("kfz") || catLower.contains("mobilität") -> "car"
        textLower.contains("gesundheit") || textLower.contains("arzt") || textLower.contains("rezept") || textLower.contains("krankenkasse") || catLower.contains("gesundheit") -> "health"
        textLower.contains("steuer") || textLower.contains("finanzamt") || textLower.contains("elster") -> "calculate"
        textLower.contains("amt") || textLower.contains("behörde") || textLower.contains("bescheid") || catLower.contains("behörde") -> "gavel"
        textLower.contains("gehalt") || textLower.contains("lohn") || textLower.contains("abrechnung") || catLower.contains("arbeit") -> "work"
        textLower.contains("ausweis") || textLower.contains("pass") || textLower.contains("führerschein") -> "badge"
        else -> "folder"
    }

    return Pair(suggestedIcon, "")
}

/**
 * Stylisches Marken-Badge / Firmenlogo Rendering
 */
@Composable
fun CompanyLogoBadge(
    logoId: String,
    size: Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    val logo = ALL_COMPANY_LOGOS.firstOrNull { it.id.equals(logoId, ignoreCase = true) }
    if (logo == null) return

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = logo.brandColor,
        shadowElevation = 1.dp,
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = logo.badgeLabel,
                color = logo.textColor,
                fontSize = (size.value * 0.40f).sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/**
 * Rendert ein eigenes, vom Nutzer hochgeladenes Bild als Logo
 */
@Composable
fun CustomUploadedLogoView(
    uriString: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(uriString) {
        try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Benutzerdefiniertes Firmenlogo",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * 0.6f))
        }
    }
}

/**
 * Universeller Renderer für Dokument- & Ordner-Icons mit Firmenlogo- und Upload-Unterstützung
 */
@Composable
fun DocumentOrFolderIcon(
    iconName: String,
    companyLogo: String = "",
    customLogoUri: String = "",
    isFolder: Boolean = false,
    isImage: Boolean = false,
    size: Dp = 40.dp,
    accentColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val primaryTint = accentColor ?: MaterialTheme.colorScheme.primary

    // 1. Eigener Logo-Upload hat höchste Priorität
    if (customLogoUri.isNotBlank()) {
        CustomUploadedLogoView(
            uriString = customLogoUri,
            size = size,
            modifier = modifier
        )
        return
    }

    // 2. Vordefiniertes Markenlogo / Firmenbadge
    if (companyLogo.isNotBlank()) {
        val logo = ALL_COMPANY_LOGOS.firstOrNull { it.id.equals(companyLogo, ignoreCase = true) }
        if (logo != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = logo.brandColor,
                shadowElevation = 2.dp,
                modifier = modifier.size(size)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = logo.badgeLabel,
                        color = logo.textColor,
                        fontSize = (size.value * 0.38f).sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
            return
        }
    }

    // 3. Konfiguriertes Icon oder Standard-Icon mit moderner Einfärbung
    val vector = if (iconName.isNotBlank() && iconName != "folder" && iconName != "folder_open") {
        getAppIconVector(iconName)
    } else if (isFolder) {
        Icons.Default.Folder
    } else if (isImage) {
        Icons.Default.Image
    } else {
        Icons.Default.PictureAsPdf
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFolder) primaryTint.copy(alpha = 0.15f)
                else if (isImage) MaterialTheme.colorScheme.tertiaryContainer
                else primaryTint.copy(alpha = 0.15f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = if (isImage) MaterialTheme.colorScheme.onTertiaryContainer else primaryTint,
            modifier = Modifier.size(size * 0.58f)
        )
    }
}

/**
 * Kompakter Icon-Picker für Zusatzfelder und Regeln mit vielen Kategorien
 */
@Composable
fun CustomFieldIconPicker(
    selectedIcon: String,
    onSelectIcon: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedGroup by remember { mutableStateOf("Alle") }
    val groups = remember {
        listOf("Alle") + ALL_APP_ICONS.map { it.group }.distinct()
    }

    val filteredIcons = remember(selectedGroup) {
        if (selectedGroup == "Alle") ALL_APP_ICONS else ALL_APP_ICONS.filter { it.group == selectedGroup }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Icon-Symbol auswählen:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )

        // Gruppen-Filter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            groups.forEach { group ->
                val isSelected = selectedGroup == group
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedGroup = group },
                    label = { Text(group, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        // Icon Raster
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 44.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredIcons, key = { it.id }) { item ->
                val isSelected = selectedIcon.equals(item.id, ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { onSelectIcon(item.id) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.name,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Universal-Dialog zum Auswählen von Ordner- & Dokument-Icons, Firmenlogos und eigenem Logo-Upload
 */
@Composable
fun UniversalIconAndLogoPickerDialog(
    title: String,
    subtitle: String,
    currentIcon: String,
    currentLogo: String,
    currentCustomLogoUri: String = "",
    isFolder: Boolean = true,
    onSave: (iconName: String, companyLogo: String, customLogoUri: String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialTab = remember {
        if (currentCustomLogoUri.isNotBlank()) 2
        else if (currentLogo.isNotBlank()) 1
        else 0
    }
    var selectedTab by remember { mutableStateOf(initialTab) }
    var selectedIcon by remember { mutableStateOf(currentIcon.ifBlank { if (isFolder) "folder" else "description" }) }
    var selectedLogo by remember { mutableStateOf(currentLogo) }
    var selectedCustomLogoUri by remember { mutableStateOf(currentCustomLogoUri) }
    var logoSearchQuery by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedCustomLogoUri = uri.toString()
            selectedLogo = ""
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header mit Live-Vorschau
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        DocumentOrFolderIcon(
                            iconName = selectedIcon,
                            companyLogo = selectedLogo,
                            customLogoUri = selectedCustomLogoUri,
                            isFolder = isFolder,
                            size = 42.dp
                        )
                        Column {
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tabs: 1. Icons | 2. Firmenlogos | 3. Eigenes Logo
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Icons", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Firmenlogos", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Eigenes Logo", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedTab == 0) {
                    // TAB 0: ICONS
                    CustomFieldIconPicker(
                        selectedIcon = selectedIcon,
                        onSelectIcon = {
                            selectedIcon = it
                            selectedLogo = ""
                            selectedCustomLogoUri = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else if (selectedTab == 1) {
                    // TAB 1: FIRMENLOGOS
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = logoSearchQuery,
                            onValueChange = { logoSearchQuery = it },
                            placeholder = { Text("Firma suchen (z.B. Telekom, Allianz, Amazon, Bank, Amt)...", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        val filteredLogos = remember(logoSearchQuery) {
                            if (logoSearchQuery.isBlank()) ALL_COMPANY_LOGOS
                            else ALL_COMPANY_LOGOS.filter {
                                it.name.contains(logoSearchQuery, ignoreCase = true) ||
                                it.keywords.any { kw -> kw.contains(logoSearchQuery.trim(), ignoreCase = true) }
                            }
                        }

                        if (selectedLogo.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Ausgewähltes Logo aktiv", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                OutlinedButton(
                                    onClick = { selectedLogo = "" },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Logo entfernen", fontSize = 11.sp)
                                }
                            }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 92.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredLogos, key = { it.id }) { logo ->
                                val isSelected = selectedLogo.equals(logo.id, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedLogo = logo.id
                                            selectedCustomLogoUri = ""
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        CompanyLogoBadge(logoId = logo.id, size = 32.dp)
                                        Text(
                                            text = logo.name,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // TAB 2: EIGENES LOGO UPLOAD
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (selectedCustomLogoUri.isNotBlank()) {
                                    Text("Aktuelle Vorschau deines Firmenlogos:", style = MaterialTheme.typography.labelMedium)
                                    CustomUploadedLogoView(
                                        uriString = selectedCustomLogoUri,
                                        size = 64.dp
                                    )
                                    Button(
                                        onClick = { selectedCustomLogoUri = "" },
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Eigenes Logo entfernen", fontSize = 12.sp)
                                    }
                                } else {
                                    Icon(
                                        Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "Eigenes Logo oder Firmenbild hochladen",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Wähle eine Bilddatei aus deiner Galerie (z.B. PNG, JPG oder SVG deines Arbeitgebers oder Vertragspartners).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (selectedCustomLogoUri.isNotBlank()) "Anderes Bild auswählen" else "Bild aus Galerie wählen")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Abbrechen")
                    }
                    Button(
                        onClick = {
                            onSave(selectedIcon, selectedLogo, selectedCustomLogoUri)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Speichern", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Kompatibilitäts-Wrapper für bisherige FolderIconAndLogoPickerDialog Aufrufe
 */
@Composable
fun FolderIconAndLogoPickerDialog(
    folderName: String,
    currentIcon: String,
    currentLogo: String,
    currentCustomLogoUri: String = "",
    onSave: (iconName: String, companyLogo: String, customLogoUri: String) -> Unit,
    onDismiss: () -> Unit
) {
    UniversalIconAndLogoPickerDialog(
        title = "Ordner-Symbol & Logo",
        subtitle = folderName,
        currentIcon = currentIcon,
        currentLogo = currentLogo,
        currentCustomLogoUri = currentCustomLogoUri,
        isFolder = true,
        onSave = onSave,
        onDismiss = onDismiss
    )
}
