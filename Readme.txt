================================================================================
                              myDocAnizer-Mobile
      The Privacy-First, 100% On-Device Document Management & Smart Scanner App
================================================================================

Version:       2.4.0 (Build 2026.09)
License:       GNU Affero General Public License v3.0 (AGPLv3) for source code;
               All branding, logos, trademarks & store assets copyright mr.locke84.
Repository:    https://github.com/mr-locke84/myDocAnizer-Mobile
Architecture:  Kotlin / Jetpack Compose / Android Room (SQLite + FTS4) /
               Google ML Kit Offline OCR / Local HuggingFace GGUF Nano-LLMs /
               AES-256-GCM Zero-Knowledge Client-Side Encryption / P2P Direct WiFi Sync

--------------------------------------------------------------------------------
1. MISSION & PHILOSOPHIE
--------------------------------------------------------------------------------
myDocAnizer-Mobile ist der Gegenentwurf zu kommerziellen Scanner-Apps mit undurch-
sichtigen Cloud-Uploads, teuren Abofallen und Tracking.

Kernversprechen:
  * 100% PRIVATSPHÄRE & DSGVO: Kein einziges gescanntes Dokument und kein OCR-Text
    verlässt jemals unverschlüsselt das Smartphone.
  * ZERO-CLOUD KI: Die App nutzt ausschließlich On-Device KI-Modelle (quantisierte
    GGUF-Gewichte via HuggingFace) und Offline-OCR via Google ML Kit.
  * 0 € TOKEN-KOSTEN: Weder für den Nutzer noch für den Entwickler fallen laufende
    API-Kosten bei OpenAI, Google Cloud oder Anthropic an.
  * ZERO-KNOWLEDGE VERSCHLÜSSELUNG: Backups und optionale Cloud-Sicherungen (WebDAV,
    Nextcloud, Google Drive) werden lokal auf dem Gerät via AES-256-GCM und PBKDF2
    (100.000 Iterationen) verschlüsselt. Niemand außer dem Besitzer kann mitlesen.
  * TRANSPARENTE MONETARISIERUNG: Ein ehrliches Hybrid-Modell aus Free Community,
    modularen Lifetime-Einmalkäufen und fairen Monats-/Projekt-Pässen ohne Lock-in.

--------------------------------------------------------------------------------
2. SYSTEM-ARCHITEKTUR & MODULE
--------------------------------------------------------------------------------

+------------------------------------------------------------------------------+
|                             myDocAnizer-Mobile                               |
+------------------------------------------------------------------------------+
| 1. Dokumenten-Tresor (DMS)  | 2. Scanner & OCR        | 3. On-Device LLM     |
|   • DIN-Ordner (A01 - A09)  |   • Offline ML Kit OCR  |   • GGUF Inferenz    |
|   • Batch-Inbox für Scans   |   • Auto-B/W Threshold  |   • Hardware-Check   |
|   • Custom Fields (Zusatz)  |   • Multi-Page Capture  |   • Lokales RAG Q&A  |
|   • FTS4 Volltext-Suche     |   • Akustische Profile  |   • Regelgenerator   |
+-----------------------------+-------------------------+----------------------+
| 4. Fristen & Regeln         | 5. Finanzen & Bank      | 6. Sync & Sicherheit |
|   • Kündigungsfrist-Regex   |   • CSV Kontoauszüge    |   • P2P WLAN-Sync    |
|   • Schlagwort-Regelwerk    |   • Belegabgleich       |   • AES-256-GCM Enc  |
|   • Auto-Logo-Zuweisung     |   • Bar-Ausgabenbuch    |   • Biometrie/Passkey|
|   • 100% deterministisch    |   • Budget & KPIs       |   • Disaster Restore |
+------------------------------------------------------------------------------+

Modul-Details:

* Modul 1: Dokumenten-Tresor (DMS Explorer)
  - Strukturierte zweistufige Ordnerhierarchie (Hauptkategorien A01 bis A09,
    z.B. Wohnen, Finanzen, Verträge, Steuern, Behörden; Unterkategorien B1.01 etc.).
  - Batch-Inbox (Posteingang) für schnelles Durchscannen im Stapel mit späterer
    Prüfung und Zuordnung.
  - Benutzerdefinierte Zusatzfelder (Custom Fields: z.B. Kundennummer, Kennzeichen,
    Steuernummer, Aktenzeichen) mit freier Typ-Definition (Text, Betrag, Datum).
  - Integrierter PDF-Viewer mit Teilen-, Export- und Druckfunktion.

* Modul 2: Smarter Kamera-Scanner & Vorverarbeitung
  - 100% Offline-Texterkennung mit Google ML Kit.
  - Automatische Beleuchtungsprüfung (isWellLit), Schieflagen-Erkennung & Begradigung.
  - Dynamischer B/W-Schwellenwertfilter für kontrastreichen, kristallklaren Text.
  - Mehrseitige Dokumentenerfassung mit Sortierung und Seitenlöschung.
  - Akustische Sound-Profile (Click, Beep, Chime, Lautlos) und haptisches Feedback.

* Modul 3: Lokale HuggingFace LLM-Suite
  - Vollständige On-Device Inferenz ohne Internet:
    * SmolLM2 135M / 360M Instruct (ultraschnell, minimaler RAM-Bedarf).
    * Qwen 2.5 0.5B / 1.5B Instruct (starkes deutsches Sprachverständnis).
    * DeepSeek-R1 Distill Qwen 1.5B (Chain-of-Thought Reasoning für Steuer/Finanzen).
    * Phi-3.5 Mini Instruct (überragende Logik bei mehrseitigen Verträgen & Klauseln).
    * Ministral 3B Instruct (native Mehrsprachigkeit: DE, EN, FR, ES, IT).
    * Gemma 2 2B Instruct (DeepMind Architektur mit hoher Faktentreue bei Beträgen).
    * Llama 3.2 1B / 3B Instruct (High-End On-Device Sprachmodelle).
    * Qwen 2.5 Coder 1.5B (Spezialist für Tabellen, CSV und Betragsprüfung).
  - Automatisches Hardware-Barometer: Liest RAM und Kerne aus und markiert Modelle
    als Optimal, Hohe Last oder Nicht empfohlen.
  - Remote-Katalog-Sync: Prüft auf neu erschienene, von mr.locke84 freigegebene
    und verifizierte Modelle mit Benachrichtigungsfunktion.
  - Lokaler KI-Regelgenerator: Erstellt aus einfacher Nutzersprache ("Rechnungen von
    Telekom und Stadtwerke ablegen") deterministische Schlagwort-Regeln.
  - On-Device RAG & Dokumenten-Chat: Durchsucht die lokale Datenbank und beantwortet
    Fragen zu Verträgen, anstehenden Fristen, Kosten und Absendern.

* Modul 4: Fristen- & Kündigungserkennung
  - Automatische Analyse von OCR-Texten auf Kündigungsfristen, Mindestvertragslaufzeiten,
    Zahlungsziele und Widerspruchsfristen.
  - Vorwarn-Zeiten mit Kalender-Integration und Dashboard-Warnungen.

* Modul 5: Finanzen, Haushaltsbuch & Belegabgleich
  - CSV-Import für alle gängigen deutschen Banken (Sparkasse, VR-Banken, ING, DKB,
    Postbank, Commerzbank, N26).
  - Automatischer Belegabgleich (Reconciliation): Verknüpft gescannte Rechnungen und
    Bargeld-Quittungen mit Kontoauszugsbuchungen und deckt Lücken auf.
  - Integriertes Haushaltsbuch mit Bargeld-Tracker.

* Modul 6: Synchronisation & Datensicherheit
  - Lokaler P2P Master-Master WLAN-Sync: Direkter Austausch zwischen Smartphone und
    myDocAnizer-Desktop PC im Heimnetzwerk per 6-stelligem PIN oder QR-Code Scan.
    Vollständig serverlos, kein Drittanbieter beteiligt.
  - Client-Side Zero-Knowledge Encryption: Alle Backups und Cloud-Exporte werden mit
    AES-256-GCM und PBKDF2 verschlüsselt.
  - Disaster Recovery: 1-Klick Export/Restore verschlüsselter .enc Archive (z.B. für
    USB-OTG-Sticks).
  - App-Sperre via Biometrie (Fingerabdruck, Face Unlock) und FIDO2-Passkey.

--------------------------------------------------------------------------------
3. PREISMODELL & MONETARISIERUNGS-STRATEGIE
--------------------------------------------------------------------------------
Das Modell unterscheidet bewusst zwischen zwei Nutzertypen:

A) LIFETIME (Einmalkauf) - Für nachhaltige, dauerhafte Organisation:
   - Community / Free:  0,00 €  (Tresor, Basis-Scan & OCR, Volltextsuche)
   - Smart Vault:      14,99 €  (Custom Fields, AES-256 Backups, Biometrie, Batch-Inbox)
   - Local AI & Rules: 29,99 €  (Alle HuggingFace LLMs, KI-Regeln, RAG-Chat, Fristen)
   - Ultimate Suite:   44,99 €  (Inkl. P2P WLAN-Sync zum PC, Bankabgleich & Haushaltsbuch)

B) PROJEKT-PASS (Flexibles Abo) - Für temporäre Digitalisierungsprojekte:
   - Monats-Pass:       3,49 € / Monat (monatlich kündbar)
   - 3-Monats-Pass:     7,99 € einmalig (endet automatisch nach 90 Tagen)

C) TRANSPARENTE BEISPIELE:
   - Umzug & Wohnungswechsel: 2 Monate Abo = 6,98 € (spart 38,01 € gegenüber Ultimate).
   - Steuererklärung: 1 Monat Abo = 3,49 € für kompletten Belegabgleich und PDF-Export.
   - Dauernutzer: Ab Monat 13 ist der Ultimate Lifetime Kauf die günstigere Wahl!

D) FAIR-USE & KEIN DATEN-LOCK-IN ("Read-Only Archive"):
   - Läuft ein Monats-Abo ab, bleiben ALLE zuvor erfassten Dokumente, Metadaten,
     Kategorien und Backups dauerhaft lesbar, durchsuchbar und exportierbar.
   - Gating nach Ablauf betrifft ausschließlich fortlaufende Neuscans im Stapel,
     fortlaufende On-Device LLM-Neubewertungen und die kontinuierliche P2P-Desktop-
     Synchronisation.

--------------------------------------------------------------------------------
4. BUILD- & ENTWICKLUNGS-ANWEISUNG
--------------------------------------------------------------------------------

Voraussetzungen:
  * Android SDK Version 34 (Upside Down Cake)
  * Java JDK 17 oder 21
  * Gradle 8.5+ (Kotlin DSL)

Projekt bauen:
  gradle assembleDebug

Lokale JVM & Robolectric-Tests ausführen:
  gradle :app:testDebugUnitTest

Wichtige Richtlinien:
  - Verwende niemals 'gradle clean' routinemäßig (verlangsamt den Folge-Build).
  - Führe nach jeder Änderung den Test-Befehl 'gradle :app:testDebugUnitTest' aus.

--------------------------------------------------------------------------------
5. LIZENZ, TRADEMARKS & OPEN-SOURCE
--------------------------------------------------------------------------------
Der Quellcode von myDocAnizer-Mobile ist lizenziert unter der GNU Affero General
Public License v3.0 (AGPLv3).

Der Name "myDocAnizer", "myDocAnizer-Mobile", Logos, Icons und Play Store
Grafik-Assets sind urheberrechtlich geschütztes Eigentum des Autors (mr.locke84).
Jeder ist berechtigt, den Code einzusehen, zu auditieren und eigene Builds für den
persönlichen Gebrauch zu erstellen. Der offizielle Play Store Vertrieb und das
offizielle Markenbranding verbleiben beim Projektinhaber.
================================================================================
