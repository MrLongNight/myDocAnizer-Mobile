# .github/pull_request_template.md
## 🎯 Beschreibung

<!-- Kurze Zusammenfassung der Änderungen -->
Beschreibe hier, was diese PR ändert und warum.

---

## 🔗 Bezug zu Issues

Fixes #<issue_number> (falls zutreffend)

---

## 📋 Änderungstyp

- [ ] 🐛 **Bugfix** - Fehler behoben
- [ ] ✨ **Feature** - Neue Funktionalität
- [ ] 📚 **Documentation** - Dokumentation
- [ ] ♻️ **Refactoring** - Code-Qualität
- [ ] 🔧 **Configuration** - Build/Config
- [ ] 🧪 **Testing** - Tests
- [ ] 🔐 **Security** - Sicherheits-Update

---

## 🧪 Testverifikation

- [ ] Unit Tests geschrieben/aktualisiert
- [ ] Instrumented Tests bestanden
- [ ] Manuelle Tests auf echtem Gerät durchgeführt
- [ ] Keine neuen Warnungen/Fehler

**Test-Umgebung:**
- Android Version: (z.B. 12, 13, 14, 15)
- Device: (z.B. Pixel 6, Samsung S23, etc.)
- Kotlin: 2.2.10

---

## 🔍 Code Review Checkliste

- [ ] Code folgt Kotlin Style Guide
- [ ] Keine Magic Numbers / hardcodierte Werte
- [ ] Fehlerbehandlung vorhanden
- [ ] Null-Safety beachtet
- [ ] Keine Performance-Probleme
- [ ] Keine neuen Warnungen

**Kotlin Formatting:**
```bash
./gradlew ktlintFormat
```

**Code Quality:**
```bash
./gradlew lint
./gradlew detekt
```

---

## 📊 Qualitätschecks

- [ ] `./gradlew build` erfolgreich
- [ ] `./gradlew test` erfolgreich
- [ ] `./gradlew lint` ohne kritische Fehler
- [ ] `./gradlew ktlintCheck` bestanden

---

## 🔐 Sicherheit & Datenschutz

- [ ] Keine hardcodierten Secrets/API-Keys
- [ ] Keine sensiblen Daten in Logs
- [ ] Verschlüsselung beachtet (AES-256-GCM wo nötig)
- [ ] Keine Telemetrie hinzugefügt
- [ ] Datenschutz eingehalten

---

## 📝 CHANGELOG Update

- [ ] `CHANGELOG.md` aktualisiert mit Eintrag unter `[Unreleased]`

**Format:**
```markdown
## [Unreleased]

### Added
- Neue Feature X

### Fixed
- Bug Y behoben

### Changed
- Verhalten von Z angepasst

### Security
- Sicherheitslücke XYZ geschlossen
```

---

## 📚 Dokumentation

- [ ] README.md aktualisiert (falls notwendig)
- [ ] Code-Kommentare für komplexe Logik hinzugefügt
- [ ] JavaDoc/KDoc für neue APIs
- [ ] Externe Dokumentation aktualisiert

---

## 🚀 Breaking Changes?

- [ ] Nein, vollständig rückwärtskompatibel
- [ ] Ja, und dokumentiert:
  
  ```
  BREAKING CHANGE: Beschreibung hier
  Migration: Anleitung zur Migration
  ```

---

## 📸 Screenshots / Videos (falls UI-Änderungen)

<!-- Paste images or videos here -->

---

## ⚠️ Bekannte Einschränkungen

<!-- Falls es noch offene Punkte gibt -->

---

## 🙏 Zusätzliche Notizen

<!-- Weitere Informationen für den Reviewer -->

---

**Reviewers:** @MrLongNight  
**Assignees:** Selbst zuweisen falls zutreffend
